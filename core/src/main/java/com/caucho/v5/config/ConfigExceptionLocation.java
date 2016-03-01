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

package com.caucho.v5.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.caucho.v5.config.impl.ConfigProvider;

/**
 * Configuration exception with location information.
 */
@SuppressWarnings("serial")
public class ConfigExceptionLocation extends ConfigException
  implements UserMessageLocation
{
  private String _filename;
  private int _line = -1;
  private String _location;

  /**
   * Create a null exception
   */
  public ConfigExceptionLocation()
  {
  }

  /**
   * Creates an exception with a message
   */
  public ConfigExceptionLocation(String msg)
  {
    super(msg);
  }
  
  /**
   * Creates an exception with a message
   */
  public ConfigExceptionLocation(String location, String msg)
  {
    super(location + msg);
    
    _location = location;
  }

  /**
   * Creates an exception with a message
   */
  public ConfigExceptionLocation(String msg, Throwable cause)
  {
    super(msg, cause);
  }

  /**
   * Creates an exception with a message
   */
  /*
  public LineConfigException(Throwable cause)
  {
    super(cause);
  }
  */

  public ConfigExceptionLocation(String filename, int line, String message)
  {
    super(filename + ":" + line + ": " + message + sourceLines(filename, line));

    _filename = filename;
    _line = line;
    
    _location = location(filename, line);
  }

  public ConfigExceptionLocation(String filename, int line, Throwable cause)
  {
    super(filename + ":" + line + ": "
          + cause.getMessage() + sourceLines(filename, line), 
          cause);

    _filename = filename;
    _line = line;
    
    _location = location(filename, line);
  }

  public ConfigExceptionLocation(String filename, int line,
                                 String message, 
                                 Throwable cause)
  {
    super(filename + ":" + line + ": " + message + sourceLines(filename, line), 
          cause);

    _filename = filename;
    _line = line;
    
    _location = location(filename, line);
  }
  
  private String location(String filename, int line)
  {
    return filename + ":" + line;
  }
  
  public String location()
  {
    return _location;
  }

  public String filename()
  {
    return _filename;
  }

  public int line()
  {
    return _line;
  }

  public static RuntimeException wrap(String filename, int line, Throwable e)
  {
    if (e instanceof UserMessageLocation) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      else {
        return new ConfigExceptionLocation(filename, line, e.getMessage(), e);
      }
    }
    else if (e instanceof UserMessage) {
      return new ConfigExceptionLocation(filename, line, e.getMessage(), e);
    }
    else {
      return new ConfigExceptionLocation(filename, line, e.toString(), e);
    }
  }

  public static RuntimeException wrap(String loc, Throwable e)
  {
    if (e instanceof UserMessageLocation) {
      if (e instanceof RuntimeException) {
        return (RuntimeException) e;
      }
      else {
        return new ConfigExceptionLocation(e.getMessage(), e);
      }
    }
    else if (e instanceof UserMessage) {
      return new ConfigExceptionLocation(loc + e.getMessage(), e);
    }
    else {
      return new ConfigExceptionLocation(loc + e, e);
    }
  }

  public static RuntimeException wrap(String loc, String msg, Throwable e)
  {
    if (e instanceof UserMessageLocation) {
      if (e instanceof RuntimeException) {
        return (RuntimeException) e;
      }
      else {
        return new ConfigExceptionLocation(msg, e);
      }
    }
    else {
      return new ConfigExceptionLocation(loc + msg, e);
    }
  }

  public static RuntimeException wrap(Field field, Throwable e)
  {
    return wrap(location(field), e);
  }

  public static RuntimeException wrap(Field field, String msg, Throwable e)
  {
    return wrap(location(field), msg, e);
  }

  public static RuntimeException wrap(Method method, Throwable e)
  {
    return wrap(location(method), e);
  }

  public static RuntimeException wrap(Method method, String msg, Throwable e)
  {
    return wrap(location(method), msg, e);
  }

  public static RuntimeException wrap(Constructor<?> ctor, Throwable e)
  {
    return wrap(location(ctor), e);
  }

  public static RuntimeException wrap(Constructor<?> ctor, String msg, Throwable e)
  {
    return wrap(location(ctor), msg, e);
  }

  public static RuntimeException wrap(Throwable e)
  {
    if (e instanceof UserMessageLocation) {
      return new ConfigExceptionLocation(e.getMessage(), e);
    }
    else if (e instanceof UserMessage) {
      return new ConfigException(e.getMessage(), e);
    }
    else {
      return new ConfigException(e.toString(), e);
    }
  }

  public static String location(Field field)
  {
    return field.getDeclaringClass().getName() + "." + field.getName() + ": ";
  }

  public static String location(Method method)
  {
    return method.getDeclaringClass().getName() + "." + method.getName() + "(): ";
  }
  
  public static String location(Constructor<?> ctor)
  {
    return ctor.getDeclaringClass().getName() + ".new: ";
  }

  @Override
  public String toString()
  {
    return getMessage();
  }
  
  private static String sourceLines(String fileName, int line)
  {
    return ConfigProvider.sourceLines(fileName, line);
  }
  
  /*
  public static ConfigException createLine(String loc, String msg)
  {
    if ("".equals(loc)) {
      return new ConfigException(msg);
    }
    
    String fileName = getFileName(loc);
    int line = getLine(loc);
    
    String source = ConfigUtilTemp.getSourceLines(fileName, line);
    
    return new ConfigExceptionLine(loc + msg + source);
  }
  
  private static String getFileName(String loc)
  {
    loc = loc.trim();
    
    if (loc.endsWith(":")) {
      loc = loc.substring(0, loc.length() - 1);
    }
    
    int p = loc.lastIndexOf(':');
    
    if (p <= 0) {
      return null;
    }
    else {
      return loc.substring(0, p);
    }
  }
  
  private static int getLine(String loc)
  {
    loc = loc.trim();
    
    if (loc.endsWith(":")) {
      loc = loc.substring(0, loc.length() - 1);
    }
    
    int p = loc.lastIndexOf(':');
    
    if (p <= 0) {
      return 0;
    }
    else {
      return Integer.parseInt(loc.substring(p + 1));
    }
  }
  */
}
