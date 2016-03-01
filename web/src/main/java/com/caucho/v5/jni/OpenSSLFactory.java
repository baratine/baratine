/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.io.SSLFactory;
import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.jni.JniUtil.JniLoad;
import com.caucho.v5.util.L10N;


/**
 * Abstract socket to handle both normal sockets and bin/resin sockets.
 */
public class OpenSSLFactory extends ServerSocketBar implements SSLFactory
{
  private static final L10N L = new L10N(OpenSSLFactory.class);
  private static final Logger log = Logger.getLogger(OpenSSLFactory.class.getName());

  private static final int PROTOCOL_SSL2 = 0x01;
  private static final int PROTOCOL_SSL3 = 0x02;
  private static final int PROTOCOL_TLS1 = 0x04;
  private static final int PROTOCOL_TLS1_1 = 0x08;
  private static final int PROTOCOL_TLS1_2 = 0x10;

  private static Object _sslInitLock = new Object();

  private static boolean _isEnabled;
  private static final JniTroubleshoot _jniTroubleshoot;

  private static AtomicBoolean _isInit = new AtomicBoolean();

  private static boolean _isInitSystem;

  private String _certificateFile;
  private String _keyFile;
  private String _certificateChainFile;
  private String _caCertificatePath;
  private String _caCertificateFile;
  private String _caRevocationPath;
  private String _caRevocationFile;
  private boolean _isCompression;
  private String _password;
  private String _verifyClient;
  private int _verifyDepth = -1;
  private String _cipherSuite;
  private boolean _isHonorCipherOrder;
  private String _engine;
  private String _engineCommands = "";
  private String _engineKey;
  private boolean _uncleanShutdown;
  private String _protocol;
  private int _protocolFlags = ~0;
  
  private String _nextProtocols;
  
  private ArrayList<ServerOpenssl> _serverList = new ArrayList<>();

  private boolean _enableSessionCache = true;
  private int _sessionCacheTimeout = 300;

  private ServerSocketBar _stdServerSocket;

  private long _configFd;
  private int _defaultProtocolFlags;

  /**
   * Creates a ServerSocket factory without initializing it.
   */
  public OpenSSLFactory()
  {
    _defaultProtocolFlags = ~0;
    _defaultProtocolFlags &= ~PROTOCOL_SSL2;
    _defaultProtocolFlags &= ~PROTOCOL_SSL3;
    
    _protocolFlags = _defaultProtocolFlags;
  }
  
  public static boolean isEnabled()
  {
    return _isEnabled;
  }

  /**
   * Sets the certificate file.
   */
  public void setCertificateFile(Path certificateFile)
  {
    try {
      _certificateFile = certificateFile.toRealPath().toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the certificate file.
   */
  public String getCertificateFile()
  {
    return _certificateFile;
  }

  /**
   * Sets the key file.
   */
  public void setCertificateKeyFile(Path keyFile)
  {
    try {
      _keyFile = keyFile.toRealPath().toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the key file.
   */
  public String getCertificateKeyFile()
  {
    return _keyFile;
  }

  /**
   * Sets the certificateChainFile.
   */
  public void setCertificateChainFile(Path certificateChainFile)
  {
    try {
      _certificateChainFile = certificateChainFile.toRealPath().toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the certificateChainFile
   */
  public String getCertificateChainFile()
  {
    return _certificateChainFile;
  }

  /**
   * Sets the caCertificatePath.
   */
  public void setCACertificatePath(Path caCertificatePath)
  {
    try {
      _caCertificatePath = caCertificatePath.toRealPath().toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the caCertificatePath.
   */
  public String getCACertificatePath()
  {
    return _caCertificatePath;
  }

  /**
   * Sets the caCertificateFile.
   */
  public void setCACertificateFile(Path caCertificateFile)
  {
    try {
      _caCertificateFile = caCertificateFile.toRealPath().toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the caCertificateFile.
   */
  public String getCACertificateFile()
  {
    return _caCertificateFile;
  }

  /**
   * Sets the caRevocationPath.
   */
  public void setCARevocationPath(Path caRevocationPath)
  {
    try {
      _caRevocationPath = caRevocationPath.toRealPath().toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the caRevocationPath.
   */
  public String getCARevocationPath()
  {
    return _caRevocationPath;
  }

  /**
   * Sets the caRevocationFile.
   */
  public void setCARevocationFile(Path caRevocationFile)
  {
    try {
      _caRevocationFile = caRevocationFile.toRealPath().toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the caRevocationFile.
   */
  public String getCARevocationFile()
  {
    return _caRevocationFile;
  }

  /**
   * Sets the cipher-suite
   */
  public void setCipherSuite(String cipherSuite)
  {
    _cipherSuite = cipherSuite;
  }

  /**
   * Returns the cipher suite
   */
  public String getCipherSuite()
  {
    return _cipherSuite;
  }

  public void setCompression(boolean isCompression)
  {
    _isCompression = isCompression;
  }

  public boolean getCompression()
  {
    return _isCompression;
  }

  /**
   * Sets the hoor-cipher-order
   */
  public void setHonorCipherOrder(boolean isEnable)
  {
    _isHonorCipherOrder = isEnable;
  }

  /**
   * Returns the cipher suite
   */
  public boolean isHonorCipherOrder()
  {
    return _isHonorCipherOrder;
  }

  /**
   * Sets the crypto-device (alias for engine)
   */
  public void setCryptoDevice(String engine)
  {
    setEngine(engine);
  }

  /**
   * Sets the engine
   */
  //@Configurable
  public void setEngine(String engine)
  {
    _engine = engine;
  }

  /**
   * Returns the crypto-device
   */
  public String getEngine()
  {
    return _engine;
  }

  /**
   * Sets the engine-commands
   */
  public void addEngineCommand(String command)
  {
    if (command == null || command.length() == 0) {
      return;
    }
    
    int p = command.indexOf(':');
    
    String arg = "";
    
    if (p > 0) {
      arg = command.substring(p + 1);
      command = command.substring(0, p);
    }
    
    StringBuilder sb = new StringBuilder();
    
    sb.append(_engineCommands);
    
    sb.append("\1");
        
    sb.append(command);
    
    sb.append("\1");
    
    sb.append(arg);
    
    _engineCommands = sb.toString();
  }

  /**
   * Sets the engine key for the crypto-device
   */
  //@Configurable
  public void setEngineKey(String keyName)
  {
    _engineKey = keyName;
  }

  /**
   * Returns the key-name for the crypto-device.
   */
  public String getEngineKey()
  {
    return _engineKey;
  }
  
  /**
   * Sets the next protocols.
   */
  public void setNextProtocols(String ...protocols)
  {
    if (protocols == null || protocols.length == 0) {
      _nextProtocols = null;
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
    
    _nextProtocols = sb.toString();
  }

  /**
   * Sets the password.
   */
  public void setPassword(String password)
  {
    _password = password;
  }

  /**
   * Returns the key file.
   */
  public String getPassword()
  {
    return _password;
  }
  
  /**
   * Adds a server with a unique certificate
   */
  public void addServer(ServerOpenssl server)
  {
    _serverList.add(server);
  }

  /**
   * Sets the verifyClient.
   */
  public void setVerifyClient(String verifyClient)
    throws ConfigException
  {
    if (! "optional_no_ca".equals(verifyClient)
        && ! "optional-no-ca".equals(verifyClient)
        && ! "optional".equals(verifyClient)
        && ! "require".equals(verifyClient)
        && ! "none".equals(verifyClient))
      throw new ConfigException(L.l("'{0}' is an unknown value for verify-client.  Valid values are 'optional-no-ca', 'optional', and 'require'.",
                                    verifyClient));

    if ("none".equals(verifyClient))
      _verifyClient = null;
    else
      _verifyClient = verifyClient;
  }

  /**
   * Returns the verify client
   */
  public String getVerifyClient()
  {
    return _verifyClient;
  }

  /**
   * Sets the verify depth
   */
  public void setVerifyDepth(int verifyDepth)
  {
    _verifyDepth = verifyDepth;
  }

  /**
   * Sets the unclean-shutdown
   */
  public void setUncleanShutdown(boolean uncleanShutdown)
  {
    _uncleanShutdown = uncleanShutdown;
  }

  /**
   * Returns the unclean shutdown
   */
  public boolean getUncleanShutdown()
  {
    return _uncleanShutdown;
  }

  /**
   * Enable the session cache
   */
  public void setSessionCache(boolean enable)
  {
    _enableSessionCache = enable;
  }

  /**
   * Sets the session cache timeout
   */
  public void setSessionCacheTimeout(Duration period)
  {
    _sessionCacheTimeout = (int) (period.toMillis() / 1000);
  }

  /**
   * Sets the protocol: +SSLv3
   */
  public void setProtocol(String protocol)
    throws ConfigException
  {
    _protocol = protocol;

    String []values = Pattern.compile("\\s+").split(protocol);

    int protocolFlags = _defaultProtocolFlags;
    for (int i = 0; i < values.length; i++) {
      if (values[i].equalsIgnoreCase("+all")) {
        protocolFlags = ~0;
      }
      else if (values[i].equalsIgnoreCase("-all")) {
        protocolFlags = 0;
      }
      else if (values[i].equalsIgnoreCase("+sslv2")) {
        protocolFlags |= PROTOCOL_SSL2;
      }
      else if (values[i].equalsIgnoreCase("-sslv2")) {
        protocolFlags &= ~PROTOCOL_SSL2;
      }
      else if (values[i].equalsIgnoreCase("+sslv3")) {
        protocolFlags |= PROTOCOL_SSL3;
      }
      else if (values[i].equalsIgnoreCase("-sslv3")) {
        protocolFlags &= ~PROTOCOL_SSL3;
      }
      else if (values[i].equalsIgnoreCase("+tlsv1")) {
        protocolFlags |= PROTOCOL_TLS1;
      }
      else if (values[i].equalsIgnoreCase("-tlsv1")) {
        protocolFlags &= ~PROTOCOL_TLS1;
      }
      else if (values[i].equalsIgnoreCase("+tlsv1.1")) {
        protocolFlags |= PROTOCOL_TLS1_1;
      }
      else if (values[i].equalsIgnoreCase("-tlsv1.1")) {
        protocolFlags &= ~PROTOCOL_TLS1_1;
      }
      else if (values[i].equalsIgnoreCase("+tlsv1.2")) {
        protocolFlags |= PROTOCOL_TLS1_2;
      }
      else if (values[i].equalsIgnoreCase("-tlsv1.2")) {
        protocolFlags &= ~PROTOCOL_TLS1_2;
      }
      else
        throw new ConfigException(L.l("unknown protocol value '{0}'",
                                      protocol));
    }

    if (values.length > 0)
      _protocolFlags = protocolFlags;
  }

  @Override
  public boolean isJni()
  {
    return _stdServerSocket != null && _stdServerSocket.isJni();
  }

  /**
   * Initialize
   */
  @PostConstruct
  public void init()
    throws ConfigException
  {
    if (_engine == null) {
      if (_certificateFile == null)
        throw new ConfigException(L.l("'certificate-file' is required for OpenSSL."));

      if (_password == null)
        throw new ConfigException(L.l("'password' is required for OpenSSL."));
    }
  }

  /**
   * Creates the server socket.
   */
  public ServerSocketBar create(InetAddress addr, int port)
    throws ConfigException, IOException
  {
    synchronized (_sslInitLock) {
      if (_stdServerSocket != null)
        throw new IOException(L.l("Can't create duplicate ssl factory."));

      initConfig();

      _stdServerSocket = ServerSocketJni.createJNI(addr, port);

      initSSL();

      return this;
    }
  }

  /**
   * Creates the server socket.
   */
  public ServerSocketBar bind(ServerSocketBar ss)
    throws ConfigException, IOException
  {
    synchronized (_sslInitLock) {
      if (_stdServerSocket != null)
        throw new ConfigException(L.l("Can't create duplicte ssl factory."));

      try {
        initConfig();
      } catch (RuntimeException e) {
        e.printStackTrace();
        throw e;
      }

      _stdServerSocket = ss;

      initSSL();

      return this;
    }
  }

  private void initSSL()
    throws IOException
  {
    JniServerSocketImpl jniServerSocket = (JniServerSocketImpl) _stdServerSocket;

    boolean isOk = false;
    try {
      jniServerSocket.setSSL(true);

      nativeInit(jniServerSocket.getFd(), _configFd);
      isOk = true;
    } finally {
      if (! isOk)
        _stdServerSocket = null;

      if (! isOk)
        jniServerSocket.close();
    }

    if (_stdServerSocket == null)
      throw new IOException(L.l("Can't create OpenSSL factory."));
  }

  @Override
  public void setTcpNoDelay(boolean delay)
  {
    _stdServerSocket.setTcpNoDelay(delay);
  }

  @Override
  public boolean isTcpNoDelay()
  {
    return _stdServerSocket.isTcpNoDelay();
  }

  /**
   * Sets the socket timeout for connections.
   */
  @Override
  public void setConnectionSocketTimeout(int ms)
  {
    _stdServerSocket.setConnectionSocketTimeout(ms);
  }

  /**
   * Sets the socket's listen backlog.
   */
  @Override
  public void listen(int backlog)
  {
    _stdServerSocket.listen(backlog);
  }

  @Override
  public boolean accept(SocketBar socket)
    throws IOException
  {
    JniSocketImpl jniSocket = (JniSocketImpl) socket;

    if (! _stdServerSocket.accept(socket)) {
      return false;
    }

    long fd;

    synchronized (jniSocket) {
      fd = open(jniSocket.getFd(), _configFd);
    }

    if (fd == 0) {
      Thread.dumpStack();
      jniSocket.close();
      throw new IOException(L.l("failed to open SSL socket"));
    }
    else {
      jniSocket.setSecure(true);

      return true;
    }
  }

  /*
  @Override
  public int acceptInitialRead(QSocket socket,
                               byte []buffer, int offset, int length)
    throws IOException
  {
    JniSocketImpl jniSocket = (JniSocketImpl) socket;

    return jniSocket.acceptInit(buffer, offset, length);
  }
  */

  public SocketBar createSocket()
  {
    return _stdServerSocket.createSocket();
  }

  public InetAddress getLocalAddress()
  {
    return _stdServerSocket.getLocalAddress();
  }

  public int getLocalPort()
  {
    return _stdServerSocket.getLocalPort();
  }

  public void close()
    throws IOException
  {
    ServerSocketBar ss = _stdServerSocket;
    _stdServerSocket = null;

    if (ss != null)
      ss.close();
  }

  public synchronized void initConfig()
    throws ConfigException
  {
    _jniTroubleshoot.checkIsValid();

    if (_configFd != 0)
      throw new ConfigException(L.l("Configuration is already initialized."));

    String certificateFile = _certificateFile;
    String keyFile = _keyFile;

    if (certificateFile == null) {
      certificateFile = keyFile;
    }

    if (_engine == null) {
      if (certificateFile == null)
        throw new ConfigException(L.l("certificate file is missing"));

      if (keyFile == null) {
        keyFile = certificateFile;
      }

      /*
      if (keyFile == null)
        throw new ConfigException(L.l("key file is missing"));
        */

      if (_password == null) {
        throw new ConfigException(L.l("password is missing"));
      }
    }

    _configFd = initConfig(certificateFile, keyFile, _password,
                           _certificateChainFile,
                           _caCertificatePath, _caCertificateFile,
                           _caRevocationPath, _caRevocationFile,
                           getCipherSuite(), isHonorCipherOrder(),
                           getCompression(),
                           _protocolFlags,
                           _uncleanShutdown);

    if (_configFd == 0) {
      throw new ConfigException("Error initializing SSL server socket");
    }

    String engineCommands = _engineCommands;
    
    setEngine(_configFd, _engine, engineCommands, _engineKey);
    setVerify(_configFd, _verifyClient, _verifyDepth);
    setSessionCache(_configFd, _enableSessionCache, _sessionCacheTimeout);
    
    if (_nextProtocols != null) {
      setNextProtocols(_configFd, _nextProtocols);
    }
  }

  private static boolean initSystem()
  {
    synchronized (_isInit) {
      if (! _isInit.compareAndSet(false, true)) {
        return _isInitSystem;
      }

      try {
        _isInitSystem = initSystemNative();
      } catch (Exception e) {
        _isInitSystem = false;
      }
      
      return _isInitSystem;
    }
  }

  /**
   * Initializes the configuration
   */
  native long initConfig(String certificateFile,
                         String keyFile,
                         String password,
                         String certificateChainFile,
                         String caCertificatePath,
                         String caCertificateFile,
                         String caRevocationPath,
                         String caRevocationFile,
                         String cipherSuite,
                         boolean isHonorCipherOrder,
                         boolean isCompression,
                         int protocolFlags,
                         boolean uncleanShutdown)
    throws ConfigException;

  /**
   * Sets the engine name
   */
  native void setEngine(long fd, 
                        String engineName, 
                        String engineCommands,
                        String engineKey);

  /**
   * Sets the verify depth.
   */
  native void setVerify(long fd, String verifyClient, int verifyDepth);

  /**
   * Sets the session cache
   */
  native void setSessionCache(long fd, boolean enable, int timeout);

  /**
   * Sets the next protocols.
   */
  native boolean setNextProtocols(long fd, String nextProtocols);
  

  /**
   * Initialize the socket
   */
  native void nativeInit(long ssFd, long configFd)
    throws ConfigException;

  /**
   * Opens the connection for SSL.
   */
  native long open(long fd, long configFd);

  /**
   * Initialize the system
   */
  native static boolean initSystemNative()
    throws ConfigException;

  /**
   * Close the system
   */
  native static void closeSystemNative()
    throws ConfigException;

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _stdServerSocket + "]";
  }
  
  public static class ServerOpenssl {
    private String _name;
    private String _certificateFile;
    private String _keyFile;
    private String _password;
    
    //@ConfigArg(0)
    public void setName(String serverName)
    {
      _name = serverName;
    }

    //@ConfigArg(1)
    public void setCertificateFile(String certificateFile)
    {
      _certificateFile = certificateFile;
    }

    public void setKeyFile(String keyFile)
    {
      _keyFile = keyFile;
    }
    
    public void setPassword(String password)
    {
      _password = password;
    }
  }

  static {
    _jniTroubleshoot
    = JniUtil.load(OpenSSLFactory.class,
                   new JniLoad() { 
                     public void load(String path) { System.load(path); }},
                   "baratinessl_npn", "baratinessl");
    
    try {
      if (_jniTroubleshoot.isEnabled()) {
        _isEnabled = initSystemNative();
        
        if (! _isEnabled) {
          _jniTroubleshoot.disable();
        }        
      }
    } catch (Throwable e) {
      log.log(Level.FINEST, e.toString(), e);
    }
  }
}

