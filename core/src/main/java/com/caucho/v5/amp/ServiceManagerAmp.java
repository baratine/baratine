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

package com.caucho.v5.amp;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.manager.ServiceConfig;
import com.caucho.v5.amp.session.ContextSession;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.InboxFactoryAmp;
import com.caucho.v5.amp.spi.LookupAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ProxyFactoryAmp;
import com.caucho.v5.amp.spi.RegistryAmp;
import com.caucho.v5.amp.spi.ServiceBuilderAmp;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

import io.baratine.inject.InjectManager;
import io.baratine.inject.Key;
import io.baratine.service.QueueFullHandler;
import io.baratine.service.Result;
import io.baratine.service.ServiceManager;
import io.baratine.service.ServiceRef;
import io.baratine.spi.Message;

/**
 * Manages an AMP domain.
 */
public interface ServiceManagerAmp extends ServiceManager, LookupAmp
{
  static ServiceManagerAmp current()
  {
    return (ServiceManagerAmp) ServiceManager.current();
  }
  
  @Override
  ServiceRefAmp service(String address);
  
  /**
   * The current service, or if called from outside of a service, the
   * system service.
   */
  ServiceRef currentService();
  
  Message currentMessage();
 
  <T> T newProxy(ServiceRefAmp actorRef, 
                    Class<T> api,
                    Class<?> ...apis);
  
  /*
  <T> T createPinProxy(ServiceRefAmp actorRef, 
                    Class<T> api,
                    Class<?> ...apis);
                    */

  String address(Class<?> type);

  ServiceBuilderAmp newService();
  
  ServiceRefAmp toService(Object value);

  @Override
  ServiceBuilderAmp newService(Object value);

  @Override
  ServiceBuilderAmp newService(Supplier<?> supplier);

  @Override
  ServiceBuilderAmp newService(Class<?> type);

  ServiceBuilderAmp service(Key<?> key, Class<?> apiClass);
  
  //ServiceRefAmp service(ActorAmp actor);
  
  //ServiceConfig.Builder newServiceConfig();
                           
  //ServiceRefAmp service(ActorFactoryAmp actorFactory);
  
  ServiceRefAmp pin(ServiceRefAmp parent,
                    Object listener);
  
  ServiceRefAmp pin(ServiceRefAmp context,
                    Object listener,
                    String path);
  
  ServiceRefAmp bind(ServiceRefAmp service, String address);
  
  /*
  ServiceRefAmp service(QueueServiceFactoryInbox serviceFactory,
                         ServiceConfig config);
                         */

  
  /**
   * Returns the domain's broker.
   */
  RegistryAmp registry();
  
  String getName();
  
  String getDebugId();
  
  InjectManager inject();
  
  /**
   * @return
   */
  InboxAmp inboxSystem();

  /*
  <T> T createQueue(InboxAmp mailbox, 
                    Object bean, 
                    String address,
                    Class<T> api);
                    */
  
  ProxyFactoryAmp getProxyFactory();

  ActorAmp createActor(Object bean, ServiceConfig config);
  
  //ActorAmp createActor(String actorName, Object bean, ServiceConfig config);
  /*
  ActorAmp createActor(Object child, 
                       String address, String childPath,
                       ActorContainerAmp container,
                       ServiceConfig config);
                       */
  
  // ActorAmp createMainActor(Class<?> beanClass, String name, ServiceConfig config);
  
  /*
  ActorAmp createActorSession(Object bean,
                              String key,
                              ContextSession context,
                              ServiceConfig config);
                              */
  
  <T> T run(long timeout,
            TimeUnit unit,
            Consumer<Result<T>> task);

  default void run(Runnable task)
  {
    OutboxAmp outboxCurrent = OutboxAmp.current();
    Object context = null;
    
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(this)) {
      context = outbox.getAndSetContext(inboxSystem());
      
      task.run();
    } finally {
      if (outboxCurrent != null) {
        outboxCurrent.getAndSetContext(context);
      }
    }
  }

  
  ServiceRefAmp getServiceRef(Object proxy);
  
  InboxFactoryAmp inboxFactory();

  QueueFullHandler getQueueFullHandler();
  
  Supplier<OutboxAmp> outboxFactory();

  OutboxAmp getOutboxSystem();
  
  JournalAmp openJournal(String name);

  MessageAmp systemMessage();
  // OutboxAmp getSystemOutbox();
  
  boolean isClosed();
  void close();
  void shutdown(ShutdownModeAmp mode);
  
  //
  // module
  //
  
  // ModuleAmp getModule();
  
  //RampSystem getSystem();
  
  <T> DisruptorBuilder<T> disruptor(Class<T> api);
  
  /**
   * Creates a module builder.
   */
  // ModuleRef.Builder module(String name, String version);
  
  void addAutoStart(ServiceRef serviceRef);
  
  void start();

  boolean isAutoStart();
  void setAutoStart(boolean isAutoStart);
  String getSelfServer();
  void setSelfServer(String hostName);
  //String getPeerServer();
  //void setPeerServer(String hostName);
  
  //
  // debug/stats
  //
  
  boolean isDebug();
  
  void addRemoteMessageWrite();
  
  long getRemoteMessageWriteCount();
  
  void addRemoteMessageRead();
  
  long getRemoteMessageReadCount();

  ContextSession createContextServiceSession(String path, Class<?> beanClass);
  
  ClassLoader classLoader();
  
  Trace trace();
  
  static ServiceManagerBuilderAmp newManager()
  {
    return Amp.newManagerBuilder();
  }
  
  interface Trace extends AutoCloseable {
    @Override
    void close();
  }
  
  interface DisruptorBuilder<T>
  {
    //DisruptorBuilder<T> peer(T serviceImpl);
    
    DisruptorBuilder<T> next(T serviceImpl);
    
    ServiceRef build();
    
    ServiceRef build(ServiceConfig config);
  }
}
