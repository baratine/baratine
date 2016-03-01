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

package com.caucho.v5.log;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.caucho.v5.log.impl.EnvironmentLogger;


/**
 * Implementation of the log manager.
 */
public class LogManagerImpl extends LogManager
{
  private static final HashMap<String,SoftReference<EnvironmentLogger>> _envLoggers
    = new HashMap<String,SoftReference<EnvironmentLogger>>();

  public LogManagerImpl()
  {
  }

  /**
   * Adds a logger.
   */
  @Override
  public synchronized boolean addLogger(Logger logger)
  {
    EnvironmentLogger envLogger = addLogger(logger.getName(),
                                            logger.getResourceBundleName());
    
    // handle custom logger
    if (! logger.getClass().equals(Logger.class)) {
      return envLogger.addCustomLogger(logger);
    }

    return false;
  }
  
  private synchronized EnvironmentLogger addLogger(String name,
                                                   String resourceBundle)
  {
    EnvironmentLogger envLogger = null;

    SoftReference<EnvironmentLogger> loggerRef = _envLoggers.get(name);
    if (loggerRef != null)
      envLogger = loggerRef.get();

    if (envLogger == null) {
      envLogger = new EnvironmentLogger(name, resourceBundle);

      _envLoggers.put(name, new SoftReference<EnvironmentLogger>(envLogger));

      EnvironmentLogger parent = buildParentTree(name);

      if (parent != null)
        envLogger.setParent(parent);
    }

    return envLogger;
  }

  /**
   * Recursively builds the parents of the logger.
   */
  private EnvironmentLogger buildParentTree(String childName)
  {
    if (childName == null || childName.equals(""))
      return null;
    
    int p = childName.lastIndexOf('.');

    String parentName;

    if (p > 0)
      parentName = childName.substring(0, p);
    else
      parentName = "";
    
    EnvironmentLogger parent = null;
    
    SoftReference<EnvironmentLogger> parentRef = _envLoggers.get(parentName);
    if (parentRef != null)
      parent = parentRef.get();

    if (parent != null)
      return parent;
    else {
      parent = new EnvironmentLogger(parentName, null);
      _envLoggers.put(parentName, new SoftReference<EnvironmentLogger>(parent));

      EnvironmentLogger grandparent = buildParentTree(parentName);

      if (grandparent != null)
        parent.setParent(grandparent);

      return parent;
    }
  }

  /**
   * Returns the named logger.
   */
  @Override
  public synchronized Logger getLogger(String name)
  {
    SoftReference<EnvironmentLogger> envLoggerRef = _envLoggers.get(name);
    
    EnvironmentLogger envLogger = null;
    if (envLoggerRef != null)
      envLogger = envLoggerRef.get();

    if (envLogger == null)
      envLogger = addLogger(name, null);

    Logger customLogger = envLogger.getLogger();

    if (customLogger != null)
      return customLogger;
    else
      return envLogger;
  }

  /**
   * Returns an enumeration of loggers.
   */
  public Enumeration<String> getLoggerNames()
  {
    return Collections.enumeration(_envLoggers.keySet());
  }

  /**
   * Returns an enumeration of loggers.
   */
  public void reset()
  {
  }
}
