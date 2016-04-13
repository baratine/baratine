/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.ClientDisconnectException;
import com.caucho.v5.io.InetAddressUtil;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.jni.JniUtil.JniLoad;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.ModulePrivate;

/**
 * JNI socket.
 */
@ModulePrivate
public final class JniSocketImpl extends SocketBar
{
  private static final L10N L = new L10N(JniSocketImpl.class);

  private static final Logger log
    = Logger.getLogger(JniSocketImpl.class.getName());

  private static final JniTroubleshoot _jniTroubleshoot;

  private static ClientDisconnectException _clientDisconnectException;

  private JniServerSocketImpl _ss;
  private long _socketFd;
  private int _nativeFd;
  private JniStream _stream;

  private final byte []_localAddrBuffer = new byte[16];
  private final char []_localAddrCharBuffer = new char[64];

  private int _localAddrLength;
  private String _localName;
  private InetAddress _localAddr;

  private int _localPort;

  private final byte []_remoteAddrBuffer = new byte[16];
  private final char []_remoteAddrCharBuffer = new char[64];

  private int _remoteAddrLength;
  private String _remoteName;
  private InetAddress _remoteAddr;

  private int _remotePort;

  private boolean _isSecure;

  private Object _readLock = new Object();
  private Object _writeLock = new Object();

  private boolean _isNativeFlushRequired;

  private long _socketTimeout;
  private long _requestExpireTime;

  private final AtomicBoolean _isClosed = new AtomicBoolean();

  private InetSocketAddress _ipLocal;
  private InetSocketAddress _ipRemote;

  public JniSocketImpl()
  {
    _socketFd = nativeAllocate();
  }

  public static boolean isEnabled()
  {
    return _jniTroubleshoot.isEnabled();
  }

  public static String getInitMessage()
  {
    if (! _jniTroubleshoot.isEnabled())
      return _jniTroubleshoot.getMessage();
    else
      return null;
  }

  public static JniSocketImpl connect(JniSocketImpl socket,
                                      String address, 
                                      int port)
    throws IOException
  {
    if (socket == null) {
      socket = new JniSocketImpl();
    }

    if (socket.connectImpl(address, port)) {
      return socket;
    }
    else {
      socket.close();

      return null;
    }
  }

  public static JniSocketImpl connectUnix(JniSocketImpl socket,
                                          Path path)
    throws IOException
  {
    if (socket == null) {
      socket = new JniSocketImpl();
    }

    /*
    if (socket.connectUnixImpl(path)) {
      return socket;
    }
    else {
      socket.close();

      return null;
    }
    */
    return null;
  }
  
  @Override
  public long getSoTimeout()
  {
    return _socketTimeout;
  }
  
  @Override
  public void setSoTimeout(long timeout)
  {
    _socketTimeout = timeout;
  }
  

  /**
   * Creates the new server socket.
   */
  public boolean connectImpl(String address, int port)
    throws IOException
  {
    _socketTimeout = 600000;

    _nativeFd = -1;
    _isClosed.set(false);

    synchronized (_writeLock) {
      boolean isConnect = nativeConnect(_socketFd, address, port);

      return isConnect;
    }
  }

  /**
   * Creates the new server socket.
   */
  public boolean connectUnixImpl(Path path)
    throws IOException
  {
    _socketTimeout = 600000;

    _nativeFd = -1;
    _isClosed.set(false);

    synchronized (_writeLock) {
      String nativePath = path.toRealPath().toString();
      
      boolean isConnect = nativeConnectUnix(_socketFd, nativePath);

      return isConnect;
    }
  }

  boolean accept(JniServerSocketImpl ss,
                 long serverSocketFd,
                 long socketTimeout)
  {
    _ss = ss;
    _localName = null;
    _localAddr = null;
    _localAddrLength = 0;
    
    _ipLocal = null;
    _ipRemote = null;

    _remoteName = null;
    _remoteAddr = null;
    _remoteAddrLength = 0;

    _socketTimeout = socketTimeout;
    _requestExpireTime = 0;
    _nativeFd = -1;

    _isSecure = false;
    _isClosed.set(false);

    synchronized (_writeLock) {
      // initialize fields from the _fd

      boolean result = nativeAccept(serverSocketFd, _socketFd,
                                    _localAddrBuffer,
                                    _remoteAddrBuffer);

      // boolean result = nativeAccept(serverSocketFd, _socketFd);

      return result;
    }
  }

  /**
   * Initialize new connection from the socket.
   *
   * @param buf the initial buffer
   * @param offset the initial buffer offset
   * @param length the initial buffer length
   *
   * @return int as if by read
   */
  @Override
  public int acceptInitialRead(byte []buffer, int offset, int length)
  {
    synchronized (_readLock) {
      // initialize fields from the _fd
      int result = nativeAcceptInit(_socketFd, _localAddrBuffer, _remoteAddrBuffer,
                                    buffer, offset, length);

      return result;
    }
  }

  public long getFd()
  {
    return _socketFd;
  }

  public int getNativeFd()
  {
    if (_nativeFd < 0) {
      _nativeFd = getNativeFd(_socketFd);
    }

    return _nativeFd;
  }

  /**
   * Returns the server port that accepted the request.
   */
  @Override
  public int portLocal()
  {
    return _localPort;

    // return getLocalPort(_fd);
  }

  /**
   * Returns the remote client's host name.
   */
  @Override
  public String getRemoteHost()
  {
    if (_remoteName == null) {
      byte []remoteAddrBuffer = _remoteAddrBuffer;
      char []remoteAddrCharBuffer = _remoteAddrCharBuffer;

      if (_remoteAddrLength <= 0) {
        _remoteAddrLength = InetAddressUtil.createIpAddress(remoteAddrBuffer,
                                            remoteAddrCharBuffer);
      }

      _remoteName = new String(remoteAddrCharBuffer, 0, _remoteAddrLength);
    }

    return _remoteName;
  }

  /**
   * Returns the remote client's inet address.
   */
  @Override
  public InetAddress addressRemote()
  {
    if (_remoteAddr == null) {
      try {
        _remoteAddr = InetAddress.getByName(getRemoteHost());
      } catch (Exception e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }

    return _remoteAddr;
  }

  /**
   * Returns the remote client's inet address.
   */
  @Override
  public int getRemoteAddress(byte []buffer, int offset, int length)
  {
    int len = _remoteAddrLength;

    if (len <= 0) {
      len = _remoteAddrLength = InetAddressUtil.createIpAddress(_remoteAddrBuffer,
                                                                _remoteAddrCharBuffer);
    }

    char []charBuffer = _remoteAddrCharBuffer;

    for (int i = len - 1; i >= 0; i--) {
      buffer[offset + i] = (byte) charBuffer[i];
    }

    return len;
  }

  /**
   * Returns the remote client's inet address.
   */
  @Override
  public byte []getRemoteIP()
  {
    return _remoteAddrBuffer;
  }

  /**
   * Returns the remote client's port.
   */
  @Override
  public int portRemote()
  {
    return _remotePort;
  }

  /**
   * Returns the local server's host name.
   */
  @Override
  public String getLocalHost()
  {
    if (_localName == null) {
      byte []localAddrBuffer = _localAddrBuffer;
      char []localAddrCharBuffer = _localAddrCharBuffer;

      if (_localAddrLength <= 0) {
        _localAddrLength = InetAddressUtil.createIpAddress(localAddrBuffer,
                                                           localAddrCharBuffer);
      }

      _localName = new String(localAddrCharBuffer, 0, _localAddrLength);
    }

    return _localName;
  }

  /**
   * Returns the local server's inet address.
   */
  @Override
  public InetAddress addressLocal()
  {
    if (_localAddr == null) {
      try {
        _localAddr = InetAddress.getByName(getLocalHost());
      } catch (Exception e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }

    return _localAddr;
  }

  /**
   * Returns the remote client's inet address.
   */
  @Override
  public InetSocketAddress ipLocal()
  {
    if (_ipLocal == null) {
      try {
        _ipLocal = InetSocketAddress.createUnresolved(getLocalHost(),
                                                      portLocal());
      } catch (Exception e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }

    return _ipLocal;
  }

  /**
   * Returns the remote client's inet address.
   */
  @Override
  public InetSocketAddress ipRemote()
  {
    if (_ipRemote == null) {
      try {
        _ipRemote = InetSocketAddress.createUnresolved(getLocalHost(),
                                                      portLocal());
      } catch (Exception e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }

    return _ipLocal;
  }

  /**
   * Returns the local server's inet address.
   */
  public int getLocalAddress(byte []buffer, int offset, int length)
  {
    System.arraycopy(_localAddrBuffer, 0, buffer, offset, _localAddrLength);

    return _localAddrLength;
  }

  /**
   * Set true for secure.
   */
  public void setSecure(boolean isSecure)
  {
    _isSecure = isSecure;
  }

  /**
   * Returns true if the connection is secure.
   */
  @Override
  public final boolean isSecure()
  {
    // return isSecure(_fd);

    return _isSecure;
  }

  /**
   * Returns the next protocol for an ssl connection.
   */
  @Override
  public String getNegotiatedProtocol()
  {
    synchronized (this) {
      return getInfoString(_socketFd, "negotiated-protocol");
    }
  }

  /**
   * Returns the cipher for an ssl connection.
   */
  @Override
  public String cipherSuite()
  {
    synchronized (this) {
      return getCipher(_socketFd);
    }
  }

  /**
   * Returns the number of bits in the cipher for an ssl connection.
   */
  @Override
  public int cipherBits()
  {
    return getCipherBits(_socketFd);
  }

  /**
   * Returns the client certificate.
   */
  @Override
  public X509Certificate getClientCertificate()
    throws java.security.cert.CertificateException
  {
    TempBuffer tb = TempBuffer.create();
    byte []buffer = tb.buffer();
    int len = getClientCertificate(_socketFd, buffer, 0, buffer.length);
    X509Certificate cert = null;

    if (len > 0 && len < buffer.length) {
      try {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream is = new ByteArrayInputStream(buffer, 0, len);
        cert = (X509Certificate) cf.generateCertificate(is);
        is.close();
      } catch (IOException e) {
        return null;
      }
    }

    TempBuffer.free(tb);
    tb = null;

    return cert;
  }

  /**
   * Sets the expire time
   */
  @Override
  public void setRequestExpireTime(long expireTime)
  {
    _requestExpireTime = expireTime;
  }

  /**
   * Read non-blocking
   */
  @Override
  public boolean isEof()
  {
    synchronized (_readLock) {
      return nativeIsEof(_socketFd);
    }
  }

  /**
   * Reads from the socket.
   */
  public int read(byte []buffer, int offset, int length, long timeout)
    throws IOException
  {
    if (length == 0) {
      throw new IllegalArgumentException();
    }

    long requestExpireTime = _requestExpireTime;

    if (requestExpireTime > 0 && requestExpireTime < CurrentTime.currentTime()) {
      close();
      throw new ClientDisconnectException(L.l("{0}: request-timeout read",
                                              addressRemote()));
    }

    int result = 0;

    synchronized (_readLock) {
      long now = CurrentTime.getCurrentTimeActual();

      long expires;

      // gap is because getCurrentTimeActual() isn't exact
      long gap = 20;

      if (timeout >= 0)
        expires = timeout + now - gap;
      else
        expires = _socketTimeout + now - gap;

      do {
        result = readNative(_socketFd, buffer, offset, length, timeout);

        now = CurrentTime.getCurrentTimeActual();

        timeout = expires - now;
      } while (result == JniStream.TIMEOUT_EXN && timeout > 0);
    }

    return result;
  }

  /**
   * Writes to the socket.
   */
  public int write(byte []buffer, int offset, int length, boolean isEnd)
    throws IOException
  {
    int result;

    long requestExpireTime = _requestExpireTime;

    if (requestExpireTime > 0 && requestExpireTime < CurrentTime.currentTime()) {
      close();
      throw new ClientDisconnectException(L.l("{0}: request-timeout write exp={0}s",
                                              addressRemote(),
                                              CurrentTime.currentTime() - requestExpireTime));
    }

    synchronized (_writeLock) {
      long now = CurrentTime.getCurrentTimeActual();
      long expires = _socketTimeout + now;

      // _isNativeFlushRequired = true;

      do {
        result = writeNative(_socketFd, buffer, offset, length);

        //byte []tempBuffer = _byteBuffer.array();
        //System.out.println("TEMP: " + tempBuffer);
        //System.arraycopy(buffer, offset, tempBuffer, 0, length);
        //_byteBuffer.position(0);
        //_byteBuffer.put(buffer, offset, length);
        //result = writeNativeNio(_fd, _byteBuffer, 0, length);
      } while (result == JniStream.TIMEOUT_EXN
               && CurrentTime.getCurrentTimeActual() < expires);
    }

    if (isEnd) {
      // websocket/1261
      closeWrite();
    }
    
    return result;
  }

  /*
  public int writeMmap(long mmapAddress, long mmapOffset, int mmapLength)
    throws IOException
  {
    int result;

    long requestExpireTime = _requestExpireTime;

    if (requestExpireTime > 0 && requestExpireTime < CurrentTime.getCurrentTime()) {
      close();
      throw new ClientDisconnectException(L.l("{0}: request-timeout write",
                                              getRemoteAddress()));
    }


    synchronized (_writeLock) {
      long now = Alarm.getCurrentTimeActual();
      long expires = _socketTimeout + now;

      do {
        result = writeMmapNative(_socketFd, null, 0, 0,
                                 mmapAddress, mmapOffset, mmapLength);
      } while (result == JniStream.TIMEOUT_EXN
               && Alarm.getCurrentTimeActual() < expires);
    }

    return result;
  }
  */


  public boolean isMmapEnabled()
  {
    return true;
  }

  public int writeMmap(long mmapAddress,
                       long []mmapBlocks,
                       long mmapOffset,
                       long mmapLength)
    throws IOException
  {
    int result;

    long requestExpireTime = _requestExpireTime;

    if (requestExpireTime > 0 && requestExpireTime < CurrentTime.currentTime()) {
      close();
      throw new ClientDisconnectException(L.l("{0}: request-timeout write",
                                              addressRemote()));
    }

    synchronized (_writeLock) {
      long now = CurrentTime.getCurrentTimeActual();
      long expires = _socketTimeout + now;

      _isNativeFlushRequired = true;

      do {
        result = writeMmapNative(_socketFd,
                                 mmapAddress, mmapBlocks, mmapOffset, mmapLength);
      } while (result == JniStream.TIMEOUT_EXN
               && CurrentTime.getCurrentTimeActual() < expires);
    }

    return result;
  }

  public boolean isSendfileEnabled()
  {
    return (JniServerSocketImpl.isSendfileEnabledStatic()
            && _ss.isSendfileEnabled());
  }

  public int writeSendfile(byte []buffer, int offset, int length,
                           byte []fileName, int nameLength, long fileLength)
    throws IOException
  {
    int result;

    long requestExpireTime = _requestExpireTime;

    if (requestExpireTime > 0 && requestExpireTime < CurrentTime.currentTime()) {
      close();
      throw new ClientDisconnectException(L.l("{0}: request-timeout sendfile",
                                              addressRemote()));
    }


    synchronized (_writeLock) {
      long now = CurrentTime.getCurrentTimeActual();
      long expires = _socketTimeout + now;

      _isNativeFlushRequired = false;

      do {
        result = writeSendfileNative(_socketFd, buffer, offset, length,
                                     fileName, nameLength, fileLength);
        // result = writeSendfileNative(_socketFd, fileName, nameLength, fileLength);
      } while (result == JniStream.TIMEOUT_EXN
               && CurrentTime.getCurrentTimeActual() < expires);
    }

    return result;
  }

  /**
   * Flushes the socket.
   */
  public int flush()
    throws IOException
  {
    // XXX: only on cork
    if (! _isNativeFlushRequired) {
      return 0;
    }

    synchronized (_writeLock) {
      _isNativeFlushRequired = false;
      return flushNative(_socketFd);
    }
  }

  /**
   * Returns a stream impl for the socket encapsulating the
   * input and output stream.
   */
  @Override
  public StreamImpl stream()
    throws IOException
  {
    if (_stream == null) {
      _stream = new JniStream(this);
    }

    _stream.init();

    return _stream;
  }

  public long getTotalReadBytes()
  {
    return (_stream == null) ? 0 : _stream.getTotalReadBytes();
  }

  public long getTotalWriteBytes()
  {
    return (_stream == null) ? 0 : _stream.getTotalWriteBytes();
  }

  /**
   * Returns true if closed.
   */
  @Override
  public boolean isClosed()
  {
    return _isClosed.get();
  }

  /**
   * Closes the socket.
   *
   * XXX: potential sync issues
   */
  @Override
  public void forceShutdown()
  {
    // can't be locked because of shutdown

    nativeCloseFd(_socketFd);
  }

  /**
   * Closes the socket.
   */
  @Override
  public void close()
    throws IOException
  {
    if (_isClosed.getAndSet(true)) {
      return;
    }

    _ss = null;

    if (_stream != null) {
      _stream.close();
    }

    // XXX: can't be locked because of shutdown

    _nativeFd = -1;
    nativeClose(_socketFd);
  }

  /**
   * Closes the socket.
   */
  @Override
  public void closeWrite()
    throws IOException
  {
    synchronized (_writeLock) {
      nativeCloseWrite(_socketFd);
    }
  }

  @Override
  protected void finalize()
    throws Throwable
  {
    long fd = _socketFd;
    _socketFd = 0;

    try {
      super.finalize();

      nativeClose(fd);
    } catch (Throwable e) {
    }

    nativeFree(fd);
  }

  native int getNativeFd(long fd);

  native boolean nativeIsEof(long fd);

  private native boolean nativeAccept(long serverSocketFd,
                                      long socketfd,
                                      byte []localAddress,
                                      byte []remoteAddress);

  /*
  private native boolean nativeAccept(long serverSocketFd,
                                      long socketfd);
*/
  private native int nativeAcceptInit(long socketfd,
                                      byte []localAddress,
                                      byte []remoteAddress,
                                      byte []buffer,
                                      int offset,
                                      int length);

  private native boolean nativeConnect(long socketfd,
                                       String host,
                                       int port);

  private native boolean nativeConnectUnix(long socketfd,
                                           String path);

  native String getCipher(long fd);

  native int getCipherBits(long fd);

  native int getClientCertificate(long fd,
                                  byte []buffer,
                                  int offset,
                                  int length);
  
  native String getInfoString(long fd, String name);

  native int readNative(long fd, byte []buf, int offset, int length,
                        long timeout)
    throws IOException;

  private static native int writeNative(long fd, byte []buf, int offset, int length)
    throws IOException;

  /*
  private native int writeCloseNative(long fd,
                                      byte []buf, int offset, int length)
    throws IOException;
    */
  native int writeNative2(long fd,
                          byte []buf1, int off1, int len1,
                          byte []buf2, int off2, int len2)
    throws IOException;

  /*
  native int writeMmapNative(long fd,
                             long mmapAddress, long mmapOffset, int mmapLength)
    throws IOException;
    */

  native int writeMmapNative(long fd,
                             long mmapAddress,
                             long []mmapBlocks,
                             long mmapOffset,
                             long mmapLength)
    throws IOException;
  native int writeSendfileNative(long socket,
                                 byte []buffer, int offset, int length,
                                 byte []fileName, int nameLength,
                                 long fileLength)
    throws IOException;
  /*
  native int writeSendfileNative(long socket,
                                 byte []fileName, int nameLength,
                                 long fileLength)
    throws IOException;
    */

  /*
  native int writeNativeNio(long fd,
                            ByteBuffer byteBuffer, int offset, int length)
    throws IOException;
  native ByteBuffer createByteBuffer(int length);
*/

  native int flushNative(long fd) throws IOException;

  private native void nativeCloseFd(long fd);
  private native void nativeClose(long fd);
  private native void nativeCloseWrite(long fd);

  native long nativeAllocate();

  native void nativeFree(long fd);

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + getRemoteHost() + ":" + portRemote()
            + ",fd=" + getNativeFd(_socketFd) + "]");
  }

  static {
    _jniTroubleshoot
    = JniUtil.load(JniSocketImpl.class,
                   new JniLoad() { 
                     public void load(String path) { System.load(path); }},
                   "baratine");
    
    _clientDisconnectException = ClientDisconnectException.EXN;
  }
}

