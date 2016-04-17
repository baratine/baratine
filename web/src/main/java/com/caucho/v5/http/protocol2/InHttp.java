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

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.L10N;

/**
 * InputStreamHttp reads a single HTTP frame.
 */
public class InHttp
{
  private static final Logger log = Logger.getLogger(InHttp.class.getName());
  private static final L10N L = new L10N(InHttp.class);

  private final ConnectionHttp2Int _conn;
  private final InHttpHandler _inHandler;
  
  private SettingsHttp _settings = new SettingsHttp();
  
  private SettingsHttp _peerSettings = new SettingsHttp();
  
  private ReadStream _is;
  private InHeader _inHeader;
  private final byte []_header = new byte[8];
  
  // reference count for peer-initiated streams.
  private final AtomicInteger _openStream = new AtomicInteger();
  
  private boolean _isGoAway;

  public InHttp(ConnectionHttp2Int conn, 
                InHttpHandler inHandler)
  {
    Objects.requireNonNull(conn);
    Objects.requireNonNull(inHandler);
    
    _conn = conn;
    
    _inHandler = inHandler;
  }

  public InHttp(ReadStream is, InHttpHandler inHandler)
  {
    this(new DummyConnection(inHandler), inHandler);
    
    init(is);
  }
  
  protected PeerHttp getPeer()
  {
    return _conn.getPeer();
  }

  public SettingsHttp getSettings()
  {
    return _settings;
  }
  
  public SettingsHttp peerSettings()
  {
    return _peerSettings;
  }

  public void setWindow(int window)
  {
    _settings.initialWindowSize(window);
  }
  
  void init(ReadStream is)
  {
    Objects.requireNonNull(is);
    
    _is = is;
    
    switch (getPeer()) {
    case SERVER:
      _inHeader = new InHeaderRequest(_is);
      break;
      
    case CLIENT:
      _inHeader = new InHeaderResponse(_is);
      break;
      
    default:
      throw new IllegalStateException();
    }
      
    _openStream.set(1);
    _isGoAway = false;
    _peerSettings.initialWindowSize(64 * 1024);
  }
  
  public boolean onDataAvailable()
    throws IOException
  {
    int isData = -1;
    
    ReadStream is = _is;
    byte []header = _header;

    if (is == null || is.available() <= 0 || _conn.isClosed()) {
      return false;
    }

    do {
      if (! readFrame(is, header)) {
        return false;
      }
        
      // isData = 1;
    } while ((isData = is.available()) > 0);
        
    return isData >= 0;
  }
  
  boolean onPeerOpen()
  {
    int value;

    do {
      value = _openStream.get();
    } while (value > 0 && ! _openStream.compareAndSet(value, value + 1));

    return value > 0;
  }
  
  int onCloseStream()
  {
    return _openStream.decrementAndGet();
  }
  
  private boolean readFrame(ReadStream is, byte []header)
    throws IOException
  {
    if (is == null) {
      return false;
    }
    
    int length = BitsUtil.readInt16(is);
    int type = is.read();
    int flags = is.read();
    
    int streamId = BitsUtil.readInt(is);
    
    // log.warning("FRAME: ty:" + type + " ln:" + length + " sid:" + streamId + " fl:" + flags);
    // System.out.println("FRAME: ty:" + type + " ln:" + length + " sid:" + streamId + " fl:" + flags);

    if (streamId < 0) {
      return false;
    }
    
    switch (type) {
    case Http2Constants.FRAME_DATA:
      return readData(is, length, flags, streamId);
      
    case Http2Constants.FRAME_HEADERS:
      return readHeader(is, length, flags, streamId);
      
    case Http2Constants.FRAME_SETTINGS:
      return readSettings(is, length);
      
    case Http2Constants.FRAME_WINDOW_UPDATE:
      return readFlow(is, length, streamId);
      
    case Http2Constants.FRAME_RST_STREAM:
      return readReset(is, length, streamId);
      
    case Http2Constants.FRAME_PRIORITY:
      return readPriority(is, length, streamId);
      
    case Http2Constants.FRAME_GOAWAY:
      return readGoAway(is, length);
      
    case Http2Constants.FRAME_BLOCKED:
      return readBlocked(is, length, streamId);
      
    default:
      error("Unknown frame type {0}", type);
      return false;
    }
  }
  
  public boolean readSettings()
    throws IOException
  {
    ReadStream is = _is;
    
    int length = BitsUtil.readInt24(is);
    int type = is.read();
    int flags = is.read();

    int streamId = BitsUtil.readInt(is);
    
    if (streamId < 0) {
      return false;
    }
    
    if (type != Http2Constants.FRAME_SETTINGS) {
      error("Expected settings at type={0} length={1} streamId={2}",
            type, length, streamId);
      
      return false;
    }
    
    return readSettings(_is, length);
  }
  
  /**
   * settings
   */
  private boolean readSettings(ReadStream is, int length)
    throws IOException
  {
    if (length % 6 != 0) {
      error("Invalid settings length {0}", length);
      return false;
    }
    
    for (int i = 0; i < length; i += 6) {
      int code = BitsUtil.readInt16(is);
      int value = BitsUtil.readInt(is);
      
      switch (code) {
      case Http2Constants.SETTINGS_INITIAL_WINDOW_SIZE:
        _peerSettings.initialWindowSize(value);
        break;
      }
    }
    
    return true;
  }
  
  /**
   * window-update
   */
  private boolean readFlow(ReadStream is, int length, int streamId)
    throws IOException
  {
    if (length != 4) {
      error("Invalid window update length {0}", length);
      return false;
    }
    
    int credit = BitsUtil.readInt(is);
    
    if (streamId == 0) {
      _conn.getChannelZero().addSendCredit(credit);
      return true;
    }
    
    ChannelHttp2 channel = _conn.getChannel(streamId);
    
    if (channel == null) {
      return true;
    }
    
    channel.getOutChannel().addSendCredit(_conn.outHttp(), credit);
    
    return true;
  }
  
  /**
   * blocked
   */
  private boolean readBlocked(ReadStream is, int length, int streamId)
    throws IOException
  {
    if (length != 0) {
      error("Invalid blocked length {0}", length);
      return false;
    }

    if (log.isLoggable(Level.FINE)) {
      log.fine(L.l("Stream {0} is blocked (in {1})", streamId, this));
    }

    return true;
  }
  
  /**
   * go-away
   */
  private boolean readGoAway(ReadStream is, int length)
    throws IOException
  {
    int lastStream = BitsUtil.readInt(is);
    int errorCode = BitsUtil.readInt(is);
    
    is.skip(length - 8);
    
    _isGoAway = true;
    
    _conn.onReadGoAway();
    
    if (onCloseStream() <= 0) {
      _inHandler.onGoAway();
    }
    
    return true;
  }
  
  /**
   * rst_stream
   */
  private boolean readReset(ReadStream is, int length, int streamId)
    throws IOException
  {
    int errorCode = BitsUtil.readInt(is);
    
    is.skip(length - 4);
    
    _isGoAway = true;
    
    if (streamId == 0) {
      _inHandler.onGoAway();
      IoUtil.close(is);
      // XXX: close
      return true;
    }

    ChannelHttp2 channel = _conn.getChannel(streamId);
    
    if (channel != null) {
      channel.getInChannel().resetStream(errorCode);
 
      if (onCloseStream() <= 0) {
        _inHandler.onGoAway();
      }
     }
    
    return true;
  }
  
  /**
   * priority
   */
  private boolean readPriority(ReadStream is, int length, int streamId)
    throws IOException
  {
    int streamRef = BitsUtil.readInt(is);
    int weight = is.read();
    
    is.skip(length - 5);
    
    return true;
  }
  
  /**
   * data (op=0)
   */
  private boolean readData(ReadStream is,
                           int length,
                           int flags,
                           int streamId)
    throws IOException
  {
    ChannelHttp2 channel = _conn.getChannel(streamId);
    
    if (channel == null) {
      throw new IllegalStateException(L.l("Stream {0} is closed", streamId));
    }
    
    ChannelInHttp2 inChannel = channel.getInChannel();
    
    ChannelFlowHttp2 channelZero = _conn.getChannelZero();
    channelZero.onData(length, _conn, 0);
    
    boolean isEndStream = (flags & Http2Constants.END_STREAM) != 0;

    if (! isEndStream) {
      inChannel.onData(length, _conn, streamId);
    }
    
    while (length > 0) {
      TempBuffer tBuf;
    
      if (length < TempBuffer.SMALL_SIZE) {
        tBuf = TempBuffer.createSmall();
      }
      else {
        tBuf = TempBuffer.create();
      }
      
      byte []buffer = tBuf.buffer();
      
      int sublen = Math.min(length, buffer.length);
      
      sublen = is.readAll(buffer, 0, sublen);
      
      if (sublen < 0) {
        tBuf.free();
        
        return false;
      }
      
      tBuf.length(sublen);
      inChannel.onData(tBuf);
      
      length -= sublen;
    }
    
    if (isEndStream) {
      inChannel.close();
      
      if (onCloseStream() <= 0) {
        _inHandler.onGoAway();
      }
    }
    
    return true;
  }
  
  InRequest openInitialHeader(boolean isEndStream)
  {
    if (! onPeerOpen()) {
      log.warning("peer-open failed " + this);
      // stream open request after goaway
      return null;
    }
    
    int streamId = 1;
    
    InRequest request = _inHandler.newInRequest();
    
    ChannelHttp2 channel = request.getChannel(); // new InChannelHttp2(_conn, streamId, request);
    
    channel.getInChannel().addReceiveCredit(_settings.initialWindowSize());

    channel.init(_conn, streamId);
    
    if (isEndStream) {
      channel.closeRead();
    }
    
    return request;
  }
  
  /**
   * header (op=1)
   */
  private boolean readHeader(ReadStream is,
                             int length,
                             int flags,
                             int streamId)
    throws IOException
  {
    if (! onPeerOpen()) {
      log.warning("peer-open failed " + this);
      // stream open request after goaway
      return false;
    }
    
    if (streamId <= 0) {
      throw new IllegalStateException("Invalid stream: " + streamId);
    }
    
    
    ChannelHttp2 channel = _conn.getChannel(streamId);
    
    InRequest request;
    
    if (channel != null) {
      request = channel.getRequest();
    }
    else {
      request = _inHandler.newInRequest();
    }
    
    // _conn, streamId);
    if (! _inHeader.readHeaders(request, length, flags)) {
      return false;
    }
    
    if (channel == null) {
      channel = request.getChannel(); // new InChannelHttp2(_conn, streamId, request);
      // stream.addReceiveCredit(_settings.getInitialWindowSize());
    
      channel.init(_conn, streamId);
    }
    
    channel.onHeader(streamId);
    
    channel.getInChannel().addReceiveCredit(_settings.initialWindowSize());
    
    request.dispatch();
    
    boolean isEndStream = (flags & Http2Constants.END_STREAM) != 0;
    
    if (isEndStream) {
      channel.closeRead();
      
      if (onCloseStream() <= 0) {
        _inHandler.onGoAway();
      }
    }
    
    return true;
  }
  
  private void error(String msg, Object ...args)
  {
    System.out.println(L.l(msg, args));
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _conn + "]";
  }
  
  private static class DummyConnection extends ConnectionHttp2Int
  {
    protected DummyConnection(InHttpHandler handler)
    {
      super(handler, PeerHttp.SERVER);
    }
    
    @Override
    public boolean isClosed()
    {
      return false;
    }
    
    @Override
    public void putChannel(int streamId, ChannelHttp2 channel)
    {
    }
  }
}
