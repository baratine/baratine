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

package com.caucho.v5.ramp.jamp;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.inbox.OutboxAmpBase;
import com.caucho.v5.amp.remote.ChannelServer;
import com.caucho.v5.amp.remote.GatewayReply;
import com.caucho.v5.amp.service.ServiceRefUnauthorized;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LookupAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.util.L10N;

import io.baratine.service.ResultFuture;
import io.baratine.service.ServiceRef;
import io.baratine.web.RequestWeb;

/**
 * Channel for jamp-rpc.
 */
public class ChannelServerJampNew implements ChannelServer
{
  private static final Logger log
    = Logger.getLogger(ChannelServerJampNew.class.getName());
  
  private static final L10N L = new L10N(ChannelServerJampNew.class);
  
  private ConcurrentHashMap<String,ServiceRefAmp> _linkServiceMap;
  private ArrayList<ServiceRefAmp> _serviceCloseList;
  
  private final ServicesAmp _manager;
  private final LookupAmp _registry;
  
  //private HttpServletRequest _req;
  //private HttpServletResponse _res;
  
  //private Supplier<String> _sessionSupplier;
  
  //private ResultJampRpc<JampRestMessage> _future;
  private ResultFuture<JampRestMessage> _future;

  private String _sessionId;

  private OutboxAmp _outbox;

  private RequestWeb _request;

  private WebJamp _jamp;

  public ChannelServerJampNew(RequestWeb req,
                              WebJamp jamp,
                              ServicesAmp manager)
  {
    Objects.requireNonNull(manager);
    
    //_sessionSupplier = sessionSupplier;
    _manager = manager;
    _registry = manager.registry(); // XXX: registry;
    
    _request = req;
    _jamp = jamp;
    
    // _unparkQueue = unparkQueue;
    
    _outbox = new OutboxAmpBase();
    _outbox.inbox(ServiceRefAmp.current().inbox());
    //_outbox.message(manager.systemMessage());
  }

  /*
  void init(HttpServletRequest req, HttpServletResponse res)
  {
    _req = req;
    _res = res;
    
    _future = null;
  }
  */
  
  public OutboxAmp getOutbox()
  {
    return _outbox;
  }

  public void initSession(ChannelServerJampNew sessionRpc)
  {
    if (sessionRpc == null) {
      return;
    }
    
    _linkServiceMap = sessionRpc._linkServiceMap;
    _serviceCloseList = sessionRpc._serviceCloseList;
    _sessionId = sessionRpc._sessionId;
  }
  
  void finish()
  {
    //_req = null;
    //_res = null;
    _future = null;
  }

  public JampRestMessage pollMessage(long timeout, TimeUnit unit)
  {
    //ResultJampRpc<JampRestMessage> future = _future;
    ResultFuture<JampRestMessage> future = _future;
    
    if (future != null) {
      try {
        return future.get(timeout, unit);
      } catch (Throwable exn) {
        log.log(Level.FINE, exn.toString(), exn);
        
        return null;
      }
    }
    else {
      return null;
    }
  }

  public JampRestMessage pollMessage(RequestWeb req, OutJamp out)
  {
    //ResultJampRpc<JampRestMessage> future = _future;
    ResultFuture<JampRestMessage> future = _future;
    
    /*
    if (future != null) {
      try {
        return future.get(timeout, unit);
      } catch (Throwable exn) {
        log.log(Level.FINE, exn.toString(), exn);
        
        return null;
      }
    }
    else {
      return null;
    }
    */
    return null;
  }


  @Override
  public ServiceRefAmp getServiceRefOut()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public final InboxAmp getInbox()
  {
    // OutboxAmpDirect outbox = new OutboxAmpDirect();
    return _outbox.inbox();
  }
  
  @Override
  public ServicesAmp getManager()
  {
    return _manager;
  }
  
  /*
  protected String getSessionId()
  {
    if (_sessionId == null) {
      _sessionId = _servlet.createSession(this, _req, _res);
    }
    
    return _sessionId;
  }
  */
  
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
  
  ServiceRefAmp getLink(String address)
  {
    ConcurrentHashMap<String, ServiceRefAmp> linkMap = _linkServiceMap;
    
    if (linkMap != null) {
      return linkMap.get(address);
    }
    else {
      return null;
    }
  }
  
  void putLink(String address, ServiceRefAmp serviceRef)
  {
    synchronized (this) {
      ConcurrentHashMap<String, ServiceRefAmp> linkMap = _linkServiceMap;
    
      if (linkMap == null) {
        linkMap = _linkServiceMap = new ConcurrentHashMap<>();
      }
      
      linkMap.put(address, serviceRef);
    }
  }
  
  @Override
  public MethodRefAmp method(String address, String methodName)
  {
    ServiceRefAmp linkService = getLink(address);

    if (linkService != null) {
      return linkService.getMethod(methodName);
    }
    
    MethodRefAmp methodRef = _registry.method(address, methodName);
    
    if (methodRef.isClosed() && address.startsWith("/")) {
      MethodRefAmp sessionMethodRef
        = _registry.method("session://" + address, methodName);
      
      if (! sessionMethodRef.isClosed()) {
        methodRef = sessionMethodRef;
      }
    }
    
    ServiceRefAmp serviceRef = methodRef.serviceRef();
    
    String addressService = serviceRef.address();

    if (addressService.startsWith("session:")) {
      ServiceRefAmp sessionRef = lookupSession(serviceRef);
      
      putLink(address, sessionRef);

      return sessionRef.getMethod(methodName);
    }
    else if (serviceRef.isPublic() || addressService.startsWith("public:")) {
      return methodRef;
    }
    /*
    else if (address.startsWith("/")
             || addressService.startsWith("public:")) {
      return methodRef;
    }
    */
    
    if (log.isLoggable(Level.FINE)) {
      log.fine("unauthorized service " + address + " from " + this);
    }
      
    return new ServiceRefUnauthorized(getManager(), address).getMethod(methodName);
  }
  
  @Override
  public ServiceRefAmp service(String address)
  {
    ServiceRefAmp linkActor = getLink(address);

    if (linkActor != null) {
      return linkActor;
    }
    
    ServiceRefAmp serviceRef = _registry.service(address);
    String addressService = serviceRef.address();
    
    if (addressService.startsWith("session:")) {
      ServiceRefAmp sessionRef = lookupSession(serviceRef);
      
      putLink(address, sessionRef);

      return sessionRef;
    }
    else if (address.startsWith("/")) {
      return serviceRef;
    }
    else if (addressService.startsWith("public:")) {
      return serviceRef;
    }
    
    if (log.isLoggable(Level.FINE)) {
      log.fine("unauthorized service " + address + " from " + this);
    }
      
    return new ServiceRefUnauthorized(getManager(), address);
  }
  
  protected ServiceRefAmp lookupSession(ServiceRefAmp serviceRef)
  {
    String address = serviceRef.address();
    
    int p = address.indexOf(":///");
    
    if (p > 0) {
      address = address.substring(p + 4);
    }

    //String sessionId = _sessionSupplier.get();
    
    //return (ServiceRefAmp) serviceRef.lookup("/" + sessionId);
    return (ServiceRefAmp) _request.session(address);
  }
  
  @Override
  public GatewayReply createGatewayReply(String remoteName)
  {
    //ResultJampRpc<JampRestMessage> future
    //  = new ResultJampRpc<>(_unparkQueue);
    
    ResultFuture<JampRestMessage> future
      = new ResultFuture<>();
    
    _future = future;
      
    return new GatewayReplyJampNew(remoteName);
  }
  
  @Override
  public ServiceRefAmp createGatewayRef(String remoteName)
  {
    throw new IllegalArgumentException(L.l("jamp-rpc cannot support ServiceRef arguments"));
  }

  /**
   * Called when the link is closing.
   */
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    // jamp/3210
    //getReadMailbox().close();

    for (int i = _serviceCloseList.size() - 1; i >= 0; i--) {
      ServiceRefAmp service = _serviceCloseList.get(i);

      service.shutdown(mode);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _sessionId + "]";
  }
  
  private class GatewayReplyJampNew implements GatewayReply
  {
    private final String _remoteName;
    //private final ResultJampRpc<JampRestMessage> _future;
    
    public GatewayReplyJampNew(String remoteName)
    {
      Objects.requireNonNull(remoteName);
      
      _remoteName = remoteName;
    }
    
    public boolean isAsync()
    {
      return false;
    }

    @Override
    public void queryOk(HeadersAmp headers, 
                           long qid,
                           Object value)
    {
      //JampRestMessage msg;
      
      //msg = new JampRestMessage.Reply(headers, _remoteName, qid, value);
      
      //_future.ok(msg);
      
      _jamp.rpcOk(_request, _remoteName, qid, value);
    }

    @Override
    public void queryFail(HeadersAmp headers, 
                           long qid, 
                           Throwable exn)
    {
      /*
      JampRestMessage msg;
      
      msg = new JampRestMessage.Error(headers, _remoteName, qid, exn);
      
      _future.ok(msg);
      */
      
      _jamp.rpcFail(_request, _remoteName, qid, exn);
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _remoteName + "]";
    }
  }
}
