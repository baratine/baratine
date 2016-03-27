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

package com.caucho.v5.bartender.link;

import java.net.URI;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.manager.ServiceManagerAmpWrapper;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.heartbeat.HeartbeatSeedService;
import com.caucho.v5.bartender.heartbeat.HeartbeatSeedServiceSync;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.L10N;

import io.baratine.service.ServiceExceptionConnect;

/**
 * Client for connecting to the champ service.
 */
public class ClientBartender extends ServiceManagerAmpWrapper
  implements ServiceManagerClient
{
  private static final L10N L = new L10N(ClientBartender.class);
  
  private final URI _uri;
  private final boolean _isSSL;
  
  private ClientBartenderHamp _delegate;

  private String _user;
  private String _password;
  
  private Lifecycle _lifecycle = new Lifecycle();
  
  public ClientBartender(String uri)
  {
    this(uri, null, null);
  }
  
  public ClientBartender(String uri,
                         String user, String password)
  {
    try {
      _uri = new URI(uri);

      _user = user;
      _password = password;
      
      String host = _uri.getHost();
      
      if (host == null || "null".equals(host)) {
        throw new IllegalArgumentException(L.l("{0} is an invalid host",
                                               host));
      }
      
      int port = _uri.getPort();
      
      if (port <= 0) {
        throw new IllegalArgumentException(L.l("{0} is an invalid port",
                                               port));
      }
      
      String scheme = _uri.getScheme();
      
      _isSSL = scheme.endsWith("s") && ! scheme.equals("ws");
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public ClientBartender connect()
  {
    if (_delegate != null) {
      return this;
    }
    
    if (! _lifecycle.toActive()) {
      throw new ServiceExceptionConnect(L.l("Can't connect with closed client."));
    }
    
    String scheme = _uri.getScheme();
    String host = _uri.getHost();
    int port = _uri.getPort();

    if (! scheme.equals("bartender") && ! scheme.equals("bartenders")) {
      int dynPort = getDynamicPort(host, port, _isSSL);

      if (dynPort <= 0) {
        throw new ServiceExceptionConnect(L.l("Can't connect to server {0}:{1}; invalid returned port {2}",
                                              host, port, dynPort));
      }
      
      port = dynPort;
    }
    
    // System.out.println("SCHEME: " + scheme);
    
    _delegate = new ClientBartenderHamp("bartender://" + host + ":" + port,
                                        _user, _password);
    
    _delegate.connect();
    
    return this;
  }
  
  private int getDynamicPort(String host, int port, boolean isSSL)
  {
    String url;
    
    if (isSSL) {
      url = "bartenders://" + host + ":" + port;
    }
    else {
      url = "bartender://" + host + ":" + port;
    }
    
    try (ClientBartenderHamp client = new ClientBartenderHamp(url, _user, _password)) {
      HeartbeatSeedServiceSync seedService;
      seedService = client.service("remote://" + HeartbeatSeedService.ADDRESS)
                          .as(HeartbeatSeedServiceSync.class);
      
      return seedService.getPortBartender();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  protected ServicesAmp delegate()
  {
    if (_delegate == null) {
      connect();
    }
    
    return (ServicesAmp) _delegate.delegate();
  }

  @Override
  public String getUrl()
  {
    return _uri.toString();
  }

  /*
  @Override
  public boolean isClosed()
  {
    ClientBartenderHamp delegate = _delegate;
    
    return delegate == null || delegate.getDelegate().isClosed();
  }
  */

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    close();
  }
  
  @Override
  public void close()
  {
    if (! _lifecycle.toDestroy()) {
      return;
    }
    
    ClientBartenderHamp delegate = _delegate;
    _delegate = null;
    
    if (delegate != null) {
      delegate.close();
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _uri + "]";
  }
}
