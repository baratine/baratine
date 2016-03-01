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

package com.caucho.v5.health.warning;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;

/**
 * The WarningSystem is a general way to send warning and critical
 * system messages such as shutdown messages.
 */
public class WarningSystem extends SubSystemBase
{
  public static final int START_PRIORITY = 1;

  private static final Logger log = 
    Logger.getLogger(WarningSystem.class.getName());

  private final CopyOnWriteArrayList<WarningHandler> _priorityHandlers
    = new CopyOnWriteArrayList<>();
  
  private final CopyOnWriteArrayList<WarningHandler> _handlers
    = new CopyOnWriteArrayList<>();

  public WarningSystem()
  {
  }
  
  public static WarningSystem createAndAddSystem()
  {
    SystemManager system = preCreate(WarningSystem.class);
    
    WarningSystem service = new WarningSystem();
    system.addSystem(WarningSystem.class, service);
    
    return service;
  }
  
  public static WarningSystem getCurrent()
  {
    return SystemManager.getCurrentSystem(WarningSystem.class);
  }
  
  /**
   * Send a warning message to any registered handlers. A high priority warning
   * only goes to all handlers, high priority first. High priority handlers do
   * not receive non-high priority warnings.
   * 
   * @param source source of the message, usually you
   * @param msg test to print or send as an alert
   * @param isHighPriority set true to send to high priority warning handlers
   */
  public void sendWarning(Object source, String msg)
  {
    try {
      String s = getClass().getSimpleName() + ": " + msg;
    
      // if warning is high-priority then send to high priority handlers first
      System.err.println(s);
      
      for (WarningHandler handler : _priorityHandlers) {
        handler.warning(source, msg);
      }
    
      // now send to the all handlers regardless of if its high priority
      for (WarningHandler handler : _handlers) {
        handler.warning(source, msg);
      }

      log.warning(msg);
    } catch (Throwable e) {
      // WarningService must not throw exception
      log.log(Level.WARNING, e.toString(), e);
    }
  }
  
  /**
   * Send a warning message to any registered handlers. A high priority warning
   * only goes to all handlers, high priority first. High priority handlers do
   * not receive non-high priority warnings.
   * 
   * @param source source of the message, usually you
   * @param msg test to print or send as an alert
   * @param isHighPriority set true to send to high priority warning handlers
   */
  public void sendWarning(Object source, Throwable e)
  {
    try {
      e.printStackTrace();
    
      String msg = e.toString();
    
      String s = getClass().getSimpleName() + ": " + e;
    
      // if warning is high-priority then send to high priority handlers first
      System.err.println(s);
      
      for (WarningHandler handler : _priorityHandlers) {
        try {
          handler.warning(source, msg);
        } catch (Throwable e1) {
          // WarningService must not throw exception
          log.log(Level.WARNING, e1.toString(), e1);
        }
      }
    
      // now send to the all handlers regardless of if its high priority
      for (WarningHandler handler : _handlers) {
        try {
          handler.warning(source, msg);
        } catch (Throwable e1) {
          // WarningService must not throw exception
          log.log(Level.WARNING, e1.toString(), e1);
        }
      }

      log.warning(s);
    } catch (Throwable e1) {
      // WarningService must not throw exception
      log.log(Level.WARNING, e1.toString(), e1);
    }
  }

  /**
   * Sends a warning to the current service.
   */
  public static void sendCurrentWarning(Object source, String msg)
  {
    WarningSystem warning = getCurrent();
    
    if (warning != null)
      warning.sendWarning(source, msg);
    else {
      Thread.dumpStack();
      System.err.println(msg);
      log.warning(msg);
    }
  }

  /**
   * Sends a warning to the current service.
   */
  public static void sendCurrentWarning(Object source, Throwable e)
  {
    WarningSystem warning = getCurrent();
    
    if (warning != null)
      warning.sendWarning(source, e);
    else {
      e.printStackTrace();
      log.log(Level.WARNING, e.toString(), e);
    }
  }
  
  /**
   * Add a warning event handler.  High priority handlers ONLY get high 
   * priority warnings, and they are notified first.  Other handlers gets all
   * warnings after high priority handlers are notified.
   * @param handler an object that implements WarningHandler
   * @param isHighPriority high priority handlers only get high priority warnings.
   */
  public void addHandler(WarningHandler handler)
  {
    _handlers.add(handler);
  }
  
  /**
   * Add a warning event handler.  High priority handlers ONLY get high 
   * priority warnings, and they are notified first.  Other handlers gets all
   * warnings after high priority handlers are notified.
   * @param handler an object that implements WarningHandler
   * @param isHighPriority high priority handlers only get high priority warnings.
   */
  public void addPriorityHandler(WarningHandler handler)
  {
    _priorityHandlers.add(handler);
  }

  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
}
