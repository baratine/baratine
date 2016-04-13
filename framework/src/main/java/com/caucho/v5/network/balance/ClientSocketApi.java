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

package com.caucho.v5.network.balance;

import com.caucho.v5.vfs.ReadStreamOld;
import com.caucho.v5.vfs.WriteStreamOld;

/**
 * Defines a connection to the client.
 */
public interface ClientSocketApi {
  /**
   * Returns the owning pool
   */
  public ClientSocketFactory getPool();

  /**
   * Returns the input stream.
   */
  public ReadStreamOld getInputStream();

  /**
   * Returns the write stream.
   */
  public WriteStreamOld getOutputStream();

  /**
   * Returns the idle start time, 
   * i.e. the time the connection was last idle.
   */
  public long getIdleStartTime();

  /**
   * Sets the idle start time. Because of clock skew and
   * tcp delays, it's often better to use the request
   * start time instead of the request end time for the
   * idle start time.
   */
  public void setIdleStartTime(long idleStartTime);

  /**
   * Sets the idle start time.
   */
  public void clearIdleStartTime();

  /**
   * Returns true if nearing end of free time.
   */
  public boolean isIdleExpired();
  
  /**
   * Returns true if nearing end of free time.
   */
  public boolean isIdleAlmostExpired(long delta);
  
  /**
   * Returns true if the sequence id is valid.
   */
  public boolean isPoolSequenceIdValid();

  /**
   * Returns the debug id.
   */
  public String getDebugId();

  /**
   * Clears the recycled connections.
   */
  public void clearRecycle();

  /**
   * Adds the stream to the free pool.
   *
   * The idleStartTime may be earlier than the current time
   * to deal with TCP buffer delays. Typically it will be
   * recorded as the start time of the request's write.
   * 
   * @param idleStartTime the time to be used as the start 
   * of the idle period.
   */
  public void free(long idleStartTime);

  public void toActive();
  
  public boolean isClosed();

  public void close();
}
