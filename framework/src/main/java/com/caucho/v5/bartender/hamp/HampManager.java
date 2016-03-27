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

package com.caucho.v5.bartender.hamp;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.bartender.ServerBartender;

import io.baratine.web.ServiceWebSocket;


/**
 * The hamp service registered in the Resin network.
 */
public class HampManager
{
  private final AtomicLong _externalMessageReadCount = new AtomicLong();
  private final AtomicLong _externalMessageWriteCount = new AtomicLong();

  private final Supplier<ServicesAmp> _ampManagerRef;
  private final ServerBartender _selfServer;
  private final String _replyScheme;

  private final AuthHampManager _authManager;

  private Supplier<ServiceWebSocket> _bidirEndpointConfig;
  private Supplier<ServiceWebSocket> _unidirEndpointConfig;
  private String _serverPath;
  private String _unidirPath;
  
  public HampManager(HampManagerBuilder builder)
  {
    _ampManagerRef = builder.getServiceManagerRef();
    _selfServer = builder.getSelfServer();
    
    AuthHampManager authManager = builder.getAuthManager();
    
    if (authManager == null) {
      authManager = new AuthHampManager();
      authManager.setAuthenticationRequired(false);
    }
    
    _replyScheme = builder.getReplyScheme();
    
    _authManager = authManager;
    
    Objects.requireNonNull(_ampManagerRef);
    Objects.requireNonNull(_selfServer);
    Objects.requireNonNull(_authManager);
    Objects.requireNonNull(_replyScheme);
    
    _serverPath = "/server";
    _unidirPath = "/unidir";
    
    _bidirEndpointConfig = new HampServerFactory(this);
    
    String unidirPath = builder.getUnidirPath();
    
    _unidirEndpointConfig = new EndpointHampConfigUnidir(this, unidirPath);
  }

  public String serverPath()
  {
    return _serverPath;
  }

  public Supplier<ServiceWebSocket> serverFactory()
  {
    return _bidirEndpointConfig;
  }

  public String unidirPath()
  {
    return _unidirPath;
  }

  public Supplier<ServiceWebSocket> unidirFactory()
  {
    return _unidirEndpointConfig;
  }
  
  public String getAddress()
  {
    return _selfServer.getId();
  }

  public String getReplyScheme()
  {
    return _replyScheme;
  }
  
  public ServicesAmp ampManager()
  {
    return _ampManagerRef.get();
  }
  
  AuthHampManager getAuthManager()
  {
    return _authManager;
  }
  
  public void start()
  {
  }
  
  protected void onOpen(String peerHostName)
  {
  }
  
  protected void onClose(String peerHostName)
  {
  }

  public void addExternalMessageRead()
  {
    _externalMessageReadCount.incrementAndGet();
  }
  
  public long getExternalMessageReadCount()
  {
    return _externalMessageReadCount.get();
  }

  public void addExternalMessageWrite()
  {
    _externalMessageWriteCount.incrementAndGet();
  }
  
  public long getExternalMessageWriteCount()
  {
    return _externalMessageWriteCount.get();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getAddress() + "]";
  }
}
