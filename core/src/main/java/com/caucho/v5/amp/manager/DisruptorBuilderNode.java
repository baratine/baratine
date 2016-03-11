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

package com.caucho.v5.amp.manager;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.inbox.InboxQueue;
import com.caucho.v5.amp.outbox.QueueService;
import com.caucho.v5.amp.queue.DisruptorBuilderQueue;
import com.caucho.v5.amp.queue.DisruptorBuilderQueue.DeliverFactory;
import com.caucho.v5.amp.queue.QueueServiceBuilder;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.util.L10N;

/**
 * amp disruptor method
 */
public class DisruptorBuilderNode<T> extends DisruptorBuilderBase<T>
{
  private static final L10N L = new L10N(DisruptorBuilderNode.class);
  
  private final DisruptorBuilderTop<T> _top;
  
  //private final Supplier<ActorAmp> _supplier;
  //private final ServiceConfig _config;
  
  private final DeliverFactoryDisruptor _deliverFactory;
  
  //private ArrayList<DisruptorBuilderNode<T>> _peers = new ArrayList<>();
  private DisruptorBuilderNode<T> _next;
  
  DisruptorBuilderNode(DisruptorBuilderTop<T> top,
                       Supplier<ActorAmp> supplier,
                       ServiceConfig config)
  {
    Objects.requireNonNull(supplier);

    _top = top;
    
    _deliverFactory = deliverFactory(supplier, config);
  }
  
  DisruptorBuilderNode(DisruptorBuilderTop<T> top,
                       DeliverFactoryDisruptor deliverFactory)
  {
    Objects.requireNonNull(deliverFactory);

    _top = top;
    
    _deliverFactory = deliverFactory;
  }
  
  private DisruptorBuilderTop<T> getHead()
  {
    return _top;
  }
  
  protected ServiceManagerAmp getManager()
  {
    return getHead().getManager();
  }
  
  /*
  @Override
  public DisruptorBuilderAmp<T> peer(T worker)
  {
    DisruptorBuilderNode<T> peer = create(deliverFactory(worker));
    
    _peers.add(peer);
    
    return peer;
  }
  */
  
  /*
  @Override
  public DisruptorBuilderAmp<T> peer(Supplier<? extends T> supplier,
                                  ServiceConfig config)
  {
    DisruptorBuilderNode<T> peer;
    
    peer = create(deliverFactoryBean(supplier, config));
    
    _peers.add(peer);
    
    return peer;
  }
  */
  
  /*
  @Override
  public DisruptorBuilderAmp<T> peer(DeliverFactoryDisruptor deliverFactory)
  {
    DisruptorBuilderNode<T> peer;
    
    peer = create(deliverFactory);
    
    _peers.add(peer);
    
    return peer;
  }
  */

  @Override
  public DisruptorBuilderAmp<T> next(T worker)
  {
    if (_next != null) {
      throw new IllegalStateException(L.l("Only a single immediate 'next' is allowed at {0}.",
                                          worker));
    }
    
    DisruptorBuilderNode<T> next = create(deliverFactory(worker));
    
    _next = next;
    
    return next;
  }
  
  @Override
  public DisruptorBuilderAmp<T> next(Supplier<? extends T> supplierBean,
                                      ServiceConfig config)
  {
    Objects.requireNonNull(supplierBean);
    
    if (_next != null) {
      throw new IllegalStateException(L.l("Only a single immediate 'next' is allowed at {0}.",
                                          supplierBean));
    }
    
    _next = create(deliverFactoryBean(supplierBean, config));
    
    return _next;
  }
  
  @Override
  public DisruptorBuilderAmp<T> next(DeliverFactoryDisruptor deliverFactory)
  {
    Objects.requireNonNull(deliverFactory);
    
    if (_next != null) {
      throw new IllegalStateException(L.l("Only a single immediate 'next' is allowed at {0}.",
                                          deliverFactory));
    }
    
    _next = create(deliverFactory);
    
    return _next;
  }
  
  /*
  private DisruptorBuilderNode<T> create(Supplier<ActorAmp> supplier)
  {
    return create(supplier, null);
  }
    
  private DisruptorBuilderNode<T> create(Supplier<ActorAmp> supplier,
                                         ServiceConfig config)
  {
    return new DisruptorBuilderNode<T>(getHead(), supplier, config);
  }
  */
  
  private DisruptorBuilderNode<T> create(DeliverFactoryDisruptor deliverFactory)
  {
    return new DisruptorBuilderNode<T>(getHead(), deliverFactory);
  }

  @Override
  public ServiceRefAmp build()
  {
    //ServiceConfig.Builder builder = ServiceConfig.Builder.create();
    
    //return build(builder.build());
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public ServiceRefAmp build(ServiceConfig config)
  {
    Objects.requireNonNull(config);
    
    return getHead().build(config);
  }

  public QueueService<MessageAmp> buildQueue(QueueServiceBuilder<MessageAmp> queueBuilder,
                                              InboxQueue inbox)
  {
    DeliverFactory<MessageAmp> factory = _deliverFactory.get(inbox);
    
    DisruptorBuilderQueue<MessageAmp> builder;
    
    builder = queueBuilder.disruptorBuilder(factory);
    
    buildDisruptorChildren(builder, inbox);

    return builder.build();
  }
  
  /*
  void buildActors(ArrayList<RampActor> actorList)
  {
    RampActor actor = createRampActor();
      
    actorList.add(actor);

    for (RampDisruptorBuilderImpl<T> peer : _peers) {
      peer.buildActors(actorList);
    }
    
    if (_next != null) {
      _next.buildActors(actorList);
    }
  }
  */
  
  DeliverFactory<MessageAmp> createDeliverFactory(InboxQueue inbox)
  {
    return _deliverFactory.get(inbox);
  }

  /*
  ActorAmp createRampActor()
  {
    return _supplier.get();
  }
  */

  void buildDisruptorChildren(DisruptorBuilderQueue<MessageAmp> builder,
                              InboxQueue inbox)
  {
    /*
    for (DisruptorBuilderNode<T> peer : _peers) {
      DisruptorBuilderQueue<MessageAmp,InboxAmp> peerBuilder;
      
      peerBuilder = builder.peer(peer.createDeliverFactory(inbox));
      
      peer.buildDisruptorChildren(peerBuilder, inbox);
    }
    */
    
    if (_next != null) {
      DisruptorBuilderQueue<MessageAmp> next;
      next = builder.next(_next.createDeliverFactory(inbox));
      
      _next.buildDisruptorChildren(next, inbox);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _deliverFactory + "]";
  }
  
  /*
  private class SupplierActor implements Supplier<ActorAmp>
  {
    private final Supplier<?> _supplierBean;
    
    public SupplierActor(Supplier<?> supplierBean)
    {
      _supplierBean = supplierBean;
    }
    
    @Override
    public ActorAmp get()
    {
      return getManager().createActor(_supplierBean.get());
    }
  }
  */
  
  static class GatewaySupplier<T> implements Supplier<T> {
    private Class<? extends T> _workerClass;
    
    GatewaySupplier(Class<? extends T> workerClass)
    {
      _workerClass = workerClass;
    }
    
    @Override
    public T get()
    {
      try {
        return _workerClass.newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
