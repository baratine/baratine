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

package com.caucho.v5.amp.thread;

import java.util.concurrent.locks.LockSupport;
import java.util.logging.Logger;

import com.caucho.v5.health.HealthSystemFacade;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.ModulePrivate;

@ModulePrivate
class ThreadLauncher extends ThreadLauncherBase
{
  private static final Logger log
    = Logger.getLogger(ThreadLauncher.class.getName());
  
  public static final String THREAD_FULL_EVENT
    = "caucho.thread.pool.full";
  public static final String THREAD_CREATE_THROTTLE_EVENT
    = "caucho.thread.pool.throttle";
  
  private final ThreadPoolBase _pool;

  ThreadLauncher(ThreadPoolBase pool)
  {
    super(ClassLoader.getSystemClassLoader());
    
    _pool = pool;
  }
  
  @Override
  public final boolean isPermanent()
  {
    return true;
  }

  @Override
  protected void startWorkerThread()
  {
    boolean isValid = false;
    
    try {
      Thread thread = new Thread(this);
      thread.setDaemon(true);
      thread.setName("baratine-thread-launcher2");
      thread.start();
      
      isValid = true;
    } finally {
      if (! isValid) {
        HealthSystemFacade.shutdownActive(ExitCode.THREAD,
                                       "Cannot create ThreadPool thread.");
      }
    }
  }
  
  @Override
  protected long currentTimeActual()
  {
    return System.currentTimeMillis();
  }
  
  @Override
  protected void launchChildThread(int id)
  {
    try {
      ThreadAmp poolThread = new ThreadAmp(id, _pool, this);
      poolThread.start();
    } catch (Throwable e) {
      e.printStackTrace();

      String msg = "Baratine exiting because of failed thread";
      
      try {
        msg = msg + ": " + e;
      } catch (Throwable e1) {
      }

      HealthSystemFacade.shutdownActive(ExitCode.THREAD, msg);
    }
  }
  
  //
  // event handling
  
  @Override
  protected void onThreadMax()
  {
    HealthSystemFacade.fireEvent(THREAD_FULL_EVENT, 
                                 "threads=" + getThreadCount());
  }

  @Override
  protected void onThrottle(String msg)
  {
    System.err.println("Throttle: " + msg);
    
    log.warning(msg);
    
    HealthSystemFacade.fireEvent(THREAD_CREATE_THROTTLE_EVENT, 
                                 msg);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _pool + "]";
  }
}
