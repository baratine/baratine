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

package com.caucho.v5.health;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.health.shutdown.ExitCode;

/**
 * A facade for sending health events.
 */
public class HealthSystemFacade {
  private static final Logger log
    = Logger.getLogger(HealthSystemFacade.class.getName());
  
  public static final String EXIT_MESSAGE = "caucho.exit.message";
  
  private static final HealthSystemFacade _facade;
  
  protected HealthSystemFacade()
  {
  }
  
  public static String getExitMessage()
  {
    String msg = System.getProperty(EXIT_MESSAGE);
    
    if (msg != null)
      return msg;
    else
      return "";
  }
  
  public static void fireEvent(String eventName, String eventMessage)
  {
    _facade.fireEventImpl(eventName, eventMessage);
  }
  
  public static void fireFatalEvent(String eventName, String eventMessage)
  {
    _facade.fireFatalEventImpl(eventName, eventMessage);
  }
  
  protected void fireEventImpl(String eventName, String eventMessage)
  {
  }
  
  protected void fireFatalEventImpl(String eventName, String eventMessage)
  {
    //ShutdownSystem.shutdownActive(ExitCode.HEALTH, eventName + ": " + eventMessage);
    shutdownActiveImpl(ExitCode.HEALTH, eventName + ": " + eventMessage);
  }

  public static void shutdownActive(ExitCode thread, String message)
  {
    _facade.shutdownActiveImpl(thread, message);
    
  }
  
  public void shutdownActiveImpl(ExitCode code, String message)
  {
    System.err.println("FATAL: " + code + " " + message);
  }
  
  static {
    HealthSystemFacade facade = new HealthSystemFacade();
    
    try {
      Class<?> cl = Class.forName(HealthSystemFacade.class.getName() + "Pro");
      
      facade = (HealthSystemFacade) cl.newInstance();
    } catch (ClassNotFoundException e) {
      log.log(Level.ALL, e.toString(), e);
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    _facade = facade;
  }
}
