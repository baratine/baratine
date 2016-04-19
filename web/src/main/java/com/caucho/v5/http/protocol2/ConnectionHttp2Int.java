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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.WriteStream;

/**
 * InputStreamHttp reads a single HTTP frame.
 */
public class ConnectionHttp2Int
{
  private final InHttp _inHttp;
  private final OutHttp2 _outHttp;
  private final PeerHttp _peer;
  
  private ChannelFlowHttp2 _channel0;
  
  private ConcurrentHashMap<Integer,ChannelHttp2> _channelMap
    = new ConcurrentHashMap<>();
    
  private AtomicInteger _channelCount = new AtomicInteger();
  private InHttpHandler _inHandler;
  
  public ConnectionHttp2Int(InHttpHandler handler, 
                            PeerHttp peer)
  {
    Objects.requireNonNull(handler);
    Objects.requireNonNull(peer);
    
    _inHandler = handler;
    
    _inHttp = new InHttp(this, handler);
    _outHttp = new OutHttp2(this, peer);
    
    _channel0 = new ChannelFlowHttp2();
    
    _peer = peer;
  }

  /**
   * Constructor for testing.
   */
  public ConnectionHttp2Int(PeerHttp peer)
  {
    this(new DummyHandler(), peer);
  }
  
  public PeerHttp getPeer()
  {
    return _peer;
  }

  public boolean isHeaderHuffman()
  {
    return _inHandler.isHeaderHuffman();
  }

  public InHttp inHttp()
  {
    return _inHttp;
  }

  public OutHttp2 outHttp()
  {
    return _outHttp;
  }
  
  public void init(ReadStream is, WriteStream os)
  {
    outHttp().init(os);
    inHttp().init(is);
    
    _channel0.init();
    
    _channelCount.set(1);
  }
  
  public void init(ReadStream is)
  {
    inHttp().init(is);
    
    _channel0.init();
    
    _channelCount.set(1);
  }
  
  public void init(WriteStream os)
  {
    outHttp().init(os);
    
    _channel0.init();
    
    _channelCount.set(1);
  }
  
  public ChannelFlowHttp2 channelZero()
  {
    return _channel0;
  }
  
  public ChannelHttp2 getChannel(int streamId)
  {
    return _channelMap.get(streamId);
  }
  
  public void putChannel(int streamId, ChannelHttp2 channel)
  {
    if (_channelCount.get() == 0) {
      throw new IllegalStateException();
    }
    
    _channelMap.put(streamId, channel);
    
    if (_channelCount.get() == 0) {
      throw new IllegalStateException();
    }
    
    _channelCount.incrementAndGet();
  }

  public void onStreamOutClose()
  {
    InHttp inHttp = _inHttp;
    
    if (inHttp != null) {
      if (inHttp.onCloseStream() <= 0) {
        _outHttp.close();
      }
    }
  }
  
  void onReadGoAway()
  {
    _channelCount.decrementAndGet();

    if (isClosed()) {
      _outHttp.close();
      //_inHandler.onGoAway();
    }
  }
  
  void onWriteGoAway()
  {
    _inHandler.onGoAway();
  }

  void closeStream(int streamId)
  {
    _channelMap.remove(streamId);
    _channelCount.decrementAndGet();
    
    if (isClosed()) {
      _outHttp.close();
      //_inHandler.onGoAway();
    }
  }

  public boolean isClosed()
  {
    return _channelCount.get() == 0;
  }

  /**
   * Called when the socket read is complete.
   */
  public void closeRead()
  {
  }

  public void closeWrite()
  {
    
  }

  /**
   * Dummy handler for QA.
   */
  private static class DummyHandler implements InHttpHandler {
    @Override
    public InRequest newInRequest()
    {
      return null;
    }

    @Override
    public void onGoAway()
    {
    }
    
  }
}
