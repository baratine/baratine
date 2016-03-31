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

import io.baratine.service.ServiceExceptionIllegalState;
import io.baratine.stream.ResultStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.QueryErrorMessage;
import com.caucho.v5.amp.message.QueryReplyMessage;
import com.caucho.v5.amp.message.StreamResultActorMessage;
import com.caucho.v5.amp.service.ServiceRefUnauthorized;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LookupAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.RegistryAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.LruCache;

/**
 * Channel context for a server connection. The channel is a registry
 * for connection-specific services like the login service.
 */
public class ChannelServerImpl implements ChannelServer
{
  private static final L10N L = new L10N(ChannelServerImpl.class); 
  private static final Logger log
    = Logger.getLogger(ChannelServerImpl.class.getName());
  
  private final ConcurrentHashMap<String,ServiceRefAmp> _linkServiceMap
    = new ConcurrentHashMap<>();
    
  private final ArrayList<ServiceRefAmp> _serviceCloseList = new ArrayList<>();
  
  private final Supplier<ServicesAmp> _managerRef;
  private final LookupAmp _registry;
  
  private final LruCache<String,ServiceRefAmp> _replyMap = new LruCache<>(64);
  
  private HashMap<String,GatewayResultStream> _gatewayResultMap = new HashMap<>();

  private AtomicBoolean _isLogin = new AtomicBoolean();

  private ServiceRefAmp _serviceRefOut;

  private String _address;

  private String _sessionId;

  public ChannelServerImpl(Supplier<ServicesAmp> managerRef,
                           LookupAmp registry,
                           OutAmp out,
                           String sessionAddress,
                           String sessionId)
  {
    _managerRef = managerRef;
    _registry = registry;
    //_out = out;
    _address = sessionAddress;
    _sessionId = sessionId;
    
    ServicesAmp manager = managerRef.get();
    
    if (manager == null) {
      throw new ServiceExceptionIllegalState(L.l("ServiceManager is not available for this channel"));
    }
    
    ServiceRefAmp callerRef = manager.inboxSystem().serviceRef();
    
    _serviceRefOut = out.createServiceRef(manager, sessionAddress, callerRef);
    
    bind(_address, _serviceRefOut);
    
    _serviceRefOut.start();
  }

  @Override
  public ServiceRefAmp getServiceRefOut()
  {
    return _serviceRefOut;
  }
  
  /*
  @Override
  public final OutboxAmp createOutbox()
  {
    //OutboxAmpDirect outbox = new OutboxAmpDirect();
    OutboxAmp outbox = new OutboxAmpBase();
    outbox.setInbox(_serviceRefOut.getInbox());
    outbox.setMessage(_serviceRefOut.getManager().getSystemMessage());
    
    return outbox;
  }
  */
  
  @Override
  public final InboxAmp getInbox()
  {
    return _serviceRefOut.inbox();
  }
  
  @Override
  public ServicesAmp getManager()
  {
    return _managerRef.get();
  }
  
  public String getAddress()
  {
    return _address;
  }
  
  protected String getSessionId()
  {
    return _sessionId;
  }

  /**
   * Sets the foreign host name.
   */
  public void setHostName(String hostName)
  {
    // TODO Auto-generated method stub
    
  }
  
  public boolean isLogin()
  {
    return _isLogin.get();
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
    _isLogin.set(true);
  }

  public void clearLogin()
  {
    _isLogin.set(false);
  }

  /**
   * Returns the delegated broker.
   */
  protected RegistryAmp getDelegate()
  {
    return getManager().registry();
  }
  
  /**
   * Adds a new link actor.
   */
  //@Override
  public void bind(String address, ServiceRefAmp linkService)
  {
    _linkServiceMap.put(address, linkService);

    _serviceCloseList.add(linkService);
  }
  
  @Override
  public ServiceRefAmp service(String address)
  {
    ServiceRefAmp linkActor = _linkServiceMap.get(address);

    if (linkActor != null) {
      return linkActor;
    }
    
    ServiceRefAmp serviceRef = _registry.service(address);
    String addressService = serviceRef.address();

    if (addressService.startsWith("session:")) {
      ServiceRefAmp sessionRef = lookupSession(serviceRef);
      
      _linkServiceMap.put(address, sessionRef);

      return sessionRef;
    }
    else if (address.startsWith("/")) {
      return serviceRef;
    }
    else if (addressService.startsWith("public:")) {
      return serviceRef;
    }
    
    if (! isLogin()) {
      if (log.isLoggable(Level.FINE)) {
        log.fine("unauthorized service " + address + " from " + this);
      }
      
      return new ServiceRefUnauthorized(getManager(), address);
    }
    else if (! isExported(address, serviceRef)) {
      return new ServiceRefUnauthorized(getManager(), address);
    }
    else {
      return serviceRef;
    }
  }
  
  protected ServiceRefAmp lookupSession(String address)
  {
    int p = address.indexOf(":///");
    
    if (p < 0) {
      return lookupService(address);
    }
    
    if (address.length() < p + 4) {
      return lookupService(address);
    }
    
    int q = address.indexOf("/", p + 4);
    
    if (q > 0) {
      address = address.substring(0, q);
    }
  
    address = address + "/" + _sessionId;
    
    return lookupService(address);
  }
  
  protected ServiceRefAmp lookupSession(ServiceRefAmp serviceRef)
  {
    return (ServiceRefAmp) serviceRef.service("/" + _sessionId);
  }
  
  protected String toFullAddress(String address)
  {
    return "public://" + address;
  }
  
  protected ServiceRefAmp lookupService(String address)
  {
    ServiceRefAmp serviceRef = getManager().service(address);
    
    return serviceRef;
  }
  
  protected boolean isExported(String address, ServiceRefAmp serviceRef)
  {
    if (! serviceRef.isPublic() && ! address.startsWith("public:")) {
      return false;
    }
    
    return true;
  }
  
  @Override
  public GatewayReply createGatewayReply(String remoteName)
  {
    ServiceRefAmp serviceRef = createGatewayRef(remoteName);
    
    return new GatewayReplyServer(serviceRef, remoteName);
  }
  
  @Override
  public ServiceRefAmp createGatewayRef(String remoteName)
  {
    ServiceRefAmp serviceRef = _replyMap.get(remoteName);
    
    if (serviceRef == null) {
      serviceRef = _serviceRefOut.onLookup(remoteName);
      
      Objects.requireNonNull(serviceRef);
      
      _replyMap.put(remoteName, serviceRef);
    }
    
    return serviceRef;
  }
  
  @Override
  public GatewayResultStream createGatewayResultStream(String addressFrom, 
                                                       long qid)
  {
    ServiceRefAmp serviceRef = createGatewayRef(addressFrom);
    
    GatewayResultStreamServer result;
    
    result = new GatewayResultStreamServer(this, serviceRef, addressFrom, qid);
    
    _gatewayResultMap.put(addressFrom + "#" + qid, result);
    
    return result;
  }
  
  @Override
  public GatewayResultStream removeGatewayResultStream(String addressFrom, 
                                                       long qid)
  {
    return _gatewayResultMap.remove(addressFrom + "#" + qid);
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

    for (GatewayResultStream result : _gatewayResultMap.values()) {
      result.cancel();
    }
    
    _serviceRefOut.close();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _serviceRefOut + "]";
  }
  
  private class GatewayReplyServer implements GatewayReply
  {
    private final ServiceRefAmp _serviceRef;
    private final String _from;
    
    public GatewayReplyServer(ServiceRefAmp serviceRef,
                              String from)
    {
      Objects.requireNonNull(serviceRef);
      
      _serviceRef = serviceRef;
      _from = from;
    }
    
    @Override
    public boolean isAsync()
    {
      // return true;
      return false;
    }

    @Override
    public void queryOk(HeadersAmp headers, 
                           long qid,
                           Object value)
    {
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_serviceRef.manager())) {
        MessageAmp msg = new QueryReplyMessage(outbox,
                                               _serviceRef,
                                               headers, 
                                               _serviceRef.stub(), qid, 
                                               value);
      
        long timeout = InboxAmp.TIMEOUT_INFINITY;
      
        msg.offer(timeout);
      }
    }

    @Override
    public void queryFail(HeadersAmp headers, 
                           long qid, 
                           Throwable exn)
    {
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_serviceRef.manager())) {
        MessageAmp msg = new QueryErrorMessage(outbox,
                                               _serviceRef,
                                               headers, 
                                               _serviceRef.stub(), qid, 
                                               exn);
      
        long timeout = InboxAmp.TIMEOUT_INFINITY;
      
        msg.offer(timeout);
      }
    }
    
    @Override
    public ResultStream<Object> stream(HeadersAmp headers, long qid)
    {
      ResultStream<Object> rs = createGatewayResultStream(_from, qid);

      return rs;
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _serviceRef + "]";
    }
  }
  
  @SuppressWarnings("serial")
  private static class GatewayResultStreamServer implements GatewayResultStream
  {
    private final ChannelServer _channel;
    private final ServiceRefAmp _serviceRef;
    private final String _addressFrom;
    private final long _qid;
    
    private StreamResultActorMessage _prevMsg;
    private boolean _isCancelled;
    private int _sequence;
    
    public GatewayResultStreamServer(ChannelServer channel, 
                                     ServiceRefAmp serviceRef,
                                     String addressFrom,
                                     long qid)
    {
      Objects.requireNonNull(channel);
      Objects.requireNonNull(serviceRef);
      
      _channel = channel;
      _serviceRef = serviceRef;
      _addressFrom = addressFrom;
      _qid = qid;
    }
    
    private ServicesAmp getManager()
    {
      return _serviceRef.manager();
    }

    @Override
    public void accept(Object value)
    {
      StreamResultActorMessage msg = _prevMsg;

      if (msg == null || ! msg.add(value)) {
        try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
          _prevMsg = msg = new StreamResultActorMessage(outbox, _serviceRef, _qid);
          msg.add(value);
          _sequence++;
      
          long timeout = 10000;
          msg.offer(timeout);
        }
      }
    }

    @Override
    public void ok()
    {
      StreamResultActorMessage msg = _prevMsg;

      if (msg == null || ! msg.complete(_sequence)) {
        try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
          _prevMsg = msg = new StreamResultActorMessage(outbox, _serviceRef, _qid);
          msg.complete(_sequence);
      
          long timeout = 10000;
          msg.offer(timeout);
          cancelImpl();
        }
      }
    }

    @Override
    public void fail(Throwable exn)
    {
      StreamResultActorMessage msg = _prevMsg;

      if (msg == null || ! msg.failQueue(exn)) {
        try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_serviceRef.manager())) {
          _prevMsg = msg = new StreamResultActorMessage(outbox, _serviceRef, _qid);
          msg.failQueue(exn);
      
          long timeout = 10000;
          msg.offer(timeout);
          cancelImpl();
        }
      }
    }
    
    /*
    private StreamResultActorMessage createResultMessage()
    {
      OutboxAmp outbox = _serviceRef.getManager().getCurrentOutbox();
      
      StreamResultActorMessage msg
        = 
      
      return msg;
    }
    */
    
    @Override
    public ResultStream<Object> createJoin()
    {
      return this;
    }
    
    @Override
    public ResultStream<Object> createFork(ResultStream<Object> next)
    {
      return this;
    }
    
    @Override
    public boolean isCancelled()
    {
      return _isCancelled;
    }

    @Override
    public void cancel()
    {
      ok();
      cancelImpl();
    }
    
    private void cancelImpl()
    {
      _isCancelled = true;
      _channel.removeGatewayResultStream(_addressFrom, _qid);
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _serviceRef + "]";
    }
  }

}
