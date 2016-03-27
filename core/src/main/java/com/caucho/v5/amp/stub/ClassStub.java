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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpException;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.inject.type.AnnotatedTypeClass;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.L10N;

import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.AfterBatch;
import io.baratine.service.BeforeBatch;
import io.baratine.service.Journal;
import io.baratine.service.MethodRef;
import io.baratine.service.Modify;
import io.baratine.service.OnActive;
import io.baratine.service.OnDestroy;
import io.baratine.service.OnInit;
import io.baratine.service.OnLoad;
import io.baratine.service.OnLookup;
import io.baratine.service.OnSave;
import io.baratine.service.Pin;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.service.Service;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceRef;
import io.baratine.service.Shim;
import io.baratine.stream.ResultStream;
import io.baratine.stream.ResultStreamBuilder;

/**
 * Makai actor skeleton
 */
public class ClassStub
{
  private static final L10N L = new L10N(ClassStub.class);
  private static final Logger log
    = Logger.getLogger(ClassStub.class.getName());
  
  private static long _defaultTimeout = 10;
  
  private HashMap<String,Method> _methodMap = new HashMap<>();
  
  private HashMap<String,MethodAmp> _rampMethodMap = new HashMap<>();
  
  private final ServiceManagerAmp _ampManager;

  private final Class<?> _api;
  private final AnnotatedType _apiType;
  
  private boolean _isExported;
  // private final Journal _ampJournal;
  private final boolean _isJournal;
  private final long _journalDelay;
  
  private MethodAmp _onInit;
  private MethodAmp _onActive;
  private MethodAmp _onShutdown;

  private MethodAmp _onLoad;
  private MethodAmp _onSave;
  // private MethodAmp _onRestore;
  
  private MethodAmp _onLookup;
  
  private Method_0_Base _beforeBatch = Method_0_Base.NULL;
  private Method_0_Base _afterBatch= Method_0_Base.NULL;
  
  private MethodAmp _consume;
  private Class<?> _consumeApi = ServiceRef.class;
  
  private MethodAmp _subscribe;
  private Class<?> _subscribeApi = ServiceRef.class;
  
  private MethodAmp _unsubscribe;
  private Class<?> _unsubscribeApi = ServiceRef.class;
  
  private MethodHandle _getMethod;
  // private JournalAmp _journal;
  
  private boolean _isLifecycleAware;
  
  private long _timeout;
  
  public ClassStub(ServiceManagerAmp rampManager,
                      Class<?> api,
                       ServiceConfig config)
  {
    if (api.isArray()) {
      throw new IllegalArgumentException(api.getName());
    }
    
    if (ServiceRef.class.isAssignableFrom(api)) {
      throw new IllegalStateException(String.valueOf(api));
    }
    
    if (StubAmp.class.isAssignableFrom(api)) {
      throw new IllegalStateException(String.valueOf(api));
    }
    
    _ampManager = rampManager;
    _api = api;
    _apiType = new AnnotatedTypeClass(api);
    
    _timeout = _defaultTimeout;
    //log.fine(L.l("timout for {0} is {1}", api.getSimpleName(), _timeout));
    
    //Service service = api.getAnnotation(Service.class);
    
    /*
    Remote export = api.getAnnotation(Remote.class);
    
    if (export != null) {
      _isExported = export != null;
    }
    */
    
    Journal ampJournal = api.getAnnotation(Journal.class);
    
    boolean isJournal = false;
    long journalDelay = -1;
    
    if (ampJournal != null) {
      _isLifecycleAware = true;
      isJournal = true;
      journalDelay = ampJournal.delay();
    }
    
    if (config != null) {
      if (config.isJournal()) {
        isJournal = config.isJournal();
      }
      
      if (config.journalDelay() >= 0) {
        journalDelay = config.journalDelay();
      }
    }
    
    _isJournal = isJournal;
    _journalDelay = journalDelay;
  }
  
  public void introspect()
  {
    try {
      addMethods(_api);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
  
  public boolean isPublic()
  {
    return _isExported;
  }
  
  protected boolean isLocalPodNode()
  {
    return true;
  }
  
  public AnnotatedType api()
  {
    return _apiType;
  }
  
  protected ServiceManagerAmp ampManager()
  {
    return _ampManager;
  }

  public boolean isImplemented(Class<?> type)
  {
    if (OnLookup.class.equals(type)) {
      return _onLookup != null;
    }
    else if (OnLoad.class.equals(type)) {
      return _onLoad != null;
    }
    else if (OnSave.class.equals(type)) {
      return _onSave != null;
    }
    else {
      return false;
    }
  }
  
  protected void onLookup(MethodAmp onLookup)
  {
    Objects.requireNonNull(onLookup);
    
    _onLookup = onLookup;
  }
  
  protected void onLoad(MethodAmp onLoad)
  {
    Objects.requireNonNull(onLoad);
    
    _onLoad = onLoad;
    _isLifecycleAware = true;
  }
  
  protected void onSave(MethodAmp onSave)
  {
    Objects.requireNonNull(onSave);
    
    _onSave = onSave;
    _isLifecycleAware = true;
  }
  
  private void addMethods(Class<?> cl) throws IllegalAccessException
  {
    if (cl == null || cl.equals(Object.class)) {
      return;
    }
    
    addMethods(cl.getSuperclass());
    
    for (Method method : cl.getDeclaredMethods()) {
      if (Modifier.isStatic(method.getModifiers())) {
        continue;
      }
      
      if (method.isAnnotationPresent(OnActive.class)) {
        if (! isLocalPodNode()) {
          continue;
        }
        
        _onActive = createMethod(method);
        _isLifecycleAware = true;

        continue;
      }
      
      if (method.isAnnotationPresent(OnInit.class)) {
        _onInit = createMethod(method);
        _isLifecycleAware = true;

        continue;
      }
      
      if (method.isAnnotationPresent(OnDestroy.class)) {
        _onShutdown = createMethod(method);
        _isLifecycleAware = true;
        
        continue;
      }
      
      if (method.isAnnotationPresent(OnSave.class)) {
        if (! isLocalPodNode()) {
          continue;
        }
        
        _onSave = createMethod(method);
        _isLifecycleAware = true;
      }
      else if (method.isAnnotationPresent(OnLoad.class)) {
        if (! isLocalPodNode()) {
          continue;
        }
        
        _onLoad = createMethod(method);
        _isLifecycleAware = true;
      }
      else if (method.isAnnotationPresent(OnLookup.class)) {
        _onLookup = createMethod(method);
        continue;
      }
      else if (method.isAnnotationPresent(AfterBatch.class)) {
        _afterBatch = createMethodZero(method);
      }
      else if (method.isAnnotationPresent(BeforeBatch.class)) {
        _beforeBatch = createMethodZero(method);
      }
      
      if (! Modifier.isPublic(method.getModifiers())) {
        continue;
      }
      
      if (ResultStreamBuilder.class.isAssignableFrom(method.getReturnType())) {
        //continue;
      }
      
      if (MethodRef.class.equals(method.getReturnType())
          && method.isAnnotationPresent(Service.class)
          && method.getParameterTypes().length == 1
          && String.class.equals(method.getParameterTypes()[0])) {
        method.setAccessible(true);
        
        MethodHandle getMethod = MethodHandles.lookup().unreflect(method);
        
        MethodType mt = MethodType.methodType(MethodRef.class,
                                              Object.class,
                                              String.class);
        
        getMethod = getMethod.asType(mt);
        
        _getMethod = getMethod;
        
        continue;
      }
      
      String methodName = method.getName();
      
      if (_methodMap.get(methodName) == null) {
        _methodMap.put(methodName, method);

        MethodAmp rampMethod;
        
        rampMethod = createPlainMethod(method);
        
        _rampMethodMap.put(methodName, rampMethod);
      }
    }
  }
  
  public MethodAmp []getMethods()
  {
    MethodAmp []methods = new MethodAmp[_rampMethodMap.size()];
    
    _rampMethodMap.values().toArray(methods);
    
    return methods;
  }

  public MethodAmp getMethod(StubAmp actor, String methodName)
  {
    MethodAmp rampMethod = _rampMethodMap.get(methodName);
    
    if (rampMethod != null) {
      return rampMethod;
    }
    
    if (_getMethod != null) {
      return getActorMethod(actor, methodName);
    }

    /*
    throw new ServiceExceptionMethodNotFound(L.l("{0} is an unknown method in {1}",
                                               methodName, _api.getName()));
                                               */
    return new MethodAmpNull(actor, methodName);
  }
  
  protected MethodAmp createPlainMethod(Method method)
  {
    /*
    if (! isLocalPodNode()) {
      return new MethodAmpInvalidPod(method.getDeclaringClass().getName(),
                                     method.getName());
    }
    */
    
    return createMethod(method);
  }
    
  protected MethodAmp createMethod(Method method)
  {
    MethodAmp methodAmp = createMethodBase(method);
    
    if (method.isAnnotationPresent(Modify.class)) {
      methodAmp = new FilterMethodModify(methodAmp);
    }
    
    return methodAmp;
  }
  
  protected MethodAmp createMethodBase(Method method)
  {
    try {
      Parameter []params = method.getParameters();
      
      Parameter result;
      
      if ((result = result(params, Result.class)) != null) {
        MethodAmp methodStub;
        
        if (method.isVarArgs()) {
          methodStub = new MethodStubResult_VarArgs(ampManager(), method);
        }
        else {
          methodStub = new MethodStubResult_N(ampManager(), method);
        }
        
        if (result.isAnnotationPresent(Shim.class)) {
          return createCopyShim(methodStub, result);
        }
        else if (result.isAnnotationPresent(Pin.class)) {
          return createPin(methodStub, result);
        }
        else {
          return methodStub;
        }
      }
      
      if (isResult(params, ResultStream.class)) {
        if (false && method.isVarArgs()) {
          return new MethodStubResult_VarArgs(ampManager(), method);
        }
        else {
          return new MethodStubResultStream_N(ampManager(), method);
        }
      }
      
      if (isResult(params, ResultPipeOut.class)) {
        return new MethodStubResultOutPipe_N(ampManager(), method);
      }
      
      if (isResult(params, ResultPipeIn.class)) {
        return new MethodStubResultInPipe_N(ampManager(), method);
      }
      
      /*
      else if (isAmpResult(paramTypes)) {
        return new SkeletonMethodAmpResult_N(method);
      }
      */
      
      if (method.isVarArgs()) {
        return new MethodStub_VarArgs(_ampManager, method);
      }
    
      switch (params.length) {
      case 0:
        return new MethodStub_0(_ampManager, method);
        
      case 1:
        return new MethodStub_1(_ampManager, method);
      
      default:
        return new MethodStub_N(_ampManager, method);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new AmpException(e);
    }
  }
  
  private MethodAmp createCopyShim(MethodAmp delegate,
                                   Parameter result)
  {
    TypeRef resultRef = TypeRef.of(result.getParameterizedType());
    TypeRef transferRef = resultRef.to(Result.class).param(0);
    
    TransferAsset<?,?> shim = new TransferAsset(_api, transferRef.rawClass());
    
    return new MethodStubResultCopy(delegate, shim);
  }
  
  private MethodAmp createPin(MethodAmp delegate,
                              Parameter result)
  {
    Class<?> api = TypeRef.of(result.getParameterizedType())
                          .to(Result.class)
                          .param(0)
                          .rawClass();
    
    return new MethodStubResultPin(delegate, api);
  }
  
  private MethodAmp getActorMethod(StubAmp actor,
                                         String methodName)
  {
    try {
      Object bean = ((StubAmpBean) actor).bean();
      
      MethodRef methodRef = (MethodRef) _getMethod.invokeExact(bean, methodName);
      MethodRefAmp ampMethod = (MethodRefAmp) methodRef;
      
      if (ampMethod == null) {
        return new MethodAmpNull(actor, methodName);
      }
      
      return new MethodStubCustom(actor, ampMethod);
    } catch (Throwable e) {
      e.printStackTrace();
      throw new AmpException(e);
    }
  }
  
  protected Method_0_Base createMethodZero(Method method)
  {
    if (method == null) {
      return Method_0_Base.NULL;
    }
    
    if (! void.class.equals(method.getReturnType())) {
      throw new ServiceException(L.l("method {0}.{1} must return void", 
                                     method.getDeclaringClass().getName(),
                                     method.getName()));
    }
    
    if (method.getParameterCount() != 0) { 
      throw new ServiceException(L.l("method {0}.{1} must have zero arguments", 
                                     method.getDeclaringClass().getName(),
                                     method.getName()));
    }
    
    method.setAccessible(true);
    
    try {
      MethodHandle mh = MethodHandles.lookup().unreflect(method);
      
      mh = mh.asType(MethodType.methodType(void.class, Object.class));
    
      return new Method_0(mh);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }
  
  private boolean isResult(Parameter []params, Class<?> resultClass)
  {
    return result(params, resultClass) != null;
  }
  
  private Parameter result(Parameter []params, Class<?> resultClass)
  {
    int paramLen = params.length;
    
    for (int i = 0; i < paramLen; i++) {
      if (resultClass.equals(params[i].getType())) {
        return params[i];
      }
    }
    
    return null;
  }
  
  public void beforeBatch(StubAmp actor)
  {
    _beforeBatch.invoke(actor.bean());
  }
  
  public void afterBatch(StubAmp actor)
  {
    _afterBatch.invoke(actor.bean());
  }
  
  public boolean isLifecycleAware()
  {
    return _isLifecycleAware;
  }
  
  public void onActive(StubAmp actor, Result<? super Boolean> result)
  {
    try {
      MethodAmp onActive = _onActive;
      
      if (onActive == null) {
        result.ok(true);
        return;
      }
      
      // ResultFuture<ActorAmp> future = new ResultFuture<>();
      
      // QueryRefAmp queryRef = new QueryRefChainAmpCompletion(result);

      onActive.query(HeadersNull.NULL, result, actor);
      
      // future.get(_timeout, TimeUnit.SECONDS);
    } catch (Throwable e) {
      e.printStackTrace();
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  public void onInit(StubAmp actor, Result<? super Boolean> result)
  {
    try {
      MethodAmp onInit = _onInit;
      
      if (onInit == null) {
        if (result != null) {
          result.ok(true);
        }
        return;
      }
      
      if (result != null) {
        // QueryRefAmp queryRef = new QueryRefChainAmpCompletion(result);

        onInit.query(HeadersNull.NULL, result, actor);
      }
      else {
        onInit.send(HeadersNull.NULL, actor);
      }
    } catch (Throwable e) {
      e.printStackTrace();
      log.log(Level.FINE, e.toString(), e);
      result.fail(e);
    }
  }

  public boolean isJournal()
  {
    return _isJournal;
  }

  public long getJournalDelay()
  {
    return _journalDelay;
  }
  
  /*
  public JournalAmp getJournal()
  {
    return _journal;
  }
  */
  
  public void checkpointStart(StubAmp bean, Result<Boolean> result)
  {
    try {
      MethodAmp onSave = _onSave;
      
      if (onSave != null) {
        //QueryRefAmp queryRef = new QueryRefChainAmpCompletion(result);
        
        onSave.query(HeadersNull.NULL, result, bean);
      }
      else {
        result.ok(true);
      }
    } catch (Throwable e) {
      e.printStackTrace();
      result.fail(e);
    }
  }
  
  public Object onLookup(StubAmp bean, String path)
  {
    MethodAmp lookup = _onLookup;
      
    if (lookup == null) {
      return null;
    }
    
    //Outbox<Object> outbox = OutboxThreadLocal.getCurrent();
    
    try {
      //OutboxThreadLocal.setCurrent(_rampManager.getOutboxSystem());
      
      ResultFuture<Object> future = new ResultFuture<>();
      
      //QueryRefAmp queryRef = new QueryRefChainAmpCompletion(future);

      lookup.query(HeadersNull.NULL, future, bean, path);
      
      Object actor = future.get(_timeout, TimeUnit.SECONDS);
      
      return actor;
    } finally {
      //OutboxThreadLocal.setCurrent(outbox);
    }
  }
  
  public void onLoad(StubAmp actor, Result<?> result)
  {
    MethodAmp onLoad = _onLoad;

    if (onLoad != null) {
      // QueryRefAmp queryRef = new QueryRefChainAmpCompletion(result);

      onLoad.query(HeadersNull.NULL, result, actor);
    }
    else {
      result.ok(null);
    }
  }

  public void consume(StubAmp bean, ServiceRef serviceRef)
  {
    MethodAmp consume = _consume;
      
    if (consume != null) {
      Object arg = toSubscribeArg(_consumeApi, serviceRef);

      consume.send(HeadersNull.NULL, bean, arg);
    }
  }

  public void subscribe(StubAmp bean, ServiceRef serviceRef)
  {
    MethodAmp subscribe = _subscribe;

    if (subscribe != null) {
      Object arg = toSubscribeArg(_subscribeApi, serviceRef);

      subscribe.send(HeadersNull.NULL, bean, arg);
    }
  }

  public void unsubscribe(StubAmp bean, ServiceRef serviceRef)
  {
    MethodAmp unsubscribe = _unsubscribe;
      
    if (unsubscribe != null) {
      Object arg = toSubscribeArg(_unsubscribeApi, serviceRef);
      
      unsubscribe.send(HeadersNull.NULL, bean, arg);
    }
  }
  
  private Object toSubscribeArg(Class<?> api, ServiceRef serviceRef)
  {
    if (api.isAssignableFrom(serviceRef.getClass())) {
      return serviceRef;
    }
    else {
      return serviceRef.as(api);
    }
  }
  
  public void shutdown(StubAmp actor,
                       ShutdownModeAmp mode)
  {
    try {
      MethodAmp shutdown = _onShutdown;
      
      if (shutdown != null && ShutdownModeAmp.GRACEFUL == mode) {
        shutdown.send(HeadersNull.NULL, actor, mode);
      }
    } catch (Throwable e) {
      e.printStackTrace();
      log.log(Level.FINE, e.toString(), e);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _api.getName() + "]";
  }
  
  private static class Method_0_Base {
    private static final Method_0_Base NULL = new Method_0_Base();
    
    public void invoke(Object bean)
    {
    }
  }
  
  private static class Method_0 extends Method_0_Base {
    private final MethodHandle _mh;
    
    Method_0(MethodHandle mh)
    {
      Objects.requireNonNull(mh);
      
      _mh = mh;
    }
    
    @Override
    public void invoke(Object bean)
    {
      try {
        _mh.invokeExact(bean);
      } catch (Throwable e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }
  }
  
  static
  {
    try {
      _defaultTimeout = Long.parseLong(System.getProperty("amp.timeout")) / 1000L;
    } catch (Throwable e) {
      
    }
  }
}
