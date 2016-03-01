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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.util;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.caucho.v5.javac.WorkDir;
import com.caucho.v5.jni.JniCauchoSystem;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.vfs.CaseInsensitive;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;

/**
 * A wrapper for Caucho system variables, allowing tests to override
 * the default variables.
 */
public class CauchoUtil {
  private static final Logger log
    = Logger.getLogger(CauchoUtil.class.getName());
  
  static EnvironmentLocal<String> _serverIdLocal
    = new EnvironmentLocal<>("caucho.server-id");
  
  public static int EXIT_BIND = 2;
  public static int EXIT_OOM = 3;
  public static int EXIT_DEADLOCK = 4;
  public static int EXIT_OTHER = 5;

  private static char _separatorChar = File.separatorChar;
  private static char _pathSeparatorChar = File.pathSeparatorChar;
  private static String _localHost;
  private static String _userDir;
  private static String _userName;
  private static PathImpl _rootDirectory;
  private static boolean _isTesting;
  private static boolean _isTestWindows;
  
  private static String _nativeName;

  private static boolean _hasJni;
  
  private static String _version;
  private static long _versionId;
  private static String _fullVersion;
  
  private static String []PROPERTIES_64 = {
     "sun.arch.data.model",
     "com.ibm.vm.bitmodel",
     "os.arch"
  };

  private static int _isUnix = -1;
  private static String _newline;

  private static JniCauchoSystem _jniCauchoSystem;
  private static boolean _isDetailedStatistics;
  private static String _classPath;
  private static ArrayList<String> _classPathList;

  private static PathImpl _homeDir;

  private CauchoUtil()
  {
  }

  /**
   * Returns true if we're currently running a test.
   */
  public static boolean isTesting()
  {
    return _isTesting;
  }

  public static void setIsTesting(boolean testing)
  {
    _isTesting = testing;
  }

  public static String getVersion()
  {
    if (_version == null) {
      _version = Version.getVersion();
    }
    
    return _version;
  }
  
  public static String getFullVersion()
  {
    if (_fullVersion == null) {
      _fullVersion = Version.getFullVersion();
    }
    
    return _fullVersion;
  }
 
  public static long getVersionId()
  {
    if (_versionId == 0) {
      _versionId = Crc64.generate(getVersion());
    }
    
    return _versionId;
  }

  /**
   * Returns a path to the work directory.  The work directory is
   * specified in the conf by /caucho.com/java/work-path.  If
   * unspecified, it defaults to /tmp/caucho.
   *
   * @return directory path to work in.
   */
  public static PathImpl getWorkPath()
  {
    PathImpl workPath = WorkDir.getLocalWorkDir();

    if (workPath != null)
      return workPath;

    PathImpl path = WorkDir.getTmpWorkDir();

    try {
      path.mkdirs();
    } catch (IOException e) {
    }

    return path;
  }

  public static String getServerId()
  {
    return _serverIdLocal.get();
  }

  public static String getUserDir()
  {
    if (_userDir == null)
      _userDir = System.getProperty("user.dir");

    return _userDir;
  }

  public static char getFileSeparatorChar()
  {
    return _separatorChar;
  }

  public static char getPathSeparatorChar()
  {
    return _pathSeparatorChar;
  }

  public static String getNewlineString()
  {
    if (_newline == null) {
      Thread thread = Thread.currentThread();
      ClassLoader oldLoader = thread.getContextClassLoader();

      try {
        thread.setContextClassLoader(ClassLoader.getSystemClassLoader());

        _newline = System.getProperty("line.separator");
        if (_newline != null) {
        }
        else if (isWindows())
          _newline = "\r\n";
        else
          _newline = "\n";
      } finally {
        thread.setContextClassLoader(oldLoader);
      }
    }

    return _newline;
  }

  public static boolean isWindows()
  {
    return _separatorChar == '\\' || _isTestWindows;
  }

  public static boolean isTest()
  {
    return CurrentTime.isTest();
  }

  public static boolean isCaseInsensitive()
  {
    return CaseInsensitive.isCaseInsensitive();
  }

  public static boolean isUnix()
  {
    if (_isUnix >= 0)
      return _isUnix == 1;

    _isUnix = 0;

    if (_separatorChar == '/' && VfsOld.lookup("/bin/sh").canRead())
      _isUnix = 1;

    return _isUnix == 1;
  }

  public static void setWindowsTest(boolean isWindows)
  {
    _isTesting = true;
    _isTestWindows = isWindows;
    PathImpl.setTestWindows(isWindows);
  }

  public static String getLocalHost()
  {
    if (_localHost != null)
      return _localHost;

    try {
      InetAddress addr = InetAddress.getLocalHost();
      _localHost = addr.getHostName();
    } catch (Exception e) {
      _localHost = "127.0.0.1";
    }

    return _localHost;
  }

  public static String getUserName()
  {
    if (_userName == null)
      _userName = System.getProperty("user.name");

    return _userName;
  }

  /**
   * Set true to cause the tracking of detailed statistcs, default false.
   * Detailed statistics cause various parts of Baratine to keep more detailed
   * statistics at the possible expense of performance.
   */
  public static void setDetailedStatistics(boolean isVerboseStatistics)
  {
    _isDetailedStatistics = isVerboseStatistics;
  }

  /**
   * Detailed statistics cause various parts of the server to keep more detailed
   * statistics at the possible expense of some performance.
   */
  public static boolean isDetailedStatistics()
  {
    return _isDetailedStatistics;
  }

  /**
   * Loads a class from the context class loader.
   *
   * @param name the classname, separated by '.'
   *
   * @return the loaded class.
   */
  public static Class<?> loadClass(String name)
    throws ClassNotFoundException
  {
    return loadClass(name, false, null);
  }

  /**
   * Loads a class from a classloader.  If the loader is null, uses the
   * context class loader.
   *
   * @param name the classname, separated by '.'
   * @param init if true, resolves the class instances
   * @param loader the class loader
   *
   * @return the loaded class.
   */
  public static Class<?> loadClass(String name, boolean init, ClassLoader loader)
    throws ClassNotFoundException
  {
    if (loader == null)
      loader = Thread.currentThread().getContextClassLoader();

    if (loader == null || loader.equals(CauchoUtil.class.getClassLoader()))
      return Class.forName(name);
    else
      return Class.forName(name, init, loader);
  }

  /**
   * Returns the system classpath, including the bootpath
   */
  public static String getClassPath()
  {
    if (_classPath != null)
      return _classPath;

    String cp = System.getProperty("java.class.path");

    String boot = System.getProperty("sun.boot.class.path");
    if (boot != null && ! boot.equals(""))
      cp = cp + File.pathSeparatorChar + boot;

    Pattern pattern = Pattern.compile("" + File.pathSeparatorChar);

    String []path = pattern.split(cp);

    CharBuffer cb = new CharBuffer();

    for (int i = 0; i < path.length; i++) {
      PathImpl subpath = VfsOld.lookup(path[i]);

      if (subpath.canRead() || subpath.isDirectory()) {
        if (cb.length() > 0)
          cb.append(File.pathSeparatorChar);

        cb.append(path[i]);
      }
    }

    _classPath = cb.toString();

    return _classPath;
  }

  public static PathImpl getHomeDir()
  {
    if (_homeDir == null) {
      String homeDirName = System.getProperty("home.dir");
      
      if (homeDirName != null) {
        _homeDir = VfsOld.lookup(homeDirName);
        
        return _homeDir;
      }
      
      ClassLoader loader = CauchoUtil.class.getClassLoader();
      
      if (loader == null) {
        loader = ClassLoader.getSystemClassLoader();
      }

      String className = CauchoUtil.class.getName().replace('.', '/') + ".class";
      URL url = loader.getResource(className);

      String path = String.valueOf(url);
          
      if (url == null) {
        _homeDir = VfsOld.lookup();
      }
      else if (path.startsWith("jar:")) {
        int p = path.indexOf(':');
        int q = path.indexOf('!');

        path = path.substring(p + 1, q);

        PathImpl pwd = VfsOld.lookup(path).getParent().getParent();
        
        _homeDir = pwd;
      }
      else if (path.startsWith("file:")) {
        path = path.substring(0, path.length() - className.length());
        
        int p = path.indexOf("/modules/baratine");
        
        if (p > 0) {
          path = path.substring(0, p);
        }
        
        if (path.endsWith("/build/classes/")) {
          path = path.substring(0, path.length() - "/build/classes/".length());
        }

        PathImpl pwd = VfsOld.lookup(path);
        
        _homeDir = pwd;
      }
      else {
        _homeDir = VfsOld.lookup();
      }
    }
    
    return _homeDir;
  }
  

  /**
   * Returns the system classpath, including the bootpath
   */
  public static ArrayList<String> getClassPathList()
  {
    if (_classPathList != null)
      return _classPathList;

    ArrayList<String> list = new ArrayList<String>();

    String classPath = getClassPath();
    String []classPathArray
      = classPath.split("[" + getPathSeparatorChar() + "]");

    for (int i = 0; i < classPathArray.length; i++) {
      if (! list.contains(classPathArray[i]))
        list.add(classPathArray[i]);
    }

    _classPathList = list;

    return _classPathList;
  }

  public static void exitOom(Class<?> cl, Throwable e)
  {
    try {
      System.err.println(cl + " restarting due to OutOfMemoryError " + e);
      ThreadDump.create().dumpThreads();
    } finally {
      Runtime.getRuntime().halt(EXIT_OOM);
    }
  }

  /**
   * @return
   */
  public static boolean is64Bit()
  {
    for (String prop : PROPERTIES_64) {
      String value = System.getProperty(prop);

      if (value != null && value.indexOf("64") >= 0) {
        return true;
      }
    }
    
    return false;
  }
}
