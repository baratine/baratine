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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.amp.deliver.WorkerDeliver;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.network.port.PollTcpManager.PollResult;
import com.caucho.v5.util.CurrentTime;

/**
 * Represents a protocol-independent connection.  Protocol servers and
 * their associated Requests use Connection to retrieve the read and
 * write streams and to get information about the connection.
 *
 * <p>TcpConnection is the most common implementation.  The test harness
 * provides a string based Connection.
 */
public class PollContext
  implements PollController
{
  private SocketBar _socket;
  private final ReadBuffer _is;
  private final WriteBuffer _os;
  private final Runnable _task;
  private final Executor _executor;
  private final PollTcpManager _systemSelectManager;

  private PollTcpManager _selectManager;

  private long _idleStart;
  private long _idleExpire;

  private final AtomicReference<KeepaliveState> _state
    = new AtomicReference<KeepaliveState>(KeepaliveState.ACTIVE);

  /*
  public ControllerPollContext(QSocket socket,
                          ReadStream is,
                          WriteStream os,
                          Runnable task,
                          Executor executor,
                          PollTcpManager selectManager)
  {
    _socket = socket;
    _is = is;
    _os = os;
    _task = task;
    _executor = executor;
    _systemSelectManager = selectManager;
  }
  */

  /**
   * @param _networkSystem
   * @param task
   * @param _executor2
   * @param _selectManager2
   */
  public PollContext(SocketSystem networkSystem,
                          Runnable task,
                          Executor executor,
                          PollTcpManager selectManager)
  {
    _socket = networkSystem.createSocket();

    _is = new ReadBuffer();
    _os = new WriteBuffer();

    _task = task;
    _executor = executor;
    _systemSelectManager = selectManager;
  }

  public void init(SocketBar s)
    throws IOException
  {
    _socket = s;

    _is.init(s.stream());
    _os.init(s.stream());
  }

  @Override
  public SocketBar getSocket()
  {
    return _socket;
  }

  public WriteBuffer getWriteStream()
  {
    return _os;
  }

  public ReadBuffer getReadStream()
  {
    return _is;
  }

  @Override
  public long getIdleStartTime()
  {
    return _idleStart;
  }

  @Override
  public long getIdleExpireTime()
  {
    return _idleExpire;
  }

  //
  // state transitions by the connection thread
  //

  @Override
  public final boolean enableKeepaliveIfNew(PollTcpManager selectManager)
  {
    if (_selectManager == null) {
      _selectManager = selectManager;
      return true;
    }
    else {
      return false;
    }
  }

  protected final void initKeepalive()
  {
    _state.set(KeepaliveState.ACTIVE);
  }
  
  @Override
  public boolean isKeepaliveRegistered()
  {
    return false;
  }

  public final boolean toKeepalive(long timeout)
  {
    _idleStart = CurrentTime.getCurrentTimeActual();
    _idleExpire = _idleStart + timeout;

    return _systemSelectManager.startPoll(this) == PollResult.START;
  }

  /**
   * Change to keepalive, unless data is available.
   *
   * @return true if now in keepalive state. false if data is available
   */
  @Override
  public final boolean toKeepaliveStart()
  {
    KeepaliveState oldState;
    KeepaliveState newState;

    do {
      oldState = _state.get();

      if (oldState.isAvailable()) {
        return false;
      }

      newState = oldState.toKeepalive();
    } while (! _state.compareAndSet(oldState, newState));

    return true;
  }
  
  @Override
  public void toKeepaliveClose()
  {
    _state.set(KeepaliveState.ACTIVE);
  }

  public final boolean suspend()
  {
    return false;
  }

  //
  // state transitions by external threads
  //

  @Override
  public final void onPollRead()
  {
    KeepaliveState oldState = toState(KeepaliveState.ACTIVE_READ);

    if (oldState.isDetached()) {
      _executor.execute(_task);
    }
  }

  @Override
  public final void onKeepaliveTimeout()
  {
    KeepaliveState oldState = toState(KeepaliveState.TIMEOUT);

    if (oldState.isDetached()) {
      _executor.execute(_task);
    }
  }

  public final void onWake()
  {
    KeepaliveState oldState = toState(KeepaliveState.ACTIVE);

    if (oldState.isDetached()) {
      _executor.execute(_task);
    }
  }

  @Override
  public final void onPollReadClose()
  {
    KeepaliveState oldState = toState(KeepaliveState.CLOSED);

    if (oldState.isDetached()) {
      _executor.execute(_task);
    }
  }

  /**
   * For the non-poll keepalive, the keepalive thread will block
   * calling <code>fillWithTimeout.</code>
   */

  @Override
  public int fillWithTimeout(long timeout) throws IOException
  {
    return _is.fillWithTimeout(timeout);
  }

  public void close()
  {
    PollTcpManager selectManager = _selectManager;
    _selectManager = null;

    if (selectManager != null) {
      selectManager.closePoll(this);
    }

    _state.set(KeepaliveState.CLOSED);

    // XXX: timing issues
    try {
      IoUtil.close(_is);
      IoUtil.close(_os);
    } finally {
      IoUtil.close(_socket);
    }
  }

  private KeepaliveState toState(KeepaliveState targetState)
  {
    KeepaliveState oldState;
    KeepaliveState newState;

    do {
      oldState = _state.get();

      newState = targetState.toState(oldState);
    } while (! _state.compareAndSet(oldState, newState));

    return oldState;
  }

  private enum KeepaliveState {
    ACTIVE {
      @Override
      KeepaliveState toKeepalive() { return KEEPALIVE; }

      @Override
      KeepaliveState toRead() { return ACTIVE_READ; }

      @Override
      KeepaliveState toState(KeepaliveState target)
      {
        return target.toActive();
      }
    },

    ACTIVE_READ {
      @Override
      boolean isAvailable() { return true; }

      @Override
      KeepaliveState toState(KeepaliveState target)
      {
        return target.toRead();
      }
    },

    KEEPALIVE {
      @Override
      boolean isDetached() { return true; }

      @Override
      boolean isKeepalive() { return true; }

      @Override
      KeepaliveState toRead() { return ACTIVE; }

      @Override
      KeepaliveState toState(KeepaliveState target)
      {
        return target.toKeepalive();
      }
    },

    SUSPEND {
      @Override
      boolean isDetached() { return true; }

      @Override
      KeepaliveState toRead() { return SUSPEND_READ; }

      @Override
      KeepaliveState toActive()
      {
        return ACTIVE;
      }

      @Override
      KeepaliveState toState(KeepaliveState target)
      {
        return target.toSuspend();
      }
    },

    SUSPEND_READ {
      @Override
      boolean isDetached() { return true; }

      @Override
      KeepaliveState toActive()
      {
        return ACTIVE_READ;
      }
    },

    TIMEOUT {
      @Override
      boolean isAvailable() { return true; }

      @Override
      boolean isClosed() { return true; }

      @Override
      KeepaliveState toState(KeepaliveState target)
      {
        return target.toTimeout();
      }
    },

    CLOSED {
      @Override
      boolean isAvailable() { return true; }

      @Override
      boolean isClosed() { return true; }

      @Override
      KeepaliveState toState(KeepaliveState target)
      {
        return target.toDisconnect();
      }
    };

    boolean isKeepalive()
    {
      return false;
    }

    boolean isDetached()
    {
      return false;
    }

    boolean isAvailable()
    {
      return false;
    }

    boolean isClosed()
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

    KeepaliveState toSuspend()
    {
      return this;
    }

    KeepaliveState toActive()
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

    KeepaliveState toState(KeepaliveState target)
    {
      throw new UnsupportedOperationException(toString());
    }
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

  /* (non-Javadoc)
   * @see com.caucho.network.listen.KeepaliveConnection#getPort()
   */
  @Override
  public PortSocket getPort()
  {
    // TODO Auto-generated method stub
    return null;
  }
}
