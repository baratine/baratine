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

package com.caucho.v5.jni;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.util.HomeUtil;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.ModulePrivate;

/**
 * Common error management for JNI loading
 */
@ModulePrivate
public class JniTroubleshoot {
  private static final Logger log
    = Logger.getLogger(JniTroubleshoot.class.getName());
  private static final L10N L = new L10N(JniTroubleshoot.class);

  private static final HashSet<String> _loggedLibraries = new HashSet<String>();

  private String _className;
  private String _libraryName;

  private Throwable _cause;

  private boolean _isValid;

  public JniTroubleshoot(Class<?> cl, String libraryName, boolean isValid)
  {
    _className = cl.getName();
    _libraryName = libraryName;
    _isValid = isValid;
  }

  public JniTroubleshoot(Class<?> cl, String libraryName)
  {
    this(cl, libraryName, true);
  }

  public JniTroubleshoot(Class<?> cl, String libraryName, Throwable cause)
  {
    this(cl, libraryName, false);

    _cause = cause;
  }

  public void log()
  {
    if (! _isValid && EnvLoader.isLoggingInitialized()) {
      boolean isLogged = false;

      synchronized (_loggedLibraries) {
        isLogged = _loggedLibraries.contains(_libraryName);

        if (! isLogged)
          _loggedLibraries.add(_libraryName);
      }
      
      if (! isLogged) {
        if (log.isLoggable(Level.FINEST) && _cause != null) {
          log.log(Level.FINEST, getMessage(), _cause);
        }
        else {
          log.config(getMessage());
        }
      }
    }
  }

  public String getMessage()
  {
    Path lib = getLib();

    if (! Files.exists(lib)) {
      return L.l("Unable to find native library '{0}' for {1}. "
                 + "Baratine expects to find this library in: {2}\n"
                 + "The JVM exception was: {3}\n",
                 _libraryName, _className, lib, _cause);
    }
    else {
      return L.l("Found library '{0}' as '{1}', but the load failed. "
                 + "The JVM exception was: {2}\n",
                 _libraryName, lib, _cause);
    }
  }

  public void checkIsValid()
  {
    if (! _isValid) {
      throw new UnsupportedOperationException(getMessage(), _cause);
    }
  }

  public boolean isEnabled()
  {
    log();

    return _isValid;
  }
  
  public void disable(Throwable cause)
  {
    if (_cause == null)
      _cause = cause;
    
    _isValid = false;
  }
  
  public void disable()
  {
    _isValid = false;
  }
  
  public Throwable getCause()
  {
    return _cause;
  }

  private boolean isMacOSX()
  {
    return System.getProperty("os.name").equals("Mac OS X");
  }

  private boolean isWin()
  {
    return System.getProperty("os.name").startsWith("Windows");
  }

  private Path getLibexec()
  {
    Path homeDir = HomeUtil.getHomeDir();

    if (isWin()) {
      /*
      if (CauchoUtil.is64Bit())
        return homeDir.lookup("win64");
      else
        return homeDir.lookup("win32");
        */
      return homeDir.resolve("win64");
    }
    else {
      return homeDir.resolve("libexec64");
      /*
      if (CauchoUtil.is64Bit())
        return homeDir.lookup("libexec64");
      else
        return homeDir.lookup("libexec");
        */
    }
  }

  private Path getLib()
  {
    Path libexec = getLibexec();

    if (isMacOSX()) {
      return libexec.resolve("lib" + _libraryName + ".jnilib");
    }
    else if (isWin()) {
      return libexec.resolve(_libraryName + ".dll");
    }
    else {
      return libexec.resolve("lib" + _libraryName + ".so");
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _libraryName + "," + _isValid + "]";
  }
}
