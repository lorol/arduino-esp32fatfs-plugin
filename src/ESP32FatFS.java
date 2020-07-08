/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Tool to put the contents of the sketch's "data" subfolder
  into an FatFS partition image and upload it to an ESP32 MCU

  Copyright (c) 2015 Hristo Gochkov (hristo at espressif dot com)

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package com.esp32.mkfatfs;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JOptionPane;

import processing.app.PreferencesData;
import processing.app.Editor;
import processing.app.Base;
import processing.app.BaseNoGui;
import processing.app.Platform;
import processing.app.Sketch;
import processing.app.tools.Tool;
import processing.app.helpers.ProcessUtils;
import processing.app.debug.TargetPlatform;

import org.apache.commons.codec.digest.DigestUtils;
import processing.app.helpers.FileUtils;

import cc.arduino.files.DeleteFilesOnShutdown;

/**
 * Example Tools menu entry.
 */
public class ESP32FatFS implements Tool {
  Editor editor;


  public void init(Editor editor) {
    this.editor = editor;
  }


  public String getMenuTitle() {
    return "ESP32 FatFS Data Upload";
  }

  private int listenOnProcess(String[] arguments){
      try {
        final Process p = ProcessUtils.exec(arguments);
        Thread thread = new Thread() {
          public void run() {
            try {
              InputStreamReader reader = new InputStreamReader(p.getInputStream());
              int c;
              while ((c = reader.read()) != -1)
                  System.out.print((char) c);
              reader.close();
              
              reader = new InputStreamReader(p.getErrorStream());
              while ((c = reader.read()) != -1)
                  System.err.print((char) c);
              reader.close();
            } catch (Exception e){}
          }
        };
        thread.start();
        int res = p.waitFor();
        thread.join();
        return res;
      } catch (Exception e){
        return -1;
      }
    }

  private void sysExec(final String[] arguments){
    Thread thread = new Thread() {
      public void run() {
        try {
          if(listenOnProcess(arguments) != 0){
            editor.statusError("FatFS Upload failed!");
          } else {
            editor.statusNotice("FatFS Image Uploaded");
          }
        } catch (Exception e){
          editor.statusError("FatFS Upload failed!");
        }
      }
    };
    thread.start();
  }

  private String getBuildFolderPath(Sketch s) {
    // first of all try the getBuildPath() function introduced with IDE 1.6.12
    // see commit arduino/Arduino#fd1541eb47d589f9b9ea7e558018a8cf49bb6d03
    try {
      String buildpath = s.getBuildPath().getAbsolutePath();
      return buildpath;
    }
    catch (IOException er) {
       editor.statusError(er);
    }
    catch (Exception er) {
      try {
        File buildFolder = FileUtils.createTempFolder("build", DigestUtils.md5Hex(s.getMainFilePath()) + ".tmp");
        return buildFolder.getAbsolutePath();
      }
      catch (IOException e) {
        editor.statusError(e);
      }
      catch (Exception e) {
        // Arduino 1.6.5 doesn't have FileUtils.createTempFolder
        // String buildPath = BaseNoGui.getBuildFolder().getAbsolutePath();
        java.lang.reflect.Method method;
        try {
          method = BaseNoGui.class.getMethod("getBuildFolder");
          File f = (File) method.invoke(null);
          return f.getAbsolutePath();
        } catch (SecurityException ex) {
          editor.statusError(ex);
        } catch (IllegalAccessException ex) {
          editor.statusError(ex);
        } catch (InvocationTargetException ex) {
          editor.statusError(ex);
        } catch (NoSuchMethodException ex) {
          editor.statusError(ex);
        }
      }
    }
    return "";
  }

  private long parseInt(String value){
    if(value.startsWith("0x")) return Long.parseLong(value.substring(2), 16);
    else return Integer.parseInt(value);
  }

  private long getIntPref(String name){
    String data = BaseNoGui.getBoardPreferences().get(name);
    if(data == null || data.contentEquals("")) return 0;
    return parseInt(data);
  }

  private void createAndUpload(){
    long spiStart = 0, spiSize = 0, spiPage = 256, spiBlock = 4096, spiOffset = 4096;
    String partitions = "";
    
    if(!PreferencesData.get("target_platform").contentEquals("esp32")){
      System.err.println();
      editor.statusError("FatFS Not Supported on "+PreferencesData.get("target_platform"));
      return;
    }

    TargetPlatform platform = BaseNoGui.getTargetPlatform();

    String toolExtension = ".py";
    if(PreferencesData.get("runtime.os").contentEquals("windows")) {
      toolExtension = ".exe";
    } else if(PreferencesData.get("runtime.os").contentEquals("macosx")) {
      toolExtension = "";
    }

    String pythonCmd;
    if(PreferencesData.get("runtime.os").contentEquals("windows"))
        pythonCmd = "python.exe";
    else
        pythonCmd = "python";
    
    String mkspiffsCmd;
    if(PreferencesData.get("runtime.os").contentEquals("windows"))
        mkspiffsCmd = "mkfatfs.exe";
    else
        mkspiffsCmd = "mkfatfs";

    String espotaCmd = "espota.py";
    if(PreferencesData.get("runtime.os").contentEquals("windows"))
        espotaCmd = "espota.exe";
    
    Boolean isNetwork = false;
    File espota = new File(platform.getFolder()+"/tools");
    File esptool = new File(platform.getFolder()+"/tools");
    String serialPort = PreferencesData.get("serial.port");

    if(!BaseNoGui.getBoardPreferences().containsKey("build.partitions")){
      System.err.println();
      editor.statusError("Partitions Not Defined for "+BaseNoGui.getBoardPreferences().get("name"));
      return;
    }
    
    try {
      partitions = BaseNoGui.getBoardPreferences().get("build.partitions");
      if(partitions == null || partitions.contentEquals("")){
        editor.statusError("Partitions Not Found for "+BaseNoGui.getBoardPreferences().get("name"));
        return;
      }
    } catch(Exception e){
      editor.statusError(e);
      return;
    }

    File partitionsFile = new File(platform.getFolder() + "/tools/partitions", partitions + ".csv");
    if (!partitionsFile.exists() || !partitionsFile.isFile()) {
      System.err.println();
      editor.statusError("FatFS Error: partitions file " + partitions + ".csv not found!");
      return;
    }

    try {
      BufferedReader partitionsReader = new BufferedReader(new FileReader(partitionsFile));
      String partitionsLine = "";
      while ((partitionsLine = partitionsReader.readLine()) != null) {
        if(partitionsLine.contains("ffat")) {
          partitionsLine = partitionsLine.substring(partitionsLine.indexOf(",")+1);
          partitionsLine = partitionsLine.substring(partitionsLine.indexOf(",")+1);
          partitionsLine = partitionsLine.substring(partitionsLine.indexOf(",")+1);
          while(partitionsLine.startsWith(" ")) partitionsLine = partitionsLine.substring(1);
          String pStart = partitionsLine.substring(0, partitionsLine.indexOf(","));
          partitionsLine = partitionsLine.substring(partitionsLine.indexOf(",")+1);
          while(partitionsLine.startsWith(" ")) partitionsLine = partitionsLine.substring(1);
          String pSize = partitionsLine.substring(0, partitionsLine.indexOf(","));
          spiStart = parseInt(pStart) + spiOffset;
          spiSize = parseInt(pSize) - spiOffset;
        }
      }
      if(spiSize == 0){
        System.err.println();
        editor.statusError("FatFS Error: partition size could not be found!");
        return;
      }
    } catch(Exception e){
      editor.statusError(e);
      return;
    }

    File tool = new File(platform.getFolder() + "/tools", mkspiffsCmd);
    if (!tool.exists() || !tool.isFile()) {
      tool = new File(platform.getFolder() + "/tools/mkfatfs", mkspiffsCmd);
      if (!tool.exists()) {
        tool = new File(PreferencesData.get("runtime.tools.mkfatfs.path"), mkspiffsCmd);
        if (!tool.exists()) {
            System.err.println();
            editor.statusError("FatFS Error: mkfatfs not found!");
            return;
        }
      }
    }
	System.out.println("mkfatfs : " + tool.getAbsolutePath());
	System.out.println();

    //make sure the serial port or IP is defined
    if (serialPort == null || serialPort.isEmpty()) {
      System.err.println();
      editor.statusError("FatFS Error: serial port not defined!");
      return;
    }

    //find espota if IP else find esptool
    if(serialPort.split("\\.").length == 4){
      isNetwork = true;
      espota = new File(platform.getFolder()+"/tools", espotaCmd);
      if(!espota.exists() || !espota.isFile()){
        System.err.println();
        editor.statusError("FatFS Error: espota not found!");
        return;
      }
	  System.out.println("espota : "+espota.getAbsolutePath());
      System.out.println();	
    } else {
      String esptoolCmd = "esptool"+toolExtension;
      esptool = new File(platform.getFolder()+"/tools", esptoolCmd);
      if(!esptool.exists() || !esptool.isFile()){
        esptool = new File(platform.getFolder()+"/tools/esptool_py", esptoolCmd);
        if(!esptool.exists()){
          esptool = new File(PreferencesData.get("runtime.tools.esptool_py.path"), esptoolCmd);
          if (!esptool.exists()) {
              System.err.println();
              editor.statusError("FatFS Error: esptool not found!");
              return;
          }
        }
      }
	  System.out.println("esptool : "+esptool.getAbsolutePath());
      System.out.println();	
    }
    
    //load a list of all files
    int fileCount = 0;
    File dataFolder = new File(editor.getSketch().getFolder(), "data");
    if (!dataFolder.exists()) {
        dataFolder.mkdirs();
    }
    if(dataFolder.exists() && dataFolder.isDirectory()){
      File[] files = dataFolder.listFiles();
      if(files.length > 0){
        for(File file : files){
          if((file.isDirectory() || file.isFile()) && !file.getName().startsWith(".")) fileCount++;
        }
      }
    }

    String dataPath = dataFolder.getAbsolutePath();
    String toolPath = tool.getAbsolutePath();
    String sketchName = editor.getSketch().getName();
    String imagePath = getBuildFolderPath(editor.getSketch()) + "/" + sketchName + ".fatfs.bin";
    String uploadSpeed = BaseNoGui.getBoardPreferences().get("upload.speed");

    Object[] options = { "Yes", "No" };
    String title = "FatFS Create";
    String message = "No files have been found in your data folder!\nAre you sure you want to create an empty FatFS image?";

    if(fileCount == 0 && JOptionPane.showOptionDialog(editor, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]) != JOptionPane.YES_OPTION){
      System.err.println();
      editor.statusError("FatFS Warning: mkfatfs canceled!");
      return;
    }

    editor.statusNotice("FatFS Creating Image...");
    System.out.println("[FatFS] data   : "+dataPath);
	System.out.println("[FatFS] offset : "+spiOffset);
    System.out.println("[FatFS] start  : "+spiStart);
    System.out.println("[FatFS] size   : "+(spiSize/1024));
	//System.out.println("[FatFS] page   : "+spiPage);
    //System.out.println("[FatFS] block  : "+spiBlock);


    try {
	  //if(listenOnProcess(new String[]{toolPath, "-c", dataPath, "-p", spiPage+"", "-b", spiBlock+"", "-s", spiSize+"", imagePath}) != 0){
		if(listenOnProcess(new String[]{toolPath, "-c", dataPath, "-s", spiSize+"", imagePath}) != 0){

        System.err.println();
        editor.statusError("FatFS Create Failed!");
        return;
      }
    } catch (Exception e){
      editor.statusError(e);
      editor.statusError("FatFS Create Failed!");
      return;
    }

    editor.statusNotice("FatFS Uploading Image...");
    System.out.println("[FatFS] upload : "+imagePath);

    if(isNetwork){
      System.out.println("[FatFS] IP     : "+serialPort);
      System.out.println();
      if(espota.getAbsolutePath().endsWith(".py"))
        sysExec(new String[]{pythonCmd, espota.getAbsolutePath(), "-i", serialPort, "-p", "3232", "-s", "-f", imagePath});
      else
        sysExec(new String[]{espota.getAbsolutePath(), "-i", serialPort, "-p", "3232", "-s", "-f", imagePath});
    } else {
      String flashMode = BaseNoGui.getBoardPreferences().get("build.flash_mode");
      String flashFreq = BaseNoGui.getBoardPreferences().get("build.flash_freq");
      System.out.println("[FatFS] address: "+spiStart);
      System.out.println("[FatFS] port   : "+serialPort);
      System.out.println("[FatFS] speed  : "+uploadSpeed);
      System.out.println("[FatFS] mode   : "+flashMode);
      System.out.println("[FatFS] freq   : "+flashFreq);
      System.out.println();
      if(esptool.getAbsolutePath().endsWith(".py"))
        sysExec(new String[]{pythonCmd, esptool.getAbsolutePath(), "--chip", "esp32", "--baud", uploadSpeed, "--port", serialPort, "--before", "default_reset", "--after", "hard_reset", "write_flash", "-z", "--flash_mode", flashMode, "--flash_freq", flashFreq, "--flash_size", "detect", ""+spiStart, imagePath});
      else
        sysExec(new String[]{esptool.getAbsolutePath(), "--chip", "esp32", "--baud", uploadSpeed, "--port", serialPort, "--before", "default_reset", "--after", "hard_reset", "write_flash", "-z", "--flash_mode", flashMode, "--flash_freq", flashFreq, "--flash_size", "detect", ""+spiStart, imagePath});
    }
  }

  public void run() {
    createAndUpload();
  }
}
