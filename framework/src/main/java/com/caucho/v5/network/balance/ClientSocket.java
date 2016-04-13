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

import io.baratine.service.ServiceException;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.health.meter.ActiveMeter;
import com.caucho.v5.health.meter.MeterActiveTime;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.ReadStreamOld;
import com.caucho.v5.vfs.WriteStreamOld;

/**
 * Defines a connection to the client.
 */
public class ClientSocket implements ClientSocketApi {
  private static final L10N L = new L10N(ClientSocket.class);

  private static final Logger log
    = Logger.getLogger(ClientSocket.class.getName());

  private ClientSocketFactory _pool;
  
  // The pools sequence id when the stream was allocated.
  private long _poolSequenceId;

  private ReadStreamOld _is;
  private WriteStreamOld _os;

  private boolean _isAuthenticated;

  private ActiveMeter _connProbe;
  private MeterActiveTime _requestTimeProbe;
  private ActiveMeter _idleProbe;

  private long _requestStartTime;
  private boolean _isIdle;

  private long _idleStartTime;

  private String _debugId;

  ClientSocket(ClientSocketFactory pool, 
               int count,
               ReadStreamOld is,
               WriteStreamOld os)
  {
    _pool = pool;
    _poolSequenceId = pool.getStartSequenceId();
    _is = is;
    _os = os;
    
    _debugId = "[" + pool.getDebugId() + ":" + count + "]";

    _connProbe = pool.getConnectionProbe();
    _requestTimeProbe = pool.getRequestTimeProbe();
    _idleProbe = pool.getIdleProbe();

    _connProbe.start();

    toActive();
  }

  /**
   * Returns the owning pool
   */
  @Override
  public ClientSocketFactory getPool()
  {
    return _pool;
  }

  /**
   * Returns the input stream.
   */
  @Override
  public ReadStreamOld getInputStream()
  {
    _idleStartTime = 0;

    return _is;
  }

  /**
   * Returns the write stream.
   */
  @Override
  public WriteStreamOld getOutputStream()
  {
    _idleStartTime = 0;

    return _os;
  }

  /**
   * Returns true if the stream has been authenticated
   */
  public boolean isAuthenticated()
  {
    return _isAuthenticated;
  }

  /**
   * Returns true if the stream has been authenticated
   */
  public void setAuthenticated(boolean isAuthenticated)
  {
    _isAuthenticated = isAuthenticated;
  }

  /**
   * Returns the idle start time, 
   * i.e. the time the connection was last idle.
   */
  @Override
  public long getIdleStartTime()
  {
    return _idleStartTime;
  }

  /**
   * Sets the idle start time. Because of clock skew and
   * tcp delays, it's often better to use the request
   * start time instead of the request end time for the
   * idle start time.
   */
  @Override
  public void setIdleStartTime(long idleStartTime)
  {
    _idleStartTime = idleStartTime;
  }

  /**
   * Sets the idle start time.
   */
  @Override
  public void clearIdleStartTime()
  {
    _idleStartTime = 0;
    _is.clearReadTime();
  }

  /**
   * Returns true if nearing end of free time.
   */
  @Override
  public boolean isIdleExpired()
  {
    long now = CurrentTime.currentTime();

    return (_pool.getLoadBalanceIdleTime() < now - _idleStartTime);
  }

  /**
   * Returns true if nearing end of free time.
   */
  @Override
  public boolean isIdleAlmostExpired(long delta)
  {
    long now = CurrentTime.currentTime();

    return (_pool.getLoadBalanceIdleTime() < now - _idleStartTime + delta);
  }
  
  /**
   * Returns true if the sequence id is valid.
   */
  @Override
  public boolean isPoolSequenceIdValid()
  {
    return _poolSequenceId == _pool.getStartSequenceId();
  }

  //
  // ActorStream output for HMTP
  //

  public String getAddress()
  {
    return "clusterStream@admin.resin";
  }
  
  public void switchToHmtp(boolean isUnidir)
  {
    try {
      WriteStreamOld out = getOutputStream();

      /*
      if (isUnidir)
        out.write(HmuxRequest.HMUX_TO_UNIDIR_HMTP);
      else
        out.write(HmuxRequest.HMUX_SWITCH_TO_HMTP);
        */
             
      out.write(0);
      out.write(1);
      boolean isAdmin = true;
      out.write(isAdmin ? 1 : 0);
      out.flush();
    } catch (IOException e) {
      throw new ServiceException(e);
    }
    
  }

  /**
   * Returns the debug id.
   */
  @Override
  public String getDebugId()
  {
    return _debugId;
  }

  /**
   * Clears the recycled connections.
   */
  @Override
  public void clearRecycle()
  {
    _pool.clearRecycle();
  }

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
  @Override
  public void free(long idleStartTime)
  {
    if (_is == null) {
      IllegalStateException exn = new IllegalStateException(L.l("{0} unexpected free of closed stream", this));
      exn.fillInStackTrace();

      log.log(Level.FINE, exn.toString(), exn);
      return;
    }

    long requestStartTime = _requestStartTime;
    _requestStartTime = 0;

    if (requestStartTime > 0)
      _requestTimeProbe.end(requestStartTime);


    // #2369 - the load balancer might set its own view of the free
    // time
    if (idleStartTime <= 0) {
      idleStartTime = _is.getReadTime();

      if (idleStartTime <= 0) {
        // for write-only, the read time is zero
        idleStartTime = CurrentTime.currentTime();
      }
    }
    
    _idleStartTime = idleStartTime;

    _idleProbe.start();
    _isIdle = true;

    _pool.free(this);
  }

  @Override
  public void toActive()
  {
    if (_isIdle) {
      _isIdle = false;
      _idleProbe.end();
    }

    _requestStartTime = _requestTimeProbe.start();
  }
  
  @Override
  public boolean isClosed()
  {
    return _is == null;
  }

  @Override
  public void close()
  {
    if (_is != null) {
      _pool.close(this);
    }

    closeImpl();
  }

  /**
   * closes the stream.
   */
  void closeImpl()
  {
    ReadStreamOld is = _is;
    _is = null;

    WriteStreamOld os = _os;
    _os = null;

    try {
      if (is != null)
        is.close();
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }

    try {
      if (os != null)
        os.close();
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }

    if (is != null) {
      _connProbe.end();

      if (_requestStartTime > 0)
        _requestTimeProbe.end(_requestStartTime);

      if (_isIdle)
        _idleProbe.end();
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _debugId + "]";
  }
}
