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

import io.baratine.service.Cancel;
import io.baratine.service.MethodRef;
import io.baratine.service.OnDestroy;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import io.baratine.spi.Headers;

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;

/**
 * Local link for channel management.
 */
@Service
public class SessionServiceAmpImpl
{
  private final ServicesAmp _manager;
  private final ServiceRefAmp _connServiceRef;
  private final ChannelManagerService _sessionManager;
  
  private String _address;
  private ArrayList<Subscription> _subscriptions;
  
  public SessionServiceAmpImpl(ServicesAmp manager,
                          ChannelManagerService channelManager,
                          ServiceRefAmp connServiceRef,
                          String address)
  {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(address);
    Objects.requireNonNull(connServiceRef);
    
    _manager = manager;
    _sessionManager = channelManager;
    _connServiceRef = connServiceRef;
    _address = address;
    
    // XXX: _sessionManager.register(address, _connServiceRef);
  }

  public void publishChannel(String path, Result<String> cont)
  {
    String baseAddress = getAddress();
    
    String address;
    
    if (path != null) {
      address = baseAddress + path;
    }
    else {
      address = baseAddress + "/";
    }

    cont.ok( address);
  }

  public String getChannelAddress()
  {
    String address = getAddress();
    
    return address;
  }
  
  public boolean subscribe(String queueAddress, String path)
  {
    String channel = getAddress() + path;
    
    ServiceRef queue = _manager.service(queueAddress);
    ServiceRef listener = _manager.service(channel);
    
    Cancel cancel = null;//queue.subscribe(listener);
    
    if (_subscriptions == null) {
      _subscriptions = new ArrayList<>();
    }
    
    _subscriptions.add(new QueueSubscription(queue, listener, cancel));
    
    return true;
  }
  
  public boolean consume(String queueAddress, String address)
  {
    String channel = getChannelAddress() + address;
    
    ServiceRef queue = _manager.service(queueAddress);
    ServiceRef listener = _manager.service(channel);
    
    Cancel cancel = null;//queue.consume(listener);
    
    if (_subscriptions == null) {
      _subscriptions = new ArrayList<>();
    }
    
    _subscriptions.add(new QueueSubscription(queue, listener, cancel));
    
    return true;
  }
  
  public boolean register(String serviceAddress, String listenerAddress,
                          String registerMethod, String unregisterMethod)
  {
    String channel = getChannelAddress() + listenerAddress;
    
    ServiceRef service = _manager.service(serviceAddress);
    ServiceRef listener = _manager.service(channel);
    
    MethodRef register = service.getMethod(registerMethod);
    MethodRef unregister = service.getMethod(unregisterMethod);
    
    Headers headers = null;
    register.send(headers, listener);
    
    if (_subscriptions == null) {
      _subscriptions = new ArrayList<>();
    }
    
    _subscriptions.add(new ServiceSubscription(unregister, listener));
    
    return true;
  }
  
  @OnDestroy
  public void stop()
  {
    if (_subscriptions == null) {
      return;
    }
    
    for (Subscription sub : _subscriptions) {
      sub.unsubscribe();
    }
    
    _subscriptions.clear();
  }
  
  private String getAddress()
  {
    return _address;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _connServiceRef + "]";
  }
  
  private abstract static class Subscription {
    abstract void unsubscribe();
  }
  
  static class QueueSubscription extends Subscription {
    private ServiceRef _queue;
    private ServiceRef _listener;
    private Cancel _cancel;
    
    QueueSubscription(ServiceRef queue,
                      ServiceRef listener,
                      Cancel cancel)
    {
      _queue = queue;
      _listener = listener;
    }
    
    @Override
    void unsubscribe()
    {
      _cancel.cancel();
      // _queue.unsubscribe(_listener);
    }
  }
  
  static class ServiceSubscription extends Subscription {
    private MethodRef _unregister;
    private ServiceRef _listener;
    
    ServiceSubscription(MethodRef unregister,
                        ServiceRef listener)
    {
      _unregister = unregister;
      _listener = listener;
    }
    
    @Override
    void unsubscribe()
    {
      Headers headers = null;
      _unregister.send(headers, _listener);
    }
  }
}
