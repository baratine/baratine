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

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpException;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.inject.type.AnnotatedTypeClass;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.L10N;

import io.baratine.inject.Key;
import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.AfterBatch;
import io.baratine.service.BeforeBatch;
import io.baratine.service.MethodRef;
import io.baratine.service.Modify;
import io.baratine.service.OnActive;
import io.baratine.service.OnDelete;
import io.baratine.service.OnDestroy;
import io.baratine.service.OnInit;
import io.baratine.service.OnLoad;
import io.baratine.service.OnLookup;
import io.baratine.service.OnSave;
import io.baratine.service.Pin;
import io.baratine.service.Result;
import io.baratine.service.ResultChain;
import io.baratine.service.ResultFuture;
import io.baratine.service.Service;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceRef;
import io.baratine.stream.ResultStream;
import io.baratine.stream.ResultStreamBuilder;
import io.baratine.vault.AutoCreate;

/**
 * Stub for a bean's class.
 */
public class StubClass
{
  private static final L10N L = new L10N(StubClass.class);
  private static final Logger log
    = Logger.getLogger(StubClass.class.getName());
  
  private static final HashMap<Class<?>,Class<?>> _boxMap = new HashMap<>();
  
  private static long _defaultTimeout = 10;
  
  //private HashMap<String,Method> _methodMap = new HashMap<>();
  
  private HashMap<String,List<MethodAmp>> _stubMethodMap = new HashMap<>();
  
  private final ServicesAmp _services;

  private final Class<?> _type;
  private final AnnotatedType _annType;
  
  // api restricts the available methods 
  private final Class<?> _api;
  
  private boolean _isPublic;
  private boolean _isAutoCreate;
  
  private MethodAmp _onInit;
  private MethodAmp _onActive;
  private MethodAmp _onShutdown;

  private MethodAmp _onLoad;
  private MethodAmp _onSave;
  private MethodAmp _onDelete;
  
  private MethodAmp _onLookup;
  
  private Method_0_Base _beforeBatch = Method_0_Base.NULL;
  private Method_0_Base _afterBatch = Method_0_Base.NULL;
  
  // private MethodHandle _getMethod;
  
  private boolean _isLifecycleAware;
  
  private long _timeout;
  
  public StubClass(ServicesAmp services,
                   Class<?> type,
                   Class<?> api)
  {
    Objects.requireNonNull(services);
    Objects.requireNonNull(type);
    Objects.requireNonNull(api);
    
    if (type.isArray()) {
      throw new IllegalArgumentException(type.getName());
    }
    
    if (ServiceRef.class.isAssignableFrom(type)) {
      throw new IllegalStateException(String.valueOf(type));
    }
    
    if (StubAmp.class.isAssignableFrom(type)) {
      throw new IllegalStateException(String.valueOf(type));
    }
    
    _services = services;
    _type = type;
    _annType = new AnnotatedTypeClass(type);
    
    _api = api;
    
    _timeout = _defaultTimeout;
  }
  
  public void introspect()
  {
    try {
      addMethods(_type);
      
      _isAutoCreate = _type.isAnnotationPresent(AutoCreate.class);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
  
  public boolean isPublic()
  {
    return _isPublic;
  }

  public boolean isAutoCreate()
  {
    return _isAutoCreate;
  }
  
  protected boolean isLocalPodNode()
  {
    return true;
  }
  
  public AnnotatedType api()
  {
    return _annType;
  }
  
  protected ServicesAmp services()
  {
    return _services;
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
    else if (OnDelete.class.equals(type)) {
      return _onDelete != null;
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
  
  protected void onDelete(MethodAmp onDelete)
  {
    Objects.requireNonNull(onDelete);
    
    _onDelete = onDelete;
    _isLifecycleAware = true;
  }
  
  private void addMethods(Class<?> cl) throws IllegalAccessException
  {
    if (cl == null || cl.equals(Object.class)) {
      return;
    }
    
    addMethods(cl.getSuperclass());
    
    for (Class<?> api : cl.getInterfaces()) {
      addMethods(api);
    }
    
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
      
      if (method.isAnnotationPresent(OnInit.class)
          || onInitDriverAnn(method) != null) {
        _onInit = createOnInitMethod(method);
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
      else if (method.isAnnotationPresent(OnDelete.class)) {
        if (! isLocalPodNode()) {
          continue;
        }
        
        _onDelete = createMethod(method);
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
      
      if (! isMethodApi(method)) {
        continue;
      }
      
      if (ResultStreamBuilder.class.isAssignableFrom(method.getReturnType())) {
        //continue;
      }
      
      if (MethodRef.class.equals(method.getReturnType())
          && method.isAnnotationPresent(Service.class)
          && method.getParameterTypes().length == 1
          && String.class.equals(method.getParameterTypes()[0])) {
        if (true) throw new UnsupportedOperationException();
        method.setAccessible(true);
        
        MethodHandle getMethod = MethodHandles.lookup().unreflect(method);
        
        MethodType mt = MethodType.methodType(MethodRef.class,
                                              Object.class,
                                              String.class);
        
        getMethod = getMethod.asType(mt);
        
        //_getMethod = getMethod;
        
        continue;
      }
      
      addMethod(method.getName(), createPlainMethod(method));
    }
  }
  
  private void addMethod(String methodName, MethodAmp stubMethod)
  {
    List<MethodAmp> methods = _stubMethodMap.get(methodName);
    
    if (methods == null) {
      methods = new ArrayList<>();
      _stubMethodMap.put(methodName, methods);
    }

    // later methods override
    methods.remove(stubMethod);

    methods.add(stubMethod);
  }
  
  /**
   * Only API methods are exposed.
   */
  private boolean isMethodApi(Method method)
  {
    if (_type == _api) {
      return true;
    }
    
    for (Method methodApi : _api.getMethods()) {
      if (methodApi.getName().equals(method.getName())) {
        return true;
      }
    }
    
    return false;
  }
  
  public MethodAmp []getMethods()
  {
    MethodAmp []methods = new MethodAmp[_stubMethodMap.size()];
    
    _stubMethodMap.values().toArray(methods);
    
    return methods;
  }

  public MethodAmp methodByName(StubAmp stub, String methodName)
  {
    List<MethodAmp> rampMethods = _stubMethodMap.get(methodName);
    
    if (rampMethods == null) {
      return new MethodAmpNull(stub, methodName);
    }
    
    if (rampMethods.size() == 1) {
      return rampMethods.get(0);
    }
    
    throw new IllegalStateException(L.l("{0} has multiple methods named {1}",
                                        stub, methodName));
    
    /*
    if (_getMethod != null) {
      return getDynamicMethod(actor, methodName);
    }
    */

    /*
    throw new ServiceExceptionMethodNotFound(L.l("{0} is an unknown method in {1}",
                                               methodName, _api.getName()));
                                               */
  }

  public MethodAmp method(StubAmp stub,
                          String methodName,
                          Class<?> []param)
  {
    List<MethodAmp> methods = _stubMethodMap.get(methodName);
    
    if (methods == null) {
      return new MethodAmpNull(stub, methodName);
    }
    
    for (MethodAmp methodStub : methods) {
      if (isMatch(methodStub.parameters(), param)) {
        return methodStub;
      }
    }
    
    return new MethodAmpNull(stub, methodName);
    
    /*
    if (_getMethod != null) {
      return getDynamicMethod(stub, methodName);
    }
    */

    /*
    throw new ServiceExceptionMethodNotFound(L.l("{0} is an unknown method in {1}",
                                               methodName, _api.getName()));
                                               */
  }
  
  private boolean isMatch(ParameterAmp []parameters, Class<?> []paramTypes)
  {
    if (parameters == null) {
      return false;
    }
    
    if (parameters.length != paramTypes.length) {
      return false;
    }
    
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].getAnnotation(Pin.class) != null) {
        continue;
      }
      
      /*
      if (! box(parameters[i].rawClass()).equals(box(paramTypes[i]))) {
        return false;
      }
      */
      if (! parameters[i].rawClass().equals(paramTypes[i])) {
        return false;
      }
    }
    
    return true;
  }
  
  protected MethodAmp createPlainMethod(Method method)
  {
    /*
    if (! isLocalPodNode()) {
      return new MethodAmpInvalidPod(method.getDeclaringClass().getName(),
                                     method.getName());
    }
    */
    
    MethodAmp methodAmp = createMethod(method);
    
    return methodAmp;
  }
  
  protected MethodAmp createOnInitMethod(Method method)
  {
    Annotation onInitAnn = onInitDriverAnn(method);

    if (onInitAnn != null) {
      MethodOnInitGenerator gen
        = services().injector().instance(Key.of(MethodOnInitGenerator.class,
                                                onInitAnn.annotationType()));
      
      if (gen != null) {
        MethodAmp methodStub = gen.createMethod(method, onInitAnn, services());
        
        return methodStub;
      }
      else {
        System.out.println("ON-Onit: " + onInitAnn + " is an unknown @OnInit");
        
        return null;
      }
    }
    
    return createMethod(method);
  }
  
  private Annotation onInitDriverAnn(Method method)
  {
    for (Annotation ann : method.getAnnotations()) {
      if (ann.annotationType().isAnnotationPresent(OnInit.class)) {
        return ann;
      }
    }
    
    return null;
  }
    
  protected MethodAmp createMethod(Method method)
  {
    MethodAmp methodAmp = createMethodBase(method);
    
    if (method.getName().startsWith("delete")) {
      methodAmp = new FilterMethodDelete(methodAmp);
    }
    else if (method.isAnnotationPresent(Modify.class)) {
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
          methodStub = new MethodStubResult_VarArgs(services(), method);
        }
        else {
          methodStub = new MethodStubResult_N(services(), method);
        }
    
        /*
        if (result.isAnnotationPresent(Shim.class)) {
          return createCopyShim(methodStub, result);
        }
        else
        */
        
        if (result.isAnnotationPresent(Pin.class)) {
          return createPin(methodStub, result);
        }
        else {
          return methodStub;
        }
      }
      
      if (isResult(params, ResultStream.class)) {
        if (false && method.isVarArgs()) {
          return new MethodStubResult_VarArgs(services(), method);
        }
        else {
          return new MethodStubResultStream_N(services(), method);
        }
      }
      
      if (isResult(params, ResultPipeOut.class)) {
        return new MethodStubResultOutPipe_N(services(), method);
      }
      
      if (isResult(params, ResultPipeIn.class)) {
        return new MethodStubResultInPipe_N(services(), method);
      }
      
      /*
      else if (isAmpResult(paramTypes)) {
        return new SkeletonMethodAmpResult_N(method);
      }
      */
      
      if (method.isVarArgs()) {
        return new MethodStub_VarArgs(_services, method);
      }
    
      switch (params.length) {
      case 0:
        return new MethodStub_0(_services, method);
        
      case 1:
        return new MethodStub_1(_services, method);
      
      default:
        return new MethodStub_N(_services, method);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new AmpException(e);
    }
  }
  
  /*
  private MethodAmp createCopyShim(MethodAmp delegate,
                                   Parameter result)
  {
    TypeRef resultRef = TypeRef.of(result.getParameterizedType());
    TypeRef transferRef = resultRef.to(Result.class).param(0);
    
    ShimConverter<?,?> shim = new ShimConverter(_type, transferRef.rawClass());
    
    return new MethodStubResultCopy(delegate, shim);
  }
  */
  
  private MethodAmp createPin(MethodAmp delegate,
                              Parameter result)
  {
    Class<?> api = TypeRef.of(result.getParameterizedType())
                          .to(ResultChain.class)
                          .param(0)
                          .rawClass();
    
    return new MethodStubResultPin(delegate, api);
  }
  
  /*
  private MethodAmp getDynamicMethod(StubAmp actor,
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
  */
  
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
      if (resultClass.isAssignableFrom(params[i].getType())) {
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
  
  public void onActive(StubAmp actor, ResultChain<? super Boolean> result)
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

  /*
  public boolean isJournal()
  {
    return _isJournal;
  }
  */

  /*
  public long getJournalDelay()
  {
    return _journalDelay;
  }
  */
  
  /*
  public JournalAmp getJournal()
  {
    return _journal;
  }
  */
  
  public void onSave(StubAmp stub, Result<Void> result)
  {
    try {
      MethodAmp onDelete = _onDelete;
      MethodAmp onSave = _onSave;
      
      if (stub.state().isDelete() && onDelete != null) {
        onDelete.query(HeadersNull.NULL, result, stub);
      }
      else if (onSave != null) {
        //QueryRefAmp queryRef = new QueryRefChainAmpCompletion(result);
        onSave.query(HeadersNull.NULL, result, stub);
      }
      else {
        result.ok(null);
      }
      
      stub.state().onSaveComplete(stub);
      
      //stub.onSaveEnd(true);
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

  /*
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
  */
  
  /*
  private Object toSubscribeArg(Class<?> api, ServiceRef serviceRef)
  {
    if (api.isAssignableFrom(serviceRef.getClass())) {
      return serviceRef;
    }
    else {
      return serviceRef.as(api);
    }
  }
  */
  
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
  
  static Class<?> box(Class<?> type)
  {
    Class<?> boxType = _boxMap.get(type);
    
    return boxType != null ? boxType : type;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _type.getName() + "]";
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
    
    _boxMap.put(void.class, Void.class);
    _boxMap.put(boolean.class, Boolean.class);
    _boxMap.put(char.class, Character.class);
    _boxMap.put(byte.class, Byte.class);
    _boxMap.put(short.class, Short.class);
    _boxMap.put(int.class, Integer.class);
    _boxMap.put(long.class, Long.class);
    _boxMap.put(float.class, Float.class);
    _boxMap.put(double.class, Double.class);
  }
}
