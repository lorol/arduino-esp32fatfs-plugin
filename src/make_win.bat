del bin\*.*
"C:\Program Files (x86)\Java\jdk1.8.0_152\bin\javac.exe" -target 1.8 -cp ".;arduino-core.jar;commons-codec-1.7.jar;pde.jar" -d bin ESP32FatFS.java
cd bin
"C:\Program Files (x86)\Java\jdk1.8.0_152\bin\jar.exe" cvfM esp32fatfs.jar *
pause
