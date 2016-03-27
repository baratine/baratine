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

import java.lang.reflect.AnnotatedType;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.service.MethodRefNull;
import com.caucho.v5.amp.service.ServiceRefAlias;
import com.caucho.v5.amp.service.ServiceRefBase;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.pod.PodRef;
import com.caucho.v5.inject.type.AnnotatedTypeClass;
import com.caucho.v5.util.L10N;

/**
 * The bartender: scheme addresses servers known to the system. The address
 * is the IP local address of the target server.
 * 
 * bartender://192.168.1.10:6810
 */
public class SchemeBartenderBase extends ServiceRefBase
{
  private static final L10N L = new L10N(SchemeBartenderBase.class);
  
  private final BartenderSystem _bartender;
  private final ServerLinkBartenderBuilder _linkBuilder;
  private final ServicesAmp _ampManager;
  private final ServerBartender _selfServer;
  
  private final ConcurrentHashMap<String,ServerLinkBartender> _linkMap
    = new ConcurrentHashMap<>();

  private ServiceRefAmp _selfRef;

  private ServiceRefAlias _selfProxyRef;
  
  public SchemeBartenderBase(BartenderSystem bartender,
                         ServerLinkBartenderBuilder builder,
                         ServerBartender selfServer)
  {
    Objects.requireNonNull(bartender);
    Objects.requireNonNull(selfServer);
    
    _bartender = bartender;
    
    _linkBuilder = builder;
    
    _ampManager = builder.getRampManager();
    _selfServer = selfServer;
    
    _selfRef = _ampManager.service("local://");
    _selfProxyRef = new ServiceRefAlias(address() + "//" + _selfServer.getId(), _selfRef);
  }
  
  @Override
  public String address()
  {
    return "bartender:";
  }
  
  protected boolean isSystem()
  {
    return false;
  }
  
  @Override
  public ServicesAmp manager()
  {
    return _ampManager;
  }
  
  @Override
  public AnnotatedType api()
  {
    return AnnotatedTypeClass.ofObject();
  }

  @Override
  public MethodRefAmp getMethod(String methodName)
  {
    return new MethodRefNull(this, address());
  }
  
  @Override
  public ServiceRefAmp bind(String address)
  {
    _ampManager.bind(this, address);
    
    return this;
  }
  
  @Override
  public ServiceRefAmp onLookup(String address)
  {
    return onLookup(address, null);
  }
  
  public ServiceRefAmp onLookup(String address, PodRef podRef)
  {
    if (! address.startsWith("//")) {
      return null;
    }
    
    int q = address.indexOf('/', 2);
    
    String hostname;
    String path;
    
    if (q > 0) {
      hostname = address.substring(2, q);
      path = address.substring(q);
    }
    else {
      hostname = address.substring(2);
      path = "";
    }
    
    if (hostname.equals(_selfServer.getId())) {
      if (! path.isEmpty()) {
        return _selfProxyRef.lookup(path);
      }
      else {
        return _selfProxyRef;
      }
    }
    
    ServerLinkBartender serverLink = addServerLink(hostname);
    
    return serverLink.lookup(path, podRef);
  }

  private ServerLinkBartender addServerLink(String hostName)
  {
    ServerLinkBartender serverLink = _linkMap.get(hostName);
    
    if (serverLink == null) {
      int p = hostName.lastIndexOf(':');
      
      ServerBartender server;

      if ("".equals(hostName)) {
        server = _selfServer;
      }
      else if ("localhost".equals(hostName)) {
        server = _selfServer;
      }
      else {
        if (p <= 0) {
          throw new IllegalArgumentException(L.l("'{0}' is an invalid host",
                                                 hostName));
        }

        String address = hostName.substring(0, p);
        int port = Integer.parseInt(hostName.substring(p + 1));
      
        server = _bartender.getServerHandle(address, port);
      }

      serverLink = new ServerLinkBartender(_linkBuilder,
                                           server,
                                           _selfServer,
                                           isSystem());
    
      _linkMap.putIfAbsent(hostName, serverLink);
      
      serverLink = _linkMap.get(hostName);
    }
    
    return serverLink;
  }

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    for (ServerLinkBartender serverLink : _linkMap.values()) {
      serverLink.close();
    }
  }
}
