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

package com.caucho.v5.websocket.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.Utf8Util;
import com.caucho.v5.websocket.io.CloseReason.CloseCode;
import com.caucho.v5.websocket.io.CloseReason.CloseCodes;

import io.baratine.io.Buffer;

/**
 * WebSocketInputStream reads a single WebSocket packet.
 *
 * <code><pre>
 * +-+------+---------+-+---------+
 * |F|xxx(3)|opcode(4)|R|len(7)   |
 * +-+------+---------+-+---------+
 * 
 * OPCODES
 *   0 - cont
 *   1 - close
 *   2 - ping
 *   3 - pong
 *   4 - text
 *   5 - binary
 * </pre></code>
 */
public class FrameInputStream
  implements WebSocketConstants
{
  private static final Logger log = Logger.getLogger(FrameInputStream.class.getName());
  private static final L10N L = new L10N(FrameInputStream.class);
  
  private static final char UTF8_ERROR = 0xfeff;
  
  private FrameListener _listener;
  //private WebSocketReader _textIn;
  private WebSocketInputStream _binaryIn;
  
  private ReadBuffer _is;
  
  /*
  private byte []_byteBuffer;
  private int _bufferOffset;
  private int _bufferLength;
  */
  
  private int _op;
  private long _length;
  private boolean _isFinal;
  
  private final byte []_mask = new byte[4];
  private boolean _isMask;
  private int _maskOffset;
  
  private final char []_charBuffer = new char[1];
  private int _frameOp;
  private int _frameOpInit;
  private CloseReason _closeReason;
  
  public void init(FrameListener listener, ReadBuffer is)
  {
    Objects.requireNonNull(is);
    
    _listener = listener;
    
    _is = is;
    /*
    _byteBuffer = is.getBuffer();
    _bufferOffset = is.getOffset();
    _bufferLength = is.getLength();
    */
  }
  
  public FrameListener getListener()
  {
    return _listener;
  }
  
  public int getOpcode()
  {
    return _op;
  }

  public int getFrameOpcode()
  {
    return _frameOp;
  }

  public long length()
  {
    return _length;
  }
  
  public boolean isFinal()
  {
    return _isFinal;
  }
  
  public byte []getMask()
  {
    return _mask;
  }
  
  /*
  public WebSocketReader initReader()
    throws IOException
  {
    if (_textIn == null) {
      _textIn = new WebSocketReader(this);
    }

    _textIn.init();
    
    return _textIn;
  }
  */
  
  public WebSocketInputStream initBinary()
    throws IOException
  {
    if (_binaryIn == null) {
      _binaryIn = new WebSocketInputStream(this);
    }

    _binaryIn.init();
    
    return _binaryIn;
  }

  public final boolean readFrameHeader()
    throws IOException
  {
    if (_is.available() <= 0) {
      return false;
    }
    
    /*
    _byteBuffer = _is.getBuffer();
    _bufferOffset = _is.getOffset();
    _bufferLength = _is.getLength();
    */
    
    long length = length();

    if (length > 0) {
      throw new IllegalStateException(L.l("new frame, but old frame is unfinished"));
    }
    
    readFrameHeaderImpl();
    
    /*
    _is.setOffset(_bufferOffset);
    _is.setLength(_bufferLength);
    */
    
    return true;
    
    /*
    while (true) {
      if (! readFrameHeaderImpl()) {
        return true;
      }

      if (handleFrame()) {
        return true;
      }
    }
    */
  }

  private boolean readFrameHeaderImpl()
    throws IOException
  {
    int frame1 = _is.read();
    int frame2 = _is.read();

    if (frame2 < 0) {
      _isFinal = true;
      _length = 0;
      fail(CloseCodes.CLOSED_ABNORMALLY, "disconnect");
      return false;
    }

    boolean isFinal = (frame1 & FLAG_FIN) == FLAG_FIN;
    int op = frame1 & 0xf;
    
    if (op != 0) {
      _op = op;
      _frameOp = op;
    }
      
    int rsv = frame1 & 0x70;
      
    if (rsv != 0) {
      fail(CloseCodes.PROTOCOL_ERROR, "illegal request");
      return false;
    }

    _isFinal = isFinal;
    
    long length = frame2 & 0x7f;

    if (length < 0x7e) {
    }
    else if (length == 0x7e) {
      length = readShort();
    }
    else {
      length = readLong();
    }

    _length = length;

    _isMask = (frame2 & 0x80) != 0;

    if (_isMask) {
      byte []mask = getMask();

      mask[0] = (byte) _is.read();
      mask[1] = (byte) _is.read();
      mask[2] = (byte) _is.read();
      mask[3] = (byte) _is.read();
        
      _maskOffset = 0;
        
      fillMask();
    }
    
    return true;
  }
  
  private void fail(CloseCodes protocolError, String string)
  {
    log.warning("WebSocket fail: " + protocolError + " " + string);
    
    _op = OP_CLOSE;
    _closeReason = new CloseReason(protocolError, string);
  }
  
  public CloseReason closeReason()
  {
    return _closeReason;
  }

  private boolean handleFrame()
    throws IOException
  {
    switch (getOpcode()) {
    case OP_PING:
      return handlePing();
      
    case OP_PONG:
      return handlePong();
  
    case OP_CLOSE:
      return handleClose();
    }

    return true;
  }
  
  private boolean handlePing()
    throws IOException
  {
    long length = length();
    
    if (! isFinal()) {
      closeError(CloseCodes.PROTOCOL_ERROR, "ping must be final");
      return true;
    }
    else if (length > 125) {
      closeError(CloseCodes.PROTOCOL_ERROR, "ping length must be less than 125");
      return true;
    }
  
    byte []value = new byte[(int) length];
  
    for (int i = 0; i < length; i++) {
      value[i] = (byte) readBinary();
    }

    getListener().onPing(value, 0, value.length);
    
    return false;
  }
  
  private boolean handlePong()
      throws IOException
  {
    if (! isFinal()) {
      closeError(CloseCodes.PROTOCOL_ERROR, "pong must be final");
      return true;
    }
    else if (length() > 125) {
      closeError(CloseCodes.PROTOCOL_ERROR, "pong must be less than 125");
      return true;
    }
  
    long length = length();
    byte []value = new byte[(int) length];
  
    for (int i = 0; i < length; i++) {
      value[i] = (byte) readBinary();
    }
    
    getListener().onPong(value, 0, value.length);
    
    return false;
  }
  
  private boolean handleClose()
      throws IOException
  {
    CloseCode closeCode = CloseCodes.PROTOCOL_ERROR;
    String closeMessage = "error";

    try {
      // if (true) return true;

      long length = length();

      if (length > 125) {
        closeCode = CloseCodes.PROTOCOL_ERROR;
        closeMessage = "close must be less than 125 in length";
      }
      else if (! isFinal()) {
        closeCode = CloseCodes.PROTOCOL_ERROR;
        closeMessage = "close final";
      }
      else if (length > 0) {
        int d1 = readBinary();
        int d2 = readBinary();

        int code = ((d1 & 0xff) << 8) + (d2 & 0xff);

        if (d2 < 0) {
          code = 1002;
        }

        length -= 2;

        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = readText()) >= 0) {
          sb.append((char) ch);
        }
        
        switch (code) {
        case 1000:
        case 1001:
        case 1003:
        case 1007:
        case 1008:
        case 1009:
        case 1010:
          closeCode = CloseCodes.getCloseCode(code);
          closeMessage = sb.toString();
          break;

        default:
          if (3000 <= code && code <= 4999) {
            closeCode = CloseCodes.NORMAL_CLOSURE;
            closeMessage = "ok";
          }
          break;
        }
      }
      else {
        closeCode = CloseCodes.NORMAL_CLOSURE;
        closeMessage = "ok";
      }

      getListener().onClose(new CloseReason(closeCode, closeMessage));

      return false;
    } finally {
      //closeError(closeCode, closeMessage);
    }
  }
  
  public int readText()
      throws IOException
  {
    int len = readText(_charBuffer, 0, 1);

    if (len <= 0) {
      return -1;
    }
    else {
      return _charBuffer[0];
    }
  }
    
  /**
   * Reads a buffer of text from the current message, returning -1 when the
   * message ends.
   */
  public int readText(char []charBuffer, int charOffset, int charLength)
    throws IOException
  {
    byte []byteBuffer = _is.buffer();
    int byteOffset = _is.offset();
    int byteLength = _is.length();

    if (byteLength <= byteOffset || length() == 0) {
      if (! fillFrameBuffer()) {
        return -1;
      }

      byteOffset = _is.offset();
      byteLength = _is.length();
    }
    
    int charEnd = charOffset + charLength;
    int i = charOffset;
    
    int byteBegin = byteOffset;
    int byteEnd = (int) Math.min(byteLength, byteOffset + length());
    
    while (i < charEnd && byteOffset < byteEnd) {
      int d1 = byteBuffer[byteOffset++] & 0xff;
      
      char ch;
      
      if (d1 < 0x80) {
        ch = (char) d1;
      }
      else if ((d1 & 0xe0) == 0xc0) {
        int d2 = byteBuffer[byteOffset++] & 0xff;
      
        ch = (char) (((d1 & 0x1f) << 6) + (d2 & 0x3f));
        
        if (d2 < 0) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
        else if ((d2 & 0xc0) != 0x80) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
        else if (ch < 0x80) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
      }
      else if ((d1 & 0xf0) == 0xe0){
        int d2 = byteBuffer[byteOffset++] & 0xff;
        int d3 = byteBuffer[byteOffset++] & 0xff;
        
        ch = (char) (((d1 & 0x0f) << 12) + ((d2 & 0x3f) << 6) + (d3 & 0x3f)); 

        if (d3 < 0) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
        else if ((d2 & 0xc0) != 0x80) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
        else if ((d3 & 0xc0) != 0x80) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
        else if (ch < 0x800) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
        else if (0xd800 <= ch && ch <= 0xdfff) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
      }
      else if ((d1 & 0xf8) == 0xf0){
        int d2 = byteBuffer[byteOffset++] & 0xff;
        int d3 = byteBuffer[byteOffset++] & 0xff;
        int d4 = byteBuffer[byteOffset++] & 0xff;
        
        int cp = (((d1 & 0x7) << 18)
                   + ((d2 & 0x3f) << 12)
                   + ((d3 & 0x3f) << 6)
                   + ((d4 & 0x3f)));
        
        cp -= 0x10000;
        
        char h = (char) (0xd800 + ((cp >> 10) & 0x3ff));
        
        charBuffer[i++] = h;
        
        ch = (char) (0xdc00 + (cp & 0x3ff));
        
        if (d4 < 0) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
        else if ((d2 & 0xc0) != 0x80) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
        else if ((d3 & 0xc0) != 0x80) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
        else if ((d4 & 0xc0) != 0x80) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
        else if (cp < 0x0) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
        else if (cp >= 0x100000) {
          closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
          ch = UTF8_ERROR;
        }
      }
      else {
        closeError(CloseCodes.NOT_CONSISTENT, "illegal utf-8");
        
        ch = UTF8_ERROR;
      }

      charBuffer[i++] = ch;
    }
    
    _is.offset(byteOffset);
    _length -= (byteOffset - byteBegin);
    
    return i - charOffset;
  }
  
  public void skipToMessageEnd()
    throws IOException
  {
    while (length() > 0 || ! isFinal()) {
      skipBinary(length());
    }
  }
  
  public void skipToFrameEnd()
    throws IOException
  {
    while (length() > 0) {
      _is.skip(length());
    }
  }
  
  public int readBinary()
    throws IOException
  {
    byte []frameBuffer = _is.buffer();
    int bufferOffset = _is.offset();
    int bufferLength = _is.length();
    long frameLength = _length;

    while (bufferLength <= bufferOffset || frameLength == 0) {
      if (! fillFrameBuffer()) {
        return -1;
      }

      bufferOffset = _is.offset();
      bufferLength = _is.length();
      frameLength = _length;
    }

    int value = frameBuffer[bufferOffset++] & 0xff;

    _is.offset(bufferOffset);
    _length = frameLength - 1;
    
    return value;
  }
  
  public int readBinary(byte []buffer, int offset, int length)
    throws IOException
  {
    byte []frameBuffer = _is.buffer();
    int bufferOffset = _is.offset();
    int bufferLength = _is.length();
    long frameLength = _length;

    while (bufferLength <= bufferOffset || frameLength == 0) {
      if (! fillFrameBuffer()) {
        return -1;
      }

      bufferOffset = _is.offset();
      bufferLength = _is.length();
      frameLength = _length;
    }

    int sublen = Math.min(bufferLength - bufferOffset, (int) frameLength);

    sublen = Math.min(sublen, length);

    System.arraycopy(frameBuffer, bufferOffset, buffer, offset, sublen);

    _is.offset(bufferOffset + sublen);
    _length = frameLength - sublen;

    return sublen;
  }
  
  public boolean readBuffer(Buffer buffer)
    throws IOException
  {
    byte []frameBuffer = _is.buffer();
    int bufferOffset = _is.offset();
    int bufferLength = _is.length();
    long frameLength = _length;

    while (true) {
      int sublen = (int) Math.min(bufferLength - bufferOffset, frameLength);
      
      if (sublen > 0) {
        buffer.addBytes(frameBuffer, bufferOffset, sublen);
        bufferOffset += sublen;
        frameLength -= sublen;
      }
      else if (frameLength > 0) {
        _is.offset(bufferOffset);
        
        if (_is.fillBuffer() <= 0) {
          throw new IOException("unexpected eof in websocket");
        }
        
        bufferOffset = _is.offset();
        bufferLength = _is.length();
      }
      else {
        _is.offset(bufferOffset);

        _length = 0;
        
        return _isFinal;
      }
    }
  }
  
  public boolean readText(StringBuilder sb)
    throws IOException
  {
    byte []frameBuffer = _is.buffer();
    int bufferOffset = _is.offset();
    int bufferLength = _is.length();
    long frameLength = _length;

    while (true) {
      int sublen = (int) Math.min(bufferLength - bufferOffset, frameLength);
      
      if (sublen > 0) {
        Utf8Util.fill(sb, frameBuffer, bufferOffset, sublen);
        
        bufferOffset += sublen;
        frameLength -= sublen;
      }
      else if (frameLength > 0) {
        _is.offset(bufferOffset);
        
        if (_is.fillBuffer() <= 0) {
          throw new IOException("unexpected eof in websocket");
        }
        
        bufferOffset = _is.offset();
        bufferLength = _is.length();
      }
      else {
        _is.offset(bufferOffset);

        _length = 0;
        
        return _isFinal;
      }
    }
  }
  
  public long skipBinary(long length)
    throws IOException
  {
    long skipLength = 0;
   
    do {
      int sublen = skipImpl(length);

      if (sublen <= 0) {
        return skipLength > 0 ? skipLength : sublen;
      }
      
      skipLength += sublen;
    } while (skipLength < length);
    
    return skipLength;
  }
  
  private int skipImpl(long length)
    throws IOException
  {
    throw new IllegalStateException();
    /*
    int bufferOffset = _bufferOffset;
    int bufferLength = _bufferLength;
    long frameLength = _length;

    while (bufferLength <= bufferOffset || frameLength == 0) {
      if (! fillFrameBuffer()) {
        return -1;
      }

      bufferOffset = _bufferOffset;
      bufferLength = _bufferLength;
      frameLength = _length;
    }

    int sublen = Math.min(bufferLength - bufferOffset, (int) frameLength);

    sublen = Math.min(sublen, (int) length);

    _bufferOffset = bufferOffset + sublen;
    _length = frameLength - sublen;

    return sublen;
    */
  }
  
  private boolean fillFrameBuffer()
    throws IOException
  {
    while (length() == 0) {
      if (isFinal()) {
        return false;
      }
      
      if (! readFrameHeader()) {
        close();
        return false;
      }
      else if (! handleFrame()) {
        
      }
      else if (getOpcode() != OP_CONT) {
        close();
        closeError(CloseCodes.PROTOCOL_ERROR, "illegal fragment");
        
        throw new IOException(L.l("received illegal fragment"));
        // return false;
      }
      else if (length() > 0) {
        return true;
      }
    }
    
    if (_is.fillBuffer() <= 0) {
      close();
      return false;
    }
    
    if (_isMask) {
      fillMask();
    }
      
    return true;
  }
  
  private void fillMask()
  {
    byte []buffer = _is.buffer();
    int byteOffset = _is.offset();
    int byteLength = _is.length();
    
    int sublen = Math.min((int) _length, byteLength - byteOffset);
    
    byte []mask = getMask();
    int maskOffset = _maskOffset;
    
    for (int i = 0; i < sublen; i++) {
      buffer[byteOffset + i] ^= mask[(maskOffset + i) & 0x3];
    }
    
    _maskOffset = (maskOffset + sublen) & 0x3;
  }
  
  private int readShort()
    throws IOException
  {
    InputStream is = _is;
    
    return ((is.read() << 8) + is.read());
  }
  
  private int readLong()
    throws IOException
  {
    InputStream is = _is;
    
    return ((is.read() << 56L)
        + (is.read() << 48L)
        + (is.read() << 40L)
        + (is.read() << 32L)
        + (is.read() << 24L)
        + (is.read() << 16L)
        + (is.read() << 8L)
        + (is.read() << 0L));
  }

  public int available()
    throws IOException
  {
    return _is.available();
  }

  public void closeError(CloseCode code, String message)
  {
    getListener().onClose(new CloseReason(code, message));
  }
  
  

  public void close()
  {
    ReadBuffer is = _is;
    _is = null;
  }
  
  public interface OutputStreamVisitor
  {
    void write(byte []buffer, int offset, int length)
      throws IOException;
  }
}
