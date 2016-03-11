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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.ServiceRefDynamic;
import com.caucho.v5.amp.actor.ServiceRefLazyProxy;
import com.caucho.v5.amp.inbox.InboxWrapper;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.outbox.Outbox;
import com.caucho.v5.amp.proxy.ProxyFactoryAmpImpl;
import com.caucho.v5.amp.session.ContextSession;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.InboxFactoryAmp;
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
import io.baratine.service.ServiceNode;
import io.baratine.service.ServiceRef;
import io.baratine.spi.Message;

/**
 * ServiceManager context during the build process.
 */
public class ServiceManagerBuildTemp implements ServiceManagerAmp
{
  private ServiceManagerBuilderAmp _builder;
  private ProxyFactoryAmpImpl _proxyFactory;
  private ServiceManagerAmp _delegate;
  
  private InboxAmp _inboxTemp;
  
  public ServiceManagerBuildTemp(ServiceManagerBuilderAmp builder)
  {
    Objects.requireNonNull(builder);
    
    _builder = builder;
  }
  
  private ServiceManagerBuilderAmp builder()
  {
    return _builder;
  }

  public void delegate(ServiceManagerAmp manager)
  {
    _delegate = manager;
  }
  
  public ServiceManagerAmp delegate()
  {
    return _delegate;
  }

  @Override
  public ServiceNode node()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServiceRefAmp service(String address)
  {
    ServiceManagerAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.service(address);
    }
    else {
      return new ServiceRefLazyProxy(this, address);
    }
  }

  @Override
  public String address(Class<?> type)
  {
    return delegate().address(type);
  }

  @Override
  public <T> T service(Class<T> api)
  {
    ServiceManagerAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.service(api);
    }
    else {
      //return new ServiceRefLazyProxy(this, address);
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public <T> T service(Class<T> api, String id)
  {
    ServiceManagerAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.service(api, id);
    }
    else {
      //return new ServiceRefLazyProxy(this, address);
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public InjectManager inject()
  {
    ServiceManagerAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.inject();
    }
    else {
      //return new ServiceRefLazyProxy(this, address);
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public ServiceRef currentService()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Message currentMessage()
  {
    // TODO Auto-generated method stub
    return null;
  }
  
  private ProxyFactoryAmp proxyFactory()
  {
    if (_proxyFactory == null) {
      _proxyFactory = new ProxyFactoryAmpImpl(this);
    }
    
    return _proxyFactory;
  }
  
  @Override
  public <T> T newProxy(ServiceRefAmp proxyRef, 
                           Class<T> api,
                           Class<?>... apis)
  {
    return proxyFactory().createProxy(proxyRef, api);
  }

  @Override
  public ServiceBuilderAmp newService()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServiceRefAmp toService(Object value)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServiceBuilderAmp newService(Object value)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServiceBuilderAmp newService(Supplier<?> supplier)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServiceBuilderAmp newService(Class<?> type)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServiceBuilderAmp service(Key<?> key, Class<?> api)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServiceRefAmp pin(ServiceRefAmp parent, Object listener)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServiceRefAmp pin(ServiceRefAmp context, Object listener, String path)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServiceRefAmp bind(ServiceRefAmp service, String address)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public RegistryAmp registry()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getName()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getDebugId()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public InboxAmp inboxSystem()
  {
    ServiceManagerAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.inboxSystem();
    }
    else {
      if (_inboxTemp == null) {
        _inboxTemp = new InboxTemp();
      }
      
      return _inboxTemp;
    }
  }

  @Override
  public ProxyFactoryAmp getProxyFactory()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ActorAmp createActor(Object bean, ServiceConfig config)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public <T> T run(long timeout, TimeUnit unit, Consumer<Result<T>> task)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServiceRefAmp toServiceRef(Object proxy)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public InboxFactoryAmp inboxFactory()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public QueueFullHandler getQueueFullHandler()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Supplier<OutboxAmp> outboxFactory()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public OutboxAmp getOutboxSystem()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public JournalAmp openJournal(String name)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public MessageAmp systemMessage()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean isClosed()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void close()
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public <T> DisruptorBuilder<T> disruptor(Class<T> api)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void addAutoStart(ServiceRef serviceRef)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void start()
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public boolean isAutoStart()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void setAutoStart(boolean isAutoStart)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public String getSelfServer()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setSelfServer(String hostName)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public boolean isDebug()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void addRemoteMessageWrite()
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public long getRemoteMessageWriteCount()
  {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public void addRemoteMessageRead()
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public long getRemoteMessageReadCount()
  {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public ContextSession createContextServiceSession(String path,
                                                    Class<?> beanClass)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ClassLoader classLoader()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Trace trace()
  {
    // TODO Auto-generated method stub
    return null;
  }
  
  private class InboxTemp extends InboxWrapper {
    @Override
    public InboxAmp delegate()
    {
      ServiceManagerAmp delegate = ServiceManagerBuildTemp.this.delegate();
      
      if (delegate != null) {
        return delegate.inboxSystem();
      }
      else {
        return null;
      }
    }
    
    @Override
    public ServiceManagerAmp manager()
    {
      ServiceManagerAmp delegate = ServiceManagerBuildTemp.this.delegate();
      
      if (delegate != null) {
        return delegate;
      }
      else {
        return ServiceManagerBuildTemp.this;
      }
    }
  }
}
