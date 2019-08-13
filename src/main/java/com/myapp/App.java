package com.myapp;

import com.sun.jna.Native;
import com.sun.jna.platform.mac.SystemB;
import com.sun.jna.platform.mac.SystemB.VMStatistics;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.Psapi.PERFORMANCE_INFORMATION;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

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
            MemInfo memInfo;
            switch (args[0]) {
                case "win":
                    memInfo = new WindowsMemoryStats();
                    break;
                case "osx":
                    memInfo = new MacOSMemoryStats();
                    break;
                case "linux":
                    memInfo = new LinuxMemoryStats();
                    break;
                default:
                    throw new RuntimeException("Invalid command line! Parameter \"platform\" should be \"osx\", " +
                            "\"win\" or \"linux\"");
            }
            Object res = memInfo.stat();
            log.info("-------------------------");
            log.info("{}: {}", memInfo.name(), res);
        } catch (Throwable e) {
            log.error("Major error:", e);
        }
    }
}

interface MemInfo<T> {
    default String name() {
        return "Available memory";
    }
    T stat();
}

class MacOSMemoryStats implements MemInfo<Integer> {
    @Override
    public String name() {
        return "Available pages";
    }

    @Override
    public Integer stat() {
        VMStatistics vmStats = new VMStatistics();
        if (0 != SystemB.INSTANCE.host_statistics(SystemB.INSTANCE.mach_host_self(), SystemB.HOST_VM_INFO, vmStats,
                new IntByReference(vmStats.size() / SystemB.INT_SIZE))) {
            throw new RuntimeException("Failed to get host VM info. Error code: " + Native.getLastError());
        }
        return vmStats.free_count + vmStats.inactive_count;
    }
}

class WindowsMemoryStats implements MemInfo<Long> {
    @Override
    public Long stat() {
        PERFORMANCE_INFORMATION perfInfo = new PERFORMANCE_INFORMATION();
        if (!Psapi.INSTANCE.GetPerformanceInfo(perfInfo, perfInfo.size())) {
            throw new RuntimeException("Failed to get Performance Info. Error code: " + Kernel32.INSTANCE.GetLastError());
        }
        return perfInfo.PageSize.longValue() * perfInfo.PhysicalAvailable.longValue();
    }
}

class LinuxMemoryStats implements MemInfo<Long> {
    @Override
    public Long stat() {
        final String filename = "/proc/meminfo";
        if (new File(filename).exists()) {
            try {
                List<String> lines = Files.readAllLines(Paths.get(filename), StandardCharsets.UTF_8);
                for (String checkLine : lines) {
                    String[] memorySplit = checkLine.split("\\s+");
                    if (memorySplit.length > 1 && "MemAvailable:".equals(memorySplit[0])) {
                        long memory = 0L;
                        try {
                            return Long.parseLong(memorySplit[1]);
                        } catch (NumberFormatException ignored) {
                        }
                        if (memorySplit.length > 2 && "kB".equals(memorySplit[2])) {
                            memory *= 1024;
                        }
                        return memory;
                    }
                }
                return 0L;
            } catch (IOException e) {
                throw new RuntimeException("Error reading file \"/proc/meminfo\"", e);
            }
        }
        throw new RuntimeException("File \"/proc/meminfo\" not found");
    }
}