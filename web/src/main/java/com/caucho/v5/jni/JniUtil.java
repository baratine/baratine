/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Baratine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Baratine; if not, write to the
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.util.HomeUtil;

/**
 * JNI calls for misc system utilities.
 */
public class JniUtil {
  private static final Logger log = Logger.getLogger(JniUtil.class.getName());
  private static final Semaphore _lock = new Semaphore(1);
  private static String _nativeName;
  
  public static final void acquire()
  {
    try {
      _lock.acquire();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public static final void release()
  {
    _lock.release();
  }

  public static final JniTroubleshoot load(Class<?> cl, 
                                           JniLoad load,
                                           String ...names)
  {
    String libName = names[0];
    
    JniTroubleshoot jniTroubleshoot
      = new JniTroubleshoot(cl, libName, false);

    // must be outside the JniUtil.acquire()
    // Throwable fileStreamDisableCause = JniFileStream.getDisableCause();
    
    JniUtil.acquire();
    try {
      "test".getBytes(System.getProperty("file.encoding"));
      
      boolean isValid = false;
      for (String path : JniUtil.getNativePaths(names)) {
        try {
          if (! isValid) {
            load.load(path);
            
            isValid = true;
            
            jniTroubleshoot = new JniTroubleshoot(cl, libName);
          }
        } catch (Throwable e) {
          jniTroubleshoot = new JniTroubleshoot(cl, libName, e);
        }
      }
      /*
      if (jniTroubleshoot.isEnabled() && ! JniFileStream.isEnabled()) {
        jniTroubleshoot.disable(fileStreamDisableCause);
      }
      */
    }
    catch (Throwable e) {
      jniTroubleshoot = new JniTroubleshoot(cl, libName, e);
    } finally {
      JniUtil.release();
    }

    return jniTroubleshoot;
  }

  public static String getLibraryPath(String libraryName)
  {
    String tail = System.mapLibraryName(libraryName);
    
    boolean is64bit = true; // CauchoUtil.is64Bit();
    Path homeDir = HomeUtil.getHomeDir();
    
    char sep = File.separatorChar;
    char pathSep = File.pathSeparatorChar;
    
    String javaLibraryPath = System.getProperty("java.library.path");
    if (javaLibraryPath != null) {
      String []paths = javaLibraryPath.split("[" + pathSep + "]");
      
      for (String path : paths) {
        if (path.equals("")) {
          continue;
        }
        
        String testPath = path + sep + tail;
        
        if (new File(testPath).canRead()) {
          return testPath;
        }
      }
    }
    
    String nativeName = getNativeName();
    
    if (nativeName == null) {
      return null;
    }
    
    Path libexec = homeDir.resolve("native").resolve(nativeName);
    
    Path lookupPath = libexec.resolve(tail);
    
    if (Files.isReadable(lookupPath)) {
      try {
        return lookupPath.toRealPath().toString();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      if (loader == null) {
        loader = ClassLoader.getSystemClassLoader();
      }
      
      String tempPath = System.getProperty("java.io.tmpdir") + "/" + tail;
      
      Path path = Paths.get(tempPath);
      
      if (Files.isReadable(path)) {
        return path.toRealPath().toString();
      }
      
      String resourceName = "com/caucho/native/" + nativeName + "/" + tail;
      
      InputStream is = loader.getResourceAsStream(resourceName);
      
      if (is != null) {
        return extractJniToTempFile(is, path);
      }
      
      return lookupPath.toRealPath().toString();
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
  
  public static String []getNativePaths(String ...libraryNames)
  {
    try {
      ArrayList<String> paths = new ArrayList<>();
    
      Path dstPath = getNativeExpand();
    
      Path srcPath;
      
      Path homeDir = HomeUtil.getHomeDir();
      
      srcPath = homeDir.resolve("native");
      
      getNativePaths(paths, srcPath, dstPath, libraryNames, false);
      
      srcPath = getNativeJarPath();
    
      getNativePaths(paths, srcPath, dstPath, libraryNames, true);
    
      String []list = new String[paths.size()];
      paths.toArray(list);
      
      return list;
    } catch (Exception e) {
      log.finer("JNI loading exceptin: " + e);
      log.log(Level.FINEST, e.toString(), e);
      
      return new String[0];
    }
  }
    
  private static void getNativePaths(ArrayList<String> paths,
                                     Path srcPath,
                                     Path dstPath,
                                     String []libraryNames,
                                     boolean isExtract)
     throws IOException
  {
    String nativeName = getNativeName();
    
    if (nativeName == null) {
      return;
    }
    
    if (srcPath == null) {
      return;
    }
    
    
    for (String libraryName : libraryNames) {
      String tail = System.mapLibraryName(libraryName);
    
      ArrayList<String> osList = new ArrayList<>();
      
      try (DirectoryStream<Path> dir = Files.newDirectoryStream(srcPath)) {
        for (Path osDir : dir) {
          String osName = osDir.getFileName().toString();
          
          if (! osName.startsWith(nativeName)) {
            continue;
          }
        
          if (Files.isReadable(srcPath.resolve(tail))) {
            osList.add(osName);
          }
        }
      }
      
      Collections.sort(osList, new VersionComparator());
      
      for (String osName : osList) {
        Path srcPathFile = srcPath.resolve(osName).resolve(tail);
        Path dstPathFile = dstPath.resolve(osName).resolve(tail);
        
        if (isExtract) {
          extractPath(srcPathFile, dstPathFile);
        
          paths.add(dstPathFile.toRealPath().toString());
        }
        else {
          paths.add(srcPathFile.toRealPath().toString());
        }
      }
      
      if (paths.size() == 0) {
        paths.add(srcPath.resolve(nativeName).resolve(tail).toRealPath().toString());
      }
    }
    
  }
  
  private static Path getNativeJarPath()
  {
    String nativeName = getNativeName();
    
    if (nativeName == null) {
      return null;
    }
  
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) {
      loader = ClassLoader.getSystemClassLoader();
    }
    
    URL url = loader.getResource("com/caucho/native/");
    
    if (url == null) {
      return null;
    }
    
    try {
      Path srcPath = Paths.get(url.toURI());
    
      return srcPath;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  private static Path getNativeExpand()
  {
    String tmpdir = System.getProperty("java.io.tmpdir");
    String user = System.getProperty("user.name");
    String tempPath = tmpdir + "/" + user + "/com/caucho/native";
    
    return Paths.get(tempPath);
  }
  
  private static String extractJniToTempFile(InputStream is, Path path)
    throws IOException
  {
    Files.createDirectories(path.getParent());
    
    Files.copy(is, path);
    /*
    try (WriteStream os = path.openWrite()) {
      os.writeStream(is);
    }
    */
    
    return path.toRealPath().toString();
  }
  
  private static void extractPath(Path srcPath, Path dstPath)
    throws IOException
  {
    /*
    if (dstPath.canRead()
        && dstPath.getCrc64() == srcPath.getCrc64()) {
      return;
    }
    */
    
    Files.createDirectories(dstPath.getParent());
    
    Files.copy(srcPath, dstPath);
    /*
    try (WriteStream os = dstPath.openWrite()) {
      os.writeFile(srcPath);
    }
    */
  }
  
  public static String getNativeName()
  {
    String nativeName = _nativeName;
    
    if (nativeName != null) {
      return _nativeName;
    }
    
    String arch = System.getProperty("os.arch");
    
    switch (arch) {
    case "amd64":
      arch = "x86_64";
      break;
    }
    
    String osName = System.getProperty("os.name");
    
    switch (osName) {
    case "Mac OS X":
      osName = "macosx";
      break;
    case "Linux":
      osName = "linux";
      break;
    }
    
    nativeName = osName + '-' + arch;
    
    _nativeName = nativeName;
    
    return nativeName;
  }
  
  private static String readLinuxVersion()
  {
    return "linux";
    
    /*
    try (InputStream is = new FileInputStream("/etc/redhat-release")) {
      ReadStream in = Vfs.openRead(is);
      
      String line;
      
      while ((line = in.readLine()) != null) {
        String []split = line.split("[\\s+]");
        
        if (split != null && split.length > 2) {
          return "rh-" + split[2];
        }
      }
    } catch (IOException e) {
      log.log(Level.ALL, e.toString(), e);
    }
    
    return "Linux";
    */
  }
  
  private static class VersionComparator implements Comparator<String>
  {
    @Override
    public int compare(String a, String b)
    {
      String tailA = getTail(a);
      String tailB = getTail(b);
      
      String []splitA = tailA.split("\\.");
      String []splitB = tailB.split("\\.");
      
      for (int i = 0; i < Math.min(splitA.length, splitB.length); i++) {
        int cmp = splitB[i].length() - splitA[i].length();
        
        if (cmp != 0) {
          return cmp;
        }
        
        cmp = compareSegment(splitB[i], splitA[i]);
        
        if (cmp != 0) {
          return cmp;
        }
      }
      
      return splitB.length - splitA.length;
    }
    
    private int compareSegment(String a, String b)
    {
      try {
        int aValue = Integer.parseInt(a);
        int bValue = Integer.parseInt(b);
        
        return aValue - bValue;
      } catch (Exception e) {
        return a.compareTo(b);
      }
    }
    
    private String getTail(String s)
    {
      int p = s.lastIndexOf('-');
      
      if (p > 0) {
        return s.substring(p + 1);
      }
      else {
        return s;
      }
    }
  }
  
  public static interface JniLoad {
    void load(String path);
  }
}
