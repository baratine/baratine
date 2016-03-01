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
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * A wrapper for Caucho system variables, allowing tests to override
 * the default variables.
 */
public class HomeUtil {

  private static Path _homeDir;

  public static Path getHomeDir()
  {
    if (_homeDir == null) {
      String homeDirName = System.getProperty("home.dir");
      
      if (homeDirName != null) {
        _homeDir = Paths.get(homeDirName);
        
        return _homeDir;
      }
      
      ClassLoader loader = HomeUtil.class.getClassLoader();
      
      if (loader == null) {
        loader = ClassLoader.getSystemClassLoader();
      }

      String className = HomeUtil.class.getName().replace('.', '/') + ".class";
      URL url = loader.getResource(className);

      String path = String.valueOf(url);
          
      if (url == null) {
        _homeDir = Paths.get(".");
      }
      else if (path.startsWith("jar:")) {
        int p = path.indexOf(':');
        int q = path.indexOf('!');

        path = path.substring(p + 1, q);

        Path pwd = Paths.get(path).getParent().getParent();
        
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

        Path pwd = Paths.get(path);
        
        _homeDir = pwd;
      }
      else {
        //_homeDir = Paths.get(".");
      }
      
      if (_homeDir == null) {
        _homeDir = Paths.get(".");
      }
    }
    
    return _homeDir;
  }

  public static boolean isUnix()
  {
    return File.separatorChar == '/';
  }

  public static boolean isWindows()
  {
    return File.separatorChar == '\\';
  }
}
