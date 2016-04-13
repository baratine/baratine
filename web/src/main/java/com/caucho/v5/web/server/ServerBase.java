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

package com.caucho.v5.web.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.http.container.HttpContainer;
import com.caucho.v5.http.container.HttpSystem;
import com.caucho.v5.jni.JniServerSocketImpl;
import com.caucho.v5.jni.OpenSSLFactory;
import com.caucho.v5.jni.SelectManagerJni;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.lifecycle.LifecycleState;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.network.NetworkSystem;
import com.caucho.v5.network.port.PollTcpManagerBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * The ServerBase is the container for a server.
 */
public class ServerBase
{
  private static Logger log = Logger.getLogger(ServerBase.class.getName());
  private static L10N L = new L10N(ServerBase.class);

  private static final EnvironmentLocal<ServerBase> _serverLocal
    = new EnvironmentLocal<ServerBase>();

  private final ServerBuilder _builder;

  private final SystemManager _systemManager;
  private final ServerBartender _selfServer;
  
  private final Lifecycle _lifecycle;

  private final long _startTime;

  private WaitForExitService _waitForExitService;
  
  private AtomicBoolean _isClosed = new AtomicBoolean();
  
  // private PodContainer _podContainer;

  /**
   * Creates a new server.
   * 
   * @param builder the server builder, including configuration
   * @param systemManager the sub-system manager
   * @param server the network server for this address:port
   * @param httpContainer the configured http container 
   */
  protected ServerBase(ServerBuilder builder,
                       SystemManager systemManager,
                       ServerBartender selfServer)
    throws Exception
  {
    Objects.requireNonNull(builder);
    Objects.requireNonNull(systemManager);
    Objects.requireNonNull(selfServer);
    
    _builder = builder;
    _startTime = builder.getStartTime();

    _systemManager = systemManager;
    _selfServer = selfServer;

    _serverLocal.set(this, _systemManager.getClassLoader());

    _lifecycle = new Lifecycle(log, getClass().getSimpleName() + "[]");
  }
  
  protected void start()
  {
    _systemManager.start();
    
    logStartComplete();
  }
  
  /**
   * Returns the current server.
   */
  public static ServerBase current()
  {
    return _serverLocal.get();
  }

  public String programName()
  {
    return getBuilder().getProgramName();
  }

  public SystemManager getSystemManager()
  {
    return _systemManager;
  }

  /**
   * Returns the classLoader
   */
  public EnvironmentClassLoader getClassLoader()
  {
    return _systemManager.getClassLoader();
  }

  /**
   * Returns the network server.
   */
  public ServerBartender getServerSelf()
  {
    return _selfServer;
  }
  
  //
  // directories
  //

  /**
   * Returns home.dir
   */
  /*
  public PathImpl getHomeDirectory()
  {
    return getBuilder().getHomeDirectory();
  }
  */

  /**
   * Gets the root directory.
   */
  public Path getRootDirectory()
  {
    return getBuilder().getRootDirectory();
  }

  public Path getDataDirectory()
  {
    return getBuilder().getDataDirectory();
  }

  /**
   * The configuration file used to start the server.
   */
  /*
  public PathImpl getConfigPath()
  {
    return _builder.getConfigPath();
  }
  */

  public Path getLogDirectory()
  {
    return _builder.getLogDirectory();
  }

  //
  // configuration
  //

  public boolean isEmbedded()
  {
    return false;
  }

  public String getClusterSystemKey()
  {
    return _builder.getClusterSystemKey();
  }

  public long getShutdownWaitMax()
  {
    return _builder.getShutdownWaitTime();
  }

  /**
   * Returns the active server.
   */
  public HttpContainer getHttp()
  {
    return _systemManager.getSystem(HttpSystem.class).getHttpContainer();
  }

  /**
   * Returns the active server.
   */
  /*
  public PodContainer getPodContainer()
  {
    return _systemManager.getSystem(PodSystem.class).getPodContainer();
  }
  */

  //
  // lifecycle
  //

  /**
   * Returns the current lifecycle state.
   */
  public LifecycleState getLifecycleState()
  {
    return _lifecycle.getState();
  }

  /**
   * Returns true if active.
   */
  public boolean isActive()
  {
    return _systemManager.isActive();
  }

  /**
   * Returns true if the server is closing.
   */
  public boolean isClosing()
  {
    return _lifecycle.isDestroying();
  }

  /**
   * Returns true if the server is closed.
   */
  public boolean isClosed()
  {
    return _lifecycle.isDestroyed();
  }
  
  //
  // start information
  //

  /**
   * Returns the initial start time.
   */
  public Date getInitialStartTime()
  {
    return getStartTime();
  }

  /**
   * Returns the start time.
   */
  public Date getStartTime()
  {
    return new Date(_startTime);
  }
  
  //
  // initialization logging
  //
  
  private void logStartComplete()
  {
    log.info("");

    // log.info("home.dir = " + getHomeDirectory().getNativePath());
    log.info("root.dir = " + getRootDirectory());

    /* XXX:
    if (getConfigPath() != null)
      log.info("conf     = " + getConfigPath().getNativePath());
      */

    if (!_selfServer.getId().equals(_selfServer.getDisplayName())) {
      log.info("server   = " + _selfServer.getId()
               + " (" + getServerSelf().getDisplayName() + ")");
    }
    else {
      log.info("server   = " + _selfServer.getId());
    }

    // log.info("cluster  = " + _selfServer.getClusterId());

    log.info("");

    logModules();

    log.info(this + " started in " + (CurrentTime.getExactTime() - _startTime) + "ms");
  }

  /**
   * Logs modules
   */
  private void logModules()
  {
    StringBuilder sb = new StringBuilder();

    /*
    if (JniFilePathImpl.isEnabled()) {
      if (sb.length() > 0)
        sb.append(" ,");

      sb.append(" file");
    }
    else if (JniFilePathImpl.getInitMessage() != null)
      log.config("  JNI file: " + JniFilePathImpl.getInitMessage());
      */
    /*
    else
      log.info("  JNI file: disabled for unknown reasons");
      */
    
    PollTcpManagerBase pollManager = NetworkSystem.currentPollManager();

    if (pollManager != null) {
      if (sb.length() > 0) {
        sb.append(",");
      }

      sb.append(" poll keepalive (max=" + pollManager.pollMax() + ")");
    }
    else if (SelectManagerJni.getInitMessage() != null) {
      log.config("  JNI poll: " + SelectManagerJni.getInitMessage());
    }
    /*
    else if (CauchoUtil.isWindows())
      log.info("  JNI keepalive: not available on Windows");
    else
      log.info("  JNI keepalive: disabled for unknown reasons");
      */

    if (JniServerSocketImpl.isEnabled()) {
      if (sb.length() > 0)
        sb.append(",");

      sb.append(" socket");
    }
    else if (JniServerSocketImpl.getInitMessage() != null) {
      log.config("  JNI socket: " + JniServerSocketImpl.getInitMessage());
    }
    else {
      log.config("  JNI socket: disabled for unknown reasons");
    }
    
    if (OpenSSLFactory.isEnabled()) {
      if (sb.length() > 0)
        sb.append(",");

      sb.append(" openssl");
    }

    if (sb.length() > 0)
      log.info("  Native:" + sb);

    log.info("");
  }

  //
  // JMX/Admin
  //

  /*
  public ServerAdmin getAdmin()
  {
    return _serverAdmin;
  }
  */

  ServerBuilder getBuilder()
  {
    return _builder;
  }

  //
  // shutdown
  //

  /**
   * Thread to wait until Baratine should be stopped.
   */
  public void waitForExit()
    throws IOException
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(_systemManager.getClassLoader());
      
      _waitForExitService = new WaitForExitService(this, 
                                                    _systemManager);

      _waitForExitService.waitForExit();
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  public void close()
  {
    shutdown(ShutdownModeAmp.GRACEFUL);
  }

  public void shutdown(ShutdownModeAmp mode)
  {
    log.info(L.l("shutdown {0}: {1}", programName(), mode));

    _systemManager.shutdown(mode);
    
    /*
    if (_linkWatchdog != null) {
      _linkWatchdog.onShutdownComplete();
    }
    */
    
    try {
      _isClosed.set(true);
      _isClosed.notifyAll();
    } catch (Throwable e) {
    }
  }

  public void join()
  {
    try {
      synchronized (_isClosed) {
        while (! _isClosed.get()) {
          _isClosed.wait();
        }
      }
    } catch (Exception e) {
    }
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();

    sb.append(getClass().getSimpleName());
    
    ServerBartender serverSelf = getServerSelf();

    if (! serverSelf.getDisplayName().equals(serverSelf.getId())) {
      sb.append("[" + serverSelf.getDisplayName()
                + "/" + serverSelf.getId() + "]");
    }
    else {
      sb.append("[" + serverSelf.getDisplayName() + "]");
    }

    return sb.toString();
  }
}
