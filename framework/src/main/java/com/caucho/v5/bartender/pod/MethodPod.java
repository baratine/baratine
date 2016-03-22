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

package com.caucho.v5.bartender.pod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.marshal.ModuleMarshal;
import com.caucho.v5.amp.marshal.PodImport;
import com.caucho.v5.amp.marshal.PodImportContext;
import com.caucho.v5.amp.marshal.ResultImport;
import com.caucho.v5.amp.marshal.ResultStreamImport;
import com.caucho.v5.amp.message.ResultStreamAmp;
import com.caucho.v5.amp.message.StreamForkMessage;
import com.caucho.v5.amp.remote.ActorLink;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.util.L10N;

import io.baratine.io.ResultPipeIn;
import io.baratine.io.ResultPipeOut;
import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionUnavailable;
import io.baratine.service.ServiceRef;
import io.baratine.stream.ResultStream;

/**
 * Method for a pod node call.
 * 
 * The method calls the owning service, and fails over if the primary is
 * down.
 * 
 * The node has already been selected using the hash of the URL.
 */
public class MethodPod implements MethodAmp
{
  private static final L10N L = new L10N(MethodPod.class);
  private static final Logger log = Logger.getLogger(MethodPod.class.getName());
  
  private final ServiceRefPod _serviceRef;
  private final String _name;
  private final Type _type;
  private final ClassLoader _sourceLoader;
  
  private MethodRefActive _methodRefActive;
  
  MethodPod(ServiceRefPod serviceRef,
            String name,
            Type type)
  {
    _name = name;
    _type = type;
    _serviceRef = serviceRef;

    _sourceLoader = Thread.currentThread().getContextClassLoader();
  }
  
  @Override
  public String name()
  {
    return _name;
  }
  
  @Override
  public Type []getGenericParameterTypes()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return localMethod.getParameterTypes();
    }
    else {
      return null;
    }
  }
  
  @Override
  public Class<?> []getParameterTypes()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return localMethod.getParameterClasses();
    }
    else {
      return null;
    }
  }
  
  @Override
  public Annotation []getAnnotations()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return localMethod.getAnnotations();
    }
    else {
      return null;
    }
  }
  
  @Override
  public boolean isVarArgs()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return localMethod.isVarArgs();
    }
    else {
      return false;
    }
  }
  
  @Override
  public boolean isModify()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return localMethod.method().isModify();
    }
    else {
      return false;
    }
  }

  @Override
  public Class<?> getReturnType()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return (Class) localMethod.getReturnType();
    }
    else {
      return null;
    }
  }

  @Override
  public Annotation[][] getParameterAnnotations()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return localMethod.getParameterAnnotations();
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isClosed()
  {
    ServiceRefAmp serviceRefActive = _serviceRef.getActiveService();
    
    return serviceRefActive == null;
  }

  @Override
  public void send(HeadersAmp headers, StubAmp actor, Object[] args)
  {
    MethodShim methodShim = findActiveMethodShim();

    methodShim.send(headers, actor, args);
  }

  @Override
  public void query(HeadersAmp headers, 
                    Result<?> result,
                    StubAmp actor,
                    Object[] args)
  {
    try {
      MethodShim methodShim = findActiveMethodShim();
      
      methodShim.query(headers, result, actor, args);
    } catch (Throwable exn) {
      result.fail(exn);
    }
  }
  
  /**
   * stream call with fork/join to all nodes in the pod.
   */
  @Override
  public <T> void stream(HeadersAmp headers, 
                         ResultStream<T> result,
                         StubAmp actor,
                         Object[] args)
  {
    int nodeCount = _serviceRef.getPod().nodeCount();
    
    if (nodeCount <= 1) {
      try {
        MethodShim methodShim = findActiveMethodShim();
        
        methodShim.stream(headers, result, actor, args);
      } catch (Throwable exn) {
        result.fail(exn);
      }
      return;
    }
    
    //ResultStreamJoin<T> joinLocal = null;
    
    ResultStreamAmp<T> resultAmp = (ResultStreamAmp<T>) result;

    // joinLocal = new ResultStreamJoin<>(result, _serviceRef.getSelfInbox());
    
    //ResultStream<T> createFork = joinLocal.fork();
    //ResultStream<T> createFork = resultAmp.fork();

    for (int i = 0; i < nodeCount; i++) {
      MethodRefActive method = findMethodRefActive(i);

      if (method != null) {
        // ResultStream<T> resultFork = joinLocal.fork();
        ResultStream<T> resultFork = resultAmp.fork();

        // MethodRefAmp methodRef = method.getMethodRef();
        //ActorAmp actorMessage = method.getMethodRef().getActor(actor);
        //ActorAmp actorMessage = method.getMethodRef().getService().getActor();
        //actorMessage = method.getMethodRef().getActor(actorMessage);

        method.getMethodShim().stream(headers, resultFork, actor, args);
        //methodRef.stream(headers, resultFork, args);
      }
      else {
        RuntimeException exn = new IllegalStateException();
        result.fail(exn);
      }
    }

    resultAmp.forkComplete();
  }
  
  /**
   * pipe publish registration call
   */
  @Override
  public <T> void outPipe(HeadersAmp headers, 
                          ResultPipeOut<T> result,
                          StubAmp actor,
                          Object[] args)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }
  
  /**
   * pipe publish registration call
   */
  @Override
  public <T> void inPipe(HeadersAmp headers, 
                          ResultPipeIn<T> result,
                          StubAmp actor,
                          Object[] args)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  /**
   * Return the local method for this pod method, when the pod is on the
   * same jvm. 
   */
  private MethodRefAmp findLocalMethod()
  {
    ServiceRefAmp serviceRefLocal = _serviceRef.getLocalService();
    
    if (serviceRefLocal == null) {
      return null;
    }

    if (_type != null) {
      return serviceRefLocal.getMethod(_name, _type);
    }
    else {
      return serviceRefLocal.getMethod(_name);
    }
  }
  
  private ServiceRefAmp getLocalService()
  {
    return _serviceRef.getLocalService();
  }
  
  /**
   * Return the method shim for the given server.
   */
  private MethodShim findActiveMethodShim(int i)
  {
    MethodRefActive methodRefActive = findMethodRefActive(i);
    
    return methodRefActive.getMethodShim();
  }
  
  private MethodRefActive findMethodRefActive(int i)
  {
    ServiceRefAmp serviceRefActive = _serviceRef.getActiveService(i);
    
    if (serviceRefActive == null) {
      throw new ServiceExceptionUnavailable(L.l("No service available for {0}",
                                                _serviceRef));
    }
    
    MethodRefActive methodRefActive = createMethodRefActive(serviceRefActive);
    
    return methodRefActive;
  }
  
  private MethodShim findActiveMethodShim()
  {
    MethodRefActive methodRefActive = findMethodRefActive();
    
    return methodRefActive.getMethodShim();
  }
  
  private MethodRefActive findMethodRefActive()
  {
    ServiceRefAmp serviceRefActive = _serviceRef.getActiveService();
    
    if (serviceRefActive == null) {
      throw new ServiceExceptionUnavailable(L.l("No service available for {0}",
                                                _serviceRef));
    }

    MethodRefActive methodRefActive = _methodRefActive;
    
    if (methodRefActive == null
        || ! methodRefActive.isValid(serviceRefActive)) {
      _methodRefActive = methodRefActive = createMethodRefActive(serviceRefActive);
    }
    
    return methodRefActive;
  }
  
  /**
   * Create an MethodRefActive for the given serviceRef.
   * 
   * The MethodRefActive contains the methodRef and the cross-pod 
   * marshal/serialization shim.
   */
  private MethodRefActive createMethodRefActive(ServiceRefAmp serviceRef)
  {
    MethodRefAmp methodRef;
    
    if (_type != null) {
      methodRef = serviceRef.getMethod(_name, _type);
    }
    else {
      methodRef = serviceRef.getMethod(_name);
    }
    
    MethodShim methodShim;
    
    ClassLoader methodLoader = methodRef.getService().getDelegateClassLoader();
    
    //System.out.println("SR: " + serviceRef + " " + serviceRef.getActor());
    
    if (methodLoader == _sourceLoader
        || serviceRef.getActor() instanceof ActorLink) {
      //methodShim = new MethodShimIdentity(methodRef.getMethod());
      methodShim = new MethodShimIdentity(methodRef, 
                                          isLocalService(serviceRef));
    }
    else {
      PodImport importContext; 
      
      importContext = PodImportContext.create(_sourceLoader).getPodImport(methodLoader);
      //importContext = ImportContext.create(methodLoader).getModuleImport(_sourceLoader);

      //methodShim = new MethodShimImport(methodRef.getMethod(), importContext);
      methodShim = new MethodShimImport(methodRef, importContext, 
                                        isLocalService(serviceRef));
    }
    
  
    return new MethodRefActive(serviceRef, 
                               methodRef,
                               methodShim);
  }

  private boolean isLocalService(ServiceRef serviceRef)
  {
    try {
      return (serviceRef == getLocalService());
    } catch (Exception e) {
      return false;
    }
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + name()
            + "," + _serviceRef.address() + "]");
  }
  
  private static class MethodRefActive {
    private final ServiceRefAmp _serviceRef;
    private final MethodRefAmp _methodRef;
    private final MethodShim _methodShim;
    
    MethodRefActive(ServiceRefAmp serviceRef,
                    MethodRefAmp methodRef,
                    MethodShim methodShim)
    {
      _serviceRef = serviceRef;
      _methodRef = methodRef;
      _methodShim = methodShim;
    }
    
    MethodRefAmp getMethod(ServiceRefAmp serviceRefActive)
    {
      if (_serviceRef == serviceRefActive) {
        return _methodRef;
      }
      else {
        return null;
      }
    }
    
    public MethodRefAmp getMethodRef()
    {
      return _methodRef;
    }
    
    public MethodShim getMethodShim()
    {
      return _methodShim;
    }
    
    boolean isValid(ServiceRefAmp serviceRefActive)
    {
      return _serviceRef == serviceRefActive;
    }
  }
  
  private interface MethodShim
  {
    void send(HeadersAmp headers, StubAmp actor, Object []args);
    
    void query(HeadersAmp headers, Result<?> result, StubAmp actor, Object []args);
    
    <T> void stream(HeadersAmp headers, 
                    ResultStream<T> queryRef,
                    StubAmp actor, 
                    Object[] args);
  }
  
  private static class MethodShimIdentity implements MethodShim
  {
    private final MethodRefAmp _methodRef;
    private final boolean _isLocalService;
    
    MethodShimIdentity(MethodRefAmp methodRef,
                       boolean isLocalService)
    {
      _methodRef = methodRef;
      _isLocalService = isLocalService;
    }

    @Override
    public void send(HeadersAmp headers, StubAmp actor, Object []args)
    {
      //_methodRef.send(headers, actor, args);
      _methodRef.method().send(headers, actor, args);
    }
    
    @Override
    public void query(HeadersAmp headers, 
                      Result<?> result,
                      StubAmp actor,
                      Object []args)
    {
      //_methodRef.query(headers, result, actor, args);
      _methodRef.method().query(headers, result, actor, args);
    }
    
    @Override
    public <T> void stream(HeadersAmp headers, 
                           ResultStream<T> result,
                           StubAmp actor, 
                           Object[] args)
    {
      //_methodRef.stream(headers, result, actor, args);
      if (_isLocalService) {
        _methodRef.method().stream(headers, result, actor, args);
      }
      else {
        ServiceRefAmp serviceRef = _methodRef.getService();

        try (OutboxAmp outbox = OutboxAmp.currentOrCreate(serviceRef.manager())) {
          StreamForkMessage<T> msg;
          
          long expires = 10000;
          
          msg = new StreamForkMessage<T>(outbox,
                                  outbox.inbox(),
                                  headers,
                                  serviceRef,
                                  _methodRef.method(),
                                  result,
                                  expires,
                                 args);

          outbox.offer(msg);
          // XXX: Need forward stream
          // _methodRef.stream(headers, result, args);        }
        }
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _methodRef + "]";
    }
  }
  
  private static class MethodShimImport implements MethodShim
  {
    //private final MethodAmp _methodTarget;
    private final MethodRefAmp _methodRef;
    private boolean _isLocalService;
    private final PodImport _importContext;
    private ModuleMarshal[] _args;
    private ModuleMarshal _resultMarshal;
    
    MethodShimImport(MethodRefAmp methodRef,
                     PodImport importContext,
                     boolean isLocalService)
    {
      _methodRef = methodRef;
      _importContext = importContext;
      _isLocalService = isLocalService;
      
      _args = _importContext.marshalArgs(methodRef.getParameterClasses());
      
      Type retType = methodRef.getReturnType();
      
      if (retType instanceof Class) {
        Class<?> retClass = (Class<?>) retType;

        _resultMarshal = _importContext.marshalResult(retClass);
      }
      else {
        _resultMarshal = _importContext.marshalResult(Object.class);
      }
    }

    @Override
    public void send(HeadersAmp headers,
                     StubAmp actor,
                     Object []args)
    {
      //_methodTarget.send(headers, actor, marshalArgs(args));
      _methodRef.method().send(headers, actor, marshalArgs(args));
    }
    
    @Override
    public void query(HeadersAmp headers,
                      Result<?> result, 
                      StubAmp actor,
                      Object []args)
    {
      ClassLoader loader = _importContext.getImportLoader();
      
      ResultImport queryRefImport
        = new ResultImport(result, _resultMarshal, loader);

      //_methodTarget.query(headers, queryRefImport, actor, marshalArgs(args));
      _methodRef.method().query(headers, queryRefImport, actor, marshalArgs(args));
    }
    
    @Override
    public <T> void stream(HeadersAmp headers, 
                           ResultStream<T> result,
                           StubAmp actor, 
                           Object[] args)
    {
      ClassLoader loader = _importContext.getImportLoader();
      
      ResultStreamImport resultImport
        = new ResultStreamImport(result, _resultMarshal, loader);

      //_methodTarget.stream(headers, resultImport, actor, marshalArgs(args));
      if (_isLocalService) {
        _methodRef.method().stream(headers, resultImport, actor, marshalArgs(args));
      }
      else {
        _methodRef.stream(headers, resultImport, marshalArgs(args));
      }
    }
    
    private Object []marshalArgs(Object []args)
    {
      if (args == null) {
        return null;
      }
      
      Object []dstArgs = new Object[args.length];
      
      int len = Math.min(dstArgs.length, _args.length);
      
      for (int i = 0; i < len; i++) {
        dstArgs[i] = _args[i].convert(args[i]);
      }
      
      for (int i = len; i < dstArgs.length; i++) {
        // XXX: MarshalObject?
        dstArgs[i] = args[i];
      }
      
      return dstArgs;
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _methodRef + "]";
    }
  }
}
