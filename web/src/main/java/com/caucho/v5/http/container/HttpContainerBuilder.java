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

package com.caucho.v5.http.container;

import java.nio.file.Path;

import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.types.BytesType;
import com.caucho.v5.http.log.AccessLog;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;

/**
 * Configuration for the <cluster> and <server> tags.
 */
// @Configurable
public class HttpContainerBuilder // implements EnvironmentBean // , XmlSchemaBean
{
  private static final L10N L = new L10N(HttpContainerBuilder.class);

  private final SystemManager _systemManager;
  
  private ServerBartender _selfServer;

  private String _serverHeader;

  private int _invocationCacheSize;

  private int _urlLengthMax;

  private int _accessLogBufferSize;

  private int _headerCountMax;

  private boolean _isIgnoreClientDisconnect;

  private int _headerSizeMax;

  private int _sendfileMinLength;

  private boolean _isSendfileEnable;

  private String _urlCharacterEncoding;
  
  //private ContainerProgram _podConfig = new ContainerProgram();

  private long _shutdownWaitMax;

  private Path _rootDirectory;

  private AccessLog _accessLog;
  
  private Config _config;
  
  public HttpContainerBuilder(ServerBartender selfServer,
                              Config config,
                              String serverHeader)
  {
    _systemManager = SystemManager.getCurrent();
    
    _selfServer = selfServer; // BartenderSystem.getCurrentSelfServer();
    _config = config;
    _serverHeader = serverHeader;
  }

  public SystemManager getResinSystem()
  {
    return _systemManager;
  }
  
  public ServerBartender getServerSelf()
  {
    return _selfServer;
  }
  
  public String getServerHeader()
  {
    return _serverHeader;
  }

  /**
   * Returns the classLoader
   */
  // @Override
  public EnvironmentClassLoader getClassLoader()
  {
    return _systemManager.getClassLoader();
  }
  
  public Config config()
  {
    return _config;
  }
  
  //
  // <cluster> configuration
  //
  
  /**
   * pod-default: adds default configuration for a pod
   */
  /*
  public void addPodDefault(ConfigProgram podConfig)
  {
    _podConfig.addProgram(podConfig);
  }
  */

  /**
   * pod-deploy
   */
  /*
  public void addPodDeploy(ConfigProgram deploy)
  {
    _podConfig.addProgram(deploy);
  }
  */

  /**
   * shutdown-wait-max: the max wait time for shutdown.
   */
  /*
  @Configurable
  public void setShutdownWaitMax(Period waitTime)
  {
    _shutdownWaitMax = waitTime.getPeriod();
  }
  */
  
  /**
   * invocation-cache-size:
   */
  //@Configurable
  public void setInvocationCacheSize(int count)
  {
    _invocationCacheSize = count;
  }
  
  public int getInvocationCacheSize()
  {
    return _invocationCacheSize;
  }

  /**
   * Sets the server header.
   */
  //@Configurable
  public void setServerHeader(String serverHeader)
  {
    if (serverHeader != null) {
      _serverHeader = serverHeader;
    }
  }

  /**
   * Sets the url-length-max
   */
  //@Configurable
  public void setUrlLengthMax(int max)
  {
    _urlLengthMax = max;
  }

  /**
   * Sets the header-size-max
   */
  //@Configurable
  public void setHeaderSizeMax(int max)
  {
    _headerSizeMax = max;
  }

  /**
   * Sets the header-count-max
   */
  //@Configurable
  public void setHeaderCountMax(int max)
  {
    _headerCountMax = max;
  }

  /**
   * Sets the url-length-max
   */
  //@Configurable
  public void setMaxUriLength(int max)
  {
    setUrlLengthMax(max);
  }
  
  //@Configurable
  public void setIgnoreClientDisconnect(boolean isIgnore)
  {
    _isIgnoreClientDisconnect = isIgnore;
  }
  
  //@Configurable
  public void setSendfileEnable(boolean isEnable)
  {
    _isSendfileEnable = isEnable;
  }
  
  //@Configurable
  public void setSendfileMinLength(BytesType bytes)
  {
    _sendfileMinLength = (int) bytes.getBytes();
  }

  /**
   * Sets the access log.
   */
  //@Configurable
  public void setAccessLog(AccessLog accessLog)
  {
    _accessLog = accessLog;
  }

  /**
   * Sets the access log.
   */
  //@Configurable
  public void setAccessLogBufferSize(BytesType bufferSizeBytes)
  {
    int bufferSize = (int) bufferSizeBytes.getBytes();
    
    _accessLogBufferSize = Math.max(128, Math.min(65536, bufferSize));
  }


  public int getAccessLogBufferSize()
  {
    return _accessLogBufferSize;
  }

  /**
   * Sets URL encoding.
   */
  //@Configurable
  public void setUrlCharacterEncoding(String encoding)
    throws ConfigException
  {
    _urlCharacterEncoding = encoding;
  }
  
  public HttpContainer build()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  /*
  public PodContainer buildPod()
  {
    BartenderSystem bartender = BartenderSystem.current();
    
    PodContainer podContainer = new PodContainer(bartender, this);
    
    //_podConfig.configure(podContainer);
    
    return podContainer;
  }
  */
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _selfServer.getDisplayName() + "]";
  }
}
