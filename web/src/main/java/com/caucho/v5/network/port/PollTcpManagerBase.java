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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.network.port;

import com.caucho.v5.util.ModulePrivate;


/**
 * A select manager handles keepalive connections.
 */
@ModulePrivate
abstract public class PollTcpManagerBase implements PollTcpManager {
  /**
   * Sets the timeout.
   */
  public void setSelectTimeout(long timeout)
  {
  }

  /**
   * Sets the max.
   */
  public void setSelectMax(int max)
  {
  }

  /**
   * Gets the max.
   */
  public int pollMax()
  {
    return -1;
  }
  
  /**
   * Starts the manager.
   */
  abstract public boolean start();
  
  /**
   * Adds a keepalive connection.
   *
   * @param conn the connection to register as keepalive
   *
   * @return true if the keepalive was successful
   */
  abstract public PollResult startPoll(PollController conn);

  public void closePoll(PollController conn)
  {
  }

  /**
   * Returns the select count.
   */
  public int getSelectCount()
  {
    return 0;
  }

  /**
   * Returns the number of available keepalives.
   */
  public int getFreeKeepalive()
  {
    return Integer.MAX_VALUE / 2;
  }

  public void onPortClose(PortSocket port)
  {
  }

  /**
   * Stops the manager.
   */
  public boolean stop()
  {
    return true;
  }

  /**
   * Closing the manager.
   */
  public void close()
  {
    stop();
  }

  public PollController createHandle(ConnectionTcp connTcp)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
