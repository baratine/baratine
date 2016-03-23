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
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.spi.HeadersAmp;

import io.baratine.service.Result;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceExceptionIllegalArgument;
import io.baratine.stream.ResultStream;

/**
 * Stub for a method returning a Result.
 */
class MethodStubResult_N extends MethodStubBase
{
  private static final Logger log
  = Logger.getLogger(MethodStubResult_N.class.getName());

  private final String _name;
  private final Method _method;
  private final MethodHandle _methodHandle;
  
  private Type []_paramTypes;
  private Annotation [][]_paramAnns;

  private Class<?>[] _paramTypesCl;

  MethodStubResult_N(ServiceManagerAmp ampManager,
                     Method method)
    throws IllegalAccessException
  {
    super(method);
    
    _method = method;
    _name = method.getName();
    
    _methodHandle = initMethodHandle(ampManager, method);
  }
  
  protected Class<?> getResultClass()
  {
    return Result.class;
  }
  
  protected Method method()
  {
    return _method;
  }
  
  protected MethodHandle methodHandle()
  {
    return _methodHandle;
  }
  
  protected MethodHandle initMethodHandle(ServiceManagerAmp ampManager,
                                          Method method)
    throws IllegalAccessException
  {
    Class<?> []paramTypes = method.getParameterTypes();
    int paramLen = paramTypes.length;
    int resultOffset = findResultOffset(paramTypes);

    method.setAccessible(true);
    MethodHandle mh = MethodHandles.lookup().unreflect(method);
    
    int []permute = new int[paramLen + 1];
    
    permute[0] = 0;
    for (int i = 0; i < resultOffset; i++) {
      permute[i + 1] = i + 2;
    }
    for (int i = resultOffset + 1; i < paramLen; i++) {
      permute[i + 1] = i + 1;
    }
    permute[resultOffset + 1] = 1;
    
    MethodType type = MethodType.genericMethodType(paramLen + 1);
    type = type.changeReturnType(void.class);
    
    mh = mh.asType(type);
    
    mh = filterMethod(ampManager,
                      mh,
                      method);
     
    mh = MethodHandles.permuteArguments(mh, type, permute);
    
    /*
    if (paramLen > 0 && ! Object[].class.equals(paramTypes[paramLen - 1])) {
    }
    */
    mh = mh.asSpreader(Object[].class, paramLen - 1);
    
    type = MethodType.methodType(void.class, 
                                 Object.class,
                                 getResultClass(),
                                 Object[].class);

    return mh.asType(type);
  }
  
  private int findResultOffset(Class<?> []paramTypes)
  {
    Class<?> resultClass = getResultClass();
    
    for (int i = 0; i < paramTypes.length; i++) {
      if (resultClass.equals(paramTypes[i])) {
        return i;
      }
    }
    
    throw new IllegalStateException();
  }
  
  @Override
  public String name()
  {
    return _name;
  }
  
  @Override
  public Annotation []getAnnotations()
  {
    return _method.getAnnotations();
  }
  
  @Override
  public boolean isVarArgs()
  {
    return _method.isVarArgs();
  }
  
  @Override
  public Class<?> getReturnType()
  {
    return Object.class;
  }
  
  @Override
  public Type []getGenericParameterTypes()
  {
    if (_paramTypes == null) {
      Class<?> []paramClasses = _method.getParameterTypes(); 
      Type []methodParamTypes = _method.getGenericParameterTypes();
      
      ArrayList<Type> paramTypeList = new ArrayList<>();

      for (int i = 0; i < paramClasses.length; i++) {
        if (Result.class.isAssignableFrom(paramClasses[i])) {
          continue;
        }
        else if (ResultStream.class.isAssignableFrom(paramClasses[i])) {
          continue;
        }
        
        paramTypeList.add(methodParamTypes[i]);
      }
      
      Type[] paramTypes = new Type[paramTypeList.size()];
      paramTypeList.toArray(paramTypes);
      
      _paramTypes = paramTypes;
    }
    
    return _paramTypes;
  }
  
  @Override
  public Class<?> []getParameterTypes()
  {
    if (_paramTypesCl == null) {
      Class<?> []paramTypes = _method.getParameterTypes(); 
      
      ArrayList<Type> paramTypeList = new ArrayList<>();

      for (int i = 0; i < paramTypes.length; i++) {
        if (Result.class.isAssignableFrom(paramTypes[i])) {
          continue;
        }
        else if (ResultStream.class.isAssignableFrom(paramTypes[i])) {
          continue;
        }
        
        paramTypeList.add(paramTypes[i]);
      }
      
      Class<?>[] paramTypeArray = new Class<?>[paramTypeList.size()];
      paramTypeList.toArray(paramTypeArray);
      
      _paramTypesCl = paramTypeArray;
    }
    
    return _paramTypesCl;
  }
  
  @Override
  public Annotation [][]getParameterAnnotations()
  {
    if (_paramAnns == null) {
      Annotation [][]methodAnns = _method.getParameterAnnotations();
      
      Annotation [][]paramAnns = new Annotation[methodAnns.length - 1][];
      System.arraycopy(methodAnns, 0, paramAnns, 0, paramAnns.length);
      
      _paramAnns = paramAnns;
    }
    
    return _paramAnns;
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object []args)
  {
    Object bean = actor.bean();
    
    if (log.isLoggable(Level.FINEST)) {
      log.finest("amp-send " + _name + "[" + bean + "] " + toList(args));
    }

    try {
      Result<?> result = Result.ignore();
      
      //_methodHandle.invokeExact(bean, cmpl, args);
      _methodHandle.invoke(bean, result, args);
    } catch (Throwable e) {
      log.log(Level.FINER, bean + ": " + e.toString(), e);
    }
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    StubAmp actor,
                    Object []args)
  {
    Object bean = actor.bean();

    if (log.isLoggable(Level.FINEST)) {
      log.finest("amp-query " + _name + "[" + bean + "] " + toList(args)
          + "\n  " + result);
    }
    
    try {
      //_methodHandle.invokeExact(bean, result, args);
      _methodHandle.invoke(bean, result, args);
    } catch (ServiceException e) {
      result.fail(e);
    } catch (IllegalArgumentException e) {
      RuntimeException exn = new ServiceExceptionIllegalArgument(bean + "." + _name + ": " + e.getMessage(), e);
      exn.fillInStackTrace();
      
      result.fail(exn);
    } catch (ClassCastException e) {
      RuntimeException exn = new ServiceExceptionIllegalArgument(bean.getClass().getSimpleName() + "." + _name + ": " + e.getMessage(), e);
      exn.fillInStackTrace();
      
      result.fail(exn);
    } catch (ArrayIndexOutOfBoundsException e) {
      if (args.length + 1 != _method.getParameterTypes().length) {
        String msg = bean + "." + _method.getName() + ": " + e.getMessage() + " " + Arrays.asList(args);

        log.log(Level.FINE, msg, e);
        
        RuntimeException exn = new ServiceExceptionIllegalArgument(msg, e);
        
        result.fail(exn);
      }
      else {
        log.log(Level.FINEST, bean + ": " + e.toString(), e);
        
        result.fail(e);
      }
    } catch (Throwable e) {
      log.log(Level.FINEST, bean + ": " + e.toString(), e);

      result.fail(e);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _name + "]";
  }
}
