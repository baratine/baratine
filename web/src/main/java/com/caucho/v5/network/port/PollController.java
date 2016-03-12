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

import java.io.IOException;

import com.caucho.v5.amp.deliver.MessageDeliver;
import com.caucho.v5.io.SocketBar;

/**
 * Represents a protocol-independent connection.  Protocol servers and
 * their associated Requests use Connection to retrieve the read and
 * write streams and to get information about the connection.
 *
 * <p>TcpConnection is the most common implementation.  The test harness
 * provides a string based Connection.
 */
public interface PollController extends MessageDeliver
{
  /**
   * Returns the connection id.  Primarily for debugging.
   */
  // int getId();

  SocketBar getSocket();

  PortSocket getPort();
  
  long getIdleStartTime();
  
  long getIdleExpireTime();
  
  // void clientDisconnect();

  boolean enableKeepaliveIfNew(PollTcpManager selectManager);
  
  boolean toKeepaliveStart();
  
  void toKeepaliveClose();
  
  int fillWithTimeout(long timeout) throws IOException;

  void onPollRead();
  
  void onKeepaliveTimeout();
  
  void onPollReadClose();

  boolean isKeepaliveRegistered();
}
