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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.remote.ChannelClient;
import com.caucho.v5.amp.remote.ChannelClientFactory;
import com.caucho.v5.amp.remote.OutAmpManager;
import com.caucho.v5.amp.remote.ServiceRefLinkFactory;
import com.caucho.v5.amp.remote.ServiceRefLinkFactoryBuilder;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.hamp.ChannelClientBartender;
import com.caucho.v5.bartender.pod.PodRef;
import com.caucho.v5.http.pod.PodContainer;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;

/**
 * Manages the link/connections to a remote Bartender server.
 */
class ServerLinkBartender
{
  private static final L10N L = new L10N(ServerLinkBartender.class);
  
  private ServiceRefAmp _linkServiceRef;
  private ServiceRefLinkFactory _linkFactory;
  
  private ConcurrentHashMap<PodRef,ServiceRefAmp> _linkRefMap
    = new ConcurrentHashMap<>();

  private ServerBartender _server;

  private ServerBartender _selfServer;
  private boolean _isSystem;

  private ServicesAmp _rampManager;

  ServerLinkBartender(ServerLinkBartenderBuilder builder,
                      ServerBartender server,
                      ServerBartender selfServer,
                      boolean isSystem)
  {
    Objects.requireNonNull(server);
    
    /* XXX:
    if (! server.isKnown()) {
      throw new IllegalStateException(L.l("{0} is not known in the current cluster.",
                                          server));
    }
    */
    
    _rampManager = builder.getRampManager();

    _server = server;
    _selfServer = selfServer;
    _isSystem = isSystem;
    
    // RampReadBroker broker = new RampReadBrokerAdapter(rampManager);

    if (! isSelfServer()) {
      /*
      ServiceConfig.Builder build = ServiceConfig.Builder.create();
    
      build.workers(3);
      build.initial(64);
      build.capacity(16 * 1024);
      build.offerTimeout(15, TimeUnit.SECONDS);
      */
      
      String path = builder.getWebSocketPath();
      
      OutAmpFactoryBartender connFactory
        = new OutAmpFactoryBartender(_rampManager,
                                     server, _selfServer,
                                     path);
      
      ServiceRefLinkFactoryBuilder linkBuilder;
      linkBuilder = new ServiceRefLinkFactoryBuilder(_rampManager, connFactory);
      
      linkBuilder.name(server.getId());
      
      ServiceRefAmp queryMapRef;
      
      if (isSystem) {
        queryMapRef = _rampManager.service("bartender://" + _selfServer.getId() + "/system"); // getSelfHostName());
        linkBuilder.scheme("bartender:");
      }
      else {
        queryMapRef = _rampManager.service("bartender-pod://" + _selfServer.getId() + "/system"); // getSelfHostName());
        linkBuilder.scheme("bartender-pod:");
      }
      
      linkBuilder.queryMapRef(queryMapRef);
      linkBuilder.channelFactory(new ChannelFactory(getSelfHostName()));
      // linkBuilder.config(build.build());
      
      _linkFactory = linkBuilder.build();
      
      if (_isSystem) {
        _linkServiceRef = _linkFactory.createLinkService("");
      }
      else {
        _linkServiceRef = _linkFactory.createLinkService("");
      }
    }
    else if (isSystem) {
      _linkServiceRef = _rampManager.service("local://");
    }
    else if (builder.getPodContainer() != null) {
      PodContainer podContainer = builder.getPodContainer();
      
      _linkServiceRef = new ServiceRefPodAppRoot(_rampManager, podContainer);
    }
    else {
      throw new IllegalStateException();
    }
    
    Objects.requireNonNull(_linkServiceRef);
  }
  
  /**
   * Lookup returns a ServiceRef for the foreign path and calling pod.
   * 
   * @param path the foreign service path
   * @param podCaller the calling pod
   */
  ServiceRefAmp lookup(String path, PodRef podCaller)
  {
    if (_linkServiceRef.address().startsWith("local:")) {
      int p = path.indexOf('/', 1);
      
      if (p > 0) {
        // champ path is /pod.0/path
        // return (ServiceRefAmp) _champService.lookup(path);
        return (ServiceRefAmp) _rampManager.service(path.substring(p));
      }
      else {
        return (ServiceRefAmp) _rampManager.service(path);
        
        //return (ServiceRefAmp) _rampManager.lookup("local://");
      }
    }
    else {
      ServiceRefAmp linkRef = getLinkServiceRef(podCaller);

      return linkRef.onLookup(path);
    }
  }
  
  private ServiceRefAmp getLinkServiceRef(PodRef podCaller)
  {
    if (_isSystem || _linkFactory == null) {
      return _linkServiceRef;
    }
    
    // Objects.requireNonNull(podCaller);
    
    if (podCaller == null) {
      return _linkServiceRef;
    }
    
    ServiceRefAmp linkRef = _linkRefMap.get(podCaller);
    
    if (linkRef == null) {
      //linkRef = _linkFactory.createLinkService("/s", podCallerId);
      linkRef = _linkFactory.createLinkService("", podCaller);

      _linkRefMap.putIfAbsent(podCaller, linkRef);
      
      linkRef = _linkRefMap.get(podCaller);
    }
    
    return linkRef;
  }

  public ServiceRefAmp getServiceRef()
  {
    return _linkServiceRef;
  }
  
  public boolean isSelfServer()
  {
    return _selfServer.isSameServer(_server);
  }
  
  public String getHostName()
  {
    return _server.getId();
  }
  
  public String getSelfHostName()
  {
    return _selfServer.getId();
  }
  
  void close()
  {
    ServiceRefAmp linkServiceRef = _linkServiceRef;

    if (linkServiceRef != null) {
      linkServiceRef.close(Result.ignore());
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getHostName() + "]";
  }
  
  private static class ChannelFactory implements ChannelClientFactory {
    private final String _selfHostName;
    
    public ChannelFactory(String selfHostName)
    {
      _selfHostName = selfHostName;
    }

    @Override
    public ChannelClient createChannelClient(ServicesAmp manager,
                                             OutAmpManager channel,
                                             String address)
    {
      String addressSelf = "bartender://" + _selfHostName;
      ServiceRefAmp queryMapRef = manager.service(addressSelf + "/system");
      
      return new ChannelClientBartender(manager, channel, addressSelf, queryMapRef);
    }
  }
}
