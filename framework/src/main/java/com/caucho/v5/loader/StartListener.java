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

package com.caucho.v5.loader;

import com.caucho.v5.util.L10N;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Waits for the start and stop events.
 */
public class StartListener implements EnvLoaderListener {
  private static final L10N L = new L10N(StartListener.class);
  private static final Logger log
    = Logger.getLogger(StartListener.class.getName());

  private Object _resource;

  /**
   * Creates the new start listener.
   *
   * @param resource the resource which needs start/stop
   */
  public StartListener(Object resource)
  {
    _resource = resource;
  }

  /**
   * Configuration callback
   */
  public void environmentConfigure(EnvironmentClassLoader loader)
  {  
  }

  /**
   * Bind callback
   */
  public void environmentBind(EnvironmentClassLoader loader)
  {  
  }
  
  /**
   * Handles the case where a class loader is activated.
   */
  public void environmentStart(EnvironmentClassLoader loader)
  {
    Method start = getStartMethod(_resource.getClass());

    if (start == null)
      return;
    
    try {
      start.invoke(_resource);
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }
  
  /**
   * Handles the case where a class loader is dropped.
   */
  public void environmentStop(EnvironmentClassLoader loader)
  {
    Method stop = getStopMethod(_resource.getClass());

    if (stop == null)
      return;
    
    try {
      stop.invoke(_resource);
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  public static Method getStartMethod(Class cl)
  {
    try {
      return cl.getMethod("start", new Class[0]);
    } catch (Throwable e) {
    }

    return null;
  }

  public static Method getStopMethod(Class cl)
  {
    try {
      return cl.getMethod("stop", new Class[0]);
    } catch (Throwable e) {
    }

    return null;
  }
}

