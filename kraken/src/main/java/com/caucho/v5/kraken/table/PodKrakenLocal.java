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

package com.caucho.v5.kraken.table;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.bartender.ServerBartender;

import io.baratine.service.ServiceRef;


/**
 * Returns nodes for the given node.
 */
public class PodKrakenLocal implements PodKrakenAmp
{
  //private ProxyKrakenRef []_proxies;
  private ConcurrentHashMap<String,ProxyKrakenRef> _proxyMap = new ConcurrentHashMap<>();

  private ServicesAmp _rampManager;
  
  public PodKrakenLocal(KrakenImpl krakenManager,
                        ServicesAmp services)
  {
    Objects.requireNonNull(krakenManager);
    
    _rampManager = services;
  }

  /**
   * Returns the number of shards.
   */
  public int getNodeCount()
  {
    return 1;
  }

  /*
  public NodePodAmp getNode(int i)
  {
    return _pod.getNode(i);
  }
  */

  /*
  public ClusterServiceKraken getService(int server)
  {
    return getProxy(server);
  }
  */

  /*
  public ClusterServiceKraken getProxy(int owner)
  {
    ServerBartender server = _pod.getServer(owner);
    
    if (server == null) {
      // XXX: baratine/80c0
      return null;
    }

    while (true) {
      ProxyKrakenRef proxyRef = _proxies[owner];
    
      if (proxyRef != null) {
        ClusterServiceKraken proxy = proxyRef.getProxy(server);
      
        if (proxy != null) {
          return proxy;
        }
      }
    
      if (! initProxy(owner, server)) {
        throw new IllegalStateException(this + " " + owner);
      }
    }
  }
  */

  public ClusterServiceKraken getProxy(ServerBartender server)
  {
    ProxyKrakenRef proxyRef = _proxyMap.get(server);
    
    if (proxyRef == null) {
      proxyRef = initProxy(server);
    }
    
    if (proxyRef == null) {
      throw new IllegalStateException(this + " " + server);
    }
    
    ClusterServiceKraken proxy = proxyRef.getProxy(server);
      
    if (proxy != null) {
      return proxy;
    }
    else {
      return null;
    }
  }

  public ClusterServiceKraken getProxy(String serverId)
  {
    ServiceRefAmp service = initService(serverId);
    
    if (service != null) {
      return service.as(ClusterServiceKraken.class);
    }
    else {
      return null;
    }
  }
  
  private ProxyKrakenRef initProxy(ServerBartender server)
  {
    if (server == null) {
      return null;
    }
    
    /*
    String serverId = server.getId();
  
    if ("".equals(serverId)) {
      return false;
    }
    */
  
    // XXX: needs to be virtual
    //String address = "champ://" + serverId + ClusterServiceKraken.UID;
  
    ServiceRefAmp serviceRef = initService(server.getId());
    
    ProxyKrakenRef proxyRef = new ProxyKrakenRef(server, serviceRef);
    
    _proxyMap.put(server.getId(), proxyRef);

    return proxyRef;
  }
  
  private ServiceRefAmp initService(String serverId)
  {
    // XXX: needs to be virtual
    String address = "bartender://" + serverId + ClusterServiceKraken.UID;
  
    ServiceRefAmp serviceRef = _rampManager.service(address);
    
    return serviceRef;
  }

  /*
  public ClusterServiceKraken[] getProxies()
  {
    return _proxies;
  }
  */
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  private class ProxyKrakenRef {
    private final ClusterServiceKraken _proxy;
    private final ServiceRef _serviceRef;
    private final ServerBartender _server;
    
    ProxyKrakenRef(ServerBartender server,
                   ServiceRef serviceRef)
    {
      _server = server;
      _serviceRef = serviceRef;
      _proxy = _serviceRef.as(ClusterServiceKraken.class);
    }
    
    ClusterServiceKraken getProxy(ServerBartender server)
    {
      if (_server == server) {
        return _proxy;
      }
      else {
        return null;
      }
    }
  }
}
