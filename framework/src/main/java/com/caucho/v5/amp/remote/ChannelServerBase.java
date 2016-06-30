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

package com.caucho.v5.amp.remote;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.service.ServiceRefNull;
import com.caucho.v5.amp.service.ServiceRefUnauthorized;
import com.caucho.v5.amp.spi.LookupAmp;
import com.caucho.v5.amp.spi.RegistryAmp;

/**
 * Broker specific to the server link. The broker will serve link-specific
 * actors like the login actor.
 * 
 * The broker requires a login to allow access to the general system. It's
 * expected that a login actor will be registered and will call the
 * <code>setLogin</code> method.
 */
public class ChannelServerBase implements ChannelServer
{
  private static final Logger log
    = Logger.getLogger(ChannelServerBase.class.getName());
  
  private final ServicesAmp _manager;
  private final LookupAmp _registry;

  public ChannelServerBase(ServicesAmp manager,
                           LookupAmp registry)
  {
    _manager = manager;
    _registry = registry;
  }

  @Override
  public ServicesAmp services()
  {
    return _manager;
  }
  
  /**
   * Mark the link as authenticated. When isLogin is true, the client
   * can access published services.
   * 
   * @uid the user id that logged in.
   */
  @Override
  public void onLogin(String uid)
  {
  }
  
  protected boolean isLogin()
  {
    return false;
  }

  /**
   * Returns the delegated broker.
   */
  protected LookupAmp getLookup()
  {
    return _registry;
  }
  
  /**
   * Adds a new link actor.
   */
  //@Override
  public void bind(String address, ServiceRefAmp linkService)
  {
  }
  
  protected ServiceRefAmp getLink(String address)
  {
    // _linkServiceMap.get(address);
    return null;
  }
  
  protected void putLink(String address, ServiceRefAmp serviceRef)
  {
  }
  
  @Override
  public ServiceRefAmp service(String address)
  {
    ServiceRefAmp linkActor = getLink(address);

    if (linkActor != null) {
      return linkActor;
    }
    
    ServiceRefAmp serviceRef = getLookup().service(address);
    
    if (serviceRef.address().startsWith("session:")) {
      ServiceRefAmp sessionRef = lookupSession(serviceRef);
      
      putLink(address, sessionRef);

      return sessionRef;
    }
    else if (address.startsWith("/")) {
      return serviceRef;
    }
    else if (serviceRef.address().startsWith("public:")) {
      return serviceRef;
    }
    
    if (! isLogin()) {
      if (log.isLoggable(Level.FINE)) {
        log.fine("unauthorized service " + address + " from " + this);
      }
      
      return new ServiceRefUnauthorized(services(), address);
    }
    else if (! isExported(address, serviceRef)) {
      return new ServiceRefUnauthorized(services(), address);
    }
    else {
      return serviceRef;
    }
  }
  
  protected ServiceRefAmp lookupSession(ServiceRefAmp serviceRef)
  {
    // return (ServiceRefAmp) serviceRef.lookup("/" + _sessionId);
    
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  protected boolean isExported(String address, ServiceRefAmp serviceRef)
  {
    return address.startsWith("public:");
  }
  
  @Override
  public ServiceRefAmp createGatewayRef(String remoteName)
  {
    return new ServiceRefNull(services(), remoteName);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
