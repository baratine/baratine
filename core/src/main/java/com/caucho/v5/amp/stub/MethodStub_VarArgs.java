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

import io.baratine.service.Result;
import io.baratine.service.ServiceException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.util.L10N;

/**
 * Creates MPC skeletons and stubs.
 */
class MethodStub_VarArgs extends MethodStubBase
{
  private static final L10N L = new L10N(MethodStub_VarArgs.class);
  private static final Logger log
  = Logger.getLogger(MethodStub_VarArgs.class.getName());

  private final String _actorName;
  private final MethodHandle _methodHandle;
  
  private final Method _method;
  
  private StubAmp _actor;

  MethodStub_VarArgs(ServiceManagerAmp rampManager,
                         Method method)
     throws IllegalAccessException
  {
    super(method);
    
    _actorName = method.getDeclaringClass().getSimpleName();
    
    Class<?> []paramTypes = method.getParameterTypes();

    method.setAccessible(true);
    _method = method;
    
    MethodHandle mh = MethodHandles.lookup().unreflect(method);
    
    /*
    System.out.println("MH0: " + mh); 
    //mh = mh.asType(MethodType.genericMethodType(paramTypes.length + 1));

    mh = filterMethod(rampManager,
                      mh,
                      method);
    

    System.out.println("MH1: " + mh);
    
    //mh = mh.asVarargsCollector(paramTypes[paramTypes.length - 1]);
    System.out.println("MH2: " + mh);
    mh = mh.asSpreader(Object[].class, paramTypes.length);
    
    MethodType type = mh.type().changeReturnType(Object.class)
                               .changeParameterType(0, Object.class);
    System.out.println("MH3: " + mh);
    /*
    MethodType type = MethodType.methodType(Object.class, 
                                            Object.class,
                                            Object[].class);
                                            */
/*
    MethodType type = MethodType.methodType(Object.class, 
                                            Object.class,
                                            Object.class);

    mh = mh.asType(type);
    */

    _methodHandle = mh;
  }
  
  public StubAmp getActor()
  {
    return _actor;
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object []args)
  {
    Object bean = ((StubAmpBean) actor).bean();

    if (log.isLoggable(Level.FINEST)) {
      log.finest("amp-send " + name() + "[" + bean + "] " + toList(args));
    }

    try {
      Object value;

      value = _method.invoke(bean, args);
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
    Object bean = ((StubAmpBean) actor).bean();

    if (log.isLoggable(Level.FINEST)) {
      log.finest("amp-query " + name() + "[" + bean + "] " + toList(args)
          + "\n  " + result);
    }

    try {
      //System.out.println("ARGS: "+ Arrays.asList(args));
      //Object value = _methodHandle.invokeExact(bean, args);
      //String []tail = (String[]) args[0];
      
      //System.out.println("TARGS: "+ tail + " " + Arrays.asList(tail));
      //Object value = _methodHandle.invokeWithArguments(bean, args);
      
      Object value = _method.invoke(bean, args);
      
      ((Result) result).ok(value);
    } catch (ArrayIndexOutOfBoundsException e) {
      if (args.length != parameters().length) {
        throw new ServiceException(L.l("{0} in {1} called with invalid argument length ({2}).",
                                   name(), actor, args.length));
                                         
      }
      
      result.fail(e);
    } catch (InvocationTargetException e) {
      log.log(Level.FINER, e.toString(), e);
      
      result.fail(e.getCause());
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
      
      result.fail(e);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _actorName + "," + name() + "]";
  }
}
