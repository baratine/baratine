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

package com.caucho.v5.amp.stub;

import java.lang.reflect.AnnotatedType;
import java.util.Objects;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.proxy.ProxyHandleAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.spi.ActorContainerAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

import io.baratine.service.OnLookup;
import io.baratine.service.OnSave;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

/**
 * Baratine actor skeleton
 */
public class StubAmpBeanBase extends StubAmpStateBase
  implements StubAmp
{
  private final StubClass _stubClass;
  private final String _name;
  
  private final ActorContainerAmp _container;
  
  private JournalAmp _journal;
  
  StubAmpBeanBase(StubClass skel,
                   String name,
                   ActorContainerAmp container)
  {
    Objects.requireNonNull(skel);
    
    _stubClass = skel;
    _name = name;
    
    boolean isJournal = false; // config.isJournal();
    long journalDelay = 0; // config.getJournalDelay();
    
    if (container != null) {
    }
    else if (_stubClass.isImplemented(OnLookup.class)
             || _stubClass.isImplemented(OnSave.class)
             || isJournal) {
      if (isJournal) {
        container = new StubContainerJournal(name, journalDelay);
      }
      else {
        container = new StubContainerBase(name);
      }
    }
    
    _container = container;
    
    if (! _stubClass.isLifecycleAware()) {
      setLoadState(LoadStateActorAmp.ACTIVE);
    }
  }
  
  public ActorContainerAmp getContainer()
  {
    return _container;
  }
  
  protected final StubClass stubClass()
  {
    return _stubClass;
  }
  
  @Override
  public String name()
  {
    return _name;
  }
  
  @Override
  public boolean isPublic()
  {
    return _stubClass.isPublic();
  }
  
  @Override
  public AnnotatedType api()
  {
    return _stubClass.api();
  }

  @Override
  public MethodAmp []getMethods()
  {
    return _stubClass.getMethods();
  }

  @Override
  public MethodAmp getMethod(String methodName)
  {
    MethodAmp method = _stubClass.getMethod(this, methodName);
    
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
    return _stubClass.isLifecycleAware();
  }
  
  @Override
  public void onInit(Result<? super Boolean> result)
  {
    _stubClass.onInit(this, result);
  }
  
  @Override
  public void onActive(Result<? super Boolean> result)
  {
    _stubClass.onActive(this, result);
    
    if (_container != null) {
      _container.onActive();
    }
  }
  
  @Override
  public JournalAmp journal()
  {
    return _journal;
  }
  
  @Override
  public void journal(JournalAmp journal)
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
    
    _stubClass.checkpointStart(this, saveResult.addBean());

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
    
    Object value = _stubClass.onLookup(this, path);
    
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
      ServicesAmp manager = parentRef.manager();
      
      String address = parentRef.address() + path;
      
      ServiceConfig config = null;
      
      StubClassFactoryAmp stubFactory = manager.stubFactory();
      
      StubAmp stub;
      
      if (value instanceof StubAmp) {
        stub = (StubAmp) value;
      }
      else {
        stub = stubFactory.stub(value, address, path, container, config);
      }
      
      serviceRef = parentRef.pin(stub, address);
      
      return container.addService(path, serviceRef);
    }
  }
  
  private ActorContainerAmp getChildContainer()
  {
    return _container;
  }
  
  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
    _stubClass.shutdown(this, mode);
  }

  @Override
  public void onLoad(Result<? super Boolean> result)
  {
    //_skel.onLoad(actor, result);
    _stubClass.onLoad(this, result);
  }

  @Override
  protected void addModifiedChild(StubAmp actor)
  {
    ActorContainerAmp container = _container;
    
    if (container != null) {
      container.addModifiedChild(actor);
    }
  }

  @Override
  protected boolean isModifiedChild(StubAmp actor)
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
    return getClass().getSimpleName() + "[" + _stubClass + "]";
  }
}
