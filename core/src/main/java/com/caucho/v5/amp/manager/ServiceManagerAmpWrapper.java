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

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.session.ContextSession;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ProxyFactoryAmp;
import com.caucho.v5.amp.spi.RegistryAmp;
import com.caucho.v5.amp.spi.ServiceBuilderAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

import io.baratine.inject.InjectManager;
import io.baratine.inject.Key;
import io.baratine.service.QueueFullHandler;
import io.baratine.service.Result;
import io.baratine.service.ServiceNode;
import io.baratine.service.ServiceRef;
import io.baratine.spi.Message;

/**
 * Creates AMP actors and proxies.
 */
abstract public class ServiceManagerAmpWrapper implements ServiceManagerAmp
{
  abstract protected ServiceManagerAmp delegate();

  @Override
  public ServiceRef currentService()
  {
    return delegate().currentService();
  }

  @Override
  public Message currentMessage()
  {
    return delegate().currentMessage();
  }

  /* XXX: waiting for JDK 8
  // @Override
  public ServiceRef service(Supplier<?> workerSupplier,
                            ServiceConfig config)
  {
    return _delegate.service(workerSupplier, config);
  }
  */

  /*
  @Override
  public <T> DisruptorBuilder<T> disruptor(Class<T> api)
  {
    return delegate().disruptor(api);
  }
  */

  @Override
  public String address(Class<?> api)
  {
    return delegate().address(api);
  }
  
  @Override
  public String getDebugId()
  {
    return delegate().getDebugId();
  }
  
  @Override
  public String getName()
  {
    return delegate().getName();
  }

  @Override
  public ServiceRefAmp service(String address)
  {
    return delegate().service(address);
  }

  @Override
  public <T> T service(Class<T> api)
  {
    return delegate().service(api);
  }

  @Override
  public <T> T service(Class<T> api, String id)
  {
    return delegate().service(api, id);
  }

  @Override
  public <T> T newProxy(ServiceRefAmp actorRef, 
                           Class<T> api,
                           Class<?>... apis)
  {
    return delegate().newProxy(actorRef, api, apis);
  }

  /*
  @Override
  public <T> T createPinProxy(ServiceRefAmp actorRef, 
                                   Class<T> api,
                                   Class<?>... apis)
  {
    return getDelegate().createPinProxy(actorRef, api, apis);
  }
  */

  @Override
  public ServiceRefAmp bind(ServiceRefAmp service, String address)
  {
    return delegate().bind(service, address);
  }

  @Override
  public ServiceBuilderAmp newService(Object worker)
  {
    return delegate().newService(worker);
  }

  @Override
  public ServiceBuilderAmp newService(Supplier<?> supplier)
  {
    return delegate().newService(supplier);
  }

  @Override
  public ServiceBuilderAmp newService(Class<?> cl)
  {
    return delegate().newService(cl);
  }

  @Override
  public ServiceBuilderAmp service(Key<?> key, Class<?> api)
  {
    return delegate().service(key, api);
  }

  @Override
  public ServiceRefAmp toService(Object worker)
  {
    return delegate().toService(worker);
  }

  /*
  @Override
  public ServiceRefAmp service(ActorAmp actor)
  {
    return getDelegate().service(actor);
  }
  */

  /*
  @Override
  public ServiceRefAmp service(ActorFactoryAmp actorFactory)
  {
    return getDelegate().service(actorFactory);
  }
  */

  @Override
  public ServiceRefAmp pin(ServiceRefAmp context, Object listener)
  {
    return delegate().pin(context, listener);
  }

  @Override
  public ServiceRefAmp pin(ServiceRefAmp context, 
                                Object listener,
                                String path)
  {
    return delegate().pin(context, listener, path);
  }

  /*
  @Override
  public ServiceRefAmp service(QueueServiceFactoryInbox serviceFactory,
                                ServiceConfig config)
  {
    return getDelegate().service(serviceFactory, config);
  }
  */
  
  @Override
  public ServiceNode node()
  {
    return delegate().node();
  }

  @Override
  public RegistryAmp registry()
  {
    return delegate().registry();
  }

  @Override
  public InboxAmp inboxSystem()
  {
    return delegate().inboxSystem();
  }

  /*
  @Override
  public OutboxAmp getSystemOutbox()
  {
    return getDelegate().getSystemOutbox();
  }
  */
  
  @Override
  public QueueFullHandler getQueueFullHandler()
  {
    return delegate().getQueueFullHandler();
  }

  /*
  @Override
  public <T> T createQueue(InboxAmp mailbox, Object bean, String address,
                           Class<T> api)
  {
    return getDelegate().createQueue(mailbox, bean, address, api);
  }
  */

  @Override
  public ActorAmp createActor(Object bean, ServiceConfig config)
  {
    return delegate().createActor(bean, config);
  }

  @Override
  public ProxyFactoryAmp getProxyFactory()
  {
    return delegate().getProxyFactory();
  }

  @Override
  public InjectManager inject()
  {
    return delegate().inject();
  }

  /*
  @Override
  public ActorAmp createActor(String name, Object bean, ServiceConfig config)
  {
    return getDelegate().createActor(name, bean, config);
  }
  */

  /*
  @Override
  public ActorAmp createActor(Object child, 
                              String path,
                              String childPath,
                              ActorContainerAmp container,
                              ServiceConfig config)
  {
    return getDelegate().createActor(child, path, childPath, container, config);
  }
  */

  /*
  @Override
  public ActorAmp createMainActor(Class<?> beanClass,
                                  String name,
                                  ServiceConfig config)
  {
    return getDelegate().createMainActor(beanClass, name, config);
  }
  */

  /*
  @Override
  public ActorAmp createActorSession(Object bean,
                                     String key,
                                     ContextSession context,
                                     ServiceConfig config)
  {
    return getDelegate().createActorSession(bean, key, context, config);
  }
  */

  /*
  @Override
  public OutboxAmp getCurrentOutbox()
  {
    return getDelegate().getCurrentOutbox();
  }
  */

  @Override
  public Supplier<OutboxAmp> outboxFactory()
  {
    return delegate().outboxFactory();
  }

  /*
  @Override
  public <T> T run(ResultFuture<T> future,
                   long timeout,
                   TimeUnit unit,
                   Runnable task)
  {
    return getDelegate().run(future, timeout, unit, task);
  }
  */

  @Override
  public <T> T run(long timeout,
                   TimeUnit unit,
                   Consumer<Result<T>> task)
  {
    return delegate().run(timeout, unit, task);
  }

  @Override
  public ServiceRefAmp toServiceRef(Object proxy)
  {
    return delegate().toServiceRef(proxy);
  }

  @Override
  public OutboxAmp getOutboxSystem()
  {
    return delegate().getOutboxSystem();
  }

  @Override
  public JournalAmp openJournal(String name)
  {
    return delegate().openJournal(name);
  }

  /*
  @Override
  public MessageAmp systemMessage()
  {
    return delegate().systemMessage();
  }
  */
  
  //
  // modules
  //
  
  /*
  @Override
  public RampSystem getSystem()
  {
    return getDelegate().getSystem();
  }
  
  @Override
  public ModuleAmp getModule()
  {
    return getDelegate().getModule();
  }
  
  public ModuleRef.Builder module(String name, String version)
  {
    return getDelegate().module(name, version);
  }
  */

  @Override
  public void addAutoStart(ServiceRef serviceRef)
  {
    delegate().addAutoStart(serviceRef);
  }
  
  @Override
  public boolean isClosed()
  {
    return delegate().isClosed();
  }
  
  @Override
  public boolean isAutoStart()
  {
    return delegate().isAutoStart();
  }
  
  @Override
  public void setAutoStart(boolean isAutoStart)
  {
    delegate().setAutoStart(isAutoStart);
  }
  
  @Override
  public String getSelfServer()
  {
    return delegate().getSelfServer();
  }
  
  @Override
  public void setSelfServer(String hostName)
  {
    delegate().setSelfServer(hostName);
  }
  
  /*
  @Override
  public String getPeerServer()
  {
    return getDelegate().getPeerServer();
  }
  
  @Override
  public void setPeerServer(String hostName)
  {
    getDelegate().setPeerServer(hostName);
  }
  */
  
  @Override
  public void start()
  {
    delegate().start();
  }

  @Override
  public void close()
  {
    delegate().close();
  }

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    delegate().shutdown(mode);
  }
  
  //
  // stats
  //

  @Override
  public void addRemoteMessageWrite()
  {
    delegate().addRemoteMessageWrite();
  }

  @Override
  public long getRemoteMessageWriteCount()
  {
    return delegate().getRemoteMessageWriteCount();
  }

  @Override
  public void addRemoteMessageRead()
  {
    delegate().addRemoteMessageRead();
  }

  @Override
  public long getRemoteMessageReadCount()
  {
    return delegate().getRemoteMessageReadCount();
  }

  // XXX: refactor
  @Override
  public ContextSession createContextServiceSession(String path, Class<?> beanClass)
  {
    return delegate().createContextServiceSession(path, beanClass);
  }

  /*
  @Override
  public ServiceBuilderAmp newService()
  {
    return delegate().newService();
  }
  */

  /*
  @Override
  public ServiceConfig.Builder newServiceConfig()
  {
    return getDelegate().newServiceConfig();
  }
  */

  @Override
  public boolean isDebug()
  {
    return false;
  }
  
  @Override
  public ClassLoader classLoader()
  {
    return delegate().classLoader();
  }
  
  @Override
  public Trace trace()
  {
    return delegate().trace();
  }
  
  /*
  @Override
  public void setSystemExecutorFactory(Supplier<Executor> factory)
  {
    getDelegate().setSystemExecutorFactory(factory);
  }
  */
}
