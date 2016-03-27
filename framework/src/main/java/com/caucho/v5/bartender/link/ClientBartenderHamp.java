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

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.remote.ChannelClientImpl;
import com.caucho.v5.amp.remote.ClientAmpBase;
import com.caucho.v5.amp.remote.OutAmpFactory;
import com.caucho.v5.bartender.hamp.ChannelClientBartender;
import com.caucho.v5.util.L10N;

/**
 * Client for connecting to the champ service.
 */
public class ClientBartenderHamp extends ClientAmpBase
{
  private static final L10N L = new L10N(ClientBartenderHamp.class);
  
  private final String _uri;
  
  private OutAmpFactoryBartenderClient _connectionFactory;

  private String _selfAddress;
  
  public ClientBartenderHamp(String uri)
  {
    this(uri, null, null);
  }
  
  public ClientBartenderHamp(String uri,
                              String user, String password)
  {
    super(ServicesAmp.newManager().get(), uri);
    
    try {
      URI userUri = new URI(uri);

      String host = userUri.getHost();
      
      if (host == null || "null".equals(host)) {
        throw new IllegalArgumentException(L.l("{0} is an invalid host",
                                               host));
      }
      
      int port = userUri.getPort();
      
      if (port <= 0) {
        throw new IllegalArgumentException(L.l("{0} is an invalid port",
                                               port));
      }
      
      _selfAddress = "system:";
      String selfHostName = "";
      
      String scheme = "bartender";
      
      if (userUri.getScheme().endsWith("s")) {
        scheme = "bartenders";
      }
      
      _uri = scheme + "://" + host + ":" + port + "/bartender";
      
      _connectionFactory
        = new OutAmpFactoryBartenderClient(delegate(),
                                           selfHostName,
                                           _uri, user, password);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  protected ServicesAmp delegate()
  {
    return (ServicesAmp) super.delegate();
  }
  
  @Override
  protected OutAmpFactory getOutFactory()
  {
    return _connectionFactory;
  }
  
  @Override
  protected ChannelClientImpl createChannel(String address)
  {
    return new ChannelClientBartender(delegate(), getOutAmpManager(), 
                                      address,
                                      delegate().service("system:"));
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _uri + "]";
  }
}
