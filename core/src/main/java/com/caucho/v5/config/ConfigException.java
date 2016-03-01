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

package com.caucho.v5.config;

import java.lang.reflect.InvocationTargetException;

import com.caucho.v5.config.impl.RuntimeExceptionConfig;

/**
 * Thrown by the various Builders
 */
@SuppressWarnings("serial")
public class ConfigException
  extends RuntimeExceptionConfig
  implements UserMessage // , DisplayableException
{
  /**
   * Create a null exception
   */
  public ConfigException()
  {
  }

  /**
   * Creates an exception with a message
   */
  public ConfigException(String msg)
  {
    super(msg);
  }

  /**
   * Creates an exception with a message and throwable
   */
  public ConfigException(String msg, Throwable e)
  {
    super(msg, e);
  }

  /**
   * Creates an exception with a throwable
   */
  protected ConfigException(Throwable e)
  {
    super(message(e), e);
  }

  protected static String message(Throwable e)
  {
    if (e instanceof UserMessage)
      return e.getMessage();
    else
      return e.toString();
  }

  /*
  public static RuntimeException create(String location, Throwable e)
  {
    e = getCause(e);

    if (e instanceof ConfigExceptionLine) {
      throw (ConfigExceptionLine) e;
    }
    else if (e instanceof UserMessage) {
      return new ConfigException(location + e.getMessage(), e);
    }
    else {
      return new ConfigException(location + e, e);
    }
  }

  public static RuntimeException createLine(String loc, Throwable e)
  {
    if ("".equals(loc)) {
      return create(e);
    }

    e = getCause(e);

    if (e instanceof ConfigExceptionLine) {
      throw (ConfigExceptionLine) e;
    }
    
    String fileName = getFileName(loc);
    int line = getLine(loc);
    
    String source = ConfigUtilTemp.getSourceLines(fileName, line);
    
    if (e instanceof UserMessage) {
      return new ConfigExceptionLine(loc + e.getMessage() + source, e);
    }
    else {
      return new ConfigExceptionLine(loc + e + source, e);
    }
  }
  */
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

  public static RuntimeException create(Field field, Throwable e)
  {
    return create(loc(field), e);
  }

  public static RuntimeException create(Method method, Throwable e)
  {
    return create(loc(method), e);
  }

  public static RuntimeException create(Method method, String msg, Throwable e)
  {
    return new ConfigException(loc(method) + msg, e);
  }

  public static RuntimeException create(Method method, String msg)
  {
    return new ConfigException(loc(method) + msg);
  }
  */

  public static ConfigException createConfig(Throwable e)
  {
    if (e instanceof ConfigException)
      return (ConfigException) e;
    else
      return new ConfigException(e);
  }

  public static RuntimeException wrap(Throwable exn)
  {
    Throwable cause = cause(exn);

    if (cause instanceof RuntimeException) {
      return (RuntimeException) cause;
    }
    else if (cause instanceof UserMessageLocation) {
      return new ConfigExceptionLocation(cause.getMessage(), cause);
    }
    else if (cause instanceof UserMessage) {
      return new ConfigException(cause.getMessage(), cause);
    }
    else {
      return new RuntimeExceptionConfig(cause);
    }
  }

  public static RuntimeException wrap(String msg, Throwable exn)
  {
    Throwable cause = cause(exn);

    if (cause instanceof RuntimeException) {
      return (RuntimeException) cause;
    }
    else if (cause instanceof UserMessageLocation) {
      return new ConfigExceptionLocation(msg, cause);
    }
    else if (cause instanceof UserMessage) {
      return new ConfigException(msg, cause);
    }
    else {
      return new RuntimeExceptionConfig(cause);
    }
  }
  
  /**
   * Unwraps noise from the exception trace.
   */
  static Throwable cause(Throwable e)
  {
    while (e.getCause() != null
        && (e instanceof InstantiationException
            || e instanceof InvocationTargetException
            || e.getClass().equals(RuntimeExceptionConfig.class))) {
      e = e.getCause();
    }
    
    return e;
  }
  

  /*
  public void print(PrintWriter out)
  {
    out.println(Html.escapeHtml(getMessage()));
  }
  */

  /*
  public static String loc(Field field)
  {
    return field.getDeclaringClass().getSimpleName() + "." + field.getName() + ": ";
  }

  public static String loc(Method method)
  {
    return method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(): ";
  }
  */
}
