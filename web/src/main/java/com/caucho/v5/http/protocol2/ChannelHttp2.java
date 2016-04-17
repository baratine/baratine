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

package com.caucho.v5.http.protocol2;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * InputChannelHttp manages the flow for a single HTTP stream.
 */
public class ChannelHttp2
{
  private final InRequest _request;
  
  private ConnectionHttp2Int _conn;
  private int _streamId;
  
  private final ChannelInHttp2 _inChannel;
  private final ChannelOutHttp2 _outChannel;
  
  private final AtomicReference<StateChannel> _stateRef
    = new AtomicReference<>(StateChannel.IDLE);

  public ChannelHttp2(InRequest request)
  {
    Objects.requireNonNull(request);

    _request = request;
    
    _inChannel = new ChannelInHttp2(this, request);
    _outChannel = new ChannelOutHttp2(this);
  }

  public int getId()
  {
    return _streamId;
  }

  public void init(ConnectionHttp2Int conn, int streamId)
  {
    Objects.requireNonNull(conn);
    
    _conn = conn;
    _streamId = streamId;
    
    _outChannel.init(streamId, conn.outHttp());
    _inChannel.init(conn, streamId);
    
    if (_stateRef.get().toActive(_stateRef)) {
      conn.putChannel(_streamId, this);
    }
  }

  public void onHeader(int streamId)
  {
    if (streamId != _streamId) {
      throw new IllegalStateException(_streamId + " to " + streamId);
    }

    _stateRef.get().onHeader(_stateRef);
  }
  
  public ChannelInHttp2 getInChannel()
  {
    return _inChannel;
  }

  public InRequest getRequest()
  {
    return getInChannel().getRequest();
  }
  
  public ChannelOutHttp2 getOutChannel()
  {
    return _outChannel;
  }
  
  void closeWrite()
  {
    if (_stateRef.get().toCloseWrite(_stateRef)) {
      _conn.closeStream(_streamId);
      
      getInChannel().getRequest().closeChannel();
    }
  }
  
  void closeRead()
  {
    boolean isClose = _stateRef.get().toCloseRead(_stateRef);
    
    getInChannel().getRequest().closeRead();

    if (isClose) {
      _conn.closeStream(_streamId);
      
      getInChannel().getRequest().closeChannel();
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _streamId + "," + _stateRef.get() + "]";
  }
  
  private enum StateChannel {
    IDLE {
      @Override
      boolean toActive(AtomicReference<StateChannel> stateRef)
      {
        if (stateRef.compareAndSet(IDLE, ACTIVE)) {
          return true;
        }
        else {
          return stateRef.get().toActive(stateRef);
        }
      }
    },
    
    ACTIVE {
      @Override
      boolean toActive(AtomicReference<StateChannel> stateRef)
      {
        return false;
      }

      @Override
      boolean toCloseWrite(AtomicReference<StateChannel> stateRef)
      {
        if (stateRef.compareAndSet(ACTIVE, CLOSE_WRITE)) {
          return false;
        }
        else {
          return stateRef.get().toCloseWrite(stateRef);
        }
      }

      @Override
      void onHeader(AtomicReference<StateChannel> stateRef)
      {
      }
      
      @Override
      boolean toCloseRead(AtomicReference<StateChannel> stateRef)
      {
        if (stateRef.compareAndSet(ACTIVE, CLOSE_READ)) {
          return false;
        }
        else {
          return stateRef.get().toCloseRead(stateRef);
        }
      }
    },
    
    CLOSE_READ {
      @Override
      boolean toCloseWrite(AtomicReference<StateChannel> stateRef)
      {
        if (stateRef.compareAndSet(CLOSE_READ, CLOSE)) {
          return true;
        }
        else {
          return stateRef.get().toCloseWrite(stateRef);
        }
      }
    },
    
    CLOSE_WRITE {
      @Override
      boolean toCloseRead(AtomicReference<StateChannel> stateRef)
      {
        if (stateRef.compareAndSet(CLOSE_WRITE, CLOSE)) {
          return true;
        }
        else {
          return stateRef.get().toCloseRead(stateRef);
        }
      }

      @Override
      void onHeader(AtomicReference<StateChannel> stateRef)
      {
      }
    },
    
    CLOSE {
      @Override
      boolean toActive(AtomicReference<StateChannel> stateRef)
      {
        if (stateRef.compareAndSet(CLOSE, ACTIVE)) {
          return true;
        } else {
          return stateRef.get().toActive(stateRef);
        }
      }
    },
    
    DESTROY {
    };
    
    boolean toActive(AtomicReference<StateChannel> stateRef)
    {
      throw new IllegalStateException(this + " cannot activate");
    }
    
    boolean toCloseWrite(AtomicReference<StateChannel> stateRef)
    {
      throw new IllegalStateException(this + " cannot close-write");
    }
    
    boolean toCloseRead(AtomicReference<StateChannel> stateRef)
    {
      throw new IllegalStateException(this + " cannot close-read");
    }
    
    void onHeader(AtomicReference<StateChannel> stateRef)
    {
      throw new IllegalStateException(this + " cannot accept header");
    }
  }
}
