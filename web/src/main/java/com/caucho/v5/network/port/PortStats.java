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

package com.caucho.v5.network.port;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.caucho.v5.health.meter.CountMeter;
import com.caucho.v5.health.meter.MeterService;
import com.caucho.v5.util.L10N;

/**
 * Represents a protocol connection.
 */
//@Configurable
public class PortStats
{
  private static final L10N L = new L10N(PortStats.class);

  private static final Logger log
    = Logger.getLogger(PortStats.class.getName());

  private static final CountMeter _keepaliveMeter
    = MeterService.createCountMeter("Caucho|Port|Keepalive Count");

  // statistics
  
  private final PortTcp _port;

  //private final AtomicLong _lifetimeRequestCount = new AtomicLong();
  private final AtomicLong _lifetimeKeepaliveCount = new AtomicLong();
  private final AtomicLong _lifetimeKeepaliveSelectCount = new AtomicLong();
  private final AtomicLong _lifetimeClientDisconnectCount = new AtomicLong();
  private final AtomicLong _lifetimeRequestTime = new AtomicLong();
  private final AtomicLong _lifetimeReadBytes = new AtomicLong();
  private final AtomicLong _lifetimeWriteBytes = new AtomicLong();
  private final AtomicLong _lifetimeThrottleDisconnectCount = new AtomicLong();

  PortStats(PortTcp port)
  {
    _port = port;
  }

  //
  // statistics
  //

  /**
   * Returns the number of connections
   */
  /*
  int getConnectionCount()
  {
    return _activeConnectionCount.get();
  }
  */

  void addLifetimeKeepaliveCount()
  {
    _keepaliveMeter.start();
    _lifetimeKeepaliveCount.incrementAndGet();
  }

  public long getLifetimeKeepaliveCount()
  {
    return _lifetimeKeepaliveCount.get();
  }

  void addLifetimeKeepalivePollCount()
  {
    _lifetimeKeepaliveSelectCount.incrementAndGet();
  }

  public long getLifetimeKeepaliveSelectCount()
  {
    return _lifetimeKeepaliveSelectCount.get();
  }

  void addLifetimeClientDisconnectCount()
  {
    _lifetimeClientDisconnectCount.incrementAndGet();
  }

  public long getLifetimeClientDisconnectCount()
  {
    return _lifetimeClientDisconnectCount.get();
  }

  void addLifetimeRequestTime(long time)
  {
    _lifetimeRequestTime.addAndGet(time);
  }

  public long getLifetimeRequestTime()
  {
    return _lifetimeRequestTime.get();
  }

  void addLifetimeReadBytes(long bytes)
  {
    _lifetimeReadBytes.addAndGet(bytes);
  }

  public long getLifetimeReadBytes()
  {
    return _lifetimeReadBytes.get();
  }

  void addLifetimeWriteBytes(long bytes)
  {
    _lifetimeWriteBytes.addAndGet(bytes);
  }

  public long getLifetimeWriteBytes()
  {
    return _lifetimeWriteBytes.get();
  }

  long getLifetimeThrottleDisconnectCount()
  {
    return _lifetimeThrottleDisconnectCount.get();
  }
}
