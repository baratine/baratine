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

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpException;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.MethodMessageBase;
import com.caucho.v5.amp.message.SendMessage_0;
import com.caucho.v5.amp.message.SendMessage_1;
import com.caucho.v5.amp.message.SendMessage_N;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.stream.StreamBuilderImpl;
import com.caucho.v5.bytecode.JavaClass;
import com.caucho.v5.bytecode.JavaClassLoader;
import com.caucho.v5.bytecode.JavaMethod;
import com.caucho.v5.bytecode.attr.CodeWriterAttribute;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.ProxyClassLoader;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.ModulePrivate;

import io.baratine.io.ResultPipeIn;
import io.baratine.io.ResultPipeOut;
import io.baratine.service.AfterBatch;
import io.baratine.service.BeforeBatch;
import io.baratine.service.MethodRef;
import io.baratine.service.OnActive;
import io.baratine.service.OnConsume;
import io.baratine.service.OnDestroy;
import io.baratine.service.OnInit;
import io.baratine.service.OnLoad;
import io.baratine.service.OnLookup;
import io.baratine.service.OnSave;
import io.baratine.service.OnSubscribe;
import io.baratine.service.OnUnsubscribe;
import io.baratine.service.Result;
import io.baratine.service.ResultStream;
import io.baratine.service.Service;
import io.baratine.service.Timeout;
import io.baratine.stream.ResultStreamBuilder;

/**
 * Extends a decorator, implementing the missing methods.
 */
@ModulePrivate
public class ProxyGeneratorAmp<T> {
  private static final L10N L = new L10N(ProxyGeneratorAmp.class);
  private static final Logger log 
    = Logger.getLogger(ProxyGeneratorAmp.class.getName());
  
  //private static final long QUERY_TIMEOUT = 10 * 60 * 1000L;
  //private static final long QUERY_TIMEOUT = 10 * 1000L;
  
  // private static long _defaultTimeout = 10L * 1000L;
  private static long _defaultTimeout = 60L * 1000L;
  
  private final Class<T> _api;
  private final ClassLoader _classLoader;
  private final ArrayList<Class<?>> _apiList;
  
  private Class<T> _proxyClass;
  private Constructor<?> _proxyCtor;
  
  private HashMap<String,Method> _methodMap = new HashMap<>();
  private JavaClass _jClass;
  
  private ProxyGeneratorAmp(Class<T> api,
                            ClassLoader loader)
  {
    _classLoader = loader;

    /*
    if (! api.isInterface()) {
      throw new IllegalStateException(L.l("invalid interface {0}", api));
    }
    */
    ArrayList<Class<?>> apiList = new ArrayList<>();
    
    Constructor<?> zeroCtor = null;

    if (! api.isInterface()) {
      for (Constructor<?> ctorItem : api.getDeclaredConstructors()) {
        if (ctorItem.getParameterTypes().length == 0) {
          zeroCtor = ctorItem;
          break;
        }
      }

      // String typeClassName = cl.getName().replace('.', '/');

      if (zeroCtor == null) {
        ArrayList<Class<?>> interfaces = getInterfaces(api);

        if (interfaces.size() > 0) {
          // XXX:
          api = (Class) interfaces.get(0);
          
          apiList.addAll(interfaces);
        }
        else {
          throw new ConfigException(L.l("'{0}' does not have a zero-arg public or protected constructor.  Scope adapter components need a zero-arg constructor, e.g. @RequestScoped stored in @ApplicationScoped.",
                                        api.getName()));
        }
      }
      else {
        apiList.add(api);
      }
    }
    else {
      apiList.add(api);
    }

    _apiList = apiList;
    _api = api;
  }
  
  static Constructor<?> create(Class<?> cl,
                               ClassLoader loader)
  {
    return createImpl(cl, loader);
  }
  
  /**
   * Must return Constructor, not MethodHandle because MethodHandles
   * cache the type in the permgen.
   */
  private static Constructor<?> createImpl(Class<?> cl,
                                           ClassLoader loader)
  {
    /**
    if (! Modifier.isAbstract(cl.getModifiers())) {
      throw new IllegalArgumentException();
    }
    */
    
    ProxyGeneratorAmp<?> adapter
      = new ProxyGeneratorAmp(cl, loader);
    
    Class<?> proxyClass = adapter.generate();
    
    return proxyClass.getConstructors()[0];
  }
  
  private Class<T> generate()
  {
    generateProxy(_api);
    
    return getProxyClass();
  }
  
  public Class<T> getProxyClass()
  {
    return _proxyClass;
  }

  private void generateProxy(Class<?> cl)
  {
    try {
      Constructor<?> zeroCtor = null;

      for (Constructor<?> ctorItem : cl.getDeclaredConstructors()) {
        if (ctorItem.getParameterTypes().length == 0) {
          zeroCtor = ctorItem;
          break;
        }
      }
      
      String typeClassName = cl.getName().replace('.', '/');
      
      if (zeroCtor == null && ! cl.isInterface()) {
        throw new ConfigException(L.l("'{0}' does not have a zero-arg public or protected constructor.  Scope adapter components need a zero-arg constructor, e.g. @RequestScoped stored in @ApplicationScoped.",
                                      cl.getName()));
      }

      if (zeroCtor != null) {
        zeroCtor.setAccessible(true);
      }
      
      String thisClassName;
      
      thisClassName = typeClassName + "__AmpProxy";
      
      if (thisClassName.startsWith("java")) {
        thisClassName = "amp/" + thisClassName;
      }
      
      String cleanName = thisClassName.replace('/', '.');
      
      boolean isPackagePrivate = false;
      
      ClassLoader loader = _classLoader;
      
      if (! Modifier.isPublic(cl.getModifiers()) 
          && ! Modifier.isProtected(cl.getModifiers())) {
        isPackagePrivate = true;
      }

      if (isPackagePrivate) {
        loader = cl.getClassLoader();
      }
      /*
      else {
        loader = Thread.currentThread().getContextClassLoader();
      }
      */
      
      //loader = cl.getClassLoader();
      
      try {
        _proxyClass = (Class<T>) Class.forName(cleanName, false, loader);
      } catch (ClassNotFoundException e) {
        log.log(Level.ALL, e.toString(), e);
      }

      if (_proxyClass != null) {
        return;
      }
      
      //JavaClassLoader jLoader = new JavaClassLoader(cl.getClassLoader());
      JavaClassLoader jLoader = new JavaClassLoader(loader);

      JavaClass jClass = new JavaClass(jLoader);
      _jClass = jClass;
      
      jClass.setAccessFlags(Modifier.PUBLIC);

      jClass.setWrite(true);

      jClass.setMajor(51);
      jClass.setMinor(0);

      String superClassName;


      jClass.setThisClass(thisClassName);

      if (_api.isInterface()) {
        // superClassName = AbstractAmpProxy.class.getName().replace('.', '/');
        superClassName = Object.class.getName().replace('.', '/');
        jClass.setSuperClass(superClassName);
      
        jClass.addInterface(_api.getName().replace('.', '/'));
      }
      else {
        // superClassName = AbstractAmpProxy.class.getName().replace('.', '/');
        superClassName = _api.getName().replace('.', '/');
        jClass.setSuperClass(superClassName);
      }
      
      jClass.addInterface(ProxyHandleAmp.class.getName().replace('.', '/'));
      jClass.addInterface(Serializable.class);
      
      for (Class<?> api : _apiList) {
        if (api.equals(_api)) {
        }
        else if (Serializable.class.equals(api)) {
        }
        else {
          jClass.addInterface(api.getName().replace('.', '/'));
        }
      }
      
      jClass.createField("_serviceRef", ServiceRefAmp.class)
            .setAccessFlags(Modifier.PRIVATE|Modifier.FINAL);
      jClass.createField("_inboxSystem", InboxAmp.class)
            .setAccessFlags(Modifier.PRIVATE|Modifier.FINAL);
      jClass.createField("_messageFactory", MessageFactoryAmp.class)
            .setAccessFlags(Modifier.PRIVATE|Modifier.FINAL);

      for (Method method : getMethods()) {
        Class<?> []paramTypes = method.getParameterTypes();
        int paramLen = paramTypes.length;

        if (Modifier.isStatic(method.getModifiers())) {
          continue;
        }
        
        if (Modifier.isFinal(method.getModifiers())) {
          continue;
        }
        
        if (! Modifier.isPublic(method.getModifiers())) {
          continue;
        }
        
        if (method.getDeclaringClass().equals(Object.class)) {
          continue;
        }
        
        if (method.getName().equals("toString")
            && paramLen == 0) {
          continue;
        }
        
        if (method.getName().equals("hashCode")
            && paramLen == 0) {
          continue;
        }
        
        if (method.getName().equals("equals")
            && paramLen == 1 && paramTypes[0].equals(Object.class)) {
          continue;
        }
        
        if (method.isAnnotationPresent(OnActive.class)) {
          // cloud/0421, XXX: needs jamp QA
          continue;
        }
        
        if (method.isAnnotationPresent(OnInit.class)) {
          continue;
        }
        
        if (method.isAnnotationPresent(OnLoad.class)) {
          continue;
        }
        
        if (method.isAnnotationPresent(OnSave.class)) {
          continue;
        }
        
        if (method.isAnnotationPresent(OnLookup.class)) {
          continue;
        }
        
        if (method.isAnnotationPresent(OnConsume.class)) {
          continue;
        }
        
        if (method.isAnnotationPresent(OnSubscribe.class)) {
          continue;
        }
        
        if (method.isAnnotationPresent(OnUnsubscribe.class)) {
          continue;
        }
        
        if (method.isAnnotationPresent(OnDestroy.class)) {
          continue;
        }
        
        if (method.isAnnotationPresent(AfterBatch.class)) {
          continue;
        }
        
        if (method.isAnnotationPresent(BeforeBatch.class)) {
          continue;
        }
        
        // XXX: Need QA
        
        
        int ampResult = findAmpResult(paramTypes, Result.class);
        int ampResultStream = findAmpResult(paramTypes, ResultStream.class);
        int ampResultOutPipe = findAmpResult(paramTypes, ResultPipeOut.class);
        int ampResultInPipe = findAmpResult(paramTypes, ResultPipeIn.class);
        
        if (ResultStreamBuilder.class.isAssignableFrom(method.getReturnType())) {
          if (ampResult >= 0 || ampResultStream >= 0) {
            throw new IllegalStateException(L.l("result and StreamBuilder forbidden"));
          }
          
          createStreamBuilderMethod(jClass, method);
        }
        else if (paramLen > 0 
                 && ampResult >= 0) {
          if (! void.class.equals(method.getReturnType())) {
            throw new IllegalArgumentException(String.valueOf(method));
          }
          
          /*
          if (ampResult != paramTypes.length - 1) {
            throw new IllegalStateException(L.l("invalid Result position. Result must be the final argument."));
          }
          */
          
          createAmpResultMethod(jClass, method, ampResult);
        }
        else if (ampResultStream >= 0) {
          if (! void.class.equals(method.getReturnType())) {
            throw new IllegalArgumentException(L.l("Method '{0}' must return void with Result or ResultStream argument",
                                                   method.getName(), String.valueOf(method)));
          }
     
          createAmpResultStreamMethod(jClass, method, ampResultStream);
        }
        else if (ampResultOutPipe >= 0) {
          if (! void.class.equals(method.getReturnType())) {
            throw new IllegalArgumentException(L.l("Method '{0}' must return void with Result or ResultOutPipe argument",
                                                   method.getName(), String.valueOf(method)));
          }
     
          createAmpResultOutPipeMethod(jClass, method, ampResultOutPipe);
        }
        else if (ampResultInPipe >= 0) {
          if (! void.class.equals(method.getReturnType())) {
            throw new IllegalArgumentException(L.l("Method '{0}' must return void with Result or ResultOutPipe argument",
                                                   method.getName(), String.valueOf(method)));
          }
     
          createAmpResultInPipeMethod(jClass, method, ampResultInPipe);
        }
        else if (! void.class.equals(method.getReturnType())) {
          createQueryFutureMethod(jClass, method);
        }
        else {
          createSendMethod(jClass, method);
        }
      }

      createConstructor(jClass, superClassName);
      
      createToString(jClass);
      createHashCode(jClass);
      createEquals(jClass);
      
      createWriteReplace(jClass);
      
      createGetServiceRef(jClass);
      
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      // WriteStream out = VfsOld.openWrite(bos);

      jClass.write(bos);

      bos.close();

      byte[] buffer = bos.toByteArray();

      boolean isDebug = false;

      if (isDebug) {
        try {
          String userName = System.getProperty("user.name");

          try (OutputStream out = new FileOutputStream("file:/tmp/"
                           + userName
                           + "/qa/"
                           + thisClassName.replace('/', '_')
                           + ".class")) {
            out.write(buffer, 0, buffer.length); 
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

      if (isPackagePrivate) {
        if (! (loader instanceof DynamicClassLoader)) {
          throw new AmpException(L.l("{0} cannot be created because it's package-private outside of Resin",
                                      cleanName));
        }
        DynamicClassLoader dynLoader = (DynamicClassLoader) loader;
        // ioc/0517
        _proxyClass = (Class<T>) dynLoader.loadClass(cleanName, buffer);
      }
      else {
        ProxyClassLoader proxyLoader = new ProxyClassLoader(loader);

        _proxyClass = (Class<T>) proxyLoader.loadClass(cleanName, buffer);
      }
      
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  private ArrayList<Method> getMethods()
  {
    ArrayList<Method> methods = new ArrayList<>();
    
    for (Class<?> api : _apiList) {
      for (Method method : api.getMethods()) {
        if (! methods.contains(method)) {
          methods.add(method);
        }
      }
    }
    
    return methods;
  }
  
  private int findAmpResult(Class<?> []paramTypes, Class<?> api)
  {
    for (int i = 0; i < paramTypes.length; i++) {
      if (api.equals(paramTypes[i])) {
        return i;
      }
    }
    
    return -1;
  }
  
  private ArrayList<Class<?>> getInterfaces(Class<?> cl)
  {
    ArrayList<Class<?>> apiList = new ArrayList<>();

    getInterfaces(cl, apiList);
    
    return apiList;
  }
  
  private void getInterfaces(Class<?> cl, ArrayList<Class<?>> apiList)
  {
    if (cl == null) {
      return;
    }
    
    getInterfaces(cl.getSuperclass(), apiList);
    
    for (Class<?> api: cl.getInterfaces()) {
      if (! apiList.contains(api)) {
        apiList.add(api);
      }
    }
  }
  
  private void createConstructor(JavaClass jClass, String superClassName)
  {

    JavaMethod ctor = jClass.createMethod("<init>", 
                                          void.class,
                                          ServiceRefAmp.class,
                                          InboxAmp.class,
                                          MessageFactoryAmp.class);
    ctor.setAccessFlags(Modifier.PUBLIC);

    CodeWriterAttribute code = ctor.createCodeWriter();
    code.setMaxLocals(4);
    code.setMaxStack(5);
    
    code.pushObjectVar(0);
    
    code.invokespecial(superClassName,
                       "<init>",
                       void.class);

    code.pushObjectVar(0);
    code.pushObjectVar(1);
    
    code.putField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);

    code.pushObjectVar(0);
    code.pushObjectVar(2);
    
    code.putField(jClass.getThisClass(),
                  "_inboxSystem",
                  InboxAmp.class);

    code.pushObjectVar(0);
    code.pushObjectVar(3);
    
    code.putField(jClass.getThisClass(),
                  "_messageFactory",
                  MessageFactoryAmp.class);
    
    for (Method method : _methodMap.values()) {
      String methodName = method.getName();
      
      jClass.createField(getMethodFieldName(methodName),
                         MethodAmp.class)
            .setAccessFlags(Modifier.PRIVATE);
      
      code.pushObjectVar(0);
      
      code.pushObjectVar(1);
      code.pushConstant(methodName);
      
      Class<?> retType = getBoxedClass(method.getReturnType());
      code.pushConstantClass(retType);
      
      code.invokestatic(ProxyUtilsAmp.class,
                        "__caucho_getMethod",
                        MethodAmp.class,
                        ServiceRefAmp.class,
                        String.class,
                        Class.class);
      code.putField(jClass.getThisClass(),
                    getMethodFieldName(methodName),
                    MethodAmp.class);
    }
    
    code.addReturn();
    code.close();
    
  }
  
  private Class<?> getBoxedClass(Class<?> cl)
  {
    if (! cl.isPrimitive()) {
      return cl;
    }
    
    if (void.class.equals(cl)) {
      return Void.class;
    }
    else if (boolean.class.equals(cl)) {
      return Boolean.class;
    }
    else if (char.class.equals(cl)) {
      return Character.class;
    }
    else if (byte.class.equals(cl)) {
      return Byte.class;
    }
    else if (short.class.equals(cl)) {
      return Short.class;
    }
    else if (int.class.equals(cl)) {
      return Integer.class;
    }
    else if (long.class.equals(cl)) {
      return Long.class;
    }
    else if (float.class.equals(cl)) {
      return Float.class;
    }
    else if (double.class.equals(cl)) {
      return Double.class;
    }
    else {
      return null;
    }
  }

  private void createSendMethodOld(JavaClass jClass,
                                Method method)
  {
    String methodName = method.getName();
    Class<?> []parameterTypes = method.getParameterTypes();
    Annotation [][]parameterAnns = method.getParameterAnnotations();
    
    addMethod(method);
    
    CodeWriterAttribute code = createMethodHeader(jClass, method);
    
    code.setMaxLocals(1 + 2 * parameterTypes.length);
    code.setMaxStack(9 + 2 * parameterTypes.length);
    
    int argLen = parameterTypes.length;
    
    switch (argLen) {
    case 0:
      code.newInstance(SendMessage_0.class);
      break;
    case 1:
      code.newInstance(SendMessage_1.class);
      break;
    default:
      code.newInstance(SendMessage_N.class);
      break;
    }
    
    code.dup();
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);

    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  getMethodFieldName(methodName),
                  MethodAmp.class);
 
    partitionMethod(code, parameterTypes, parameterAnns);

    switch (parameterTypes.length) {
    case 0:
      pushRawParameters(code, parameterTypes, parameterAnns);

      code.invokespecial(SendMessage_0.class,
                         "<init>",
                         void.class,
                         ServiceRefAmp.class,
                         MethodAmp.class);
      break;

    case 1:
      pushRawParameters(code, parameterTypes, parameterAnns);

      code.invokespecial(SendMessage_1.class,
                         "<init>",
                         void.class,
                         ServiceRefAmp.class,
                         MethodAmp.class,
                         Object.class);
      break;

    default:
      pushParameters(code, parameterTypes, parameterAnns);

      code.invokespecial(SendMessage_N.class,
                         "<init>",
                         void.class,
                         ServiceRefAmp.class,
                         MethodAmp.class,
                         Object[].class);
    }
    
    code.pushConstant(InboxAmp.TIMEOUT_INFINITY);
    
    code.invoke(MethodMessageBase.class,
                "offer",
                void.class,
                long.class);

    code.addReturn();

    code.close();
  }

  private void createSendMethod(JavaClass jClass,
                                Method method)
  {
    String methodName = method.getName();
    Class<?> []parameterTypes = method.getParameterTypes();
    Annotation [][]parameterAnns = method.getParameterAnnotations();
    
    addMethod(method);
    
    CodeWriterAttribute code = createMethodHeader(jClass, method);
    
    code.setMaxLocals(1 + 2 * parameterTypes.length);
    code.setMaxStack(9 + 2 * parameterTypes.length);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_messageFactory",
                  MessageFactoryAmp.class);
    
    int argLen = parameterTypes.length;
    
    /*
    switch (argLen) {
    case 0:
      code.newInstance(SendMessage_0.class);
      break;
    case 1:
      code.newInstance(SendMessage_1.class);
      break;
    default:
      code.newInstance(SendMessage_N.class);
      break;
    }
    
    code.dup();
    */
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);

    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  getMethodFieldName(methodName),
                  MethodAmp.class);
 
    partitionMethod(code, parameterTypes, parameterAnns);

    switch (parameterTypes.length) {
    case 0:
      pushRawParameters(code, parameterTypes, parameterAnns);

      code.invokeInterface(MessageFactoryAmp.class,
                  "send",
                  void.class,
                  ServiceRefAmp.class,
                  MethodAmp.class);
      break;

    case 1:
      pushRawParameters(code, parameterTypes, parameterAnns);

      code.invokeInterface(MessageFactoryAmp.class,
                  "send",
                  void.class,
                  ServiceRefAmp.class,
                  MethodAmp.class,
                  Object.class);
      break;

    default:
      pushParameters(code, parameterTypes, parameterAnns);

      code.invokeInterface(MessageFactoryAmp.class,
                  "send",
                  void.class,
                  ServiceRefAmp.class,
                  MethodAmp.class,
                  Object[].class);
    }

    /*
    code.pushConstant(InboxAmp.TIMEOUT_INFINITY);
    
    code.invoke(MethodMessageBase.class,
                "offer",
                void.class,
                long.class);
                */

    code.addReturn();

    code.close();
  }

  private void createStreamBuilderMethod(JavaClass jClass,
                                         Method method)
  {
    String methodName = method.getName();
    Class<?> []parameterTypes = method.getParameterTypes();
    Annotation [][]parameterAnns = method.getParameterAnnotations();
    
    addMethod(method);
    
    CodeWriterAttribute code = createMethodHeader(jClass, method);
    
    code.setMaxLocals(1 + 2 * parameterTypes.length);
    code.setMaxStack(9 + 2 * parameterTypes.length);
    
    int argLen = parameterTypes.length;
    
    code.newInstance(StreamBuilderImpl.class);
    
    code.dup();
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  getMethodFieldName(methodName),
                  MethodAmp.class);
 
    partitionMethod(code, parameterTypes, parameterAnns);

    pushParameters(code, parameterTypes, parameterAnns);

    code.invokespecial(StreamBuilderImpl.class,
                       "<init>",
                       void.class,
                       ServiceRefAmp.class,
                       MethodAmp.class,
                       Object[].class);

    code.addObjectReturn();

    code.close();
  }
  
  private void addMethod(Method method)
  {
    if (_methodMap.get(method.getName()) == null) {
      _methodMap.put(method.getName(), method);
    }
  }
  
  /**
   * T foo(X a1, Y a2)
   * {
   *    new AmpQueryMessageActorCompletion(__caucho_getCurrentContext(),
   *                                       cont,
   *                                       timeout,
   *                                       _methodRef,
   *                                       new Object[] {a1, a2}).send();
   * }
   */
  private void createQueryFutureMethod(JavaClass jClass,
                                       Method method)
  {
    String methodName = method.getName();
    Class<?> []parameterTypes = method.getParameterTypes();
    Annotation [][]parameterAnns = method.getParameterAnnotations();

    int paramLength = parameterTypes.length;
    
    addMethod(method);
    
    CodeWriterAttribute code = createMethodHeader(jClass, method);
    
    code.setMaxLocals(1 + 2 * parameterTypes.length);
    code.setMaxStack(10 + 2 * parameterTypes.length);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_messageFactory",
                  MessageFactoryAmp.class);
    
    // code.pushObjectVar(getLength(parameterTypes, resultOffset) + 1);
    
    Timeout timeoutAnn = method.getAnnotation(Timeout.class);
    
    if (timeoutAnn != null) {
      long timeout = timeoutAnn.unit().toMillis(timeoutAnn.value());
      code.pushConstant(timeout);
    }
    
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  getMethodFieldName(methodName),
                  MethodAmp.class);
    
    partitionMethod(code, parameterTypes, parameterAnns);
    
    pushParameters(code, parameterTypes, parameterAnns,
                   1, 0, paramLength, -1);

    if (timeoutAnn != null) {
      code.invokeInterface(MessageFactoryAmp.class,
                           "queryFuture",
                           Object.class, // QueryWithResultMessage.class,
                           long.class,
                           ServiceRefAmp.class,
                           MethodAmp.class,
                           Object[].class);
    }
    else {
      code.invokeInterface(MessageFactoryAmp.class,
                           "queryFuture",
                           Object.class, // QueryWithResultMessage.class,
                           ServiceRefAmp.class,
                           MethodAmp.class,
                           Object[].class);
    }

    popReturn(code, method.getReturnType());

    code.close();
  }
  
  /**
   * void foo(X a1, Y a2, Result<T> cont)
   * {
   *    new AmpQueryMessageActorCompletion(__caucho_getCurrentContext(),
   *                                       cont,
   *                                       timeout,
   *                                       _methodRef,
   *                                       new Object[] {a1, a2}).send();
   * }
   */
  private void createAmpResultMethod(JavaClass jClass,
                                     Method method,
                                     int resultOffset)
  {
    String methodName = method.getName();
    Class<?> []parameterTypes = method.getParameterTypes();
    Annotation [][]parameterAnns = method.getParameterAnnotations();

    addMethod(method);
    
    CodeWriterAttribute code = createMethodHeader(jClass, method);
    
    code.setMaxLocals(1 + 2 * parameterTypes.length);
    code.setMaxStack(10 + 2 * parameterTypes.length);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_messageFactory",
                  MessageFactoryAmp.class);
    
    int argLen = parameterTypes.length - 1;
    // code.dup();

    /*
    switch (argLen) {
    case 0:
      //code.newInstance(QueryWithResultMessage_0.class);
      code.invokeInterface(MessageFactory.class,
                           "queryResult",
                           QueryWithResultMessage.class,
                           Result.class);
      break;
      
    case 1:
      code.invokeInterface(MessageFactory.class,
                           "queryResult",
                           QueryWithResultMessage.class,
                           Result.class,
                           Object.class);
      //code.newInstance(QueryWithResultMessage_1.class);
      break;
      
    default:
      //code.newInstance(QueryWithResultMessage_N.class);
      code.invokeInterface(MessageFactory.class,
                           "queryResult",
                           QueryWithResultMessage.class,
                           Result.class,
                           Object.class,
                           Object.class);
      break;
    }
    */
    
    /*
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_inboxSystem",
                  RampMailbox.class);
                  */
    
    code.pushObjectVar(getLength(parameterTypes, resultOffset) + 1);
    
    long timeout = _defaultTimeout;
    
    code.pushConstant(timeout);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  getMethodFieldName(methodName),
                  MethodAmp.class);
    
    partitionMethod(code, parameterTypes, parameterAnns);
    
    switch (argLen) {
    case 0:
      pushRawParameters(code, parameterTypes, parameterAnns,
                        1, 
                        0, 
                        argLen);

      code.invokeInterface(MessageFactoryAmp.class,
                           "queryResult",
                           void.class, // QueryWithResultMessage.class,
                           Result.class,
                           long.class,
                           ServiceRefAmp.class,
                           MethodAmp.class);
      /*
      code.invokespecial(QueryWithResultMessage_0.class,
                         "<init>",
                         void.class,
                         Result.class,
                         long.class,
                         ServiceRefAmp.class,
                         MethodAmp.class);
                         */
      break;
      
    case 1:
      pushRawParameters(code, parameterTypes, parameterAnns,
                        1,
                        0, 
                        argLen + 1,
                        resultOffset);

      /*
      code.invokespecial(QueryWithResultMessage_1.class,
                         "<init>",
                         void.class,
                         Result.class,
                         long.class,
                         ServiceRefAmp.class,
                         MethodAmp.class,
                         Object.class);
                         */
      
      code.invokeInterface(MessageFactoryAmp.class,
                           "queryResult",
                           void.class, // QueryWithResultMessage.class,
                           Result.class,
                           long.class,
                           ServiceRefAmp.class,
                           MethodAmp.class,
                           Object.class);
      break;
      
    default:
      pushParameters(code, parameterTypes, parameterAnns, 
                     1, 
                     0, 
                     argLen + 1, // paramLength,
                     resultOffset);

      /*
      code.invokespecial(QueryWithResultMessage_N.class,
                         "<init>",
                         void.class,
                         Result.class,
                         long.class,
                         ServiceRefAmp.class,
                         MethodAmp.class,
                         Object[].class);
                         */
      
      code.invokeInterface(MessageFactoryAmp.class,
                           "queryResult",
                           void.class, // QueryWithResultMessage.class,
                           Result.class,
                           long.class,
                           ServiceRefAmp.class,
                           MethodAmp.class,
                           Object[].class);
      
      break;
    }
    
    /*
    code.pushConstant(InboxAmp.TIMEOUT_INFINITY);
    
    code.invoke(MessageMethod.class,
                "offer",
                void.class,
                long.class);
                */

    code.addReturn();

    code.close();
  }
  
  /**
   * void foo(X a1, Y a2, ResultStream<T> result)
   * {
   *    stream(__caucho_getCurrentContext(),
   *                                       cont,
   *                                       timeout,
   *                                       _methodRef,
   *                                       new Object[] {a1, a2}).send();
   * }
   */
  private void createAmpResultStreamMethod(JavaClass jClass,
                                           Method method,
                                           int resultOffset)
  {
    String methodName = method.getName();
    Class<?> []parameterTypes = method.getParameterTypes();
    Annotation [][]parameterAnns = method.getParameterAnnotations();

    addMethod(method);
    
    CodeWriterAttribute code = createMethodHeader(jClass, method);
    
    code.setMaxLocals(1 + 2 * parameterTypes.length);
    code.setMaxStack(10 + 2 * parameterTypes.length);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_messageFactory",
                  MessageFactoryAmp.class);
    
    int argLen = parameterTypes.length - 1;
    
    code.pushObjectVar(getLength(parameterTypes, resultOffset) + 1);
    
    long timeout = _defaultTimeout;
    
    code.pushConstant(timeout);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  getMethodFieldName(methodName),
                  MethodAmp.class);
    
    partitionMethod(code, parameterTypes, parameterAnns);
    
    pushParameters(code, parameterTypes, parameterAnns, 
                   1, 
                   0, 
                   argLen + 1, // paramLength,
                   resultOffset);
      
    code.invokeInterface(MessageFactoryAmp.class,
                         "streamResult",
                         void.class, // QueryWithResultMessage.class,
                         ResultStream.class,
                         long.class,
                         ServiceRefAmp.class,
                         MethodAmp.class,
                         Object[].class);

    code.addReturn();

    code.close();
  }
  
  private void createAmpResultOutPipeMethod(JavaClass jClass,
                                            Method method,
                                            int resultOffset)
  {
    createAmpResultPipeMethod(jClass, method, resultOffset,
                              ResultPipeOut.class,
                              "resultOutPipe");
  }
  
  private void createAmpResultInPipeMethod(JavaClass jClass,
                                            Method method,
                                            int resultOffset)
  {
    createAmpResultPipeMethod(jClass, method, resultOffset,
                              ResultPipeIn.class,
                              "resultInPipe");
  }
  
  private void createAmpResultPipeMethod(JavaClass jClass,
                                         Method method,
                                         int resultOffset,
                                         Class<?> resultType,
                                         String messageMethod)
  {
    String methodName = method.getName();
    Class<?> []parameterTypes = method.getParameterTypes();
    Annotation [][]parameterAnns = method.getParameterAnnotations();

    addMethod(method);
    
    CodeWriterAttribute code = createMethodHeader(jClass, method);
    
    code.setMaxLocals(1 + 2 * parameterTypes.length);
    code.setMaxStack(10 + 2 * parameterTypes.length);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_messageFactory",
                  MessageFactoryAmp.class);
    
    int argLen = parameterTypes.length - 1;
    
    code.pushObjectVar(getLength(parameterTypes, resultOffset) + 1);
    
    long timeout = _defaultTimeout;
    
    code.pushConstant(timeout);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  getMethodFieldName(methodName),
                  MethodAmp.class);
    
    partitionMethod(code, parameterTypes, parameterAnns);
    
    pushParameters(code, parameterTypes, parameterAnns, 
                   1, 
                   0, 
                   argLen + 1, // paramLength,
                   resultOffset);
      
    code.invokeInterface(MessageFactoryAmp.class,
                         messageMethod,
                         void.class, // QueryWithResultMessage.class,
                         resultType,
                         long.class,
                         ServiceRefAmp.class,
                         MethodAmp.class,
                         Object[].class);

    code.addReturn();

    code.close();
  }

  private void createGetServiceRef(JavaClass jClass)
  {
    CodeWriterAttribute code = createMethodHeader(jClass, "__caucho_getServiceRef",
                                                  ServiceRefAmp.class);
    
    code.setMaxLocals(2);
    code.setMaxStack(10);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);
    
    
    code.addObjectReturn();

    code.close();
  }

  private void createToString(JavaClass jClass)
  {
    CodeWriterAttribute code = createMethodHeader(jClass, "toString",
                                                  String.class);
    
    code.setMaxLocals(2);
    code.setMaxStack(10);
    
    code.newInstance(StringBuilder.class);
    code.dup();
    
    code.invokespecial(StringBuilder.class,
                       "<init>",
                       void.class);
    
    code.pushConstant(jClass.getShortName() + "[");
    code.invoke(StringBuilder.class,
                "append",
                StringBuilder.class,
                String.class);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);
    code.invoke(StringBuilder.class,
                "append",
                StringBuilder.class,
                Object.class);
    
    code.pushConstant("]");
    code.invoke(StringBuilder.class,
                "append",
                StringBuilder.class,
                String.class);
    
    code.invoke(StringBuilder.class,
                "toString",
                String.class);

    code.addObjectReturn();

    code.close();
  }

  private void createHashCode(JavaClass jClass)
  {
    CodeWriterAttribute code = createMethodHeader(jClass, "hashCode",
                                                  int.class);
    
    code.setMaxLocals(2);
    code.setMaxStack(10);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);
    
    code.invoke(Object.class,
                "hashCode",
                int.class);

    code.addIntReturn();

    code.close();
  }

  private void createEquals(JavaClass jClass)
  {
    CodeWriterAttribute code = createMethodHeader(jClass, "equals",
                                                  boolean.class,
                                                  Object.class);
    
    code.setMaxLocals(2);
    code.setMaxStack(10);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);
    
    code.pushObjectVar(1);
    
    code.invokestatic(ProxyUtilsAmp.class,
                      "equalsProxy",
                      boolean.class,
                      ServiceRefAmp.class,
                      Object.class);

    code.addIntReturn();

    code.close();
  }

  private void createWriteReplace(JavaClass jClass)
  {
    CodeWriterAttribute code = createMethodHeader(jClass, "writeReplace",
                                                  Object.class);
    
    code.setMaxLocals(2);
    code.setMaxStack(10);
    
    code.newInstance(ProxyHandle.class);
    code.dup();
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  "_serviceRef",
                  ServiceRefAmp.class);
    
    code.invokeInterface(ServiceRefAmp.class, "address", String.class);
    
    code.pushConstantClass(_api);
    
    code.invokespecial(ProxyHandle.class,
                      "<init>",
                      void.class,
                      String.class,
                      Class.class);

    code.addObjectReturn();

    code.close();
  }
  
  private int getLength(Class<?> []types, int limit)
  {
    int offset = 0;
    
    for (int i = 0; i < limit; i++) {
      Class<?> type = types[i];
      
      if (type.equals(long.class) || type.equals(double.class)) {
        offset += 2;
      }
      else {
        offset += 1;
      }
    }
    
    return offset;
  }
  
  private CodeWriterAttribute createMethodHeader(JavaClass jClass,
                                                 Method method)
  {
    String descriptor = createDescriptor(method);
    JavaMethod jMethod = jClass.createMethod(method.getName(),
                                             descriptor);
    jMethod.setAccessFlags(Modifier.PUBLIC|Modifier.FINAL);

    CodeWriterAttribute code = jMethod.createCodeWriter();
    
    return code;
    
  }
  
  private CodeWriterAttribute createMethodHeader(JavaClass jClass,
                                                 String methodName,
                                                 Class<?> retType,
                                                 Class<?> ...paramTypes)
  {
    String descriptor = createDescriptor(retType, paramTypes);
    JavaMethod jMethod = jClass.createMethod(methodName,
                                             descriptor);
    jMethod.setAccessFlags(Modifier.PUBLIC|Modifier.FINAL);

    CodeWriterAttribute code = jMethod.createCodeWriter();
    
    return code;
    
  }
  
  private void partitionMethod(CodeWriterAttribute code, 
                               Class<?> []parameterTypes,
                               Annotation [][]parameterAnns)
  {
    int index = 1;

    for (int i = 0; i < parameterTypes.length; i++) {
      //PartitionKey key = getAnnotation(PartitionKey.class, parameterAnns[i]);
      
      Class<?> type = parameterTypes[i];
      
      //if (key == null) {
        if (long.class.equals(type) || double.class.equals(type)) {
          index += 2;
        }
        else {
          index++;
        }
        
        if (true) continue;
      //}
      
      if (boolean.class.equals(type)) {
        code.pushIntVar(index);
        code.invokestatic(ProxyUtilsAmp.class, "partition",
                          MethodRef.class,
                          MethodRef.class,
                          boolean.class);
      }
      else if (int.class.equals(type)
               || byte.class.equals(type)
               || short.class.equals(type)) {
        code.pushIntVar(index);
        code.invokestatic(ProxyUtilsAmp.class, "partition",
                          MethodRef.class,
                          MethodRef.class,
                          int.class);
      }
      else if (long.class.equals(type)) {
        code.pushLongVar(index);
        code.invokestatic(ProxyUtilsAmp.class, "partition",
                     MethodRef.class,
                     MethodRef.class,
                     long.class);
      }
      else if (String.class.equals(type)) {
        code.pushObjectVar(index);
        code.invokestatic(ProxyUtilsAmp.class, "partition",
                          MethodRef.class,
                          MethodRef.class,
                          String.class);
      }
      else {
        code.pushObjectVar(index);
        code.invokestatic(ProxyUtilsAmp.class, "partition",
                          MethodRef.class,
                          MethodRef.class,
                          Object.class);
      }

      return;
    }
  }
  
  private void pushParameters(CodeWriterAttribute code, 
                              Class<?> []parameterTypes,
                              Annotation [][]parameterAnns)
  {
    pushParameters(code, parameterTypes, parameterAnns, 
                   1, 0, parameterTypes.length, -1);
  }
  
  private void pushParameters(CodeWriterAttribute code, 
                              Class<?> []parameterTypes,
                              Annotation [][]parameterAnns,
                              int index,
                              int paramOffset,
                              int paramLength,
                              int paramSkip)
  {
    if (false && paramLength == 0) {
      code.pushNull();
    }
    else {
      if (paramSkip >= 0) {
        code.pushInt(paramLength - 1);
      }
      else {
        code.pushInt(paramLength);
      }
      code.newObjectArray(Object.class);
    
      int arrayIndex = 0;
      for (int i = 0; i < paramLength; i++) {
        if (i + paramOffset == paramSkip) {
          index++;
          continue;
        }
        
        Class<?> paramType = parameterTypes[i + paramOffset];
        Annotation []anns = parameterAnns[i + paramOffset];
        
        code.dup();
      
        code.pushInt(arrayIndex++);
        
        index = boxVariable(code, index, paramType, anns);
      
        code.setArrayObject();
      }
    }
  }
  
  private void pushRawParameters(CodeWriterAttribute code, 
                                 Class<?> []parameterTypes,
                                 Annotation [][]parameterAnns)
  {
    pushRawParameters(code, parameterTypes, parameterAnns,
                      1, 0, parameterTypes.length);
  }
  
  private void pushRawParameters(CodeWriterAttribute code, 
                                 Class<?> []parameterTypes,
                                 Annotation [][]parameterAnns,
                                 int index,
                                 int paramOffset,
                                 int paramLength)
  {
    for (int i = 0; i < paramLength; i++) {
      Class<?> paramType = parameterTypes[i + paramOffset];
      Annotation []anns = parameterAnns[i + paramOffset];
      
      index = boxVariable(code, index, paramType, anns);
    }
  }
  
  private void pushRawParameters(CodeWriterAttribute code, 
                                 Class<?> []parameterTypes,
                                 Annotation [][]parameterAnns,
                                 int index,
                                 int paramOffset,
                                 int paramLength,
                                 int resultOffset)
  {
    for (int i = 0; i < paramLength; i++) {
      if (i == resultOffset) {
        index++;
      }
      else {
        Class<?> paramType = parameterTypes[i + paramOffset];
        Annotation []anns = parameterAnns[i + paramOffset];
      
        index = boxVariable(code, index, paramType, anns);
      }
    }
  }
  
  private <T> T getAnnotation(Class<T> api, Annotation []anns)
  {
    if (anns == null) {
      return null;
    }
    
    for (Annotation ann : anns) {
      if (api.equals(ann.annotationType())) {
        return (T) ann;
      }
    }
    
    return null;
  }
  
  private int boxVariable(CodeWriterAttribute code, 
                          int index, 
                          Class<?> type,
                          Annotation []anns)
  {
    Service ampService = getAnnotation(Service.class, anns);
    
    if (type.equals(boolean.class)) {
      code.pushIntVar(index);
      code.invokestatic(Boolean.class, "valueOf", Boolean.class, boolean.class);
      
      return index + 1;
    }
    else if (type.equals(byte.class)) {
      code.pushIntVar(index);
      code.invokestatic(Byte.class, "valueOf", Byte.class, byte.class);
      
      return index + 1;
    }
    else if (type.equals(short.class)) {
      code.pushIntVar(index);
      code.invokestatic(Short.class, "valueOf", Short.class, short.class);
      
      return index + 1;
    }
    else if (type.equals(char.class)) {
      code.pushIntVar(index);
      code.invokestatic(Character.class, "valueOf", Character.class, char.class);
      
      return index + 1;
    }
    else if (type.equals(int.class)) {
      code.pushIntVar(index);
      code.invokestatic(Integer.class, "valueOf", Integer.class, int.class);
      
      return index + 1;
    }
    else if (type.equals(long.class)) {
      code.pushLongVar(index);
      code.invokestatic(Long.class, "valueOf", Long.class, long.class);
      
      return index + 2;
    }
    else if (type.equals(float.class)) {
      code.pushFloatVar(index);
      code.invokestatic(Float.class, "valueOf", Float.class, float.class);
      
      return index + 1;
    }
    else if (type.equals(double.class)) {
      code.pushDoubleVar(index);
      code.invokestatic(Double.class, "valueOf", Double.class, double.class);
      
      // code.invokespecial(Double.class, "<init>", void.class, double.class);
      
      return index + 2;
    }
    else if (ampService != null) {
      code.pushObjectVar(index);
      
      // XXX: should be Class
      code.pushConstantClass(type);
      
      code.pushObjectVar(0);
      code.getField(_jClass.getThisClass(),
                    "_inboxSystem",
                    InboxAmp.class);
      
      code.invokestatic(ProxyUtilsAmp.class,
                        "makeProxy",
                        Object.class,
                        Object.class,
                        Class.class,
                        InboxAmp.class);
      
      return index + 1;
    }
    else {
      code.pushObjectVar(index);
      
      return index + 1;
    }
  }
  
  private void popReturn(CodeWriterAttribute code, Class<?> retType)
  {
    if (retType.equals(void.class)) {
      code.addReturn();
    }
    else if (retType.equals(Object.class)) {
      code.addObjectReturn();
    }
    else if (retType.equals(boolean.class)) {
      code.invokestatic(ProxyUtilsAmp.class, 
                        "__caucho_toBoolean",
                        boolean.class,
                        Object.class);
      code.addIntReturn();
    }
    else if (retType.equals(byte.class)) {
      code.invokestatic(ProxyUtilsAmp.class, 
                        "__caucho_toByte",
                        byte.class,
                        Object.class);
      code.addIntReturn();
    }
    else if (retType.equals(short.class)) {
      code.invokestatic(ProxyUtilsAmp.class, 
                        "__caucho_toShort",
                        short.class,
                        Object.class);
      code.addIntReturn();
    }
    else if (retType.equals(char.class)) {
      code.invokestatic(ProxyUtilsAmp.class, 
                        "__caucho_toChar",
                        char.class,
                        Object.class);
      code.addIntReturn();
    }
    else if (retType.equals(int.class)) {
      code.invokestatic(ProxyUtilsAmp.class, 
                        "__caucho_toInt",
                        int.class,
                        Object.class);
      code.addIntReturn();
    }
    else if (retType.equals(long.class)) {
      code.invokestatic(ProxyUtilsAmp.class, 
                        "__caucho_toLong",
                        long.class,
                        Object.class);
      code.addLongReturn();
    }
    else if (retType.equals(float.class)) {
      code.invokestatic(ProxyUtilsAmp.class, 
                        "__caucho_toFloat",
                        float.class,
                        Object.class);
      code.addFloatReturn();
    }
    else if (retType.equals(double.class)) {
      code.invokestatic(ProxyUtilsAmp.class, 
                        "__caucho_toDouble",
                        double.class,
                        Object.class);
      code.addDoubleReturn();
    }
    else {
      code.cast(retType);
      
      code.addObjectReturn();
    }
  }
  
  private String getMethodFieldName(String methodName)
  {
    return "__caucho_ampMethod_" + methodName;
  }

  private String createDescriptor(Method method)
  {
    return createDescriptor(method.getReturnType(), method.getParameterTypes());
  }

  private String createDescriptor(Class<?> retType, Class<?> ...paramTypes)
  {
    StringBuilder sb = new StringBuilder();

    sb.append("(");

    if (paramTypes != null) {
      for (Class<?> param : paramTypes) {
        sb.append(createDescriptor(param));
      }
    }

    sb.append(")");
    sb.append(createDescriptor(retType));

    return sb.toString();
  }

  private String createDescriptor(Class<?> cl)
  {
    if (cl.isArray())
      return "[" + createDescriptor(cl.getComponentType());

    String primValue = _prim.get(cl);

    if (primValue != null)
      return primValue;

    return "L" + cl.getName().replace('.', '/') + ";";
  }

  private static HashMap<Class<?>,String> _prim
    = new HashMap<Class<?>,String>();

  static {
    _prim.put(boolean.class, "Z");
    _prim.put(byte.class, "B");
    _prim.put(char.class, "C");
    _prim.put(short.class, "S");
    _prim.put(int.class, "I");
    _prim.put(long.class, "J");
    _prim.put(float.class, "F");
    _prim.put(double.class, "D");
    _prim.put(void.class, "V");
  }

  static
  {
    try {
      _defaultTimeout = Integer.parseInt(System.getProperty("amp.timeout"));
    } catch (Throwable e) {
      
    }
  }

}
