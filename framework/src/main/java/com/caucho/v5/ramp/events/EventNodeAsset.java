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

package com.caucho.v5.ramp.events;

import io.baratine.service.Cancel;
import io.baratine.service.MethodRef;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.StubAmpBase;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.MethodAmpBase;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.util.L10N;

/**
 * actor to handle inbound calls.
 */
class EventNodeAsset extends StubAmpBase
{
  private static final L10N L = new L10N(EventNodeAsset.class);
  private static final Logger log = Logger.getLogger(EventNodeAsset.class.getName());
  private static final ServiceRefComparator CMP = new ServiceRefComparator();
  
  private String _address;
  
  private EventSchemeImpl _root;
  
  private final ArrayList<ServiceRef> _subscriberList
    = new ArrayList<>();
    
  private final ArrayList<ServiceRef> _consumerList
    = new ArrayList<>();
    
  private long _sequence;
  
  public EventNodeAsset(EventSchemeImpl root,
                         String address)
  {
    _root = root;
    _address = address;
  }
  
  @Override
  public String name()
  {
    return _address;
  }
  
  String getPath()
  {
    return _address;
  }
  
  EventSchemeImpl getEvents()
  {
    return _root;
  }
  
  @Override
  public void subscribe(ServiceRef subscriber)
  {
    subscribeImpl(subscriber);
  }
    
  public Cancel subscribeImpl(ServiceRef subscriber)
  {
    _subscriberList.add(subscriber);

    Collections.sort(_subscriberList, CMP);
      
    subscribe();
    
    return new CancelSubscribe(subscriber);
  }

  void subscribe(EventNodeServer eventNodeRemote)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void consume(ServiceRef consumer)
  {
    consumeImpl(consumer);
  }

  public Cancel consumeImpl(ServiceRef consumer)
  {
    _consumerList.add(consumer);
    
    Collections.sort(_consumerList, CMP);
    
    subscribe();
    
    return new CancelSubscribe(consumer);
  }

  @Override
  public void unsubscribe(ServiceRef subscriber)
  {
    _subscriberList.remove(subscriber);
    _consumerList.remove(subscriber);
  }
  
  protected void subscribe()
  {
  }

  @Override
  public MethodAmp getMethod(String name)
  {
    return new RampPublishMethod(name);
    
    /*
    switch (name) {
    case "subscribe":
      return new RampSubscribeMethod(name);
      
    case "consume":
      return new RampConsumeMethod(name);
      
    default:
    }
    */
  }
  
  @Override
  public StubAmp onLookup(String path, ServiceRefAmp parentRef)
  {
    return _root.lookupPubSubNode(_address + path);
  }
  
  public void publish(String methodName, Object[] args)
  {
    publishImpl(methodName, args);
  }

  void publishFromRemote(String methodName, Object[] args)
  {
    publishImpl(methodName, args);
  }

  protected void publishImpl(String methodName, Object[] args)
  {
    long sequence = _sequence++;
    
    int size = _subscriberList.size();
    
    for (int i = 0; i < size; i++) {
      ServiceRef listener = _subscriberList.get(i);
      
      if (! listener.isClosed()) {
        MethodRef methodRef = listener.getMethod(methodName);

        methodRef.send(args);
      }
      else {
        _subscriberList.remove(i--);
        size--;
      }
    }
    
    size = _consumerList.size();
    if (size > 0) {
      ServiceRef consumer = _consumerList.get((int) (sequence % size));
      
      MethodRef method = consumer.getMethod(methodName);
      
      method.send(args);
    }
  
    /*
    size = _remoteList.size();
    for (int i = 0; i < size; i++) {
      EventNodeServer listener = _remoteList.get(i);
      
      listener.publish(methodName, args);
    }
    */
  }

  void onServerUpdate(ServerBartender server)
  {
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _address + "]";
  }
  
  class RampPublishMethod extends MethodAmpBase {
    private String _name;
    
    RampPublishMethod(String name)
    {
      _name = name;
    }

    @Override
    public void send(HeadersAmp headers,
                     StubAmp actor,
                     Object []args)
    {
      publish(_name, args);
    }

    @Override
    public void query(HeadersAmp headers,
                      Result<?> result,
                      StubAmp actor,
                      Object []args)
    {
      send(headers, actor, args);
      
      result.ok(null);
      /*
      ServiceException exn = new ServiceExceptionIllegalArgument(L.l("{0} event cannot use blocking queries",
                                                                 _address));
      
      result.fail(exn);
      */
    }
    
    public String toString()
    {
      return (getClass().getSimpleName() + "[" + _name
              + "," + EventNodeAsset.this + "]");
    }
  }
  
  /*
  private class RampSubscribeMethod extends MethodAmpBase {
    private String _name;
    
    RampSubscribeMethod(String name)
    {
      _name = name;
    }

    @Override
    public void send(HeadersAmp headers,
                     ActorAmp actor,
                     Object []args)
    {
      subscribe(ServiceRef.toServiceRef(args[0]));
    }

    @Override
    public void query(HeadersAmp headers,
                      Result<?> result,
                      ActorAmp actor,
                      Object []args)
    {
      CancelHandle cancel = subscribeImpl(ServiceRef.toServiceRef(args[0]));
      
      cancel = ServiceRef.current().pin(cancel).as(CancelHandle.class);
      
      ((Result) result).complete(cancel);
    }
    
    public String toString()
    {
      return (getClass().getSimpleName() + "[" + _name
              + "," + EventNodeActor.this + "]");
    }
  }
  */
  
  /*
  private class RampConsumeMethod extends MethodAmpBase {
    private String _name;
    
    RampConsumeMethod(String name)
    {
      _name = name;
    }

    @Override
    public void send(HeadersAmp headers,
                     ActorAmp actor,
                     Object []args)
    {
      consume(ServiceRef.toServiceRef(args[0]));
    }

    @Override
    public void query(HeadersAmp headers,
                      Result<?> result,
                      ActorAmp actor,
                      Object []args)
    {
      CancelHandle cancel = consumeImpl(ServiceRef.toServiceRef(args[0]));
      
      cancel = ServiceRef.current().pin(cancel).as(CancelHandle.class);
      
      ((Result) result).complete(cancel);
    }
    
    public String toString()
    {
      return (getClass().getSimpleName() + "[" + _name
              + "," + EventNodeActor.this + "]");
    }
  }
  */
  
  private class CancelSubscribe implements Cancel {
    private ServiceRef _subscribeRef;
    
    CancelSubscribe(ServiceRef serviceRef)
    {
      Objects.requireNonNull(serviceRef);
      
      _subscribeRef = serviceRef;
    }
    
    public void cancel()
    {
      unsubscribe(_subscribeRef);
    }
  }
  
  private static class ServiceRefComparator implements Comparator<ServiceRef>
  {
    @Override
    public int compare(ServiceRef a, ServiceRef b)
    {
      return a.toString().compareTo(b.toString());
    }
  }
}
