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

import java.util.function.Supplier;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.inbox.InboxQueue;
import com.caucho.v5.amp.queue.DisruptorBuilderQueue.DeliverFactory;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.MessageAmp;

/**
 * amp disruptor method
 */
abstract public class DisruptorBuilderBase<T> implements DisruptorBuilderAmp<T>
{
  abstract protected ServiceManagerAmp getManager();
  
  protected DeliverFactoryDisruptor deliverFactory(T worker)
  {
    ServiceConfig config = null;
    
    ActorAmp actor = getManager().createActor(worker, config);
    
    Supplier<ActorAmp> supplier = ()->actor;

    return deliverFactory(supplier, config);
  }
  
  protected DeliverFactoryDisruptor 
  deliverFactoryBean(Supplier<? extends T> supplierBean,
                     ServiceConfig config)
  {
    Supplier<ActorAmp> supplier = new SupplierActor(supplierBean, config);

    return deliverFactory(supplier, config);
  }

  protected DeliverFactoryDisruptor deliverFactory(Supplier<ActorAmp> supplierActor,
                                                   ServiceConfig config)
  {
    return new DeliverFactoryImpl(supplierActor, config);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  private class SupplierActor implements Supplier<ActorAmp>
  {
    private final Supplier<?> _supplierBean;
    private ServiceConfig _config;
    
    public SupplierActor(Supplier<?> supplierBean, ServiceConfig config)
    {
      _supplierBean = supplierBean;
      _config = config;
    }
    
    @Override
    public ActorAmp get()
    {
      return getManager().createActor(_supplierBean.get(), _config);
    }
  }

  private static class DeliverFactoryImpl implements DeliverFactoryDisruptor
  {
    private final Supplier<ActorAmp> _supplier;
    private final ServiceConfig _config;
    
    DeliverFactoryImpl(Supplier<ActorAmp> supplierActor,
                       ServiceConfig config)
    {
      _supplier = supplierActor;
      _config = config;
    }
    
    @Override
    public DeliverFactory<MessageAmp> get(InboxQueue inbox)
    {
      return inbox.createDeliverFactory(_supplier, _config);
    }
  }
}
