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

package com.caucho.v5.bartender.websocket;

import static com.caucho.v5.websocket.io.WebSocketConstants.FLAG_FIN;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_BINARY;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_TEXT;
import static io.baratine.web.HttpStatus.METHOD_NOT_ALLOWED;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.http.protocol.OutHttp;
import com.caucho.v5.http.protocol.OutHttpProxy;
import com.caucho.v5.http.protocol.OutResponseBase;
import com.caucho.v5.http.websocket.ConnectionWebSocketBaratine;
import com.caucho.v5.http.websocket.WebSocketBase;
import com.caucho.v5.http.websocket.WebSocketManager;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.network.port.StateConnection;
import com.caucho.v5.util.Base64Util;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.ModulePrivate;
import com.caucho.v5.util.Utf8Util;
import com.caucho.v5.web.webapp.RequestBaratine;
import com.caucho.v5.web.webapp.RequestBaratineImpl;
import com.caucho.v5.websocket.io.FrameInputStream;
import com.caucho.v5.websocket.io.WebSocketBaratine;
import com.caucho.v5.websocket.io.WebSocketConstants;

import io.baratine.io.Buffer;
import io.baratine.io.Buffers;
import io.baratine.pipe.Pipe;
import io.baratine.service.ServiceRef;
import io.baratine.web.HttpStatus;
import io.baratine.web.ServiceWebSocket;

/**
 * websocket server container
 */
@ModulePrivate
public class WebSocketBartender<T,S>
  extends WebSocketBase<T,S>
  implements WebSocketBaratine<S>
{
  private static final L10N L = new L10N(WebSocketBartender.class);
  private static final Logger log
    = Logger.getLogger(WebSocketBartender.class.getName());
  
  private String _uri;

  //private FrameInputStream _fIs;
  
  private InWebSocket _inBinary = new InWebSocketSkip();
  private InWebSocket _inText = new InWebSocketSkip();
  
  private char []_charBuf = new char[256];

  private OutResponseBase _os;

  private RequestBaratine _request;
  private ServiceWebSocket _service;
  
  private MessageState _state = MessageState.IDLE;
  private TempBuffer _tBuf;
  private int _headOffset;
  private int _offset;
  private int _frameLength;
  private OutHttpProxy _outProxy;
  private OutWebSocketWriter _outWriter;
  private int _opMessage;

  public WebSocketBartender(ServiceWebSocket service)
  {
    super(new WebSocketManager());
    
    Objects.requireNonNull(service);
    _service = service;
  }

  public boolean handshake(RequestBaratine request)
    throws Exception
  {
    Objects.requireNonNull(request);
    
    _request = request;
    
    RequestBaratineImpl req = (RequestBaratineImpl) request;
    //ResponseBaratineImpl res = (ResponseBaratineImpl) response;
    
    if (log.isLoggable(Level.FINE)) {
      log.fine(this + " upgrade HTTP to WebSocket");
    }

    String method = req.method();
    String uri = req.uri();

    if (! "GET".equals(method)) {
      req.halt(METHOD_NOT_ALLOWED);

      throw new IllegalStateException(L.l("HTTP Method must be 'GET', because the WebSocket protocol requires 'GET'.\n  remote-IP: {0}",
                                          req.ip()));
    }

    String connection = req.header("Connection");
    String upgrade = req.header("Upgrade");

    if (! "websocket".equalsIgnoreCase(upgrade)) {
      req.halt(HttpStatus.BAD_REQUEST);

      throw new IllegalStateException(L.l("HTTP Upgrade header '{0}' must be 'WebSocket', because the WebSocket protocol requires an Upgrade: WebSocket header.\n  remote-IP: {1}",
                                          upgrade,
                                          req.ip()));
    }

    if (connection == null
        || connection.toLowerCase().indexOf("upgrade") < 0) {
      req.halt(HttpStatus.BAD_REQUEST);

      throw new IllegalStateException(L.l("HTTP Connection header '{0}' must be 'Upgrade', because the WebSocket protocol requires a Connection: Upgrade header.\n  remote-IP: {1}",
                                          connection,
                                          req.ip()));
    }

    String key = req.header("Sec-WebSocket-Key");

    if (key == null) {
      req.halt(HttpStatus.BAD_REQUEST);

      throw new IllegalStateException(L.l("HTTP Sec-WebSocket-Key header is required, because the WebSocket protocol requires an Origin header.\n  remote-IP: {0}",
                                          req.ip()));
    }
    else if (key.length() != 24) {
      req.halt(HttpStatus.BAD_REQUEST);

      throw new IllegalStateException(L.l("HTTP Sec-WebSocket-Key header is invalid '{0}' because it's not a 16-byte value.\n  remote-IP: {1}",
                                          key,
                                          req.ip()));
    }

    String version = req.header("Sec-WebSocket-Version");

    String requiredVersion = WebSocketConstants.VERSION;
    if (! requiredVersion.equals(version)) {
      req.halt(HttpStatus.BAD_REQUEST);

      throw new IllegalStateException(L.l("HTTP Sec-WebSocket-Version header with value '{0}' is required, because the WebSocket protocol requires an Sec-WebSocket-Version header.\n  remote-IP: {1}",
                                          requiredVersion,
                                          req.ip()));
    }

    String extensions = req.header("Sec-WebSocket-Extensions");
    boolean isMasked = true;

    if (extensions != null && extensions.indexOf("x-unmasked") >= 0) {
      isMasked = false;
    }

    ArrayList<String> serverExtensionList = new ArrayList<String>();

    if (! isMasked) {
      serverExtensionList.add("x-unmasked");
    }

    req.status(HttpStatus.SWITCHING_PROTOCOLS);//, "Switching Protocols");
    req.header("Upgrade", "websocket");
    req.header("Connection", "Upgrade");

    String accept = calculateWebSocketAccept(key);

    req.header("Sec-WebSocket-Accept", accept);

    if (serverExtensionList.size() > 0) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < serverExtensionList.size(); i++) {
        if (i > 0)
          sb.append(", ");

        sb.append(serverExtensionList.get(i));
      }

      req.header("Sec-WebSocket-Extensions", sb.toString());
    }

    req.length(0);
    
    connect(req);
    
    return true;
  }
  
  public void connect(RequestBaratineImpl req)
  {
    FrameInputStream fIs;

    fIs = new FrameInputStream();
    
    // _fIs = fIs;
    
    fIs.init(null, req.requestHttp().connTcp().readStream());
    
    frameInput(fIs);

    // Endpoint endpoint = _endpointSkeleton.newEndpoint(_factory, paths);
    // Endpoint endpoint = wsCxt.getFactory().get();

    //WebSocketBaratine wsConn = null;;

    ConnectionWebSocketBaratine connWs
      = new ConnectionWebSocketBaratine(this, req.requestHttp());

    // order for duplex
    req.upgrade(connWs);
    
    req.flush();
    
    _outProxy = req.connHttp().outProxy();
    
    _outWriter = new OutWebSocketWriter();

    _service.open(this);
    
    ServiceRef.flushOutbox();
  }
  
  /*
  //@Override
  public void init(ReadStream is, OutResponseBase os)
  {
    Objects.requireNonNull(is);
    Objects.requireNonNull(os);
    
    _fIs.init(this, is);
    
    _os = os;
  }
  */

  //@Override
  public void open()
  {
    if (_inBinary == null) {
      _inBinary = new InWebSocketSkip();
    }
    
    if (_inText == null) {
      _inText = new InWebSocketSkip();
    }
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
  public void write(Buffer data)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void writePart(Buffer data)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void write(byte []buffer, int offset, int length)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void writePart(byte []buffer, int offset, int length)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void write(String data)
  {
    char cBuf[] = _charBuf;
    int length = data.length();
    int offset = 0;
    
    _state = _state.toText(this, length);
    
    TempBuffer tBuf = _tBuf;
    
    while (true) {
      int sublen = Math.min(length - offset, cBuf.length);
      
      data.getChars(offset, offset + sublen, cBuf, 0);
      
      int cOffset = Utf8Util.fill(tBuf, cBuf, 0, sublen);
      
      offset += cOffset;
      
      if (offset == length) {
        fillHeader(true);
        _state = _state.toIdle();
        return;
      }
      else if (tBuf.buffer().length - tBuf.length() < 4) {
        fillHeader(false);
        send(tBuf);
      }
    }
  }

  @Override
  public void writePart(String data)
  {
    // TODO Auto-generated method stub
    
  }

  /*
  @Override
  public void read(OutStream<Buffer> handler)
  {
    _inBinary = new InReadBinary(handler);
    
    System.out.println("INB: " + _inBinary);
  }
  */

  /*
  @Override
  public void readChunk(OutStream<Chunk<Buffer>> handler)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void readStringChunk(OutStream<Chunk<String>> handler)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void readString(OutStream<String> handler)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void readInputStream(OutStream<InputStream> handler)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void readReader(OutStream<Reader> handler)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void readFrame(OutStream<Frame> handler)
  {
    _inBinary = new InFrameBinary(handler);
  }

  @Override
  public void close(int reason, String text)
  {
    System.out.println("CLOSE: " + reason);
  }
  */

  /*
  @Override
  public void read(OutPipe<Buffer> handler, int prefetch)
  {
  }
  */
  
  private void toTextFromIdle(int length)
  {
    TempBuffer tBuf = TempBuffer.create();
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
  
  @Override
  protected void send(TempBuffer tBuf)
  {
    _outProxy.write(_outWriter, tBuf, false);
  }
  
  /*
  private OutWebSocket2 getOut()
  {
    OutWebSocket2 outWs = _outWs;
    
    if (outWs == null) {
      _outWs = outWs = new OutWebSocket2(new OutFlushImpl());
      outWs.setMasked(false);
    }
    outWs.init();
    
    return outWs;
  }
  */
  
  //
  // impl
  //

  //@Override
  /*
  public StateConnection serviceOld()
  {
    try {
      System.out.println("SVC-3: " + this + " " + _fIs); 
      while (_fIs.readFrameHeader()) {
        int op = _fIs.getFrameOpcode();
        boolean isFinal = _fIs.isFinal();
        
        System.out.println("  OP: " + op);

        switch (op) {
        case WebSocketConstants.OP_BINARY:
          _opMessage = WebSocketConstants.OP_BINARY;

          _inBinary.read(_fIs);
          break;
      
        case WebSocketConstants.OP_CONT:
          switch (_opMessage) {
          case OP_BINARY:
            _inBinary.read(_fIs);
            break;
            
          default:
            System.out.println("UNKNOWN: " + _opMessage);
            return StateConnection.CLOSE;
          }
      
        default:
          return StateConnection.CLOSE;
        }
      }
    
      return StateConnection.READ;
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
      e.printStackTrace();
      
      return StateConnection.CLOSE;
    }
  }
  */
  
  public StateConnection service()
  {
    if (readFrame()) {
      return StateConnection.READ;
    }
    else {
      return StateConnection.CLOSE;
    }
  }

  private String calculateWebSocketAccept(String key)
  {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA1");

      int length = key.length();
      for (int i = 0; i < length; i++) {
        md.update((byte) key.charAt(i));
      }

      String guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
      length = guid.length();
      for (int i = 0; i < length; i++) {
        md.update((byte) guid.charAt(i));
      }

      byte []digest = md.digest();

      return Base64Util.encode(digest);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String toString()
  {
    if (_request != null) {
      return getClass().getSimpleName() + "[" + _request.uri() + "]";
    }
    else {
      return getClass().getSimpleName() + "[]";
    }
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
  
  private static class InReadBinary implements InWebSocket
  {
    private Pipe<Buffer> _out;
    
    private InReadBinary(Pipe<Buffer> out)
    {
      Objects.requireNonNull(out);
      
      _out = out;
    }
    
    @Override
    public void read(FrameInputStream fIs)
      throws IOException
    {
      Buffer buffer = Buffers.factory().create();
      
      fIs.readBuffer(buffer);
      
      _out.next(buffer);
    }
  }
  
  private static class InFrameBinary implements InWebSocket
  {
    private Pipe<Frame> _out;
    
    private InFrameBinary(Pipe<Frame> out)
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
      
      byte []buffer = new byte[(int) len];
      
      int sublen = fIs.readBinary(buffer, 0, len);
      
      FrameBinary frame = new FrameBinary(len, isPart, buffer);
      
      _out.next(frame);
    }
  }
  
  private static class FrameBinary implements Frame
  {
    private long _length;
    private boolean _isPart;
    private byte []_data;
    
    FrameBinary(int length, 
                boolean isPart,
                byte []buffer)
    {
      _length = length;
      _isPart = isPart;
      _data = buffer;
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
      return Buffers.factory().create(_data);
    }
    
    public String toString()
    {
      return (getClass().getSimpleName()
              + "[" + _length
              + "," + Hex.toShortHex(_data, 0, (int) _length)
              + (_isPart ? ",part" : "")
              + "]");
    }
  }
  
  enum MessageState {
    IDLE {
      @Override
      public MessageState toBinary() { return BINARY; }

      @Override
      public MessageState toText(WebSocketBartender ws, int length)
      {
        ws.toTextFromIdle(length);
        
        return TEXT;
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
      public MessageState toCont() { return CONT; }
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
    
    public MessageState toText(WebSocketBartender out, int length)
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
  
  private class OutWebSocketWriter implements OutHttp
  {
    @Override
    public boolean write(WriteStream out,
                         Buffer buffer,
                         boolean isEnd)
    {
      if (out != null) {
        try {
          out.write(buffer);
          out.flush(); // xxx:
        } catch (Exception e) {
          log.log(Level.WARNING, e.toString(), e);
        }
      }
      
      return false;
    }

    @Override
    public void disconnect(WriteStream out)
    {
    }

    @Override
    public boolean canWrite(long sequence)
    {
      return true;
    }
  }
}
