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

package com.caucho.v5.http.websocket;

import static com.caucho.v5.websocket.io.WebSocketConstants.FLAG_FIN;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_BINARY;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_CLOSE;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_CONT;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_PING;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_PONG;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_TEXT;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.http.protocol.OutResponseBase;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.network.port.StateConnection;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.Utf8Util;
import com.caucho.v5.web.webapp.RequestBaratine;
import com.caucho.v5.websocket.io.FrameInputStream;
import com.caucho.v5.websocket.io.WebSocketBaratine;
import com.caucho.v5.websocket.io.WebSocketConstants;

import io.baratine.io.Buffer;
import io.baratine.pipe.Credits;
import io.baratine.pipe.Pipe;
import io.baratine.service.ServiceRef;
import io.baratine.web.ServiceWebSocket;
import io.baratine.web.WebSocketClose;
import io.baratine.web.WebSocketClose.WebSocketCloses;

/**
 * websocket server container
 */
abstract public class WebSocketBase<T,S> implements WebSocketBaratine<S>
{
  private static final L10N L = new L10N(WebSocketBase.class);
  private static final Logger log
    = Logger.getLogger(WebSocketBase.class.getName());
  
  private static final InWebSocketSkip WS_SKIP = new InWebSocketSkip();
  
  private String _uri;
  
  private WebSocketManager _manager;

  private FrameInputStream _fIs;
  
  private InWebSocket _inBinary = WS_SKIP;
  private InWebSocket _inText = WS_SKIP;
  private InWebSocket _inPong;
  private InWebSocket _inPing;
  
  private ServiceWebSocket<T,S> _service;
  
  private char []_charBuf = new char[256];

  private OutResponseBase _os;

  private RequestBaratine _request;
  
  private MessageState _state = MessageState.IDLE;
  private TempBuffer _tBuf;
  private int _headOffset;
  private int _offset;
  private int _frameLength;

  private int _opMessage;
  
  private long _sequenceOut;
  private Credits _credits = new CreditsWebSocket();
  
  protected WebSocketBase(WebSocketManager manager)
  {
    Objects.requireNonNull(manager);
    
    _manager = manager;
  }

  @Override
  public void open()
  {
    if (_inBinary == null) {
      _inBinary = new InWebSocketSkip();
    }
    
    if (_inText == null) {
      _inText = new InWebSocketSkip();
    }
    
    if (_inPing == null) {
    //    _inPing = new InWebSocketPing(_service); 
    }
  }
  
  protected void frameInput(FrameInputStream fIs)
  {
    Objects.requireNonNull(fIs);
    
    _fIs = fIs;
  }

  @Override
  public String uri()
  {
    return _request.uri();
  }

  @Override
  public String path()
  {
    return _request.path();
  }

  @Override
  public String pathInfo()
  {
    return _request.pathInfo();
  }

  @Override
  public void next(S data)
  {
    _sequenceOut++;
    
    try {
      _manager.serialize(this, data);
    } catch (IOException e) {
      throw new RuntimeException(e); 
    }
  }
  
  public Credits credits()
  {
    return _credits;
  }

  @Override
  public void write(Buffer data)
  {
    write(data, true);
  }

  @Override
  public void writePart(Buffer data)
  {
    write(data, false);
  }

  private void write(Buffer buffer, boolean isFinal)
  {
    Objects.requireNonNull(buffer);

    _state = _state.toBinary(this, buffer.length());
    
    if (_tBuf == null) {
      _tBuf = TempBuffer.allocate();
    }
    
    TempBuffer tBuf = _tBuf;
    int tOffset = tBuf.length();
    int tLength = tBuf.buffer().length;
    int offset = 0;
    int length = buffer.length();
    int end = offset + length;
    
    while (true) {
      int sublen = Math.min(end - offset, tLength - tOffset);
      
      buffer.getBytes(offset, tBuf.buffer(), tOffset, sublen);
      
      offset += sublen;
      tOffset += sublen;

      if (offset == end) {
        tBuf.length(tOffset);
        
        fillHeader(isFinal);
        
        if (isFinal) {
          _state = _state.toIdle();
        }
        flush(); // XXX:
        return;
      }
      else if (tOffset == tLength) {
        tBuf.length(tOffset);
        fillHeader(false);
        send(tBuf);
        _tBuf = null;
        // XXX: 
      }
    }
  }

  @Override
  public void write(byte []buffer, int offset, int length)
  {
    write(buffer, offset, length, true);
  }

  @Override
  public void writePart(byte []buffer, int offset, int length)
  {
    write(buffer, offset, length, false);
  }

  private void write(byte []buffer, int offset, int length, boolean isFinal)
  {
    Objects.requireNonNull(buffer);

    _state = _state.toBinary(this, length);
    
    TempBuffer tBuf = _tBuf;
    int tOffset = tBuf.length();
    int tLength = tBuf.buffer().length;
    int end = offset + length;
    
    while (true) {
      int sublen = Math.min(end - offset, tLength - tOffset);
      
      System.arraycopy(buffer, offset, tBuf.buffer(), tOffset, sublen);
      
      offset += sublen;
      tOffset += sublen;
      
      if (offset == end) {
        tBuf.length(tOffset);
        
        fillHeader(isFinal);
        
        if (isFinal) {
          _state = _state.toIdle();
        }
        
        flush(); // XXX:
        return;
      }
      else if (tOffset == tLength) {
        tBuf.length(tOffset);
        fillHeader(false);
        send(tBuf);
        _tBuf = tBuf = TempBuffer.allocate();
        // XXX: 
      }
    }
  }

  @Override
  public void write(String data)
  {
    write(data, true);
  }

  @Override
  public void writePart(String data)
  {
    write(data, false);
  }

  private void write(String data, boolean isFinal)
  {
    int length = data.length();
    
    _state = _state.toText(this, length);
    
    writeString(data, isFinal);
    
    if (isFinal) {
      _state = _state.toIdle();
    }
    
    flush(); // XXX:
  }
  
  @Override
  public void close(WebSocketClose reason, String text)
  {
    Objects.requireNonNull(reason);
    
    _state = _state.toClose(this, text.length() + 2);
    
    TempBuffer tBuf = _tBuf;
    byte []buffer = tBuf.buffer();
    int length = tBuf.length();
    
    int code = reason.code();
    
    buffer[length + 0] = (byte) (code >> 8);
    buffer[length + 1] = (byte) (code);
    
    tBuf.length(length + 2);
    
    writeString(text, true);
    flushEnd();
  }

  @Override
  public void pong(String data)
  {
    toPongFromIdle(data.length());
    
    Objects.requireNonNull(data);

    MessageState state = _state;
    
    _state = MessageState.PONG;
    
    writeString(data, true);
    
    _state = state;
    flush();
  }

  @Override
  public void ping(String data)
  {
    toPongFromIdle(data.length());
    
    Objects.requireNonNull(data);

    MessageState state = _state;
    
    _state = MessageState.PING;
    
    writeString(data, true);
    
    _state = state;
    flush();
  }

  private void writeString(String data, boolean isFinal)
  {
    char cBuf[] = _charBuf;
    int length = data.length();
    int offset = 0;
    
    TempBuffer tBuf = _tBuf;
    
    while (true) {
      int sublen = Math.min(length - offset, cBuf.length);
      
      data.getChars(offset, offset + sublen, cBuf, 0);
      
      int cOffset = Utf8Util.fill(tBuf, cBuf, 0, sublen);
      
      offset += cOffset;
      
      if (offset == length) {
        fillHeader(isFinal);
        return;
      }
      else if (tBuf.buffer().length - tBuf.length() < 4) {
        fillHeader(false);
        send(tBuf);
        _tBuf = tBuf = TempBuffer.allocate();
      }
    }
  }

  //@Override
  protected final void read(ServiceWebSocket<Buffer,S> handler)
  {
    Objects.requireNonNull(handler);

    //_inBinary = new InReadBuffer(wrap(handler));
    _inBinary = new InReadBuffer(handler);
    
    _service = (ServiceWebSocket) handler;
  }

  protected final void readString(ServiceWebSocket<String,S> handler)
  {
    //handler = wrap(handler);
    
    _inText = new InReadString(handler);
    
    _service = (ServiceWebSocket) handler;
  }

  protected void readInputStream(Pipe<InputStream> handler)
  {
    // TODO Auto-generated method stub
    
  }

  protected void readReader(Pipe<Reader> handler)
  {
    // TODO Auto-generated method stub
    
  }

  protected final void readFrame(ServiceWebSocket<Frame,S> handler)
  {
    _inBinary = new InFrameBinary(handler);
    _inText = new InFrameText(handler);
    _inPong = new InFramePong(handler);
    _inPing = new InFramePing(handler);
    
    _service = (ServiceWebSocket) handler;
  }
  
  private void readClose(FrameInputStream fIs)
    throws IOException
  {
    int c1 = fIs.readBinary();
    int c2 = fIs.readBinary();
    
    int code = (c1 << 8) + c2;
    
    StringBuilder sb = new StringBuilder();
    fIs.readText(sb);
    
    if (_service != null) {
      WebSocketClose codeWs = WebSocketCloses.of(code);
      _service.close(codeWs, sb.toString(), this);
    }
    else {
      close();
    }
    //System.out.println("READ_C: " + code + " " + sb);;
  }
  
  protected <X> Pipe<X> wrap(Pipe<X> handler)
  {
    ServiceRefAmp selfRef = ServiceRefAmp.current();
    
    // XXX: calling pinned lambdas is an issue
    //handler = new OutPipeWrapper<>(handler);
    
    Pipe<X> wrappedHandler = selfRef.pin(handler).as(Pipe.class);

    return wrappedHandler;
  }

  public void fail(Throwable exn)
  {
    log.log(Level.WARNING, exn.toString(), exn);
  }
  
  @Override
  public boolean isClosed()
  {
    return false;
  }

  /*
  @Override
  public void read(OutPipe<Buffer> handler, int prefetch)
  {
  }
  */
  
  private void toTextFromIdle(int length)
  {
    TempBuffer tBuf = TempBuffer.allocate();
    _tBuf = tBuf;
    
    if (length >> 2 < 0x7d) {
      _frameLength = 2;
    }
    else {
      _frameLength = 4;
    }
    
    _headOffset = 0;
    tBuf.length(_headOffset + _frameLength);
  }
  
  private void toBinaryFromIdle(int length)
  {
    TempBuffer tBuf = TempBuffer.allocate();
    _tBuf = tBuf;
    
    if (length < 0x7d) {
      _frameLength = 2;
    }
    else {
      _frameLength = 4;
    }
    
    _headOffset = 0;
    tBuf.length(_headOffset + _frameLength);
  }
  
  private void toCloseFromIdle(int length)
  {
    TempBuffer tBuf = TempBuffer.allocate();
    _tBuf = tBuf;
    
    if (length < 0x7d) {
      _frameLength = 2;
    }
    else {
      _frameLength = 4;
    }
    
    _headOffset = 0;
    tBuf.length(_headOffset + _frameLength);
  }
  
  private void toPongFromIdle(int length)
  {
    TempBuffer tBuf = TempBuffer.allocate();
    _tBuf = tBuf;
    
    if (length >> 2 < 0x7d) {
      _frameLength = 2;
    }
    else {
      _frameLength = 4;
    }
    
    _headOffset = 0;
    tBuf.length(_headOffset + _frameLength);
  }
  
  private int fillHeader(boolean isFinal)
  {
    TempBuffer tBuf = _tBuf;

    byte []buffer = tBuf.buffer();
    int tailOffset = tBuf.length();

    // don't flush empty chunk
    if (tailOffset == _headOffset + _frameLength && ! isFinal) {
      return -1;
    }

    int length = tailOffset - _headOffset - _frameLength;

    int code1 = _state.code();
      
    _state = MessageState.CONT;
      
    if (isFinal) {
      code1 |= FLAG_FIN;
    }

    int mask = 0;
    
    /*
    if (_isMasked) {
      mask = 0x80;
        
      for (int i = 0; i < length; i++) {
        buffer[i + 8] ^= (byte) buffer[4 + (i & 0x3)]; 
      }
    }
    */
    
    int headOffset = _headOffset;
    int frameLength = _frameLength;

    if (frameLength == 2) {
      buffer[headOffset + 0] = (byte) code1;
      buffer[headOffset + 1] = (byte) (length | mask);
    }
    else if (frameLength == 4) {
      buffer[headOffset + 0] = (byte) code1;
      buffer[headOffset + 1] = (byte) (0x7e | mask);
      buffer[headOffset + 2] = (byte) (length >> 8);
      buffer[headOffset + 3] = (byte) (length);
    }
    else {
      throw new IllegalStateException(String.valueOf(frameLength));
    }
        
    return 0;
  }

  @Override
  public void flush()
  {
    //complete(false);
    
    TempBuffer tBuf = _tBuf;
    
    if (tBuf == null) {
      return;
    }
    
    _tBuf = null;
    
    send(tBuf);
  }

  //@Override
  public void flushEnd()
  {
    //complete(false);
    
    TempBuffer tBuf = _tBuf;
    _tBuf = null;
    
    sendEnd(tBuf);
  }
  
  private void readPing(FrameInputStream fIs)
    throws IOException
  {
    int len = (int) fIs.length();
    
    boolean isPart = ! fIs.isFinal();
    if (isPart) {
      throw new IllegalStateException();
    }
    
    StringBuilder sb = new StringBuilder();
    
    fIs.readText(sb);
    
    if (_service != null) {
      _service.ping(sb.toString(), WebSocketBase.this);
    }
  }
  
  private void readPong(FrameInputStream fIs)
    throws IOException
  {
    int len = (int) fIs.length();
    
    boolean isPart = ! fIs.isFinal();
    if (isPart) {
      throw new IllegalStateException();
    }
    
    StringBuilder sb = new StringBuilder();
    
    fIs.readText(sb);
    
    if (_service != null) {
      _service.pong(sb.toString(), WebSocketBase.this);
    }
  }
  
  abstract protected void send(TempBuffer tBuf);
  
  protected void sendEnd(TempBuffer tBuf)
  {
    send(tBuf);
  }
  
  //
  // impl
  //

  //@Override
  
  protected boolean readFrame()
  {
    try {
      while (_fIs.readFrameHeader()) {
        int op = _fIs.getFrameOpcode();
        boolean isFinal = _fIs.isFinal();

        switch (op) {
        case WebSocketConstants.OP_BINARY:
          _opMessage = WebSocketConstants.OP_BINARY;

          _inBinary.read(_fIs);
          break;
          
        case WebSocketConstants.OP_TEXT:
          _opMessage = WebSocketConstants.OP_TEXT;

          _inText.read(_fIs);
          break;
      
        case WebSocketConstants.OP_CONT:
          switch (_opMessage) {
          case OP_BINARY:
            _inBinary.read(_fIs);
            break;
            
          case OP_TEXT:
            _inText.read(_fIs);
            break;
            
          default:
            System.out.println("UNKNOWN: " + _opMessage);
            return false;
          }
          
        case WebSocketConstants.OP_CLOSE:
          readClose(_fIs);
          break;
          
        case WebSocketConstants.OP_PING:
          if (_inPing != null) {
            _inPing.read(_fIs);
          }
          else {
            readPing(_fIs);
          }
          break;
          
        case WebSocketConstants.OP_PONG:
          if (_inPong != null) {
            _inPong.read(_fIs);
          }
          else {
            readPong(_fIs);
          }
          break;
      
        default:
          System.out.println("UNKNOWN: " + op);
          return false;
        }
      }
    
      return true;
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
      e.printStackTrace();
      
      return true;
    } finally {
      ServiceRef.flushOutbox();
    }
  }

  public StateConnection service()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + uri() + "]";
  }
  
  //
  // readers
  //
  
  private static interface InWebSocket
  {
    void read(FrameInputStream fIs)
      throws IOException;
    
  }
  
  private static class InWebSocketSkip implements InWebSocket
  {
    @Override
    public void read(FrameInputStream fIs)
      throws IOException
    {
      fIs.skipToFrameEnd();
    }
  }
  
  private class InReadBuffer implements InWebSocket
  {
    private ServiceWebSocket<Buffer,S> _out;
    
    private InReadBuffer(ServiceWebSocket<Buffer,S> out)
    {
      Objects.requireNonNull(out);
      
      _out = out;
    }
    
    @Override
    public void read(FrameInputStream fIs)
      throws IOException
    {
      Buffer buffer = Buffer.create();
      
      fIs.readBuffer(buffer);
      
      _out.next(buffer, WebSocketBase.this);
      
      ServiceRef.flushOutbox();
    }
  }
  
  private class InFrameBinary implements InWebSocket
  {
    private ServiceWebSocket<Frame,S> _out;
    
    private InFrameBinary(ServiceWebSocket<Frame,S> out)
    {
      Objects.requireNonNull(out);
      
      _out = out;
    }
    
    @Override
    public void read(FrameInputStream fIs)
      throws IOException
    {
      int op = fIs.getOpcode();
      int len = (int) fIs.length();
      boolean isPart = ! fIs.isFinal();
      
      Buffer buffer = Buffer.create();
      
      fIs.readBuffer(buffer);
      
      _out.next(new FrameBinary(len, isPart, buffer), WebSocketBase.this);
      /*
      
      byte []buffer = new byte[(int) len];
      
      int sublen = fIs.readBinary(buffer, 0, len);
      
      FrameBinary frame = new FrameBinary(len, isPart, buffer);
      */
      
      //_out.next(frame);
    }
  }
  
  private class InReadString implements InWebSocket
  {
    private ServiceWebSocket<String,S> _out;
    
    private InReadString(ServiceWebSocket<String,S> out)
    {
      Objects.requireNonNull(out);
      
      _out = out;
    }
    
    @Override
    public void read(FrameInputStream fIs)
      throws IOException
    {
      StringBuilder sb = new StringBuilder();
      
      fIs.readText(sb);
      
      _out.next(sb.toString(), WebSocketBase.this);
    }
  }
  
  private class InFrameText implements InWebSocket
  {
    private ServiceWebSocket<Frame,S> _out;
    
    private InFrameText(ServiceWebSocket<Frame,S> out)
    {
      Objects.requireNonNull(out);
      
      _out = out;
    }
    
    @Override
    public void read(FrameInputStream fIs)
      throws IOException
    {
      int op = fIs.getOpcode();
      int len = (int) fIs.length();
      boolean isPart = ! fIs.isFinal();
      
      StringBuilder sb = new StringBuilder();
      
      fIs.readText(sb);
      
      _out.next(new FrameText(len, isPart, sb.toString()), WebSocketBase.this);
    }
  }
  
  private class InFramePong implements InWebSocket
  {
    private ServiceWebSocket<Frame,S> _out;
    
    private InFramePong(ServiceWebSocket<Frame,S> out)
    {
      Objects.requireNonNull(out);
      
      _out = out;
    }
    
    @Override
    public void read(FrameInputStream fIs)
      throws IOException
    {
      int len = (int) fIs.length();
      
      boolean isPart = ! fIs.isFinal();
      if (isPart) {
        throw new IllegalStateException();
      }
      
      StringBuilder sb = new StringBuilder();
      
      fIs.readText(sb);
      
      _out.next(new FramePong(len, isPart, sb.toString()), WebSocketBase.this);
      
      _out.pong(sb.toString(), WebSocketBase.this);
    }
  }
  
  private class InFramePing implements InWebSocket
  {
    private ServiceWebSocket<Frame,S> _out;
    
    private InFramePing(ServiceWebSocket<Frame,S> out)
    {
      Objects.requireNonNull(out);
      
      _out = out;
    }
    
    @Override
    public void read(FrameInputStream fIs)
      throws IOException
    {
      int len = (int) fIs.length();
      
      boolean isPart = ! fIs.isFinal();
      if (isPart) {
        throw new IllegalStateException();
      }
      
      StringBuilder sb = new StringBuilder();
      
      fIs.readText(sb);
      
      _out.next(new FramePing(len, isPart, sb.toString()), 
                WebSocketBase.this);
      
      _out.ping(sb.toString(), WebSocketBase.this);
    }
  }
  
  private class InWebSocketPing implements InWebSocket
  {
    private ServiceWebSocket<?,S> _out;
    
    private InWebSocketPing(ServiceWebSocket<?,S> out)
    {
      Objects.requireNonNull(out);
      
      _out = out;
    }
    
    @Override
    public void read(FrameInputStream fIs)
      throws IOException
    {
      readPing(fIs);
    }
  }
  
  private static class FrameBinary implements Frame
  {
    private long _length;
    private boolean _isPart;
    private Buffer _data;
    
    FrameBinary(int length, 
                boolean isPart,
                Buffer data)
    {
      _length = length;
      _isPart = isPart;
      _data = data;
    }
    
    @Override
    public boolean part()
    {
      return _isPart;
    }

    @Override
    public FrameType type()
    {
      return FrameType.BINARY;
    }

    @Override
    public String text()
    {
      return null;
    }

    @Override
    public Buffer binary()
    {
      return _data;
    }
    
    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
              + "[" + _length
              + (_isPart ? ",part" : "")
              + "," + _data
              + "]");
    }
  }
  
  private static class FrameText implements Frame
  {
    private long _length;
    private boolean _isPart;
    private String _data;
    
    FrameText(int length, 
              boolean isPart,
              String data)
    {
      _length = length;
      _isPart = isPart;
      _data = data;
    }
    
    @Override
    public boolean part()
    {
      return _isPart;
    }

    @Override
    public FrameType type()
    {
      return FrameType.TEXT;
    }

    @Override
    public String text()
    {
      return _data;
    }

    @Override
    public Buffer binary()
    {
      throw new IllegalStateException();
    }
    
    public String toString()
    {
      return (getClass().getSimpleName()
              + "[" + _length
              + (_isPart ? ",part" : "")
              + "," + _data
              + "]");
    }
  }
  
  private static class FramePong extends FrameText
  {
    FramePong(int length, 
              boolean isPart,
              String data)
    {
      super(length, isPart, data);
    }
  }
  
  private static class FramePing extends FrameText
  {
    FramePing(int length, 
              boolean isPart,
              String data)
    {
      super(length, isPart, data);
    }
  }
  
  enum MessageState {
    IDLE {
      @Override
      public MessageState toBinary() { return BINARY; }

      @Override
      public MessageState toText(WebSocketBase ws, int length)
      {
        ws.toTextFromIdle(length);
        
        return TEXT;
      }

      @Override
      public MessageState toBinary(WebSocketBase ws, int length)
      {
        ws.toBinaryFromIdle(length);
        
        return BINARY;
      }

      @Override
      public MessageState toClose(WebSocketBase ws, int length)
      {
        ws.toCloseFromIdle(length);
        
        return CLOSE;
      }
    },
    
    BINARY
    {
      @Override
      public boolean isActive() { return true; }
      
      @Override
      public int code() { return OP_BINARY; }
      
      @Override
      public MessageState toBinary() { return BINARY; }
      
      @Override
      public MessageState toCont() { return CONT; }
    },
    
    TEXT
    {
      @Override
      public boolean isActive() { return true; }
      
      @Override
      public int code() { return OP_TEXT; }
    },
    
    CONT {
      @Override
      public boolean isActive() { return true; }
      
      @Override
      public int code() { return OP_CONT; }

      @Override
      public MessageState toBinary(WebSocketBase ws, int length)
      {
        ws.toBinaryFromIdle(length);
        
        return this;
      }

      @Override
      public MessageState toText(WebSocketBase ws, int length)
      {
        ws.toTextFromIdle(length);
        
        return this;
      }
      
      @Override
      public MessageState toCont() { return CONT; }
    },
    
    PING
    {
      @Override
      public boolean isActive() { return true; }
      
      @Override
      public int code() { return OP_PING; }
    },
    
    PONG
    {
      @Override
      public boolean isActive() { return true; }
      
      @Override
      public int code() { return OP_PONG; }
    },
    
    CLOSE {
      @Override
      public int code() { return OP_CLOSE; }
    },
    
    DESTROYED {
      
    };
    
    public boolean isActive()
    {
      return false;
    }
    
    public MessageState toBinary()
    {
      throw new IllegalStateException(toString());
    }
    
    public MessageState toText(WebSocketBase out, int length)
    {
      throw new IllegalStateException(toString());
    }
    
    public MessageState toClose(WebSocketBase out, int length)
    {
      throw new IllegalStateException(toString());
    }
    
    public MessageState toBinary(WebSocketBase out, int length)
    {
      throw new IllegalStateException(toString());
    }
    
    public MessageState toCont()
    {
      throw new IllegalStateException(toString());
    }
    
    public MessageState toIdle()
    {
      return IDLE;
    }
    
    public int code()    
    {
      throw new IllegalStateException(toString());
    }
  }
  
  /*
  private class OutFlushImpl implements OutWebSocketFlush
  {
    @Override
    public void flush(TempBuffer tBuf, int length)
    {
      byte []buffer = tBuf.buffer();
      
      System.out.println("FLU: " + new String(buffer, 0, length) + " " + Hex.toHex(buffer, 0, length));
      // TODO Auto-generated method stub
      
    }
    
  }
  */
  
  class CreditsWebSocket implements Credits
  {
    private int _prefetch = 64;
    
    @Override
    public long get()
    {
      return _sequenceOut + _prefetch;
    }

    @Override
    public int available()
    {
      return _prefetch;
    }
    
  }
}
