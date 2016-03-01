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
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.journal.JournalFactoryAmp;
import com.caucho.v5.amp.spi.LookupManagerBuilderAmp;
import com.caucho.v5.amp.spi.ProxyFactoryAmp;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.util.L10N;

import io.baratine.service.QueueFullHandler;
import io.baratine.service.ServiceInitializer;
import io.baratine.service.ServiceNode;

/**
 * Factory for creating an AMP manager.
 */
abstract public class ServiceManagerBuilderBase implements ServiceManagerBuilderAmp
{
  private static final L10N L = new L10N(ServiceManagerBuilderBase.class);
  private static final Logger log
    = Logger.getLogger(ServiceManagerBuilderBase.class.getName());
  
  private String _name;
  private String _debugId;
  
  private LookupManagerBuilderAmp _brokerFactory;
  private ProxyFactoryAmp _proxyFactory;
  private JournalFactoryAmp _journalFactory;
  private QueueFullHandler _queueFullHandler;
  private boolean _isContextManager = true;
  private ServiceNode _podNode;
  private ClassLoader _loader = Thread.currentThread().getContextClassLoader();
  
  private boolean _isAutoStart = true;
  
  private boolean _isBare;
  
  private boolean _isDebug;
  private long _debugQueryTimeout;

  // private ModuleAmp _module;
  
  @Override
  public ServiceManagerBuilderBase name(String name)
  {
    _name = name;
    
    return this;
  }
  
  @Override
  public String getName()
  {
    return _name;
  }
  
  @Override 
  public ServiceManagerBuilderBase classLoader(ClassLoader loader)
  {
    _loader = loader;
    
    return this;
  }
  
  @Override 
  public ClassLoader getClassLoader()
  {
    return _loader;
  }
  
  public void setDebugId(String debugId)
  {
    _debugId = debugId;
  }
  
  @Override
  public String getDebugId()
  {
    if (_debugId != null) {
      return _debugId;
    }
    else {
      return _name;
    }
  }

  @Override
  public LookupManagerBuilderAmp getBrokerFactory()
  {
    return _brokerFactory;
  }

  @Override
  public ServiceManagerBuilderAmp setBrokerFactory(LookupManagerBuilderAmp factory)
  {
    _brokerFactory = factory;

    return this;
  }

  @Override
  public ProxyFactoryAmp proxyFactory()
  {
    return _proxyFactory;
  }

  @Override
  public ServiceManagerBuilderAmp proxyFactory(ProxyFactoryAmp factory)
  {
    _proxyFactory = factory;

    return this;
  }
  
  @Override
  public ServiceNode getPodNode()
  {
    return _podNode;
  }
  
  @Override
  public ServiceManagerBuilderBase setPodNode(ServiceNode podNode)
  {
    _podNode = podNode;
    
    return this;
  }

  @Override
  public JournalFactoryAmp getJournalFactory()
  {
    return _journalFactory;
  }

  @Override
  public ServiceManagerBuilderAmp setJournalFactory(JournalFactoryAmp factory)
  {
    Objects.requireNonNull(factory);

    _journalFactory = factory;

    return this;
  }

  @Override
  public ServiceManagerBuilderAmp setJournalMaxCount(int maxCount)
  {
    _journalFactory.setMaxCount(maxCount);

    return this;
  }

  @Override
  public ServiceManagerBuilderAmp setJournalDelay(long timeout)
  {
    _journalFactory.setDelay(timeout);

    return this;
  }

  @Override
  public long getJournalDelay()
  {
    return _journalFactory.getDelay();
  }
  
  @Override
  public ServiceManagerBuilderAmp setQueueFullHandler(QueueFullHandler handler)
  {
    _queueFullHandler = handler;
    
    return this;
  }
  
  @Override
  public QueueFullHandler getQueueFullHandler()
  {
    return _queueFullHandler;
  }
  
  @Override
  public ServiceManagerBuilderAmp contextManager(boolean isContextManager)
  {
    _isContextManager = isContextManager;
    
    return this;
  }
  
  @Override
  public boolean isContextManager()
  {
    return _isContextManager;
  }
  
  @Override
  public ServiceManagerBuilderBase autoStart(boolean isAutoStart)
  {
    _isAutoStart = isAutoStart;
    
    return this;
  }
  
  @Override
  public boolean isAutoStart()
  {
    return _isAutoStart;
  }
  
  @Override
  public ServiceManagerBuilderBase bare(boolean isBare)
  {
    _isBare = isBare;
    
    return this;
  }
  
  @Override
  public boolean isBare()
  {
    return _isBare;
  }
  
  @Override
  public boolean isDebug()
  {
    return _isDebug;
  }
  
  @Override
  public ServiceManagerBuilderBase debug(boolean isDebug)
  {
    _isDebug = isDebug;
    
    return this;
  }
  
  @Override
  public long getDebugQueryTimeout()
  {
    return _debugQueryTimeout;
  }
  
  @Override
  public ServiceManagerBuilderBase debugQueryTimeout(long timeout)
  {
    _debugQueryTimeout = timeout;
    
    return this;
  }

  @Override
  abstract public ServiceManagerAmp start();
  
  protected void bind(ServiceManagerAmp manager)
  {
    if (isBare()) {
      return;
    }
    
    ArrayList<ServiceInitializer> providerList = new ArrayList<>();

    Iterator<ServiceInitializer> iter;

    iter = ServiceLoader.load(ServiceInitializer.class).iterator();

    while (iter.hasNext()) {
      try {
        providerList.add(iter.next());
      } catch (Throwable e) {
        if (log.isLoggable(Level.FINER)) {
          log.log(Level.FINER, e.toString(), e);
        }
        else {
          log.fine(L.l("{0} while processing {1}",
                       e.toString(), ServiceInitializer.class.getName()));
        }
      }
    }

    Collections.sort(providerList, (a,b)->
        a.getClass().getSimpleName().compareTo(b.getClass().getSimpleName()));
    
    for (ServiceInitializer provider : providerList) {
      try {
        provider.init(manager);
      } catch (Throwable e) {
        if (log.isLoggable(Level.FINER)) {
          log.log(Level.FINER, e.toString(), e);
        }
        else {
          log.fine(L.l("'{0}' while processing {1}", e.toString(), provider));
        }
      }
    }
  }
}
