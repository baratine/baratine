/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.io.ClientDisconnectException;
import com.caucho.v5.io.ClientTimeoutException;
import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.ModulePrivate;

/**
 * Stream using with JNI.
 */
@ModulePrivate
public class JniStream extends StreamImpl {
  private static final Logger log = Logger.getLogger(JniStream.class.getName());
  private static final L10N L = new L10N(JniStream.class);

  private final static int INTERRUPT_EXN = -2;
  private final static int DISCONNECT_EXN = -3;
  public final static int TIMEOUT_EXN = -4;

  //private static NullPath NULL_PATH;

  private final JniSocketImpl _socket;

  private IOException _readException;

  private long _totalReadBytes;
  private long _totalWriteBytes;

  /**
   * Create a new JniStream based on the java.io.* stream.
   */
  public JniStream(JniSocketImpl socket)
  {
    _socket = socket;
    
    Objects.requireNonNull(socket);
    
    /*
    if (NULL_PATH == null) {
      NULL_PATH = new NullPath("jni-stream");
    }
  
    setPath(NULL_PATH);
    */
  }

  public void init()
  {
    _readException = null;
  }

  @Override
  public boolean canRead()
  {
    return ! _socket.isClosed();
  }

  @Override
  public boolean isClosed()
  {
    return _socket.isClosed();
  }

  @Override
  public int read(byte []buf, int offset, int length)
    throws IOException
  {
    if (buf == null) {
      throw new NullPointerException();
    }
    else if (offset < 0 || buf.length < offset + length) {
      throw new ArrayIndexOutOfBoundsException();
    }
    else if (_readException != null) {
      throw _readException;
    }

    int result = _socket.read(buf, offset, length, -1);

    if (result > 0) {
      _totalReadBytes += result;
      return result;
    }
    else if (result < -1) {
      if (result == TIMEOUT_EXN)
        _socket.close();
      _readException = exception(result);

      throw _readException;
    }
    else if (length == 0 && result == 0)
      return 0;
    else
      return -1;
  }

  @Override
  public int readTimeout(byte []buf, int offset, int length, long timeout)
    throws IOException
  {
    if (buf == null)
      throw new NullPointerException();
    else if (offset < 0 || buf.length < offset + length)
      throw new ArrayIndexOutOfBoundsException();

    int result = _socket.read(buf, offset, length, timeout);

    if (result > 0) {
      _totalReadBytes += result;
      return result;
    }
    else if (result == TIMEOUT_EXN) {
      return ReadBuffer.READ_TIMEOUT;
    }
    else if (result < -1) {
      throw exception(result);
    }
    else
      return -1;
  }

  @Override
  public int getAvailable()
    throws IOException
  {
    // return isEof() ? -1 : 0;

    return 0;
  }

  // XXX: needs update
  @Override
  public boolean isEof() throws IOException
  {
    return _socket.isEof();
  }

  @Override
  public boolean canWrite()
  {
    return ! _socket.isClosed();
  }

  @Override
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    if (length <= 0) {
      return;
    }
    else if (buf == null) {
      throw new NullPointerException();
    }
    else if (offset < 0 || buf.length < offset + length) {
      throw new ArrayIndexOutOfBoundsException();
    }

    int result = _socket.write(buf, offset, length, isEnd);

    if (result < -1) {
      // server/1l21: -1 with exception is necessary to catch client disconnect
      throw exception(result);
    }
    else if (result == -1) {
      throw new ClientDisconnectException(L.l("unexpected end of file in write"));
    }

    _totalWriteBytes += result;
  }

  @Override
  public boolean isMmapEnabled()
  {
    return _socket.isMmapEnabled();
  }

  /*
  @Override
  public void writeMmap(long mmapAddress, long mmapOffset, int mmapLength)
    throws IOException
  {
    _socket.writeMmap(mmapAddress, mmapOffset, mmapLength);
  }
  */

  @Override
  public void writeMmap(long mmapAddress,
                        long []mmapBlocks,
                        long mmapOffset,
                        long mmapLength)
    throws IOException
  {
    _socket.writeMmap(mmapAddress, mmapBlocks, mmapOffset, mmapLength);
  }

  @Override
  public boolean isSendfileEnabled()
  {
    return _socket.isSendfileEnabled();
  }

  @Override
  public void writeSendfile(byte []buffer, int offset, int length,
                            byte []fileName, int nameLength,
                            long fileLength)
    throws IOException
  {
    _socket.writeSendfile(buffer, offset, length,
                          fileName, nameLength, fileLength);
  }

  @Override
  public void flush()
    throws IOException
  {
    _socket.flush();
  }

  public long getTotalReadBytes()
  {
    return _totalReadBytes;
  }

  public long getTotalWriteBytes()
  {
    return _totalWriteBytes;
  }

  IOException exception(int result)
     throws IOException
  {
    switch (result) {
    case INTERRUPT_EXN:
      return new InterruptedIOException("interrupted i/o");

    case DISCONNECT_EXN:
      return new ClientDisconnectException("connection reset by peer");

    case TIMEOUT_EXN:
      return new ClientTimeoutException("client timeout");

    default:
      return new ClientDisconnectException("unknown exception=" + result);
    }
  }

  /**
   * Closes the write half of the stream.
   */
  @Override
  public void closeWrite() throws IOException
  {
    _socket.closeWrite();
  }

  /**
   * Closes the stream.
   */
  @Override
  public void close() throws IOException
  {
    _socket.close();
  }

  /*
  public void finalize()
    throws IOException
  {
    close();
  }
  */

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _socket + "]";
  }
}
