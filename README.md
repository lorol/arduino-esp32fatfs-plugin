# arduino-esp32fatfs-plugin
## Arduino ESP32 FatFS filesystem uploader 

- Arduino plugin, which packs sketch <b>data</b> folder into FatFS filesystem image and uploads the image to ESP32 flash memory
- Identical to the [original one for SPIFFS](https://github.com/me-no-dev/arduino-esp32fs-plugin/)

## Notes

- You need to select on Arduino IDE *Tools > Partition Scheme* menu a choice with FAT partition
- The usable size of FAT partition is reduced with 1 sector of 4096 bytes (0x1000) to resolve wear leveling space requirement
- For same reason, the image file is flashed with +4096 bytes (0x1000) offset of partition address csv table entry

## Installation

- Make sure you use one of the supported versions of Arduino IDE and have ESP32 core installed.
- Download the tool archive from [here](https://github.com/lorol/arduino-esp32fatfs-plugin/raw/master/src/bin/esp32fatfs.jar)
- In your Arduino sketchbook directory, create tools directory if it doesn't exist yet.
- Unpack the tool into tools directory (the path will look like ```<home_dir>/Arduino/tools/ESP32FatFS/tool/esp32fatfs.jar```).
- You need an executable to create the image. See in [extra folder](https://github.com/lorol/arduino-esp32fatfs-plugin/tree/master/extra) a too for Win or take it from the author [here - mkfatfs tool](https://github.com/labplus-cn/mkfatfs/releases/tag/v1.0)  Thanks to [labplus-cn](https://github.com/labplus-cn/mkfatfs)
- You can adapt it to use other fatfs image-creating tools, like [ESP32_fatfsimage](https://github.com/marcmerlin/esp32_fatfsimage)  w/ binary for Linux, you need to change the parameters example size is /1024
- Restart Arduino IDE. 

## Usage

- Open a sketch (or create a new one and save it).
- Go to sketch directory (choose Sketch > Show Sketch Folder).
- Create a directory named `data` and any files you want in the file system there.
- Make sure you have selected a board, port, and closed Serial Monitor.
- Select *Tools > ESP32 FatFS Data Upload* menu item. This should start uploading the files into ESP32 flash file system.

  When done, IDE status bar will display FatFS Image Uploaded message. Might take a few minutes for large file system sizes.
  
## Screenshot

![Screenshot](tool.png)

## Credits and license

- This work is based on the [original tool](https://github.com/me-no-dev/arduino-esp32fs-plugin/ ) Copyright (c) 2015 Hristo Gochkov (hristo at espressif dot com)
- Licensed under GPL v2 ([text](LICENSE))

## Quick build on Win:

- Install Java JDK 
- Find the path of javac.exe and jar.exe
- Edit make_win.bat accordingly
- Copy files <b>arduino-core.jar , commons-codec-1.7.jar , pde.jar</b>  from your Arduino IDE installation to the folder where is located <b>make_win.bat</b>
- Run <b>make_win.bat</b>
- Find the <b>build jar</b> in /bin directory 
