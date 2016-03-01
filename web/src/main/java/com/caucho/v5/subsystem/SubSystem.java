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

package com.caucho.v5.subsystem;

import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * Interface for a service registered with the Server.
 */
public interface SubSystem
{
  // the start priority of an environment service with no network dependencies
  public static final int START_PRIORITY_CLASSLOADER_BIND = 20;
  public static final int START_PRIORITY_ENV_SYSTEM = 30;
  
  // the start priority of the network port service
  public static final int START_PRIORITY_NETWORK = 40;
  
  // the local store
  public static final int START_PRIORITY_KRAKEN  = 45;
  
  public static final int START_PRIORITY_HEARTBEAT = 50;
  
  // the lowest priority of a cluster service
  public static final int START_PRIORITY_CLUSTER_SERVICE = 60;
  
  public static final int START_PRIORITY_CLASSLOADER = 70;
  public static final int START_PRIORITY_DEFAULT = 80;

  /**
   * Returns the start priority of the service, used to determine which
   * services to start first.
   */
  int getStartPriority();
  
  /**
   * Starts the service.
   * @throws Exception 
   */
  void start()
    throws Exception;
  
  /**
   * Returns the stop priority of the service, used to determine which
   * services to stop first.
   */
  int getStopPriority();

  /**
   * Stops the service.
   * @throws Exception 
   */
  void stop(ShutdownModeAmp mode) throws Exception;
  
  /**
   * Destroys the service.
   */
  void destroy();
}
