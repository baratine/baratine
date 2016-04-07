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

package com.caucho.v5.network.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.io.SSLFactory;
import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.io.Vfs;
import com.caucho.v5.jni.ServerSocketWrapper;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;

/**
 * Abstract socket to handle both normal sockets and bin/resin sockets.
 */
public class SSLFactoryJsse implements SSLFactory
{
  private static final Logger log
    = Logger.getLogger(SSLFactoryJsse.class.getName());
  
  private static final L10N L = new L10N(SSLFactoryJsse.class);
  
  //private Path _keyStoreFile;
  private String _verifyClient;
  private String _keyStoreType = "jks";
  //private String _keyManagerFactory;
  private String _sslContext = "TLS";
  private String []_cipherSuites;
  private String []_cipherSuitesForbidden;
  private String []_protocols;
  
  private String []_enabledProtocols;

  private String _selfSignedName;

  private KeyStore _keyStore;
  
  private Config _config;
  private String _prefix;

  private SSLSocketFactory _sslSocketFactory;
  
  /**
   * Creates a ServerSocket factory without initializing it.
   */
  public SSLFactoryJsse(Config config, String prefix)
  {
    _config = config;
    _prefix = prefix;
  }

  /**
   * Sets the enabled cipher suites
   */
  public void setCipherSuites(String []ciphers)
  {
    _cipherSuites = ciphers;
  }

  /**
   * Sets the enabled cipher suites
   */
  public void setCipherSuitesForbidden(String []ciphers)
  {
    _cipherSuitesForbidden = ciphers;
  }

  /**
   * Sets the key store
   */
  public void setKeyStoreFile(Path keyStoreFile)
  {
    //_keyStoreFile = keyStoreFile;
  }

  /**
   * Returns the certificate file.
   */
  private Path keyStoreFile()
  {
    String fileName = _config.get(_prefix + ".ssl.key-store");
    
    if (fileName == null) {
      return null;
    }
    
    return Vfs.path(fileName);
  }

  /**
   * Sets the password.
   */
  public void setPassword(String password)
  {
//    _password = password;
  }

  /**
   * Returns the key-store password
   */
  private String keyStorePassword()
  {
    String password = _config.get(_prefix + ".ssl.key-store-password");
    
    if (password != null) {
      return password;
    }
    else {
      return _config.get(_prefix + ".ssl.password");
    }
  }

  /**
   * Returns the key password
   */
  private String keyPassword()
  {
    String password = _config.get(_prefix + ".ssl.key-password");
    
    if (password != null) {
      return password;
    }
    else {
      return keyStorePassword();
    }
  }

  /**
   * Sets the certificate alias
   */
  /*
  public void setAlias(String alias)
  {
    _alias = alias;
  }
  */

  /**
   * Returns the alias.
   */
  private String alias()
  {
    String alias = _config.get(_prefix + ".ssl.alias", null);
    
    return alias;
  }

  /**
   * Sets the verifyClient.
   */
  public void setVerifyClient(String verifyClient)
  {
    _verifyClient = verifyClient;
  }

  /**
   * Returns the key file.
   */
  public String getVerifyClient()
  {
    return _verifyClient;
  }

  /**
   * Sets the key-manager-factory
   */
  /*
  public void setKeyManagerFactory(String keyManagerFactory)
  {
    _keyManagerFactory = keyManagerFactory;
  }
  */
  
  private String keyManagerFactory()
  {
    return KeyManagerFactory.getDefaultAlgorithm();
  }

  /**
   * Sets the self-signed certificate name
   */
  public void setSelfSignedCertificateName(String name)
  {
    _selfSignedName = name;
  }

  /**
   * Sets the ssl-context
   */
  public void setSSLContext(String sslContext)
  {
    _sslContext = sslContext;
  }

  /**
   * Sets the key-store
   */
  public void setKeyStoreType(String keyStore)
  {
    _keyStoreType = keyStore;
  }

  /**
   * Sets the protocol
   */
  /*
  public void setProtocol(String protocol)
  {
    _protocols = protocol.split("[\\s,]+");
  }
  */

  /**
   * Initialize
   */
  @PostConstruct
  public void init()
  {
    try {
      if (keyStoreFile() != null && keyStorePassword() == null) {
        throw new ConfigException(L.l("'password' is required for JSSE."));
      }

      if (keyStorePassword() != null && keyStoreFile() == null) {
        throw new ConfigException(L.l("'key-store-file' is required for JSSE."));
      }

      if (alias() != null && keyStoreFile() == null) {
        throw new ConfigException(L.l("'alias' requires a key store for JSSE."));
      }

      if (keyStoreFile() == null && _selfSignedName == null) {
        throw new ConfigException(L.l("JSSE requires a key-store-file or a self-signed-certificate-name."));
      }

      if (keyStoreFile() != null) {
        keyStore();
      }
      
      _sslSocketFactory = createFactory();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.createConfig(e);
    }
  }
  
  private KeyStore keyStore()
    throws Exception
  {
    if (_keyStore != null) {
      return _keyStore;
    }
    
    if (keyStoreFile() == null) {
      return null;
    }
    
    KeyStore keyStore = KeyStore.getInstance(_keyStoreType);
    
    try (InputStream is = Files.newInputStream(keyStoreFile())) {
      Objects.requireNonNull(is);
      
      keyStore.load(is, keyStorePassword().toCharArray());
    }
    
    String keyAlias = null;
    
    Enumeration<?> e = keyStore.aliases();
    while (e.hasMoreElements()) {
      String alias = (String) e.nextElement();
      
      if (keyStore.isKeyEntry(alias)) {
        keyAlias = alias;
      }
    }
    
    if (keyAlias == null) {
      throw new ConfigException(L.l("Keystore '{0}' has no valid keys",
                                    keyStoreFile()));
    }
    
    String alias = alias();
    
    if (alias == null) {
      _keyStore = keyStore;
      
      return keyStore;
    }
    
    if (alias != null) {
      Key key = keyStore.getKey(alias, keyPassword().toCharArray());

      if (key == null)
        throw new ConfigException(L.l("JSSE alias '{0}' does not have a corresponding key.",
                                      alias));

      Certificate []certChain = keyStore.getCertificateChain(alias);

      if (certChain == null)
        throw new ConfigException(L.l("JSSE alias '{0}' does not have a corresponding certificate chain.",
                                      alias));
      
      keyStore = KeyStore.getInstance(_keyStoreType);
      keyStore.load(null, keyStorePassword().toCharArray());

      keyStore.setKeyEntry(alias, key, keyPassword().toCharArray(), certChain);
      
      _keyStore = keyStore;
    }
    
    return _keyStore;
  }

  /**
   * Creates the SSLSocketFactory
   */
  private SSLSocketFactory createFactory()
    throws Exception
  {
    SSLSocketFactory ssFactory = null;
    
    String host = "localhost";
    int port = 8086;
    
    if (_keyStore == null) {
      return createAnonymousFactory(null, port);
    }
    
    SSLContext sslContext = SSLContext.getInstance(_sslContext);

    KeyManagerFactory kmf
    = KeyManagerFactory.getInstance(keyManagerFactory());

    kmf.init(_keyStore, keyStorePassword().toCharArray());

    sslContext.init(kmf.getKeyManagers(), null, null);

    /*
      if (_cipherSuites != null)
        sslContext.createSSLEngine().setEnabledCipherSuites(_cipherSuites);

     */
    SSLEngine engine = sslContext.createSSLEngine();
    
    _enabledProtocols = enabledProtocols(engine.getEnabledProtocols());
    
    engine.setEnabledProtocols(_enabledProtocols);

    ssFactory = sslContext.getSocketFactory();
    
    return ssFactory;
  }

  /**
   * Creates the SSL ServerSocket.
   */
  public ServerSocketBar create(InetAddress host, int port)
    throws IOException, GeneralSecurityException
  {
    SSLServerSocketFactory ssFactory = null;
    
    if (_keyStore != null) {
      SSLContext sslContext = SSLContext.getInstance(_sslContext);

      KeyManagerFactory kmf
        = KeyManagerFactory.getInstance(keyManagerFactory());
    
      kmf.init(_keyStore, keyStorePassword().toCharArray());
      
      sslContext.init(kmf.getKeyManagers(), null, null);

      /*
      if (_cipherSuites != null)
        sslContext.createSSLEngine().setEnabledCipherSuites(_cipherSuites);

      if (_protocols != null)
        sslContext.createSSLEngine().setEnabledProtocols(_protocols);
      */
      
      SSLEngine engine = sslContext.createSSLEngine();
      
      engine.setEnabledProtocols(enabledProtocols(engine.getSupportedProtocols()));

      ssFactory = sslContext.getServerSocketFactory();
    }
    else {
      ssFactory = createAnonymousServerFactory(host, port);
    }
    
    ServerSocket serverSocket;

    int listen = 100;

    if (host == null)
      serverSocket = ssFactory.createServerSocket(port, listen);
    else
      serverSocket = ssFactory.createServerSocket(port, listen, host);

    SSLServerSocket sslServerSocket = (SSLServerSocket) serverSocket;
    
    if (_cipherSuites != null) {
      sslServerSocket.setEnabledCipherSuites(_cipherSuites);
    }
    
    if (_cipherSuitesForbidden != null) {
      String []cipherSuites = sslServerSocket.getEnabledCipherSuites();
      
      if (cipherSuites == null)
        cipherSuites = sslServerSocket.getSupportedCipherSuites();
      
      ArrayList<String> cipherList = new ArrayList<String>();
      
      for (String cipher : cipherSuites) {
        if (! isCipherForbidden(cipher, _cipherSuitesForbidden)) {
          cipherList.add(cipher);
        }
      }
      
      cipherSuites = new String[cipherList.size()];
      cipherList.toArray(cipherSuites);
      
      sslServerSocket.setEnabledCipherSuites(cipherSuites);
    }

    sslServerSocket.setEnabledProtocols(enabledProtocols(sslServerSocket.getSupportedProtocols()));
    
    if ("required".equals(_verifyClient))
      sslServerSocket.setNeedClientAuth(true);
    else if ("optional".equals(_verifyClient))
      sslServerSocket.setWantClientAuth(true);

    return new ServerSocketWrapper(serverSocket);
  }
  
  private String []enabledProtocols(String []supportedProtocols)
  {
    if (_protocols != null) {
      return _protocols;
    }
    
    ArrayList<String> enabledProtocols = new ArrayList<>();
    
    for (String protocol : supportedProtocols) {
      enabledProtocols.add(protocol);
    }
    
    enabledProtocols.remove("SSLv2");
    enabledProtocols.remove("SSLv2Hello");
    enabledProtocols.remove("SSLv3");
    
    String []protocols = new String[enabledProtocols.size()];
    enabledProtocols.toArray(protocols);
    
    return protocols;
  }
  
  private boolean isCipherForbidden(String cipher,
                                    String []forbiddenList)
  {
    for (String forbidden : forbiddenList) {
      if (cipher.equals(forbidden))
        return true;
    }
    
    return false;
  }

  private SSLServerSocketFactory createAnonymousServerFactory(InetAddress hostAddr,
                                                        int port)
    throws IOException, GeneralSecurityException
  {
    SSLContext sslContext = SSLContext.getInstance(_sslContext);

    String []cipherSuites = _cipherSuites;

    /*
    if (cipherSuites == null) {
      cipherSuites = sslContext.createSSLEngine().getSupportedCipherSuites();
    }
    */

    String selfSignedName = _selfSignedName;

    if (selfSignedName == null
        || "".equals(selfSignedName)
        || "*".equals(selfSignedName)) {
      if (hostAddr != null)
        selfSignedName = hostAddr.getHostName();
      else {
        InetAddress addr = InetAddress.getLocalHost();

        selfSignedName = addr.getHostAddress();
      }
    }
    
    SelfSignedCert cert = createSelfSignedCert(selfSignedName, cipherSuites);

    if (cert == null)
      throw new ConfigException(L.l("Cannot generate anonymous certificate"));
      
    sslContext.init(cert.getKeyManagers(), null, null);

    // SSLEngine engine = sslContext.createSSLEngine();

    SSLServerSocketFactory factory = sslContext.getServerSocketFactory();

    return factory;
  }

  private SSLSocketFactory createAnonymousFactory(InetAddress hostAddr,
                                                  int port)
    throws IOException, GeneralSecurityException
  {
    SSLContext sslContext = SSLContext.getInstance(_sslContext);

    String []cipherSuites = _cipherSuites;

    /*
    if (cipherSuites == null) {
      cipherSuites = sslContext.createSSLEngine().getSupportedCipherSuites();
    }
    */

    String selfSignedName = _selfSignedName;

    if (selfSignedName == null
        || "".equals(selfSignedName)
        || "*".equals(selfSignedName)) {
      if (hostAddr != null)
        selfSignedName = hostAddr.getHostName();
      else {
        InetAddress addr = InetAddress.getLocalHost();

        selfSignedName = addr.getHostAddress();
      }
    }
    
    SelfSignedCert cert = createSelfSignedCert(selfSignedName, cipherSuites);

    if (cert == null)
      throw new ConfigException(L.l("Cannot generate anonymous certificate"));
      
    sslContext.init(cert.getKeyManagers(), null, null);

    // SSLEngine engine = sslContext.createSSLEngine();

    return sslContext.getSocketFactory();
  }
  
  private SelfSignedCert createSelfSignedCert(String name, 
                                              String []cipherSuites)
  {
    SelfSignedCert cert = SelfSignedCert.create(name, cipherSuites);
    
    return cert;
    
    /*
    try {
      certDir.mkdirs();
      
      Path certPath = certDir.lookup(name + ".cert");
      
      try (WriteStream os = certPath.openWrite()) {
        Hessian2Output hOut = new Hessian2Output(os);
        
        hOut.writeObject(cert);
        
        hOut.close();
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    return cert;
    */
  }
  
  /**
   * Creates the SSL ServerSocket.
   */
  @Override
  public ServerSocketBar bind(ServerSocketBar ss)
    throws ConfigException, IOException, GeneralSecurityException
  {
    throw new ConfigException(L.l("jsse is not allowed here"));
  }

  @Override
  public SSLSocket ssl(SocketChannel chan)
    throws IOException
  {
    Objects.requireNonNull(chan);
    
    Socket sock = chan.socket();
    
    SSLSocket sslSock = (SSLSocket) _sslSocketFactory.createSocket(sock, null, false);

    sslSock.setEnabledProtocols(_enabledProtocols);
    
    return sslSock;
  }
}

