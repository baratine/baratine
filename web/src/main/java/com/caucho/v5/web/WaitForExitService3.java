/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.web;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.health.shutdown.ShutdownSystem;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;

/**
 * The wait-for-exit service waits for Resin to exit.
 */
class WaitForExitService3
{
  private static final Logger log
    = Logger.getLogger(WaitForExitService3.class.getSimpleName());

  private static final L10N L = new L10N(WaitForExitService3.class);

  private WebServerImpl _server;
  private SystemManager _resinSystem;
  // private Socket _linkSocket;
  //private LinkChildServiceImpl _linkChildServiceImpl;

  //private LinkWatchdogService _linkWatchdog;

  /**
   * Creates a new resin server.
   */
  WaitForExitService3(WebServerImpl server,
                     SystemManager systemManager)
  {
    _server = server;
    _resinSystem = systemManager;

    /*
    //_linkSocket = linkSocket;
    _linkChildServiceImpl = linkChildService;
    _linkWatchdog = linkWatchdog;

    if (linkWatchdog != null) {
      WarningSystem warning = WarningSystem.getCurrent();
      warning.addHandler(new ServerWarningHandler(linkWatchdog));
    }
    */
  }

  /*
  LinkChildServiceImpl getLinkServiceImpl()
  {
    return _linkChildServiceImpl;
  }
  
  LinkWatchdogService getLinkWatcdog()
  {
    return _linkWatchdog;
  }
  */

  /**
   * Thread to wait until Resin should be stopped.
   */
  void waitForExit()
  {
    Runtime runtime = Runtime.getRuntime();

    ShutdownSystem shutdown = _resinSystem.getSystem(ShutdownSystem.class);

    if (shutdown == null) {
      throw new IllegalStateException(L.l("'{0}' requires an active {1}",
                                          this,
                                          ShutdownSystem.class.getSimpleName()));
    }

    /*
     * If the server has a parent process watching over us, close
     * gracefully when the parent dies.
     */
    while (! _server.isClosing()) {
      try {
        Thread.sleep(10);

        if (! checkMemory(runtime)) {
          shutdown.shutdown(ShutdownModeAmp.IMMEDIATE,
                            ExitCode.MEMORY,
                            "Server shutdown from out of memory");
          // dumpHeapOnExit();
          return;
        }

        if (! checkFileDescriptor()) {
          shutdown.shutdown(ShutdownModeAmp.IMMEDIATE,
                            ExitCode.MEMORY,
                            "Server shutdown from out of file descriptors");
          //dumpHeapOnExit();
          return;
        }

        synchronized (this) {
          wait(10000);
        }
      } catch (OutOfMemoryError e) {
        String msg = "Server shutdown from out of memory";

        ShutdownSystem.shutdownOutOfMemory(msg);
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);

        return;
      }
    }
  }

  private boolean checkMemory(Runtime runtime)
    throws InterruptedException
  {
    long minFreeMemory = 0;//_resinConfig.getMinFreeMemory();

    if (minFreeMemory <= 0) {
      // memory check disabled
      return true;
    }
    else if (2 * minFreeMemory < getFreeMemory(runtime)) {
      // plenty of free memory
      return true;
    }
    else {
      if (log.isLoggable(Level.FINER)) {
        log.finer(L.l("free memory {0} max:{1} total:{2} free:{3}",
                          "" + getFreeMemory(runtime),
                          "" + runtime.maxMemory(),
                          "" + runtime.totalMemory(),
                          "" + runtime.freeMemory()));
      }

      log.info(L.l("Forcing GC due to low memory. {0} free bytes.",
                       getFreeMemory(runtime)));

      runtime.gc();

      Thread.sleep(1000);

      runtime.gc();

      if (getFreeMemory(runtime) < minFreeMemory) {
        log.severe(L.l("Restarting due to low free memory. {0} free bytes",
                           getFreeMemory(runtime)));

        return false;
      }
    }

    // second memory check
    allocateMemory();

    return true;
  }

  private Object allocateMemory()
  {
    return new Object();
  }

  private boolean checkFileDescriptor()
  {
    try {
      /* XXX:
      PathImpl path = _server.getConfigPath();

      ReadStream is = path.openRead();
      is.close();
      */

      return true;
    } catch (Exception e) {
      log.severe(L.l("Restarting due to file descriptor failure:\n{0}",
                     e));

      return false;
    }
  }

  private static long getFreeMemory(Runtime runtime)
  {
    long maxMemory = runtime.maxMemory();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();

    // Some JDKs (JRocket) return 0 for the maxMemory
    if (maxMemory < totalMemory)
      return freeMemory;
    else
      return maxMemory - totalMemory + freeMemory;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[id=" + _resinSystem.getId() + "]";
  }

}
