package com.myapp;

import com.sun.jna.Native;
import com.sun.jna.platform.mac.SystemB;
import com.sun.jna.platform.mac.SystemB.VMStatistics;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.Psapi.PERFORMANCE_INFORMATION;
import com.sun.jna.platform.win32.WinNT.OSVERSIONINFOEX;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Hello world!
 */
public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                throw new RuntimeException("Invalid command line! Parameter \"platform\" is missed!\n" +
                        "Usage:\n" +
                        "java -jar jnatest-1.0-SNAPSHOT.jar win|osx|linux");
            }
            OsStat osStat;
            switch (args[0]) {
                case "win":
                    osStat = new CombinedOsStat(new WindowsOsInfo(), new WindowsMemoryStats());
                    break;
                case "osx":
                    osStat = new MacOSMemoryStats();
                    break;
                case "linux":
                    osStat = new LinuxMemoryStats();
                    break;
                default:
                    throw new RuntimeException("Invalid command line! Parameter \"platform\" should be \"osx\", " +
                            "\"win\" or \"linux\"");
            }
            String res = osStat.stat();
            log.info("-------------------------");
            log.info("{}", res);
        } catch (Throwable e) {
            log.error("Major error:", e);
        }
    }
}

interface OsStat {
    String stat();
}

class CombinedOsStat implements OsStat {
    private Collection<OsStat> metrics;

    public CombinedOsStat(OsStat... metrics) {
        this.metrics = Arrays.asList(requireNonNull(metrics));
    }

    @Override
    public String stat() {
        final StringBuilder res = new StringBuilder();
        metrics.forEach(osStat -> res.append(osStat.stat()).append("\n"));
        return res.toString();
    }
}

class MacOSMemoryStats implements OsStat {
    @Override
    public String stat() {
        VMStatistics vmStats = new VMStatistics();
        if (0 != SystemB.INSTANCE.host_statistics(SystemB.INSTANCE.mach_host_self(), SystemB.HOST_VM_INFO, vmStats,
                new IntByReference(vmStats.size() / SystemB.INT_SIZE))) {
            throw new RuntimeException("Failed to get host VM info. Error code: " + Native.getLastError());
        }
        return "Free pages: " + (vmStats.free_count + vmStats.inactive_count);
    }
}

class WindowsMemoryStats implements OsStat {
    @Override
    public String stat() {
        PERFORMANCE_INFORMATION perfInfo = new PERFORMANCE_INFORMATION();
        if (!Psapi.INSTANCE.GetPerformanceInfo(perfInfo, perfInfo.size())) {
            throw new RuntimeException("Failed to get Performance Info. Error code: " + Kernel32.INSTANCE.GetLastError());
        }
        return "Available memory: " + (perfInfo.PageSize.longValue() * perfInfo.PhysicalAvailable.longValue());
    }
}

class WindowsOsInfo implements OsStat {
    @Override
    public String stat() {
        OSVERSIONINFOEX versionInfo = new OSVERSIONINFOEX();
        if (!Kernel32.INSTANCE.GetVersionEx(versionInfo)) {
            throw new RuntimeException("Failed to Initialize OSVersionInfoEx. Error code: " + Kernel32.INSTANCE.GetLastError());
        }
        return "OS code name: " + parseCodeName(versionInfo.wSuiteMask.intValue())
                + "\nBuild: " + versionInfo.dwBuildNumber.toString();
    }

    private String parseCodeName(int suiteMask) {
        List<String> suites = new ArrayList<>();
        if ((suiteMask & 0x00000002) != 0) {
            suites.add("Enterprise");
        }
        if ((suiteMask & 0x00000004) != 0) {
            suites.add("BackOffice");
        }
        if ((suiteMask & 0x00000008) != 0) {
            suites.add("Communication Server");
        }
        if ((suiteMask & 0x00000080) != 0) {
            suites.add("Datacenter");
        }
        if ((suiteMask & 0x00000200) != 0) {
            suites.add("Home");
        }
        if ((suiteMask & 0x00000400) != 0) {
            suites.add("Web Server");
        }
        if ((suiteMask & 0x00002000) != 0) {
            suites.add("Storage Server");
        }
        if ((suiteMask & 0x00004000) != 0) {
            suites.add("Compute Cluster");
        }
        return String.join(",", suites);
    }
}

class LinuxMemoryStats implements OsStat {
    @Override
    public String stat() {
        final String filename = "/proc/meminfo";
        if (new File(filename).exists()) {
            try {
                List<String> lines = Files.readAllLines(Paths.get(filename), StandardCharsets.UTF_8);
                for (String checkLine : lines) {
                    String[] memorySplit = checkLine.split("\\s+");
                    if (memorySplit.length > 1 && "MemAvailable:".equals(memorySplit[0])) {
                        long memory = 0L;
                        try {
                            return "Available memory: " + memorySplit[1];
                        } catch (NumberFormatException ignored) {
                        }
                        if (memorySplit.length > 2 && "kB".equals(memorySplit[2])) {
                            memory *= 1024;
                        }
                        return "Available memory: " + memory;
                    }
                }
                return "Available memory: Not detected";
            } catch (IOException e) {
                throw new RuntimeException("Error reading file \"/proc/meminfo\"", e);
            }
        }
        throw new RuntimeException("File \"/proc/meminfo\" not found");
    }
}