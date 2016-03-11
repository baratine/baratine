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

import java.util.Objects;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServiceManagerAmp.DisruptorBuilder;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.ActorAmpDisruptor;
import com.caucho.v5.amp.inbox.InboxQueue;
import com.caucho.v5.amp.inbox.QueueServiceFactoryInbox;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.outbox.QueueService;
import com.caucho.v5.amp.proxy.SkeletonClass;
import com.caucho.v5.amp.queue.QueueServiceBuilder;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.util.L10N;

/**
 * amp disruptor method
 */
public class DisruptorBuilderTop<T> extends DisruptorBuilderBase<T>
{
  private static final L10N L = new L10N(DisruptorBuilderTop.class);

  private final ServiceBuilderImpl _serviceBuilder;
  private final AmpManager _ampManager;
  private final Class<T> _api;
  
  private String _name;
  
  private final JournalAmp _journal;
  private ActorAmp _actorMain;
  
  //private DisruptorBuilderNode<T> _peer;
  private DisruptorBuilderNode<T> _next;
  
  public DisruptorBuilderTop(ServiceBuilderImpl serviceBuilder,
                             AmpManager ampManager, 
                                 Class<T> api,
                                 JournalAmp journal)
  {
    _serviceBuilder = serviceBuilder;
    _ampManager = ampManager;
    _api = api;
    _journal = journal;
    
    _name = "class:" + api.getName();
  }
  
  @Override
  protected AmpManager getManager()
  {
    return _ampManager;
  }
  
  Class<T> getApi()
  {
    return _api;
  }
  
  public DisruptorBuilder<T> name(String name)
  {
    Objects.requireNonNull(name);
    
    _name = name;
    
    return this;
  }
  
  public DisruptorBuilder<T> actorMain(ActorAmp actor)
  {
    _actorMain = actor;
    
    return this;
  }
  
  /*
  @Override
  public DisruptorBuilder<T> peer(T worker)
  {
    if (_peer != null) {
      return _peer.peer(worker);
    }
    
    if (_next != null) {
      throw new IllegalStateException(L.l("peer workers must be defined before next at {0}.",
                                          worker));
    }
    
    _peer = new DisruptorBuilderNode<T>(this, deliverFactory(worker));
    
    return _peer;
  }
  */
  /*
  
  @Override
  public DisruptorBuilderAmp<T> peer(Supplier<? extends T> supplierBean,
                                     ServiceConfig config)
  {
    if (_peer != null) {
      return _peer.peer(supplierBean, config);
    }
    
    if (_next != null) {
      throw new IllegalStateException(L.l("peer workers must be defined before next at {0}.",
                                          supplierBean));
    }
    
    _peer = create(deliverFactoryBean(supplierBean, config));
    
    return _peer;
  }
  */
  
  /*
  @Override
  public DisruptorBuilderAmp<T> peer(DeliverFactoryDisruptor deliverFactory)
  {
    if (_peer != null) {
      return _peer.peer(deliverFactory);
    }
    
    if (_next != null) {
      throw new IllegalStateException(L.l("peer workers must be defined before next at {0}.",
                                          deliverFactory));
    }
    
    _peer = create(deliverFactory);
    
    return _peer;
  }
  */
  
  private DisruptorBuilderNode<T> create(DeliverFactoryDisruptor deliverFactory)
  {
    /*
    if (config != null && config.getMaxWorkers() > 1) {
      return new DisruptorBuilderNodeMulti<T>(this, supplier, config);
    }
    else {
    }
    */
    return new DisruptorBuilderNode<T>(this, deliverFactory);
  }

  @Override
  public DisruptorBuilderAmp<T> next(T worker)
  {
    if (_next != null) {
      throw new IllegalStateException(L.l("Only a single immediate 'next' is allowed at {0}.",
                                          worker));
    }
    
    /*
    if (_peer != null) {
      return _peer.next(worker);
    }
    */
    
    _next = new DisruptorBuilderNode<T>(this, deliverFactory(worker));
    
    return _next;
  }

  @Override
  public DisruptorBuilderAmp<T> next(Supplier<? extends T> supplier,
                                  ServiceConfig config)
  {
    if (_next != null) {
      throw new IllegalStateException(L.l("Only a single immediate 'next' is allowed at {0}.",
                                          supplier));
    }
    
    /*
    if
    (_peer != null) {
      return _peer.next(supplier, config);
    }
    */
    
    // Supplier<ActorAmp> supplierActor = new SupplierActor(supplier);
    
    _next = new DisruptorBuilderNode<T>(this, deliverFactoryBean(supplier, config));
    
    return _next;
  }

  @Override
  public DisruptorBuilderAmp<T> next(DeliverFactoryDisruptor deliverFactory)
  {
    if (_next != null) {
      throw new IllegalStateException(L.l("Only a single immediate 'next' is allowed at {0}.",
                                          deliverFactory));
    }
    
    /*
    if (_peer != null) {
      return _peer.next(deliverFactory);
    }
    */
    
    // Supplier<ActorAmp> supplierActor = new SupplierActor(supplier);
    
    _next = new DisruptorBuilderNode<T>(this, deliverFactory);
    
    return _next;
  }

  @Override
  public ServiceRefAmp build()
  {
    /*
    ServiceConfig.Builder builder = ServiceConfig.Builder.create();
    
    return build(builder.build());
    */
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public ServiceRefAmp build(ServiceConfig config)
  {
    Objects.requireNonNull(config);
    
    return buildImpl(config);
  }

  private ServiceRefAmp buildImpl(ServiceConfig config)
  {
    // XXX: needs to be through proxy factory.
    SkeletonClass skel = new SkeletonClass(_ampManager, _api, config);
    skel.introspect();
    
    // XXX: 
    ActorAmp actor = new ActorAmpDisruptor(skel, _journal, _actorMain, _name);
    
    //ActorAmp actor = _actorMain;
    
    QueueServiceFactoryInbox serviceFactory
      = new DisruptorServiceFactory(actor);

    return _serviceBuilder.service(serviceFactory, config);
  }
  
  /*
  private RampActor []buildActors()
  {
    ArrayList<RampActor> actorList = new ArrayList<>();
    
    if (_peer != null) {
      _peer.buildActors(actorList);
    }
    else if (_next != null) {
      _next.buildActors(actorList);
    }
    else {
      throw new IllegalStateException(L.l("No actors are defined."));
    }
    
    RampActor []actors = new RampActor[actorList.size()];
    actorList.toArray(actors);
    
    return actors;
  }
  */
  
  private static class WorkerSupplier<T> implements Supplier<T> {
    private T _worker;
    
    WorkerSupplier(T worker)
    {
      _worker = worker;
    }
    
    @Override
    public T get()
    {
      return _worker;
    }
  }

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
  
  class DisruptorServiceFactory implements QueueServiceFactoryInbox
  {
    private ActorAmp _actor;
    
    DisruptorServiceFactory(ActorAmp actor)
    {
      _actor = actor;
    }
    
    public String getName()
    {
      return _actor.getName();
    }
    
    @Override
    public QueueService<MessageAmp> build(QueueServiceBuilder<MessageAmp> queueBuilder,
                                          InboxQueue inbox)
    {
      /*
      if (_peer != null) {
        return _peer.buildQueue(queueBuilder, inbox);
      }
      else
      */ 
      
      if (_next != null) {
        return _next.buildQueue(queueBuilder, inbox);
      }
      else {
        throw new IllegalStateException();
      }
      
      // builder.actor(queueMailbox.createQueueWorkers(_actor));
      
      // return builder.build(queueBuilder);
    }

    @Override
    public ActorAmp getMainActor()
    {
      return _actor;
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _actor + "]";
    }
  }
}
