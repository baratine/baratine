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

import java.util.function.Supplier;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.remote.ChannelServerImpl;
import com.caucho.v5.amp.remote.OutAmp;
import com.caucho.v5.amp.spi.RegistryAmp;
import com.caucho.v5.http.container.HttpSystem;

/**
 * Factory for creating link brokers.
 */
public class ChannelServerLink extends ChannelServerImpl
{
  public static final String LINK_ADDRESS = "link:///auth";

  public ChannelServerLink(Supplier<ServicesAmp> managerRef,
                              RegistryAmp registry,
                              OutAmp conn,
                              String address,
                              String chanId)
  {
    super(managerRef, registry, conn, address, chanId);
    
    // _conn = (HampConnection) channel.getConnection(this);
  }
  
  /*
  public void onNonce(String algorithm, String nonce)
  {
    OutHamp out = (OutHamp) getConnection();

    out.onNonce(algorithm, nonce);
  }
  */

  @Override
  protected ServiceRefAmp lookupService(String address)
  {
    String prefix = "cluster://";

    if (address.startsWith(prefix)) {
      int p = address.indexOf('/', prefix.length() + 1);
      
      if (p > 0) {
        String cluster = address.substring(prefix.length(), p);
        String path = address.substring(p);
    
        return lookupService(cluster, path);
      }
    }
    
    ServiceRefAmp serviceRef = getManager().service(address);
    
    return serviceRef;
  }
  
  protected ServiceRefAmp lookupService(String cluster, String path)
  {
    String address = "public://" + path;

    if ("main".equals(cluster)) {
      return getManager().service(address);
    }
    else {
      ServicesAmp webAppManager = getWebAppManager(cluster);
      
      if (webAppManager != null) {
        return webAppManager.service(address);
      }
      else {
        return getManager().service(address);
      }
    }
  }
  
  protected ServicesAmp getWebAppManager(String pod)
  {
    HttpSystem httpSystem = HttpSystem.getCurrent();
    
    if (httpSystem == null) {
      return null;
    }
    /*
    HttpContainerServlet httpContainer = httpSystem.getHttpContainer();
    
    Host host = httpContainer.getHost("default", 0);

    if (host == null) {
      return null;
    }
    
    WebApp webApp = host.getWebAppContainer().findSubWebAppByURI("/s/" + pod);
    */

    /*
    if (webApp != null) {
      return webApp.getRampManager();
    }
    else {
      return null;
    }
    */
    
    return null;
  }
}
