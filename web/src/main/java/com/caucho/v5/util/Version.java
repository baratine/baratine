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

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

final public class Version
{
  private static final Logger log = Logger.getLogger(Version.class.getName());
  
  private static String _version = "unknown";
  private static String _fullVersion = "unknown";
  private static String _versionDate = "unknown";

  public static final String COPYRIGHT =
    "Copyright(c) 1998-2016 Caucho Technology. All rights reserved.";

  public static String getFullVersion()
  {
    return _fullVersion;
  }

  public static String getVersion()
  {
    return _version;
  }

  public static String getVersionDate()
  {
    return _versionDate;
  }

  public static String getCopyright()
  {
    return COPYRIGHT;
  }
  
  private static String readLine(InputStream is)
    throws IOException
  {
    StringBuilder sb = new StringBuilder();
    int ch;
    
    if (is == null) {
      return null;
    }
    
    while ((ch = is.read()) > 0 && ch != '\n') {
      sb.append((char) ch);
    }
    
    if (sb.length() > 0 || ch > 0) {
      return sb.toString();
    }
    else {
      return null;
    }
  }

  static {
    Package pkg = Version.class.getPackage();
    
    String programName = pkg.getImplementationTitle();
    
    if (programName == null) {
      programName = "Baratine";
    }
    
    _version = pkg.getImplementationVersion();
    
    if (_version == null) {
      _version = "0.11-SNAPSHOT";
    }
    
    _fullVersion = programName + "-" + _version;

    /*
    try (InputStream is = Version.class.getResourceAsStream("/META-INF/caucho/version")) {
      String line;
      
      while ((line = readLine(is)) != null) {
        line = line.trim();
        
        int p = line.indexOf(':');
        
        if (p < 0) {
          continue;
        }

        String key = line.substring(0, p).trim();
        String value = line.substring(p + 1).trim();
        
        switch (key) {
        case "version":
          _version = value;
          break;
          
        case "full_version":
          _fullVersion = value;
          break;
        
        case "version_date":
          _versionDate = value;
          break;
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      log.log(Level.FINER, e.toString(), e);
    }
    */
  }
}
