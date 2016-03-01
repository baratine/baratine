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
 * @author Nam Nguyen
 */

package com.caucho.v5.vfs;

import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.logging.*;

import com.caucho.v5.io.ClientDisconnectException;
import com.caucho.v5.io.StreamImpl;

/**
 * Specialized stream to handle sockets.
 *
 * <p>Unlike VfsStream, when the read() throws and IOException or
 * a SocketException, SocketStream will throw a ClientDisconnectException.
 */
public class DatagramStream extends StreamImpl {
  private static final Logger log
    = Logger.getLogger(DatagramStream.class.getName());
  
  private DatagramSocket _s;
  
  private DatagramInputStream _is;
  private DatagramOutputStream _os;

  private boolean _throwReadInterrupts = false;

  private long _totalReadBytes;
  private long _totalWriteBytes;

  public DatagramStream(DatagramSocket s)
  {
    _s = s;
    _is = new DatagramInputStream(s);
    _os = new DatagramOutputStream(s);
  }
  
  public InputStream getInputStream()
  {
    return _is;
  }
  
  public OutputStream getOutputStream()
  {
    return _os;
  }
  
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
  
  /**
   * Returns the position.
   */
  public long getPosition()
  {
    return _totalReadBytes;
  }
  
  public boolean setPosition(long offset)
  {
    return false;
  }
  
  /**
   * Unread the last byte.
   */
  public void unread()
    throws IOException
  {
    _is.unread();
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
   * Returns true since the socket stream can be read.
   */
  @Override
  public boolean canRead()
  {
    return _s != null;
  }
  
  /**
   * Skips bytes in the file.
   *
   * @param n the number of bytes to skip
   *
   * @return the actual bytes skipped.
   */
  public long skip(long n)
    throws IOException
  {
    if (_s == null)
      return -1;
    
    return _is.skip(n);
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
  public int read(byte []buf, int offset, int length)
    throws IOException
  {
    try {
      if (_s == null || _is == null)
        return -1;

      int readLength = _is.read(buf, offset, length);

      if (readLength >= 0)
        _totalReadBytes += readLength;

      return readLength;
    } catch (InterruptedIOException e) {
      if (_throwReadInterrupts)
        throw e;
      
      log.log(Level.FINEST, e.toString(), e);
    } catch (IOException e) {
      if (_throwReadInterrupts)
        throw e;

      log.log(Level.FINER, e.toString(), e);
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
  public int readTimeout(byte []buf, int offset, int length, long timeout)
    throws IOException
  {
    if (_s == null || _is == null)
      return -1;

    int oldTimeout = _s.getSoTimeout();

    try {
      _s.setSoTimeout((int) timeout);

      int readLength = read(buf, offset, length);

      return readLength;
    } finally {
      _s.setSoTimeout(oldTimeout);
    }
  }

  /**
   * Returns the number of bytes available to be read from the input stream.
   */
  public int getAvailable() throws IOException
  {
    if (_is == null)
      return -1;

    return _is.available();
  }
  
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
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    if (_s == null)
      return;
    
    try {
      _os.write(buf, offset, length);
      _totalWriteBytes += length;
    } catch (IOException e) {
      try {
        close();
      } catch (IOException e1) {
      }

      throw ClientDisconnectException.create(e);
    }
  }
  
  /**
   * Flushes the socket.
   */
  /*
  public void flush() throws IOException
  {
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
  }
  */
  
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
  public void closeWrite()
    throws IOException
  {
    OutputStream os = _os;
    _os = null;

    os.close();
  }
  
  /**
   * Closes the underlying sockets and socket streams.
   */
  public void close() throws IOException
  {
    DatagramSocket s = _s;
    _s = null;

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
  }
  
  static class DatagramInputStream
    extends InputStream
  {
    private final DatagramSocket _s;
    private final DatagramPacket _packet;
    
    private final byte []_buf;
    private int _length;
    private int _offset;
    
    DatagramInputStream(DatagramSocket s)
    {
      _s = s;
      _buf = new byte[1024 * 64];
      
      _packet = new DatagramPacket(_buf, _buf.length);
    }
    
    public int available()
      throws IOException
    {
      return _length - _offset;
    }
    
    public void unread()
      throws IOException
    {
      if (_offset > 0)
        _offset--;
    }
    
    public int read()
      throws IOException
    {
      byte []b = new byte[1];
      
      read(b, 0, 1);
      
      return b[0];
    }
    
    public int read(byte []b, int off, int len)
      throws IOException
    {
      if (_length - _offset == 0)
        receivePacket();
      
      int sublen = Math.min(_length - _offset, len);
      
      System.arraycopy(_buf, _offset, b, off, sublen);
      
      _offset += sublen;
      
      return sublen;
    }
    
    /*
    private int readPacket(byte []b, int off, int len)
      throws IOException
    {
      if (_length - _offset == 0)
        receivePacket();
      
      int sublen = Math.min(_length - _offset, len);
      
      System.arraycopy(_buf, _offset, b, off, sublen);
      
      _offset += sublen;
      
      return sublen;
    }
    */
    
    public long skip(long n)
      throws IOException
    {
      int sublen = (int) Math.min(_length - _offset, n);
      
      _offset += sublen;
      
      return sublen;
      
      /*
      while (readLength < n) {
        if (_length - _offset == 0)
          receivePacket();
        
        int sublen = (int) Math.min(_length - _offset, n - readLength);
        
        _offset += sublen;
        readLength += sublen;
      }
      */
    }
    
    private void receivePacket()
      throws IOException
    {
      _s.receive(_packet);
      
      //_buf = _packet.getData();
      //_offset = _packet.getOffset();
      _offset = 0;
      _length = _packet.getLength();
    }
  }
  
  static class DatagramOutputStream
    extends OutputStream
  {
    private final DatagramSocket _s;
    private final DatagramPacket _packet;
    
    DatagramOutputStream(DatagramSocket s)
    {
      _s = s;
      _packet = new DatagramPacket(new byte[0], 0,
                                   _s.getInetAddress(), _s.getPort());
    }
    
    public void write(int b)
      throws IOException
    {
      write(new byte[] {(byte) b}, 0, 1);
    }
    
    public void write(byte []b, int off, int len)
      throws IOException
    {
      /*
      int writeLength = 0;
      
      while (writeLength < len) {
        int sublen = Math.min(len - writeLength, 65507);
        
        _packet.setData(b, off + writeLength, sublen);
        
        _s.send(_packet);
        
        writeLength += sublen;
      }
      */
      
      _packet.setData(b, off, len);
      _s.send(_packet);
    }
  }
}

