/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.io;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.baratine.io.Buffer;

/**
 * Specialized stream to handle sockets.
 *
 * <p>Unlike VfsStream, when the read() throws and IOException or
 * a SocketException, SocketStream will throw a ClientDisconnectException.
 */
//@ModulePrivate
public class SocketChannelStream extends StreamImpl
{
  private static final Logger log
    = Logger.getLogger(SocketChannelStream.class.getName());
  
  private static byte []UNIX_NEWLINE = new byte[] { (byte) '\n' };
  
  private static final int SIZE = 16 * 1024;

  private SocketChannel _s;
  //private InputStream _is;
  //private OutputStream _os;
  private boolean _needsFlush;
  private byte []_newline = UNIX_NEWLINE;
  
  private ByteBuffer _readBuffer;
  private ByteBuffer _writeBuffer;

  private boolean _throwReadInterrupts = false;
  private boolean _isCloseWrite;

  private long _totalReadBytes;
  private long _totalWriteBytes;

  public SocketChannelStream()
  {
    _readBuffer = ByteBuffer.allocateDirect(SIZE);
    _writeBuffer = ByteBuffer.allocateDirect(SIZE);
  }

  public SocketChannelStream(SocketChannel s)
  {
    this();
    
    init(s);
  }

  /**
   * Initialize the SocketStream with a new Socket.
   *
   * @param s the new socket.
   */
  public void init(SocketChannel s)
  {
    _s = s;
    
    //_is = null;
    //_os = null;
    _needsFlush = false;
    
    _readBuffer.clear().flip();
    _writeBuffer.clear();
  }

  /**
   * Initialize the SocketStream with a new Socket.
   *
   * @param s the new socket.
   */
  /*
  public void init(InputStream is, OutputStream os)
  {
    _is = is;
    _os = os;
    _needsFlush = false;
  }
  */

  /**
   * If true, throws read interrupts instead of returning an end of
   * fail.  Defaults to false.
   */
  public void setThrowReadInterrupts(boolean allowThrow)
  {
    _throwReadInterrupts = allowThrow;
  }

  /**
   * If true, throws read interrupts instead of returning an end of
   * fail.  Defaults to false.
   */
  public boolean getThrowReadInterrupts()
  {
    return _throwReadInterrupts;
  }

  public void setNewline(byte []newline)
  {
    _newline = newline;
  }

  @Override
  public byte []getNewline()
  {
    return _newline;
  }

  /**
   * Returns true if stream is readable and bytes can be skipped.
   */
  @Override
  public boolean hasSkip()
  {
    return canRead();
  }

  /**
   * Skips bytes in the file.
   *
   * @param n the number of bytes to skip
   *
   * @return the actual bytes skipped.
   */
  /*
  @Override
  public long skip(long n)
    throws IOException
  {
    if (_is == null) {
      if (_s == null)
        return -1;

      _is = _s.getInputStream();
    }

    return _is.skip(n);
  }
    */

  /**
   * Returns true since the socket stream can be read.
   */
  @Override
  public boolean canRead()
  {
    //return _is != null || _s != null;
    return _s != null;
  }

  /**
   * Reads bytes from the socket.
   *
   * @param buf byte buffer receiving the bytes
   * @param offset offset into the buffer
   * @param length number of bytes to read
   * @return number of bytes read or -1
   * @exception throws ClientDisconnectException if the connection is dropped
   */
  @Override
  public int read(byte []buf, int offset, int length) throws IOException
  {
    try {
      SocketChannel s = _s;
      
      if (s == null) {
        return -1;
      }
      
      int remaining = _readBuffer.remaining();
      if (remaining <= 0) {
        _readBuffer.clear();
        
        if (s.read(_readBuffer) < 0) {
          _readBuffer.flip();
          return -1;
        }
        
        _readBuffer.flip();
        
        remaining = _readBuffer.remaining();
      }
      
      int sublen = Math.min(remaining, length); 

      _readBuffer.get(buf, offset, sublen);
      
      int readLength = sublen;

      if (readLength >= 0) {
        _totalReadBytes += readLength;
      }

      return readLength;
    } catch (InterruptedIOException e) {
      if (_throwReadInterrupts)
        throw e;
      
      log.log(Level.FINEST, e.toString(), e);
    } catch (IOException e) {
      if (_throwReadInterrupts) {
        throw e;
      }

      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, e.toString(), e);
      }
      else {
        log.finer(e.toString());
      }
      // server/0611
      /*
      try {
        close();
      } catch (IOException e1) {
      }
      */
    }

    return -1;
  }

  /**
   * Reads bytes from the socket.
   *
   * @param buf byte buffer receiving the bytes
   * @param offset offset into the buffer
   * @param length number of bytes to read
   * @return number of bytes read or -1
   * @exception throws ClientDisconnectException if the connection is dropped
   */
  @Override
  public int readTimeout(byte []buf, int offset, int length, long timeout)
    throws IOException
  {
    return read(buf, offset, length);
    /*
    Socket s = _s;
      
    if (s == null) {
      return -1;
    }
    
    int oldTimeout = s.getSoTimeout();
    s.setSoTimeout((int) timeout);
    
    try {
      int result = read(buf, offset, length);
      
      if (result >= 0) {
        return result;
      }
      else if (_is == null || _is.available() < 0) {
        return -1;
      }
      else {
        return ReadBuffer.READ_TIMEOUT;
      }
    } finally {
      s.setSoTimeout(oldTimeout);
    }
    */
  }

  /**
   * Returns the number of bytes available to be read from the input stream.
   */
  @Override
  public int getAvailable() throws IOException
  {
    if (_s == null) {
      return -1;
    }

    return _readBuffer.remaining(); // XXX:
  }

  @Override
  public boolean canWrite()
  {
    return _s != null;
  }

  /**
   * Writes bytes to the socket.
   *
   * @param buf byte buffer containing the bytes
   * @param offset offset into the buffer
   * @param length number of bytes to read
   * @param isEnd if the write is at a close.
   *
   * @exception throws ClientDisconnectException if the connection is dropped
   */
  @Override
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    if (_s == null) {
      return;
    }
    
    try {
      _needsFlush = true;
      
      while (length > 0) {
        _writeBuffer.clear();
        int sublen = Math.min(length, _writeBuffer.remaining());
        
        _writeBuffer.put(buf, offset, sublen);
        _writeBuffer.flip();
        
        _s.write(_writeBuffer);
        
        length -= sublen;
        offset += sublen;
      }

      _totalWriteBytes += length;
    } catch (IOException e) {
      IOException exn = ClientDisconnectException.create(this + ":" + e, e);
      
      try {
        close();
      } catch (IOException e1) {
      }

      throw exn;
    }
  }

  /**
   * Writes an nio buffer to the socket.
   */
  @Override
  public void write(Buffer buffer, boolean isEnd)
    throws IOException
  {
    if (_s == null) {
      buffer.free();
      return;
    }
    
    try {
      _needsFlush = true;
      
      if (buffer.isDirect()) {
        _totalWriteBytes += buffer.length(); 
        _s.write(buffer.direct());
        return;
      }
      
      _totalWriteBytes += buffer.length();
      
      while (buffer.length() > 0) {
        _writeBuffer.clear();
        
        buffer.read(_writeBuffer);
        _writeBuffer.flip();
        
        _s.write(_writeBuffer);
      }
    } catch (IOException e) {
      IOException exn = ClientDisconnectException.create(this + ":" + e, e);
      
      try {
        close();
      } catch (IOException e1) {
      }

      throw exn;
    } finally {
      buffer.free();
    }
  }

  /**
   * Flushes the socket.
   */
  @Override
  public void flush() throws IOException
  {
    /*
    if (_os == null || ! _needsFlush)
      return;

    _needsFlush = false;
    try {
      _os.flush();
    } catch (IOException e) {
      try {
        close();
      } catch (IOException e1) {
      }
      
      throw ClientDisconnectException.create(e);
    }
    */
  }

  public void resetTotalBytes()
  {
    _totalReadBytes = 0;
    _totalWriteBytes = 0;
  }

  public long getTotalReadBytes()
  {
    return _totalReadBytes;
  }

  public long getTotalWriteBytes()
  {
    return _totalWriteBytes;
  }

  /**
   * Closes the write half of the stream.
   */
  @Override
  public void closeWrite() throws IOException
  {
    if (_isCloseWrite) {
      return;
    }
    
    _isCloseWrite = true;
    
    //OutputStream os = _os;
    //_os = null;
    
    boolean isShutdownOutput = false;

    // since the output stream is opened lazily, we might
    // need to open it
    if (_s != null) {
      try {
        _s.shutdownOutput();
        
        isShutdownOutput = true;
      } catch (UnsupportedOperationException e) {
        log.log(Level.FINEST, e.toString(), e);
      } catch (Exception e) {
        log.finer(e.toString());
        log.log(Level.FINEST, e.toString(), e);
      }
    }

    /*
    // SSLSocket doesn't support shutdownOutput()
    if (! isShutdownOutput && os != null) {
      os.close();
    }
    */
  }

  /**
   * Closes the underlying sockets and socket streams.
   */
  @Override
  public void close() throws IOException
  {
    SocketChannel s = _s;
    _s = null;
    
    if (s != null) {
      s.close();
    }

    /*
    OutputStream os = _os;
    _os = null;

    InputStream is = _is;
    _is = null;

    try {
      if (os != null)
        os.close();
      
      if (is != null)
        is.close();
    } finally {
      if (s != null)
        s.close();
    }
  */
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _s + "]";
  }
}

