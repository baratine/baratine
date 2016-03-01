/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
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
 * @author Alex Rojkov
 */

package com.caucho.v5.bartender.hamp;

import java.net.InetAddress;
import java.net.URI;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.bartender.link.ClientBartender;
import com.caucho.v5.bartender.link.ClientBartenderLocal;
import com.caucho.v5.util.HostUtil;
import com.caucho.v5.util.L10N;

/**
 * Deploy Client API
 */
public class ClientBartenderFactory
{
  private static final L10N L = new L10N(ClientBartenderFactory.class);
  
  private static final Logger log
    = Logger.getLogger(ClientBartenderFactory.class.getName());
  
  private String _userName;
  private String _password;

  private String _url;

  private boolean _isLocal;

  public ClientBartenderFactory(String url,
                                String userName, String password)
  {
    Objects.requireNonNull(url);
    
    _userName = userName;
    _password = password;
    
    _url = url;
    
    initUrl();
  }

  public ClientBartenderFactory(String url)
  {
    Objects.requireNonNull(url);
    
    _url = url;
    
    initUrl();
  }
  
  private void initUrl()
  {
    try {
      URI uri = new URI(_url);
      
      InetAddress addr = InetAddress.getByName(uri.getHost());
      
      if (HostUtil.isLocalAddress(addr)) {
        int port = uri.getPort();
        
        ServerHeartbeat server = (ServerHeartbeat) BartenderSystem.getCurrentSelfServer();
        
        if (server != null && (server.port() == port || server.getPortBartender() == port)) {
          _isLocal = true;
        }
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }

  public ServiceManagerClient create()
  {
    if (_isLocal) {
      return new ClientBartenderLocal();
    }
    else {
      return new ClientBartender(_url, _userName, _password);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _url + "]";
  }
}

