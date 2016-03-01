/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.network.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import com.caucho.v5.io.SocketBar;
import com.caucho.v5.jni.JniSocketImpl;
import com.caucho.v5.jni.JniTroubleshoot;
import com.caucho.v5.jni.JniUtil;
import com.caucho.v5.jni.JniUtil.JniLoad;
import com.caucho.v5.util.L10N;


/**
 * Client factory for openssl
 */
public class OpenSSLClientFactory
{
  private static final L10N L = new L10N(OpenSSLClientFactory.class);

  private static final int PROTOCOL_SSL2 = 0x01;
  private static final int PROTOCOL_SSL3 = 0x02;
  private static final int PROTOCOL_TLS1 = 0x04;

  private static Object _sslInitLock = new Object();

  private static boolean _isEnabled;
  private static final JniTroubleshoot _jniTroubleshoot;

  private static AtomicBoolean _isInit = new AtomicBoolean();

  private static boolean _isInitSystem;

  private long _configFd;
  
  private String _serverName;
  private String _offeredProtocols;

  /**
   * Creates a ServerSocket factory without initializing it.
   */
  public OpenSSLClientFactory()
  {
  }
  
  public static boolean isEnabled()
  {
    return _isEnabled;
  }
  
  public void setServerName(String serverName)
  {
    _serverName = serverName;
  }
  
  public void setOfferedProtocols(String ...protocols)
  {
    if (protocols == null || protocols.length == 0) {
      _offeredProtocols = null;
      return;
    }
    
    StringBuilder sb = new StringBuilder();
    
    for (String protocol : protocols) {
      if (protocol == null || "".equals(protocol)) {
        continue;
      }
      
      sb.append((char) protocol.length());
      sb.append(protocol);
    }
    
    _offeredProtocols = sb.toString();
  }
  
  public SocketBar connect(String host, int port)
    throws IOException
  {
    InetAddress inetAddr = InetAddress.getByName(host);
    String address = inetAddr.getHostAddress();
    
    JniSocketImpl jniSocket = JniSocketImpl.connect(null, address, port);
    
    if (jniSocket == null) {
      return null;
    }
    
    long fd = jniSocket.getFd();
    
    long sslFd = openClient(fd,
                            _serverName,
                            _offeredProtocols);
    
    if (sslFd == 0) {
      jniSocket.close();
      return null;
    }
    
    return jniSocket;
  }

  private static boolean initSystem()
  {
    synchronized (_isInit) {
      if (! _isInit.compareAndSet(false, true)) {
        return _isInitSystem;
      }

      try {
        //_isInitSystem = initSystemNative();
        _isInitSystem = true;
      } catch (Exception e) {
        _isInitSystem = false;
      }
      
      return _isInitSystem;
    }
  }

  /**
   * Initializes the configuration
   */
  /*
  native long initConfig()
    throws ConfigException;
    */

  /**
   * Opens the connection for SSL.
   */
  native long openClient(long fd,
                         String serverName,
                         String offeredProtocols);

  /**
   * Initialize the system
   */
  //native static boolean initSystemNative()
  //  throws ConfigException;

  /**
   * Close the system
   */
  //native static void closeSystemNative()
  //  throws ConfigException;

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }

  static {
    _jniTroubleshoot
    = JniUtil.load(OpenSSLClientFactory.class,
                   new JniLoad() { 
                     public void load(String path) { System.load(path); }},
                   "baratinessl_npn", "baratinessl");
  }

}

