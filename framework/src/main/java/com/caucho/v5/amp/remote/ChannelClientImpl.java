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

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.QueryErrorMessage;
import com.caucho.v5.amp.message.QueryReplyMessage;
import com.caucho.v5.amp.service.ServiceRefNull;
import com.caucho.v5.amp.service.ServiceRefUnauthorized;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.RegistryAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Broker specific to the server link. The broker will serve link-specific
 * actors like the login actor.
 * 
 * The broker requires a login to allow access to the general system. It's
 * expected that a login actor will be registered and will call the
 * <code>setLogin</code> method.
 */
public class ChannelClientImpl implements ChannelClient
{
  private static final Logger log = Logger.getLogger(ChannelClientImpl.class.getName());
  
  private final ConcurrentHashMap<String,ServiceRefAmp> _linkServiceMap
    = new ConcurrentHashMap<>();
    
  private final ArrayList<StubAmp> _closeList = new ArrayList<>();
  
  private final ServiceManagerAmp _manager;

  private OutAmpManager _outManager;
  
  private ServiceRefAmp _serviceRefOut;

  private ServiceRefAmp _callerRef;
  
  public ChannelClientImpl(ServiceManagerAmp manager,
                           OutAmpManager outManager,
                           String channelAddress,
                           ServiceRefAmp callerRef)
  {
    _manager = manager;
    _outManager = outManager;
    
    String addressRemote = channelAddress;
    String addressSelf = channelAddress;
    
    // XXX:
    // ServiceRefAmp callerRef = _manager.getSystemInbox().getServiceRef();
    
    StubAmpOut actorOut
      = new StubAmpOutClient(manager, outManager, addressRemote, callerRef, this);
    
    actorOut.init(manager);
    
    _serviceRefOut = actorOut.getServiceRef();

    _callerRef = callerRef;
    //String address = actorOut.getRemoteAddress();
    //String address = callerRef.getAddress();
    String address = _serviceRefOut.address();

    /*
    if (! callerRef.isValid()) {
      Thread.dumpStack();
    }
    */
    
    if (address != null) {
      bind(address, _serviceRefOut);
      // bind(channelAddress, callerRef);
    }
    
    /*
    if (address != null) {
      //bind(address, _serviceRefOut);
      bind(address, callerRef);
    }
    */

    /*
    if (addressSelf != null) {
      //bind(address, _serviceRefOut);
      bind(addressSelf, callerRef);
    }
    */
  }
  
  @Override
  public ServiceManagerAmp getManager()
  {
    return _manager;
  }

  /*
  @Override
  public OutboxAmp createOutbox()
  {
    //OutboxAmpDirect outbox = new OutboxAmpDirect();
    //outbox.setInbox(_serviceRefOut.getInbox());
    
    //return outbox;
    
    return _manager.getSystemOutbox();
  }
  */
  
  protected OutAmpManager getChannel()
  {
    return _outManager;
  }
  
  @Override
  public ServiceRefAmp getServiceRefOut()
  {
    return _serviceRefOut;
  }
  
  @Override
  public ServiceRefAmp getCallerRef()
  {
    return _callerRef;
  }

  /**
   * Sets the foreign host name.
   */
  public void setHostName(String hostName)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void login(OutAmp out)
  {
  }
  
  public boolean isLogin()
  {
    return true;
  }
  
  /**
   * Mark the link as authenticated. When isLogin is true, the client
   * can access published services.
   * 
   * @uid the user id that logged in.
   */
  /*
  @Override
  public void onLogin(String uid)
  {
    synchronized (_isLogin) {
      _isLogin.set(true);
    }
  }
  */

  /**
   * Returns the delegated broker.
   */
  protected RegistryAmp getLookup()
  {
    return _manager.registry();
  }
  
  /**
   * Adds a new link actor.
   */
  // @Override
  public void bind(String address, ServiceRefAmp linkService)
  {
    _linkServiceMap.put(address, linkService);
  }
  
  @Override
  public ServiceRefAmp service(String name)
  {
    ServiceRefAmp linkActor = _linkServiceMap.get(name);
    
    if (linkActor != null) {
      return linkActor;
    }

    if (! isLogin()) {
      return new ServiceRefUnauthorized(_manager, name);
    }
    
    ServiceRefAmp serviceRef = getLookup().service(name);

    if (isExported(serviceRef)) {
      return serviceRef;
    }
    else {
      return new ServiceRefNull(_manager, name);
    }
  }
  
  protected boolean isExported(ServiceRefAmp serviceRef)
  {
    return serviceRef.isPublic();
  }
  
  @Override
  public GatewayReply createGatewayReply(String remoteName)
  {
    //ServiceRefAmp serviceRef = _serviceRefOut.onLookup(remoteName);
    ServiceRefAmp serviceRef = _callerRef.onLookup(remoteName);
    
    return new GatewayReplyConnectionClient(serviceRef);
  }
  
  @Override
  public ServiceRefAmp createGatewayRef(String remoteName)
  {
    //return serviceRefOut.onLookup(remoteName);
    return _callerRef.onLookup(remoteName);
  }

  public void addClose(StubAmp actor)
  {
    _closeList.add(actor);
  }

  /**
   * Called when the link is closing.
   */
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    // jamp/3210
    //getReadMailbox().close();
    try {
      getServiceRefOut().shutdown(mode);
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    for (ServiceRefAmp service : _linkServiceMap.values()) {
      service.shutdown(mode);
    }
    
    for (StubAmp actor : _closeList) {
      actor.onShutdown(mode);
    }
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _outManager + "]";
  }
  
  
  static class GatewayReplyConnectionClient implements GatewayReply
  {
    private final ServiceRefAmp _serviceRef;
    
    public GatewayReplyConnectionClient(ServiceRefAmp serviceRef)
    {
      _serviceRef = serviceRef;
    }
    
    @Override
    public boolean isAsync()
    {
      return true;
    }

    @Override
    public void queryOk(HeadersAmp headers, 
                           long qid,
                           Object value)
    {
      long timeout = InboxAmp.TIMEOUT_INFINITY;
      
      // OutboxAmp outbox = _serviceRef.getManager().getCurrentOutbox();
      
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_serviceRef.manager())) {
        MessageAmp msg = new QueryReplyMessage(outbox,
                                               _serviceRef,
                                               headers, 
                                               _serviceRef.stub(), qid, 
                                               value);
      
        msg.offer(timeout);
        //msg.offerQueue(timeout);
        //msg.getWorker().wake();
      }
    }

    @Override
    public void queryFail(HeadersAmp headers, 
                           long qid, 
                           Throwable exn)
    {
      long timeout = InboxAmp.TIMEOUT_INFINITY;
      
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_serviceRef.manager())) {
        MessageAmp msg = new QueryErrorMessage(outbox,
                                               _serviceRef,
                                               headers, 
                                               _serviceRef.stub(), qid, 
                                               exn);
        msg.offer(timeout);
        
        //msg.offerQueue(timeout);
        //msg.getWorker().wake();
      }
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _serviceRef + "]";
    }
  }

}
