How to run:

```
mvn clean package
java -Djna.debug_load=true -Djna.debug_load.jna=true -jar target/jnatest-1.0-SNAPSHOT.jar <platform>
```
where `platform` is one of:
* win
* osx
* linux 
