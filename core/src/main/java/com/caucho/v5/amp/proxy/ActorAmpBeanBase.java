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

package com.caucho.v5.amp.proxy;

import io.baratine.service.OnLookup;
import io.baratine.service.OnSave;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

import java.lang.annotation.Annotation;
import java.util.Objects;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.ActorAmpStateBase;
import com.caucho.v5.amp.actor.LoadStateActorAmp;
import com.caucho.v5.amp.actor.SaveResult;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.manager.ServiceConfig;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.ActorContainerAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.ProxyFactoryAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * Baratine actor skeleton
 */
public class ActorAmpBeanBase extends ActorAmpStateBase
  implements ActorAmp
{
  private final SkeletonClass _skel;
  private final String _name;
  
  private final ActorContainerAmp _container;
  
  private JournalAmp _journal;
  
  ActorAmpBeanBase(SkeletonClass skel,
                   String name,
                   ActorContainerAmp container)
  {
    Objects.requireNonNull(skel);
    
    _skel = skel;
    _name = name;
    
    if (container != null) {
    }
    else if (_skel.isImplemented(OnLookup.class)
             || _skel.isImplemented(OnSave.class)
             || _skel.isJournal()) {
      if (_skel.isJournal()) {
        container = new ActorContainerJournal(name, _skel.getJournalDelay());
      }
      else {
        container = new ActorContainerBase(name);
      }
    }
    
    _container = container;
    
    if (! _skel.isLifecycleAware()) {
      setLoadState(LoadStateActorAmp.ACTIVE);
    }
  }
  
  public ActorContainerAmp getContainer()
  {
    return _container;
  }
  
  protected final SkeletonClass getSkeleton()
  {
    return _skel;
  }
  
  @Override
  public String getName()
  {
    return _name;
  }
  
  @Override
  public boolean isExported()
  {
    return _skel.isExported();
  }
  
  public Class<?> getApiClass()
  {
    return _skel.getApiClass();
  }
  
  public Annotation []getApiAnnotations()
  {
    return _skel.getApiAnnotations();
  }

  @Override
  public MethodAmp []getMethods()
  {
    return _skel.getMethods();
  }

  @Override
  public MethodAmp getMethod(String methodName)
  {
    MethodAmp method = _skel.getMethod(this, methodName);
    
    return method;
  }
  
  @Override
  public void beforeBatchImpl()
  {
    // _skel.preDeliver(getBean());
  }
  
  @Override
  public void afterBatchImpl()
  {
    afterBatchChildren();

    /*
    JournalAmp journal = getJournal();
    
    if (journal != null && journal.checkpointStart()) {
      // checkpointStart(x->journal.checkpointEnd(x));
    }
    */
  }
  
  public void afterBatchChildren()
  {
    ActorContainerAmp childContainer = _container;
    
    if (childContainer != null) {
      childContainer.afterBatch(this);
    }
  }

  @Override
  public void flushModified()
  {
    afterBatchChildren();
  }
  
  @Override
  public boolean isLifecycleAware()
  {
    return _skel.isLifecycleAware();
  }
  
  @Override
  public void onInit(Result<? super Boolean> result)
  {
    _skel.onInit(this, result);
  }
  
  @Override
  public void onActive(Result<? super Boolean> result)
  {
    _skel.onActive(this, result);
    
    if (_container != null) {
      _container.onActive();
    }
  }
  
  @Override
  public JournalAmp getJournal()
  {
    return _journal;
  }
  
  @Override
  public void setJournal(JournalAmp journal)
  {
    _journal = journal;
    
    /*
    if (_container instanceof ActorContainerJournal) {
      ActorContainerJournal container = (ActorContainerJournal) _container;
      
      // container.setJournalDelay(journal.getDelay());
    }
    */
  }
  
  /*
  @Override
  public boolean checkpointStart(Result<Boolean> cont)
  {
    return checkpointStartImpl(cont);
  }
  */
  
  @Override
  public boolean onSaveStartImpl(Result<Boolean> result)
  {
    SaveResult saveResult = new SaveResult(result);
    
    _skel.checkpointStart(this, saveResult.addBean());

    onSaveChildren(saveResult);
    
    saveResult.completeBean();
    
    return true;
  }

  @Override
  public void onSaveChildren(SaveResult saveResult)
  {
    ActorContainerAmp container = _container;
    
    if (container != null) {
      container.onSave(saveResult);
    }
  }
  
  @Override
  public Object onLookup(String path, ServiceRefAmp parentRef)
  {
    ActorContainerAmp container = getChildContainer();
    
    if (container == null) {
      return null;
    }
    
    ServiceRef serviceRef = container.getService(path);
    
    if (serviceRef != null) {
      return serviceRef;
    }
    
    Object value = _skel.onLookup(this, path);
    
    if (value == null) {
      return null;
    }
    else if (value instanceof ServiceRef) {
      return value;
    }
    else if (value instanceof ProxyHandleAmp) {
      ProxyHandleAmp handle = (ProxyHandleAmp) value;
      
      return handle.__caucho_getServiceRef();
    }
    else {
      ServiceManagerAmp manager = parentRef.manager();
      
      String address = parentRef.address() + path;
      
      ServiceConfig config = null;
      
      ProxyFactoryAmp proxyFactory = manager.getProxyFactory();
      
      ActorAmp actor;
      
      if (value instanceof ActorAmp) {
        actor = (ActorAmp) value;
      }
      else {
        actor = proxyFactory.createSkeleton(value, address, path, container, config);
      }
      
      serviceRef = parentRef.pin(actor, address);
      
      return container.addService(path, serviceRef);
    }
  }
  
  private ActorContainerAmp getChildContainer()
  {
    return _container;
  }
  
  @Override
  public void subscribe(ServiceRef serviceRef)
  {
  }
  
  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
    _skel.shutdown(this, mode);
  }

  @Override
  public void onLoad(Result<? super Boolean> result)
  {
    //_skel.onLoad(actor, result);
    _skel.onLoad(this, result);
  }

  @Override
  protected void addModifiedChild(ActorAmp actor)
  {
    ActorContainerAmp container = _container;
    
    if (container != null) {
      container.addModifiedChild(actor);
    }
  }

  @Override
  protected boolean isModifiedChild(ActorAmp actor)
  {
    ActorContainerAmp container = _container;
    
    if (container != null) {
      return container.isModifiedChild(actor);
    }
    else {
      return false;
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _skel + "]";
  }
}
