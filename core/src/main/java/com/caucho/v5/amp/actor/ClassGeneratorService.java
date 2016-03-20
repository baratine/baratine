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

package com.caucho.v5.amp.actor;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpException;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.proxy.ProxyUtilsAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.bytecode.JavaClass;
import com.caucho.v5.bytecode.JavaClassLoader;
import com.caucho.v5.bytecode.JavaMethod;
import com.caucho.v5.bytecode.attr.CodeWriterAttribute;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.ProxyClassLoader;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;
import io.baratine.service.Service;

/**
 * Generates a concrete service class.
 * 
 * Generates "getXXX" methods for transfer objects.
 */
public class ClassGeneratorService<T>
{
  private static final L10N L = new L10N(ClassGeneratorService.class);
  private static final Logger log 
    = Logger.getLogger(ClassGeneratorService.class.getName());
  
  private final Class<T> _type;
  private final ClassLoader _classLoader;
  
  private Class<T> _genClass;
  
  private HashMap<String,Method> _methodMap = new HashMap<>();
  private JavaClass _jClass;
  
  ClassGeneratorService(ServiceManagerAmp manager,
                        Class<T> type)
  {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(type);
    
    _type = type;
    _classLoader = manager.classLoader();
    
    if (! Modifier.isAbstract(_type.getModifiers())) {
      throw new IllegalArgumentException(L.l("{0} is an invalid generated Service because it's not abstract",
                                             _type));
    }

    Constructor<?> zeroCtor = null;

    if (! type.isInterface()) {
      for (Constructor<?> ctorItem : type.getDeclaredConstructors()) {
        if (ctorItem.getParameterTypes().length == 0) {
          zeroCtor = ctorItem;
          break;
        }
      }

      if (zeroCtor == null) {
        throw new ConfigException(L.l("'{0}' does not have a zero-arg public or protected constructor.  Scope adapter components need a zero-arg constructor, e.g. @RequestScoped stored in @ApplicationScoped.",
                                      type.getName()));
      }
    }
  }
  
  public Class<T> generate()
  {
    generateClass(_type);
    
    return _genClass;
  }

  private void generateClass(Class<?> cl)
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
      
      thisClassName = typeClassName + "__Service";
      
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
      
      try {
        _genClass = (Class<T>) Class.forName(cleanName, false, loader);
      } catch (ClassNotFoundException e) {
        log.log(Level.ALL, e.toString(), e);
      }

      if (_genClass != null) {
        return;
      }
      
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
        superClassName = Object.class.getName().replace('.', '/');
        jClass.setSuperClass(superClassName);
      
        jClass.addInterface(_type.getName().replace('.', '/'));
      }
      else {
        superClassName = _type.getName().replace('.', '/');
        jClass.setSuperClass(superClassName);
      }
      
      jClass.addInterface(Serializable.class);
      
      introspectMethods(jClass);

      createConstructor(jClass, superClassName);
      
      ByteArrayOutputStream bos = new ByteArrayOutputStream();

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
        _genClass = (Class<T>) dynLoader.loadClass(cleanName, buffer);
      }
      else {
        ProxyClassLoader proxyLoader = new ProxyClassLoader(loader);

        _genClass = (Class<T>) proxyLoader.loadClass(cleanName, buffer);
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
  
  private void createConstructor(JavaClass jClass, 
                                 String superClassName)
  {

    JavaMethod ctor = jClass.createMethod("<init>", 
                                          void.class);

    ctor.setAccessFlags(Modifier.PUBLIC);

    CodeWriterAttribute code = ctor.createCodeWriter();
    code.setMaxLocals(4);
    code.setMaxStack(5);
    
    code.pushObjectVar(0);
    
    code.invokespecial(superClassName,
                       "<init>",
                       void.class);
    
    for (Method method : _methodMap.values()) {
      String methodName = method.getName();
      
      jClass.createField(getMethodFieldName(methodName),
                         TransferAsset.class)
            .setAccessFlags(Modifier.PRIVATE);
      
      Class<?> transferClass = transferClass(method);
      
      code.pushObjectVar(0);
      
      code.pushObjectVar(0);
      code.invoke(Object.class, "getClass", Class.class);
      
      //code.pushConstant(methodName);
      if (transferClass.equals(_type)) {
        code.pushConstantClass(jClass.getName());
      }
      else {
        code.pushConstantClass(transferClass);
      }
      
      code.invokestatic(TransferUtil.class,
                        "transferGet",
                        TransferAsset.class,
                        Class.class,
                        Class.class);

      code.putField(jClass.getThisClass(),
                    getMethodFieldName(methodName),
                    TransferAsset.class);
    }

    code.addReturn();
    code.close();
    
  }
  
  private Class<?> transferClass(Method method)
  {
    Parameter []params = method.getParameters();
    
    for (Parameter param : params) {
      if (param.getType().equals(Result.class)) {
        TypeRef typeRef = TypeRef.of(param.getParameterizedType());
        TypeRef resultRef = typeRef.to(Result.class).param(0);
        
        Class<?> transferClass = resultRef.rawClass();
        
        if (transferClass.equals(_type)) {
          return _type;
        }
        else if (transferClass.equals(Object.class)
                 || Modifier.isAbstract(transferClass.getModifiers())) {
          throw new IllegalStateException(L.l("'{0}' is an illegal transfer class for method '{1}'",
                                                 transferClass.getName(),
                                                 method.toString()));
        }
        
        return transferClass;
      }
    }
    
    throw new IllegalStateException(method.toString());
  }
  
  private void addMethod(Method method)
  {
    if (_methodMap.get(method.getName()) == null) {
      _methodMap.put(method.getName(), method);
    }
  }
  
  /**
   * void get(Result<DTO> result)
   */
  private void createAmpResultMethod(JavaClass jClass,
                                     Method method,
                                     int resultOffset)
  {
    String methodName = method.getName();
    Class<?> []parameterTypes = method.getParameterTypes();
    
    if (parameterTypes.length != 1) {
      throw new IllegalStateException();
    }

    addMethod(method);
    
    CodeWriterAttribute code = createMethodHeader(jClass, method);
    
    code.setMaxLocals(1 + 2 * parameterTypes.length);
    code.setMaxStack(10 + 2 * parameterTypes.length);

    code.pushObjectVar(getLength(parameterTypes, resultOffset) + 1);
    
    code.pushObjectVar(0);
    code.getField(jClass.getThisClass(),
                  getMethodFieldName(methodName),
                  TransferAsset.class);
     
    code.pushObjectVar(0);
    
    code.invoke(TransferAsset.class,
                "toTransfer",
                Object.class,
                Object.class);
    
    code.invokeInterface(Result.class,
                         "ok",
                         void.class,
                         Object.class);

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
