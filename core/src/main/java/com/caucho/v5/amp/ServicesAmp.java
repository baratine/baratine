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
import com.caucho.v5.amp.manager.ServicesBuilderImpl;
import com.caucho.v5.amp.proxy.ProxyFactoryAmp;
import com.caucho.v5.amp.service.ServiceBuilderAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LookupAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.RegistryAmp;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.StubClassFactoryAmp;

import io.baratine.convert.Convert;
import io.baratine.inject.Injector;
import io.baratine.inject.Key;
import io.baratine.service.QueueFullHandler;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;
import io.baratine.service.Services;
import io.baratine.spi.MessageApi;

/**
 * Manages an AMP domain.
 */
public interface ServicesAmp extends Services, LookupAmp
{
  static ServicesAmp current()
  {
    return (ServicesAmp) Services.current();
  }
  
  @Override
  ServiceRefAmp service(String address);
  
  /**
   * The current service, or if called from outside of a service, the
   * system service.
   */
  ServiceRef currentService();
  
  MessageApi currentMessage();
 
  <T> T newProxy(ServiceRefAmp actorRef, 
                    Class<T> api);

  String address(Class<?> type);
  
  String address(Class<?> type, Class<?> api);
  
  String address(Class<?> type, String address);

  ServiceRefAmp toRef(Object value);

  @Override
  ServiceBuilderAmp newService(Object value);

  @Override
  <T> ServiceBuilderAmp newService(Class<T> type, 
                                   Supplier<? extends T> supplier);

  @Override
  <T> ServiceBuilderAmp newService(Class<T> type);

  ServiceBuilderAmp service(Key<?> key, Class<?> apiClass);
  
  ServiceRefAmp pin(ServiceRefAmp parent,
                    Object listener);
  
  ServiceRefAmp pin(ServiceRefAmp context,
                    Object listener,
                    String path);
  
  ServiceRefAmp bind(ServiceRefAmp service, String address);
  
  /**
   * Returns the domain's broker.
   */
  RegistryAmp registry();
  
  String getName();
  
  String getDebugId();
  
  Injector injector();
  
  InboxAmp inboxSystem();

  ProxyFactoryAmp proxyFactory();
  
  StubClassFactoryAmp stubFactory();

  <T> ClassValue<Convert<?, T>> shims(Class<T> resultType);
  
  // StubAmp createStub(Object bean, ServiceConfig config);
  
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

  
  //ServiceRefAmp toRef(Object proxy);
  
  QueueFullHandler getQueueFullHandler();
  
  Supplier<OutboxAmp> outboxFactory();

  OutboxAmp getOutboxSystem();
  
  JournalAmp openJournal(String name);

  //MessageAmp systemMessage();
  // OutboxAmp getSystemOutbox();
  
  boolean isClosed();
  void close();
  void shutdown(ShutdownModeAmp mode);
  
  void addAutoStart(ServiceRef serviceRef);
  
  void start();

  boolean isAutoStart();
  void setAutoStart(boolean isAutoStart);

  /*
  String getSelfServer();
  void setSelfServer(String hostName);
  */
  
  //
  // debug/stats
  //
  
  boolean isDebug();
  
  void addRemoteMessageWrite();
  
  long getRemoteMessageWriteCount();
  
  void addRemoteMessageRead();
  
  long getRemoteMessageReadCount();

  //ContextSession createContextServiceSession(String path, Class<?> beanClass);
  
  ClassLoader classLoader();
  
  Trace trace();
  
  static ServiceManagerBuilderAmp newManager()
  {
    return new ServicesBuilderImpl();
  }
  
  interface Trace extends AutoCloseable {
    @Override
    void close();
  }
}
