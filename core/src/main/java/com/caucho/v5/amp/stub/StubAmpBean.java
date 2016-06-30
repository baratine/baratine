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

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.proxy.ProxyHandleAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.spi.StubContainerAmp;

import io.baratine.service.OnLookup;
import io.baratine.service.OnSave;
import io.baratine.service.Result;
import io.baratine.service.ResultChain;
import io.baratine.service.ServiceRef;

/**
 * Stub instance for a bean calls bean methods based on the ClassStub.
 */
public class StubAmpBean extends StubAmpBase
{
  private final StubClass _stubClass;
  private final StubContainerAmp _container;
  
  private JournalAmp _journal;
  private Object _bean;
  private String _name;

  private boolean _isAutoCreate;

  public StubAmpBean(StubClass stubClass,
                     Object bean,
                     String name,
                     StubContainerAmp container,
                     ServiceConfig config)
  {
    Objects.requireNonNull(bean);
    Objects.requireNonNull(stubClass);
    
    if (bean instanceof ProxyHandleAmp) {
      throw new IllegalArgumentException(String.valueOf(bean));
    }
    
    _bean = bean;
    _stubClass = stubClass;
    
    if (name == null) {
      name = "anon:" + bean.getClass().getSimpleName();
    }
    
    _name = name;
    
    _isAutoCreate = _stubClass.isAutoCreate() || container == null;
    
    if (container != null) {
    }
    else if (config != null && config.isJournal()) {
      container = new StubContainerJournal(this, name, config);
    }
    else if (_stubClass.isImplemented(OnLookup.class)
             || _stubClass.isImplemented(OnSave.class)
             || _stubClass.isEnsure()) {
      container = new StubContainerBase(this, name);
    }
    /*
    else if (isContainer) {
      container = new StubContainerBase(this, name);
    }
    */
    
    _container = container;
    
    /*
    _isAutoCreate = (_stubClass.isAutoCreate()
        || ! isContainer && container == null);
        */
    if (! isLifecycleAware()) {
      state(StubStateAmpBean.ACTIVE);
    }
  }
  
  /*
  public StubAmpBean(StubClass stubClass,
                     Object bean,
                     ServiceConfig config)
  {
    this(stubClass, bean, config.name(), x->null);
  }
  */
  
  public StubContainerAmp container()
  {
    return _container;
  }
  
  protected final StubClass stubClass()
  {
    return _stubClass;
  }
  
  @Override
  public ServicesAmp services()
  {
    return stubClass().services();
  }

  /*
  @Override
  public String name()
  {
    return _name;
  }
  */
  
  @Override
  public boolean isPublic()
  {
    return _stubClass.isPublic();
  }
  
  @Override
  public boolean isAutoCreate()
  {
    return _isAutoCreate;
  }
  
  @Override
  public boolean isAutoStart()
  {
    return (_stubClass.isEnsure()
            || _container != null && _container.isAutoStart());
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
  public MethodAmp methodByName(String methodName)
  {
    MethodAmp method = _stubClass.methodByName(this, methodName);
    
    return method;
  }

  @Override
  public MethodAmp method(String methodName, Class<?> []param)
  {
    MethodAmp method = _stubClass.method(this, methodName, param);
    
    return method;
  }
  
  /*
  @Override
  public void beforeBatchImpl()
  {
    // _skel.preDeliver(getBean());
  }
  */

  /*
  @Override
  public void afterBatchImpl()
  {
    afterBatchChildren();
  }
  */
  
  public void afterBatchChildren()
  {
    StubContainerAmp childContainer = _container;
    
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
  
  /*
  @Override
  public void onInit(Result<? super Boolean> result)
  {
    _stubClass.onInit(this, result);
  }
  */
  
  @Override
  public void onActive(Result<? super Boolean> result)
  {
    _stubClass.onActive(this, result);
    
    if (_container != null) {
      _container.onActive();
      
      _stubClass.onActive(_container);
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
  /*
  @Override
  public boolean onSaveStartImpl(Result<Boolean> result)
  {
    SaveResult saveResult = new SaveResult(result);
    
    _stubClass.onSaveStart(this, saveResult.addBean());

    onSaveChildren(saveResult);
    
    saveResult.completeBean();
    
    return true;
  }
  */
  
  @Override
  public void onSave(Result<Void> result)
  {
    StubContainerAmp container = _container;
    
    if (container != null) {
      container.onSave(result);
    }
    else {
      onSaveChild(result);
    }
  }
  
  @Override
  public void onSaveChild(Result<Void> result)
  {
    _stubClass.onSave(this, result);
    
    state().onSaveComplete(this);
  }

  @Override
  public void onSaveChildren(SaveResult saveResult)
  {
    /*
    StubContainerAmp container = _container;
    
    if (container != null) {
      container.onSave(saveResult);
    }
    */
  }

  /*
  @Override
  public void onSaveEnd(boolean isComplete)
  {
    SaveResult saveResult = new SaveResult(result);
    
    _stubClass.checkpointStart(this, saveResult.addBean());

    onSaveChildren(saveResult);
    
    saveResult.completeBean();
  }
  */
  
  @Override
  public Object onLookup(String path, ServiceRefAmp parentRef)
  {
    StubContainerAmp container = childContainer();
    
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
      ServicesAmp manager = parentRef.services();
      
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
  
  private StubContainerAmp childContainer()
  {
    return _container;
  }
  
  /*
  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
    _stubClass.shutdown(this, mode);
  }
  */

  @Override
  public void onLoad(Result<? super Boolean> result)
  {
    //_skel.onLoad(actor, result);
    _stubClass.onLoad(this, result);
  }

  @Override
  protected void addModifiedChild(StubAmp actor)
  {
    StubContainerAmp container = _container;
    
    if (container != null) {
      container.addModifiedChild(actor);
    }
  }

  @Override
  protected boolean isModifiedChild(StubAmp actor)
  {
    StubContainerAmp container = _container;
    
    if (container != null) {
      return container.isModifiedChild(actor);
    }
    else {
      return false;
    }
  }

  @Override
  public ResultChain<?> ensure(MethodAmp methodAmp,
                                ResultChain<?> result, 
                                Object ...args)
  {
    return _container.ensure(this, methodAmp, result, args);
  }

  @Override
  public String name()
  {
    return _name;
  }
  
  @Override
  public final Object bean()
  {
    return _bean;
  }
  
  @Override
  public void onInit(Result<? super Boolean> result)
  {
    stubClass().onInit(this, result);
    
    beforeBatch();
  }
  
  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
    // afterBatch();
    
    stubClass().shutdown(this, mode);
  }

  @Override
  public void beforeBatchImpl()
  {
    stubClass().beforeBatch(this);
  }
  
  @Override
  public void afterBatchImpl()
  {
    stubClass().afterBatch(this);
    
    afterBatchChildren();
  }
  
  @Override
  public int hashCode()
  {
    return bean().hashCode();
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (! (o instanceof StubAmpBean)) {
      return false;
    }
    
    StubAmpBean stub = (StubAmpBean) o;
    
    return bean().equals(stub.bean());
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _bean + "]";
  }
}
