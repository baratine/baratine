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

import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionIllegalArgument;
import io.baratine.stream.ResultStream;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;

/**
 * Creates MPC skeletons and stubs.
 */
class SkeletonMethodResult_VarArgs extends SkeletonMethodResult_N
{
  private static final Logger log
    = Logger.getLogger(SkeletonMethodResult_VarArgs.class.getName());
  
  SkeletonMethodResult_VarArgs(Method method) throws IllegalAccessException
  {
    super(method);
  }
  
  @Override
  protected MethodHandle initMethodHandle(Method method)
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

    mh = mh.asFixedArity();
    mh = mh.asType(type);
    
    mh = MethodHandles.permuteArguments(mh, type, permute);
    
    mh = mh.asSpreader(Object[].class, paramTypes.length - 1);
    
    type = MethodType.methodType(void.class, 
                                 Object.class,
                                 Result.class,
                                 Object[].class);

    return mh.asType(type);
  }
  
  private int findResultOffset(Class<?> []paramTypes)
  {
    for (int i = 0; i < paramTypes.length; i++) {
      if (Result.class.equals(paramTypes[i])) {
        return i;
      }
      else if (ResultStream.class.equals(paramTypes[i])) {
        return i;
      }
    }
    
    throw new IllegalStateException();
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actor,
                   Object []args)
  {
    Object bean = actor.bean();

    if (log.isLoggable(Level.FINEST)) {
      log.finest("amp-send " + name() + "[" + bean + "] " + toList(args));
    }

    try {
      Result<?> result = null;
      
      //_methodHandle.invokeExact(bean, cmpl, args);
      methodHandle().invoke(bean, result, args);
    } catch (Throwable e) {
      log.log(Level.FINER, bean + ": " + e.toString(), e);
    }
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp actor,
                    Object []args)
  {
    Object bean = actor.bean();

    if (log.isLoggable(Level.FINEST)) {
      log.finest("amp-query " + name() + "[" + bean + "] " + toList(args)
          + "\n  " + result);
    }
    
    try {
      //_methodHandle.invokeExact(bean, result, args);
      methodHandle().invoke(bean, result, args);
    } catch (IllegalArgumentException e) {
      String msg = bean + "." + method().getName() + ": " + e.getMessage();

      log.log(Level.FINE, msg, e);
      
      RuntimeException exn = new ServiceExceptionIllegalArgument(msg, e);
      
      result.fail(exn);
    } catch (ArrayIndexOutOfBoundsException e) {
      if (args.length + 1 != method().getParameterTypes().length) {
        String msg = bean + "." + method().getName() + ": " + e.getMessage();

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
}
