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

package com.caucho.v5.amp.marshal;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.proxy.ProxyHandleAmp;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.deploy2.DeployHandle2;
import com.caucho.v5.http.pod.PodContainer;
import com.caucho.v5.http.pod.PodLoader;


/**
 * manages a module
 */
public class RampImport
{
  private static final Logger log
    = Logger.getLogger(RampImport.class.getName());
  
  private final static HashSet<Type> _constantClasses = new HashSet<>();
  private final static HashSet<String> _constantPackages = new HashSet<>();
  
  private final ClassLoader _sourceLoader;
  private final ClassLoader _targetLoader;

  private ServiceManagerAmp _sourceManager;
  //private ServiceManagerAmp _targetManager;
  
  private ConcurrentHashMap<ImportKey,ModuleMarshal> _marshalMap
    = new ConcurrentHashMap<>();
  
  private ConcurrentHashMap<Class<?>,ModuleMarshal> _marshalSourceMap
    = new ConcurrentHashMap<>();
  
  public RampImport(ClassLoader sourceLoader, ClassLoader targetLoader)
  {
    //_sourceManager = Amp.getContextManager(sourceLoader);
    //_targetManager = Amp.getContextManager(targetLoader);
    
    PodContainer podContainer = PodContainer.getCurrent();
    BartenderSystem bartender = BartenderSystem.current();
    
    PodBartender sourcePod = null;
    
    if (bartender != null && podContainer != null) {
      sourcePod = bartender.getLocalPod(sourceLoader);
      
      if (sourcePod != null) {
        String loaderName = "pods/" + sourcePod.name();
        
        DeployHandle2<PodLoader> handle = podContainer.getPodLoaderHandle(loaderName);
        
        PodLoader podLoader = handle.request();

        if (podLoader != null) {
          targetLoader = podLoader.buildClassLoader(targetLoader);
        }
      }
    }
    
    _sourceLoader = sourceLoader;
    _targetLoader = targetLoader;
  }

  public ClassLoader getTargetLoader()
  {
    return _targetLoader;
  }

  public ClassLoader getSourceLoader()
  {
    return _sourceLoader;
  }

  public ServiceManagerAmp getSourceManager()
  {
    if (_sourceManager == null) {
      _sourceManager = Amp.getContextManager(getSourceLoader());
    }
    
    return _sourceManager;
  }

  /**
   * Marshal from an array of source types to an array of dest types.
   */
  public ModuleMarshal[] marshalArgs(ParameterAmp [] sourceTypes)
  {
    if (sourceTypes == null) {
      return new ModuleMarshal[0];
    }
    
    ModuleMarshal[] marshal = new ModuleMarshal[sourceTypes.length];
    
    for (int i = 0; i < marshal.length; i++) {
      marshal[i] = marshal(sourceTypes[i].rawClass());
    }
    
    return marshal;
  }

  /**
   * Marshal from an array of source types to an array of dest types.
   */
  public ParameterAmp[] marshalParamTypes(ParameterAmp [] sourceTypes)
  {
    if (sourceTypes == null) {
      return null;
    }
    
    ParameterAmp[] paramTypes = new ParameterAmp[sourceTypes.length];
    
    for (int i = 0; i < paramTypes.length; i++) {
      paramTypes[i] = ParameterAmp.of(marshalType(sourceTypes[i].rawClass()));
    }
    
    return paramTypes;
  }

  /**
   * Marshal from an array of source types to an array of dest types.
   */
  public Class<?> marshalType(Class<?> sourceType)
  {
    if (sourceType == null) {
      return null;
    }
    
    Class<?> targetType = getTargetType(getTargetLoader(), sourceType);
      
    if (targetType != null) {
      return targetType;
    }
    else {
      return sourceType;
    }
  }

  Object convert(Object source)
  {
    if (source == null) {
      return null;
    }

    return marshal(source.getClass()).convert(source);
  }

  /**
   * Create marshal to a target argument.
   */
  ModuleMarshal marshal(Class<?> sourceType)
  {
    ModuleMarshal marshal = _marshalSourceMap.get(sourceType);
    
    if (marshal == null) {
      marshal = marshalImpl(sourceType);
      _marshalSourceMap.put(sourceType, marshal);
    }
    
    return marshal;
  }

  /**
   * Create marshal to a target argument.
   */
  private ModuleMarshal marshalImpl(Class<?> sourceType)
  {
    Class<?> targetType = getTargetType(_targetLoader, sourceType, Object.class);
    
    if (targetType == null) {
      if (ProxyHandleAmp.class.isAssignableFrom(sourceType)) {
        Class<?> targetProxy = getTargetProxy(sourceType);
        
        return new MarshalProxy(targetProxy);
      }
      
      //return MarshalIdentity.MARSHAL;
      targetType = Object.class;
    }
    
    return marshal(sourceType, targetType, targetType);
  }
  
  ModuleMarshal marshal(Class<?> sourceType, 
                        Class<?> declaredTargetType)
  {
    Objects.requireNonNull(sourceType);
    
    Class<?> targetType = getTargetType(_targetLoader, 
                                        sourceType,
                                        declaredTargetType);
    
    if (targetType == null) {
      if (ProxyHandleAmp.class.isAssignableFrom(sourceType)) {
        Class<?> targetProxy = getTargetProxy(sourceType);
        
        if (targetProxy != null) {
          return new MarshalProxy(targetProxy);
        }
        else {
          return new MarshalProxy(declaredTargetType);
        }
        
      }
      
      //return MarshalIdentity.MARSHAL;
      //sourceType = Object.class;
    }
    
    if (targetType == null || ! declaredTargetType.isAssignableFrom(targetType)) {
      targetType = declaredTargetType;
    }
    
    return marshal(sourceType, targetType, declaredTargetType);
  }

  /**
   * Returns the marshal to convert from the sourceType to the targetType.
   */
  ModuleMarshal marshal(Class<?> sourceType,
                        Class<?> targetType,
                        Class<?> declaredTargetType)
  {
    ImportKey key = new ImportKey(sourceType, targetType);
    
    ModuleMarshal marshal = _marshalMap.get(key);
    
    if (marshal != null) {
      return marshal;
    }
    
    marshal = marshalImpl(sourceType, targetType, declaredTargetType);
    
    _marshalMap.putIfAbsent(key, marshal);
    
    return marshal;
  }
  
  void addMarshalRef(Class<?> sourceType,
                     Class<?> targetType,
                     ModuleMarshal marshal)
  {
    _marshalMap.putIfAbsent(new ImportKey(sourceType, targetType), marshal);
  }
  
  private ModuleMarshal marshalImpl(final Class<?> sourceType,
                                    final Class<?> targetType,
                                    final Class<?> declaredTargetType)
  {
    if (Class.class.equals(sourceType)) {
      return new MarshalClass();
    }
    
    if (_constantClasses.contains(sourceType)) {
      return MarshalIdentity.MARSHAL;
    }
    
    String sourceClassName = sourceType.getName();
    if (sourceType.getName().startsWith("java")) {
      int p = sourceClassName.lastIndexOf('.');
      String packageName = sourceClassName.substring(0, p);
      
      if (_constantPackages.contains(packageName)) {
        return MarshalIdentity.MARSHAL;
      }
    }
    
    Method writeReplace = introspectWriteReplace(sourceType);
    
    if (writeReplace != null) {
      return new MarshalBeanReplace(this, sourceType, declaredTargetType, 
                                    writeReplace);
      
    }
    
    if (targetType == null || 
        targetType.equals(Object.class) && ! Object.class.equals(declaredTargetType)) {
      if (Class.class.equals(sourceType)) {
        return new MarshalClass();
      }
      else if (declaredTargetType == null) {
        return new MarshalObject(this);
      }
      else {
        return new MarshalBean(this, sourceType, declaredTargetType);
      }
      
      // targetType = Object.class;
    }
    
    if (targetType.isArray()) {
      if (targetType.getComponentType().isPrimitive()) {
        return MarshalIdentity.MARSHAL;
      }
      
      return new MarshalArray(this, sourceType, targetType);
    }
    
    /*
    if (sourceType.equals(targetType)
        && Modifier.isFinal(sourceType.getModifiers())) {
      return MarshalIdentity.MARSHAL;
    }
    */
    
    if (targetType.equals(Object.class)) {
      return new MarshalObject(this);
    }

    if (Map.class.isAssignableFrom(sourceType)) {
      return new MarshalMap(this, sourceType, targetType);
    }
    
    if ((targetType == int.class || targetType == Integer.class)
        && (Number.class.isAssignableFrom(sourceType) || sourceType.isPrimitive())) {
      return MarshalInt.MARSHAL;
    }
    
    if (targetType.isPrimitive()) {
      return MarshalIdentity.MARSHAL;
    }
    
    if (targetType.isEnum()) {
      return new MarshalEnum(this, sourceType, targetType);
    }
    
    if (ServiceRefAmp.class.isAssignableFrom(sourceType)) {
      return new MarshalServiceRef(targetType);
    }
    
    if (ServiceManagerAmp.class.isAssignableFrom(sourceType)) {
      return MarshalIdentity.MARSHAL;
    }

    if (targetType.isInterface()
        || Modifier.isAbstract(targetType.getModifiers())) {
      return new MarshalInterface(this, sourceType, targetType);
    }
    else {
      return new MarshalBean(this, sourceType, targetType);
    }
  }
  
  private Class<?> getTargetProxy(Class<?> sourceClass)
  {
    Class<?> superClass = sourceClass.getSuperclass();
    
    for (Class<?> ifClass : sourceClass.getInterfaces()) {
      if (Serializable.class.equals(ifClass)) {
        continue;
      }
      else if (ProxyHandleAmp.class.equals(ifClass)) {
        continue;
      }
      
      Class<?> targetClass = getTargetType(_sourceLoader, ifClass);
      
      if (targetClass != null) {
        return targetClass;
      }
    }
    
    return null;
  }

  private Class<?> getTargetType(ClassLoader loader, Class<?> srcClass)
  {
    if (srcClass == null) {
      return null;
    }
    else if (srcClass.isPrimitive()) {
      return srcClass;
    }
    else if (srcClass.getClassLoader() == null) {
      return srcClass;
    }
    else if (srcClass.getClassLoader() == getClass().getClassLoader()) {
      return srcClass;
    }
    else if (srcClass.isArray()) {
      Class<?> srcCompClass = srcClass.getComponentType();
      
      Class<?> targetCompClass = getTargetType(loader, srcCompClass);
      
      if (targetCompClass == null) {
        return srcClass;
      }
      
      return Array.newInstance(targetCompClass, 0).getClass();
    }
    
    try {
      return Class.forName(srcClass.getName(), false, loader);
    } catch (Exception e) {
      log.finer(e.toString());
      
      return null;
    }
  }
  
  private Method introspectWriteReplace(Class<?> cl)
  {
    if (cl == null) {
      return null;
    }
    
    for (Method method : cl.getDeclaredMethods()) {
      if (method.getName().equals("writeReplace")
          && method.getParameterTypes().length == 0
          && ! void.class.equals(method.getReturnType())) {
         return method;
      }
    }
    
    return introspectWriteReplace(cl.getSuperclass());
  }

  private Class<?> getTargetType(ClassLoader loader,
                                 Class<?> type,
                                 Class<?> declaredTargetType)
  {
    if (type == null) {
      return null;
    }
    else if (type.isArray()) {
      if (type.getComponentType().isPrimitive()) {
        return type;
      }
      
      Class<?> eltType = getTargetType(loader, 
                                       type.getComponentType(),
                                       declaredTargetType.getComponentType());

      if (eltType == null) {
        eltType = declaredTargetType;
      }
      
      Object v = Array.newInstance(eltType, 0);
      
      return v.getClass();
    }
    else {
      return getTargetType(loader, type);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[ex:" + _targetLoader + ",im:" + _sourceLoader + "]";
  }
  
  static class MarshalInt implements ModuleMarshal {
    private static final MarshalInt MARSHAL = new MarshalInt();

    @Override
    public Object convert(Object value)
    {
      if (value == null) {
        return null;
      }

      Number number = (Number) value;
      
      return number.intValue();
    }

    /* (non-Javadoc)
     * @see com.caucho.ramp.module.ModuleMarshal#isValue()
     */
    @Override
    public boolean isValue()
    {
      // TODO Auto-generated method stub
      return false;
    };
  }
  
  static class MarshalClass implements ModuleMarshal {
    @Override
    public Object convert(Object value)
    {
      if (value == null) {
        return null;
      }

      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      
      try {
        Class<?> sourceValue = (Class) value;
        
        // XXX: arrays/inner classes
        Class<?> targetValue = Class.forName(sourceValue.getName(), false, loader);
        
        return targetValue;
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }

    /* (non-Javadoc)
     * @see com.caucho.ramp.module.ModuleMarshal#isValue()
     */
    @Override
    public boolean isValue()
    {
      // TODO Auto-generated method stub
      return false;
    };
  }
  
  class MarshalProxy implements ModuleMarshal {
    private final Class<?> _targetClass;

    MarshalProxy(Class<?> targetClass)
    {
      _targetClass = targetClass;
      
      Objects.requireNonNull(targetClass);
    }

    @Override
    public Object convert(Object value)
    {
      if (value == null) {
        return null;
      }
      
      ProxyHandleAmp handle = (ProxyHandleAmp) value;
      
      ServiceRefAmp serviceRef = handle.__caucho_getServiceRef();
      
      ClassLoader loader = serviceRef.getDelegateClassLoader();
      
      if (loader == _targetLoader) {
      }
      else if (serviceRef instanceof ImportAware) {
        ImportAware serviceRefImport = (ImportAware) serviceRef;
        
        serviceRef = serviceRefImport.export(_targetLoader);
      }
      else {
        PodImport rampImport;
        
        rampImport = PodImportContext.create(_targetLoader).getPodImport(loader);
        
        ServiceRefImport serviceRefImport;
        
        serviceRefImport = new ServiceRefImport(serviceRef, rampImport);
        
        serviceRef = serviceRefImport;
      }
      
      Object targetValue = serviceRef.as(_targetClass);
      
      return targetValue;
    }

    @Override
    public boolean isValue()
    {
      return false;
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _targetClass.getName() + "]";
    }
  }
  
  class MarshalServiceRef implements ModuleMarshal {
    private Class<?> _targetType;
    
    MarshalServiceRef(Class<?> targetType)
    {
      _targetType = targetType;
    }
    
    @Override
    public Object convert(Object value)
    {
      if (value == null) {
        return null;
      }
      
      ServiceRefAmp serviceRef = (ServiceRefAmp) value;
      
      ClassLoader loader = serviceRef.getDelegateClassLoader();
      
      if (loader == _targetLoader) {
        
      }
      else if (serviceRef instanceof ImportAware) {
        ImportAware serviceRefImport = (ImportAware) serviceRef;
        
        serviceRef = serviceRefImport.export(_targetLoader);
      }
      else {
        PodImport rampImport;
        
        rampImport = PodImportContext.create(_targetLoader).getPodImport(loader);
        
        ServiceRefImport serviceRefImport;
        
        serviceRefImport = new ServiceRefImport(serviceRef, rampImport);
        
        serviceRef = serviceRefImport;
      }
      
      if (_targetType.isAssignableFrom(serviceRef.getClass())) {
        return serviceRef;
      }
      else {
        return serviceRef.as(_targetType);
      }
    }

    @Override
    public boolean isValue()
    {
      return false;
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[]";
    }
  }
  
  private static class ImportKey {
    private Class<?> _sourceClass;
    private Class<?> _targetClass;
    
    ImportKey(Class<?> sourceClass, Class targetClass)
    {
      _sourceClass = sourceClass;
      _targetClass = targetClass;
    }
    
    @Override
    public int hashCode()
    {
      int hash = _sourceClass.hashCode();
      
      hash = 65521 * hash + _targetClass.hashCode();
      
      return hash;
    }
    
    @Override
    public boolean equals(Object o)
    {
      if (! (o instanceof ImportKey)) {
        return false;
      }
      
      ImportKey key = (ImportKey) o;
      
      return (_sourceClass.equals(key._sourceClass)
              && _targetClass.equals(key._targetClass));
    }
  }
  
  static {
    _constantClasses.add(java.util.Date.class);
    _constantClasses.add(java.sql.Date.class);
    _constantClasses.add(String.class);
    
    _constantPackages.add("java.time");
    _constantPackages.add("java.util.regex");
    _constantPackages.add("java.util.jar");
    _constantPackages.add("java.util.zip");
  }
}
