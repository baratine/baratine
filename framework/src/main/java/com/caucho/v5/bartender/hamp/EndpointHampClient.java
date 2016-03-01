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

import io.baratine.service.ResultFuture;
import io.baratine.service.ServiceException;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.remote.OutAmp;
import com.caucho.v5.amp.remote.OutAmpFactory;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.cloud.security.SecuritySystem;
import com.caucho.v5.ramp.hamp.NonceQuery;
import com.caucho.v5.util.L10N;

/**
 * Endpoint for receiving hamp message
 */
public class EndpointHampClient extends HampService
  implements OutAmpFactory, OutAmpLogin
{
  private static final L10N L = new L10N(EndpointHampClient.class);
  private static final Logger log
    = Logger.getLogger(EndpointHampClient.class.getName());
  
  private ServerHeartbeat _server;
  private String _selfHostName;
  
  private String _uid = null;
  private String _password = null;

  private Closeable _wsClient;
  private long _serverStateSequence;

  // private HampChannelBrokerClient _broker;
  
  public EndpointHampClient(ServiceManagerAmp ampManager,
                            ServerHeartbeat server,
                            String selfHostName,
                            ChannelAmp channel)
  {
    super(ampManager, channel);

    _server = server;
    
    if (server != null) {
      _serverStateSequence = server.getStateSequence();
    }
    
    _selfHostName = selfHostName;
  }
  
  public void setAuth(String uid, String password)
  {
    _uid = uid;
    _password = password;
  }

  /**
   * @param wsClient
   */
  public void setClient(Closeable wsClient)
  {
    _wsClient = wsClient;
  }
  
  @Override
  public OutAmp getOut(ChannelAmp registryIn)
  {
    return this;
  }
  
  @Override
  public boolean isUp()
  {
    if (! super.isUp()) {
      return false;
    }
    else if (_server == null) {
      return true;
    }
    else if (true) {
      return true;
    }
    else {
      return _server.getStateSequence() == _serverStateSequence;
    }
  }
  
  @Override
  public boolean login()
  {
    String authAddress = "link:///auth";

    //ActorThreadContextImpl<?> threadContext = null;
    //RampMailbox systemMailbox = getRampManager().getSystemMailbox();
    //RampMailbox oldMailbox = systemMailbox.beginCurrentMailbox(threadContext);
    HeadersAmp headers = HeadersNull.NULL;
 
    try {
      if (_selfHostName != null) {
        send(headers, 
             authAddress, "hostName", null, 
             new Object[] { _selfHostName });
        flush();
      }
      
      // InboxAmp inboxSystem = getRampManager().getSystemInbox();
      ServiceRefAmp serviceRef = getServiceRefOut();
      //ServiceRefAmp callerRef = getCallerRef();
      //InboxAmp inbox = serviceRef.getInbox();
      InboxAmp inbox = serviceRef.inbox();
      
      ClassLoader loader = inbox.manager().classLoader();
      
      ResultFuture<?> future = new ResultFuture<>();
      
      String address = serviceRef.address();
      QueryRefAmp queryRef = inbox.addQuery(address, future, loader);

      query(headers, queryRef.getFrom(), queryRef.getId(),
            authAddress, "getNonce", null, 
            new Object[] { _uid, "nonce" });
      
      flush();
      
      NonceQuery nonce = (NonceQuery) future.get(10, TimeUnit.SECONDS);
      
      future = new ResultFuture<>();
      
      queryRef = inbox.addQuery(address, future, loader);
      
      Object cred = credentials(nonce.getAlgorithm(), nonce.getNonce());

      query(headers, 
            queryRef.getFrom(), queryRef.getId(),
            authAddress, "login", null, 
            new Object[] { _uid, cred });
      
      flush();
      
      Object value = future.get();
      
      if (log.isLoggable(Level.FINER)) {
        if (! Boolean.TRUE.equals(value)) {
          log.finer("login " + value + " " + this);
        }
      }

      return true;
    } catch (Exception e) {
      String msg = L.l("Failed login.\n  {0}", e);
      
      throw ServiceException.createAndRethrow(msg, e);
    } finally {
      // systemMailbox.endCurrentMailbox(threadContext, oldMailbox);
    }
  }
  
  private Object credentials(String algorithm, String nonce)
  {
    SecuritySystem security = SecuritySystem.getCurrent();
    
    if (security == null) {
      security = new SecuritySystem();
    }
    
    Object cred
      = security.credentials(algorithm,
                             _uid,
                             _password,
                             nonce);
    
    return cred;
  }
 
  @Override
  public void close()
  {
    try {
      super.close();

      Closeable wsClient = _wsClient;
      
      if (wsClient != null) {
        wsClient.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
      log.log(Level.FINER, e.toString(), e);
    }
  }
}
