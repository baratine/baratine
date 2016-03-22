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

package com.caucho.v5.amp.vault;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpException;
import com.caucho.v5.amp.proxy.ProxyUtilsAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.bytecode.JavaClass;
import com.caucho.v5.bytecode.JavaClassLoader;
import com.caucho.v5.bytecode.JavaMethod;
import com.caucho.v5.bytecode.attr.CodeWriterAttribute;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.ProxyClassLoader;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.Vault;

/**
 * Generates a concrete Vault class.
 */
public class ClassGeneratorVault<T>
{
  private static final L10N L = new L10N(ClassGeneratorVault.class);
  private static final Logger log 
    = Logger.getLogger(ClassGeneratorVault.class.getName());
  
  private final Class<T> _type;
  private final ClassLoader _classLoader;
  
  private Class<T> _proxyClass;
  
  private HashMap<String,Method> _methodMap = new HashMap<>();
  private JavaClass _jClass;
  
  ClassGeneratorVault(Class<T> type,
                           ClassLoader loader)
  {
    Objects.requireNonNull(type);
    
    _type = type;
    _classLoader = loader;
    
    if (! Vault.class.isAssignableFrom(_type)) {
      throw new IllegalArgumentException(L.l("{0} is an invalid Resource",
                                             _type));
    }

    /*
    if (! api.isInterface()) {
      throw new IllegalStateException(L.l("invalid interface {0}", api));
    }
    */
    ArrayList<Class<?>> apiList = new ArrayList<>();
    
    Constructor<?> zeroCtor = null;

    if (! type.isInterface()) {
      for (Constructor<?> ctorItem : type.getDeclaredConstructors()) {
        if (ctorItem.getParameterTypes().length == 0) {
          zeroCtor = ctorItem;
          break;
        }
      }

      // String typeClassName = cl.getName().replace('.', '/');

      if (zeroCtor == null) {
        ArrayList<Class<?>> interfaces = getInterfaces(type);

        throw new ConfigException(L.l("'{0}' does not have a zero-arg public or protected constructor.  Scope adapter components need a zero-arg constructor, e.g. @RequestScoped stored in @ApplicationScoped.",
                                      type.getName()));
      }
      else {
        apiList.add(type);
      }
    }
    else {
      apiList.add(type);
    }
  }
  
  static Object create(Class<?> cl,
                       ClassLoader loader,
                       VaultDriver<?,?> driver)
  {
    try {
      Constructor<?> ctor = createImpl(cl, loader);
      
      return ctor.newInstance(driver);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      else {
        throw new RuntimeException(cause);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
    
    ClassGeneratorVault<?> generator
      = new ClassGeneratorVault<>(cl, loader);
    
    Class<?> proxyClass = generator.generate();
    
    return proxyClass.getConstructors()[0];
  }
  
  private Class<T> generate()
  {
    generateProxy(_type);
    
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
      
      thisClassName = typeClassName + "__Vault";
      
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

      if (_type.isInterface()) {
        // superClassName = AbstractAmpProxy.class.getName().replace('.', '/');
        superClassName = Object.class.getName().replace('.', '/');
        jClass.setSuperClass(superClassName);
      
        jClass.addInterface(_type.getName().replace('.', '/'));
      }
      else {
        // superClassName = AbstractAmpProxy.class.getName().replace('.', '/');
        superClassName = _type.getName().replace('.', '/');
        jClass.setSuperClass(superClassName);
      }
      
      //jClass.addInterface(ProxyHandleAmp.class.getName().replace('.', '/'));
      jClass.addInterface(Serializable.class);
      
      /*
      jClass.createField("_serviceRef", ServiceRefAmp.class)
            .setAccessFlags(Modifier.PRIVATE|Modifier.FINAL);
      jClass.createField("_inboxSystem", InboxAmp.class)
            .setAccessFlags(Modifier.PRIVATE|Modifier.FINAL);
      jClass.createField("_messageFactory", MessageFactory.class)
            .setAccessFlags(Modifier.PRIVATE|Modifier.FINAL);
            */
      
      introspectMethods(jClass);

      createConstructor(jClass, superClassName);
      
      //createToString(jClass);
      //createHashCode(jClass);
      //createEquals(jClass);
      
      // createWriteReplace(jClass);
      
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
  
  private void introspectMethods(JavaClass jClass)
  {
    for (Method method : getMethods()) {
      Class<?> []paramTypes = method.getParameterTypes();
      int paramLen = paramTypes.length;

      if (! Modifier.isAbstract(method.getModifiers())) {
        continue;
      }
      
      if (Modifier.isStatic(method.getModifiers())) {
        continue;
      }
      
      if (Modifier.isFinal(method.getModifiers())) {
        continue;
      }
      
      /*
      if (! Modifier.isPublic(method.getModifiers())) {
        throw new IllegalArgumentException("Method must be public {0}", method);
      }
      */
      
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
      
      // XXX: Need QA
      
      
      int ampResult = findAmpResult(paramTypes, Result.class);

      if (ampResult < 0) {
        throw new IllegalStateException(L.l("Result argument is required {0}",
                                            method));
      }
      
      if (! void.class.equals(method.getReturnType())) {
        throw new IllegalArgumentException(L.l("method must return void {0}",
                                               method));
      }
      
      if (paramLen > 0 
          && ampResult >= 0) {
        /*
        if (ampResult != paramTypes.length - 1) {
          throw new IllegalStateException(L.l("invalid Result position. Result must be the final argument."));
        }
        */
        
        createAmpResultMethod(jClass, method, ampResult);
      }
    }
  }
  
  private ArrayList<Method> getMethods()
  {
    ArrayList<Method> methods = new ArrayList<>();
    
    for (Method method : _type.getDeclaredMethods()) {
      if (methods.contains(method)) {
        continue;
      }
      else if (! Modifier.isAbstract(method.getModifiers())) {
        continue;
      }

      methods.add(method);
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
  
  private void createConstructor(JavaClass jClass, 
                                 String superClassName)
  {

    JavaMethod ctor = jClass.createMethod("<init>", 
                                          void.class,
                                          VaultDriver.class);

    ctor.setAccessFlags(Modifier.PUBLIC);

    CodeWriterAttribute code = ctor.createCodeWriter();
    code.setMaxLocals(4);
    code.setMaxStack(5);
    
    code.pushObjectVar(0);
    
    code.invokespecial(superClassName,
                       "<init>",
                       void.class);

    //code.pushObjectVar(0);
    //code.pushObjectVar(1);
    
    for (Method method : _methodMap.values()) {
      String methodName = method.getName();
      
      jClass.createField(getMethodFieldName(methodName),
                         MethodVault.class)
            .setAccessFlags(Modifier.PRIVATE);
      
      code.pushObjectVar(0);
      
      code.pushObjectVar(1);
      
      code.pushObjectVar(0);
      code.invoke(Object.class, "getClass", Class.class);
      
      code.pushConstant(methodName);
      
      code.invokeInterface(VaultDriver.class,
                           "newMethod",
                           MethodVault.class,
                           Class.class,
                           String.class);

      code.putField(jClass.getThisClass(),
                    getMethodFieldName(methodName),
                    MethodVault.class);
    }
    
    //code.pushObjectVar(0);
    
    code.addReturn();
    code.close();
    
  }
  
  /*
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
  */
  
  private void addMethod(Method method)
  {
    if (_methodMap.get(method.getName()) == null) {
      _methodMap.put(method.getName(), method);
    }
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
                  getMethodFieldName(methodName),
                  MethodVault.class);
    
    code.pushObjectVar(getLength(parameterTypes, resultOffset) + 1);
    
    int argLen = parameterTypes.length - 1;
    
    pushParameters(code, parameterTypes, parameterAnns, 
                   1, 
                   0, 
                   argLen + 1, // paramLength,
                   resultOffset);
      
    code.invokeInterface(MethodVault.class,
                         "invoke",
                         void.class,
                         Result.class,
                         Object[].class);

    code.addReturn();

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
  
  private void pushParameters(CodeWriterAttribute code, 
                              Class<?> []parameterTypes,
                              Annotation [][]parameterAnns,
                              int index,
                              int paramOffset,
                              int paramLength,
                              int paramSkip)
  {
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
  
  /*
  private void pushRawParameters(CodeWriterAttribute code, 
                                 Class<?> []parameterTypes,
                                 Annotation [][]parameterAnns)
  {
    pushRawParameters(code, parameterTypes, parameterAnns,
                      1, 0, parameterTypes.length);
  }
  */
  
  /*
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
  */
  
  /*
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
  */
  
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
  
  /*
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
  */
  
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
}
