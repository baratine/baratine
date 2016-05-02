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
import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.amp.deliver.WorkerDeliver;
import com.caucho.v5.io.SocketBar;

/**
 * Represents a protocol-independent connection.  Protocol servers and
 * their associated Requests use Connection to retrieve the read and
 * write streams and to get information about the connection.
 *
 * <p>TcpConnection is the most common implementation.  The test harness
 * provides a string based Connection.
 */
public class PollControllerThread implements PollController
{
  private final ConnectionTcp _conn;
  private final SocketBar _socket;
  
  private PollTcpManager _pollManager;

  public PollControllerThread(ConnectionTcp conn)
  {
    _conn = conn;
    _socket = conn.socket();
  }
  
  @Override
  public PortSocket getPort()
  {
    return _conn.port();
  }

  @Override
  public SocketBar getSocket()
  {
    return _socket;
  }

  @Override
  public long getIdleStartTime()
  {
    return _conn.idleStartTime();
  }

  @Override
  public long getIdleExpireTime()
  {
    return _conn.idleExpireTime();
  }
  
  public boolean isKeepaliveStarted()
  {
    return true;
  }

  @Override
  public final boolean enableKeepaliveIfNew(PollTcpManager selectManager)
  {
    return true;
  }
  
  public boolean isKeepaliveRegistered()
  {
    //return _keepaliveState.get().isRegistered();
    return true;
  }
  
  @Override
  public final boolean toKeepaliveStart()
  {
    return true;
  }
  
  public void toKeepaliveClose()
  {
    //_keepaliveState.set(KeepaliveState.CLOSED);
  }

  @Override
  public final void onPollRead()
  {
  }

  @Override
  public final void onKeepaliveTimeout()
  {
  }

  @Override
  public final void onPollReadClose()
  {
  }

  protected void requestPollRead()
  {
    _conn.proxy().requestPollRead();
  }

  protected void requestTimeout()
  {
    _conn.proxy().requestTimeout();
  }

  /*
  protected void requestDestroy()
  {
    _conn.requestDestroy();
  }
  */

  @Override
  public int fillWithTimeout(long timeout) throws IOException
  {
    return _conn.fillWithTimeout(timeout);
  }

  @Override
  public void offerQueue(long timeout)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public WorkerDeliver worker()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  public void destroy()
  {
    PollTcpManager pollManager = _pollManager;
    _pollManager = null;
    
    if (pollManager != null) {
      pollManager.closePoll(this);
    }
  }
  
  private enum KeepaliveState {
    ACTIVE {
      @Override
      KeepaliveState toKeepalive() { return KEEPALIVE; }

      @Override
      KeepaliveState toRead() { return ACTIVE_READ; }
    },
    ACTIVE_READ {
      @Override
      boolean isAvailable() { return true; }

      @Override
      public boolean isRegistered() { return true; }
    },
    KEEPALIVE {
      @Override
      boolean isKeepalive() { return true; }

      @Override
      public boolean isRegistered() { return true; }

      @Override
      KeepaliveState toRead() { return ACTIVE; }
    },
    CLOSED {
      @Override
      boolean isAvailable() { return true; }
    };
    
    boolean isKeepalive()
    {
      return false;
    }
    
    public boolean isRegistered()
    {
      return false;
    }

    boolean isAvailable()
    {
      return false;
    }
    
    KeepaliveState toKeepalive()
    {
      throw new IllegalStateException(toString());
    }
    
    KeepaliveState toRead()
    {
      return this;
    }
    
    KeepaliveState toDisconnect()
    {
      return CLOSED;
    }
    
    KeepaliveState toTimeout()
    {
      return CLOSED;
    }
  }
}
