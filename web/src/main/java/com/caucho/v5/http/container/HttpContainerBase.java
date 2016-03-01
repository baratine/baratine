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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.http.dispatch.Invocation;
import com.caucho.v5.http.dispatch.InvocationDecoder;
import com.caucho.v5.http.dispatch.InvocationManager;
import com.caucho.v5.http.dispatch.InvocationManagerBuilder;
import com.caucho.v5.http.protocol.ConnectionHttp;
import com.caucho.v5.http.protocol.HttpBufferStore;
import com.caucho.v5.http.protocol.RequestFacade;
import com.caucho.v5.io.ClientDisconnectException;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.FreeRing;

public class HttpContainerBase<I extends Invocation> implements HttpContainer
{
  private static final Logger log
    = Logger.getLogger(HttpContainerBase.class.getName());

  private final SystemManager _systemManager;
  private final ServerBartender _selfServer;

  private Throwable _configException;

  private InvocationManager<I> _invocationManager;

  private String _serverHeader;

  private int _urlLengthMax = 8192;
  private int _headerSizeMax = TempBuffer.isSmallmem() ? 4 * 1024 : 16 * 1024;
  private int _headerCountMax = TempBuffer.isSmallmem() ? 32 : 256;
  
  private boolean _isIgnoreClientDisconnect = true;

  private boolean _isSendfileEnabled = true;
  private long _sendfileMinLength = 32 * 1024L;
  
  private final FreeRing<HttpBufferStore> _httpBufferFreeList
    = new FreeRing<>(256);
  
  private int _accessLogBufferSize;

  // stats
  
  private final AtomicLong _sendfileCount = new AtomicLong();

  private long _startTime;

  private final Lifecycle _lifecycle;

  /**
   * Creates a new http container.
   */
  public HttpContainerBase(HttpContainerBuilder builder)
  {
    _systemManager = SystemManager.getCurrent();
    Objects.requireNonNull(_systemManager);
    
    _selfServer = builder.getServerSelf();
    Objects.requireNonNull(_selfServer);
    
    _serverHeader = builder.getServerHeader();
    Objects.requireNonNull(_serverHeader);

    String id = _selfServer.getId();
    
    if ("".equals(id)) {
      throw new IllegalStateException();
    }
    
    Thread thread = Thread.currentThread();

    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(classLoader());

      _lifecycle = new Lifecycle(log, toString(), Level.FINE);

      _accessLogBufferSize = builder.getAccessLogBufferSize();

      _invocationManager = createInvocationManager(builder);
      Objects.requireNonNull(_invocationManager);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  protected InvocationManager<I> createInvocationManager(HttpContainerBuilder builder)
  {
    InvocationManagerBuilder<I> invocationBuilder
      = new InvocationManagerBuilder<>();

    /*
    if (_httpCache != null
        && invocationBuilder.getCacheSize() < _httpCache.getEntries()) {
      invocationBuilder.cacheSize(_httpCache.getEntries());
    }
    */
    
    // invocationBuilder.router(createRouter());
    
    return invocationBuilder.build();
  }

  public SystemManager getSystemManager()
  {
    return _systemManager;
  }

  public String getUniqueServerName()
  {
    return getSelfServer().getId();
  }

  /**
   * Returns the classLoader
   */
  public EnvironmentClassLoader classLoader()
  {
    return _systemManager.getClassLoader();
  }

  /**
   * Returns the configuration exception
   */
  public Throwable getConfigException()
  {
    return _configException;
  }

  /**
   * Returns the self server
   */
  public ServerBartender getSelfServer()
  {
    return _selfServer;
  }
  
  /**
   * True if client disconnects should be invisible to servlets.
   */
  public boolean isIgnoreClientDisconnect()
  {
    return _isIgnoreClientDisconnect;
  }

  /**
   * Returns the id.
   */
  public String getServerId()
  {
    return _selfServer.getId();
  }

  /**
   * Returns the id.
   */
  public String getServerDisplayName()
  {
    return _selfServer.getDisplayName();
  }

  /**
   * Gets the server header.
   */
  public String getServerHeader()
  {
    return _serverHeader;
  }

  /**
   * Gets the header-size-max
   */
  public int getHeaderSizeMax()
  {
    return _headerSizeMax;
  }

  /**
   * Gets the header-count-max
   */
  public int getHeaderCountMax()
  {
    return _headerCountMax;
  }
  
  /**
   * access-log-buffer-size is the size of the pre-allocated access log buffer. 
   */
  @Override
  public int getAccessLogBufferSize()
  {
    return _accessLogBufferSize;
  }

  /*
  public HttpCacheBase getHttpCache()
  {
    return _httpCache;
  }
  */
  
  /**
   * Returns true if sendfile is enabled.
   */
  public boolean isSendfileEnable()
  {
    return _isSendfileEnabled;
  }
  
  public long getSendfileMinLength()
  {
    return _sendfileMinLength;
  }
  
  public long getSendfileCount()
  {
    return _sendfileCount.get();
  }
  
  public void addSendfileCount()
  {
    _sendfileCount.incrementAndGet();
  }
  
  public String getURLCharacterEncoding()
  {
    return getInvocationDecoder().getEncoding();
  }
  
  protected InvocationDecoder<I> getInvocationDecoder()
  {
    return getInvocationManager().getInvocationDecoder();
  }
  
  public InvocationManager<I> getInvocationManager()
  {
    return _invocationManager;
  }

  //
  // statistics
  //

  /**
   * Returns the time the server started in ms.
   */
  public long getStartTime()
  {
    return _startTime;
  }

  /**
   * Returns the lifecycle state
   */
  public String getState()
  {
    return _lifecycle.getStateName();
  }
  
  protected int getUrlLengthMax()
  {
    return _urlLengthMax;
  }

  public HttpBufferStore allocateHttpBuffer()
  {
    HttpBufferStore buffer = _httpBufferFreeList.allocate();

    if (buffer == null) {
      buffer = new HttpBufferStore(getUrlLengthMax(),
                                   getHeaderSizeMax(),
                                   getHeaderCountMax());
    }

    return buffer;
  }

  public void freeHttpBuffer(HttpBufferStore buffer)
  {
    _httpBufferFreeList.free(buffer);
  }
  
  /**
   * Start the server.
   */
  public boolean start()
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(classLoader());

      if (! _lifecycle.toStarting()) {
        return false;
      }

      _startTime = CurrentTime.getCurrentTime();

      _lifecycle.toStarting();
      
      startImpl();

      _lifecycle.toActive();
      
      return true;
    } catch (RuntimeException e) {
      log.log(Level.WARNING, e.toString(), e);

      _lifecycle.toError();

      throw e;
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);

      _lifecycle.toError();

      // if the server can't start, it needs to completely fail, especially
      // for the watchdog
      throw new RuntimeException(e);

      // log.log(Level.WARNING, e.toString(), e);

      // _configException = e;
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  protected void startImpl()
  {  
  }

  @Override
  public boolean isSendfileEnabled()
  {
    return _isSendfileEnabled;
  }

  @Override
  public boolean isDestroyed()
  {
    return _lifecycle.isDestroyed();
  }

  /*
  @Override
  public RequestFacade createFacade(RequestHttpBase requestHttpBase)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  @Override
  public RequestFacade newRequest(ConnectionHttp conn)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void sendRequestError(Throwable e, 
                               RequestFacade requestFacade)
      throws ClientDisconnectException
  {
    e.printStackTrace();
    
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Clears the proxy cache.
   */
  public void clearCache()
  {
    // skip the clear on restart
    if (_lifecycle.isStopping()) {
      return;
    }

    if (log.isLoggable(Level.FINER)) {
      log.finest("clearCache");
    }

    // the invocation cache must be cleared first because the old
    // filter chain entries must not point to the cache's
    // soon-to-be-invalid entries
    getInvocationManager().clearCache();

    /*
    if (_httpCache != null) {
      _httpCache.clear();
    }
    */
  }
  
  /**
   * Closes the server.
   * @param mode 
   */
  public void shutdown(ShutdownModeAmp mode)
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(classLoader());

      if (! _lifecycle.toStopping()) {
        return;
      }
      
      //_httpCache.close();

      try {
        shutdownImpl(mode);
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);
      }

      _lifecycle.toDestroy();
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  protected void shutdownImpl(ShutdownModeAmp mode)
  {
    //_httpCache = null;
    
    _invocationManager = null;
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[id=" + getServerDisplayName() + "]");
  }
}
