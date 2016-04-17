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

package com.caucho.v5.vfs;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.io.StreamImpl;

/**
 * Implementation of a TCP stream.  Mostly this just forwards the
 * request to the underlying socket streams.
 */
class TcpStream extends StreamImpl {
  private static final Logger log
    = Logger.getLogger(TcpStream.class.getName());

  private SocketBar _s;
  private StreamImpl _stream;
  /*
  private InputStream _is;
  private OutputStream _os;
  */

  private TcpStream(TcpPath path,
                    long connectTimeout,
                    long socketTimeout,
                    boolean isNoDelay)
    throws IOException
  {
    //setPath(path);

    SocketSystem network = SocketSystem.current();

    InetSocketAddress addr = (InetSocketAddress) path.getSocketAddress();

    boolean isSSL = (path instanceof TcpsPath);

    _s = network.connect(addr.getAddress(), addr.getPort(), connectTimeout,
                         isSSL);

    /*
    //_s = new Socket(path.getHost(), path.getPort());
    //_s = new Socket();

    if (connectTimeout > 0)
      _s.connect(path.getSocketAddress(), (int) connectTimeout);
    else
      _s.connect(path.getSocketAddress());

    if (! _s.isConnected())
      throw new IOException("connection timeout");
      */

    if (socketTimeout < 0)
      socketTimeout = 120000;

    _s.setSoTimeout((int) socketTimeout);

    if (isNoDelay) {
      _s.tcpNoDelay(true);
    }

    try {
      if (path instanceof TcpsPath) {
        SSLContext context = SSLContext.getInstance("TLS");

        javax.net.ssl.TrustManager tm =
          new javax.net.ssl.X509TrustManager() {
            public java.security.cert.X509Certificate[]
              getAcceptedIssuers() {
              return null;
            }
            public void checkClientTrusted(
                                           java.security.cert.X509Certificate[] cert, String foo) {
            }
            public void checkServerTrusted(
                                           java.security.cert.X509Certificate[] cert, String foo) {
            }
          };


        context.init(null, new javax.net.ssl.TrustManager[] { tm }, null);
        SSLSocketFactory factory = context.getSocketFactory();

        Socket socket = _s.getSocket();

        socket = factory.createSocket(socket, path.getHost(), path.getPort(), true);

        _s.setSocket(socket);
      }
    } catch (IOException e) {
      throw e;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }

    _stream = _s.stream();

    /*
    _is = new ReadStream(stream);
    _os = new WriteStream(stream);
    */
  }
  
  public void setSoTimeout(int value)
  {
    try {
      _s.setSoTimeout(value);
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }

  public void setAttribute(String name, Object value)
  {
    if (name.equals("timeout")) {
      try {
        if (value instanceof Number)
          _s.setSoTimeout(((Number) value).intValue());
        else
          _s.setSoTimeout(Integer.parseInt(String.valueOf(value)));
      } catch (SocketException e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }
    else if (name.equals("no-delay")) {
      try {
        if (Boolean.TRUE.equals(value)) {
          _s.tcpNoDelay(true);
        }
      } catch (SocketException e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }
  }

  static TcpStream openRead(TcpPath path,
                            long connectTimeout,
                            long socketTimeout,
                            boolean isNoDelay)
    throws IOException
  {
    return new TcpStream(path, connectTimeout, socketTimeout, isNoDelay);
  }

  static TcpStream openReadWrite(TcpPath path,
                                 long connectTimeout,
                                 long socketTimeout,
                                 boolean isNoDelay)
    throws IOException
  {
    return new TcpStream(path, connectTimeout, socketTimeout, isNoDelay);
  }

  @Override
  public boolean canWrite()
  {
    return _stream != null && _stream.canWrite();
  }

  /**
   * Writes a buffer to the underlying stream.
   *
   * @param buffer the byte array to write.
   * @param offset the offset into the byte array.
   * @param length the number of bytes to write.
   * @param isEnd true when the write is flushing a close.
   */
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    StreamImpl stream = _stream;

    if (stream != null) {
      stream.write(buf, offset, length, isEnd);
    }
  }

  @Override
  public boolean canRead()
  {
    StreamImpl stream = _stream;

    return stream != null && stream.canRead();
  }

  @Override
  public int getAvailable() throws IOException
  {
    StreamImpl stream = _stream;

    if (stream != null)
      return stream.getAvailable();
    else
      return -1;
  }

  public int read(byte []buf, int offset, int length) throws IOException
  {
    StreamImpl stream = _stream;

    if (stream != null) {
      int len;

      try {
        len = stream.read(buf, offset, length);
      } catch (SocketException e) {
        log.log(Level.FINER, e.toString(), e);

        len = -1;
      }

      if (len < 0)
        close();

      return len;
    }
    else
      return -1;
  }

  @Override
  public void closeWrite() throws IOException
  {
    StreamImpl stream = _stream;
    _stream = null;

    if (stream != null) {
      stream.closeWrite();
    }
  }

  @Override
  public void close() throws IOException
  {
    StreamImpl stream = _stream;
    _stream = null;

    SocketBar s = _s;
    _s = null;

    try {
      if (stream != null) {
        stream.close();
      }
    } finally {
      if (s != null)
        s.close();
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _s + "]";
  }
}
