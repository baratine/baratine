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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;

/**
 * Creates MPC skeletons and stubs.
 */
class SkeletonMethod_1 extends SkeletonMethodBase {
  private static final Logger log
  = Logger.getLogger(SkeletonMethod_1.class.getName());

  private final MethodHandle _methodHandle;

  SkeletonMethod_1(ServiceManagerAmp rampManager,
                   Method method)
    throws IllegalAccessException
  {
    super(method);
    
    method.setAccessible(true);
    MethodHandle mh = MethodHandles.lookup().unreflect(method);

    // System.out.println("UNREF: " + mh + " " + mh.type());

    MethodType queryType = MethodType.methodType(Object.class, Object.class,
                                                 Object.class);
    mh = mh.asType(queryType);

    mh = filterMethod(rampManager,
                      mh,
                      method);

    Objects.requireNonNull(mh);
    
    _methodHandle = mh;
    
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actor,
                   Object arg1)
  {
    Object bean = actor.bean();

    if (log.isLoggable(Level.FINEST)) {
      log.finer("amp-send " + name() + "[" + bean + "] (" + arg1 + ")");
    }

    try {
      Object result = _methodHandle.invokeExact(bean, arg1);
    } catch (Throwable e) {
      log.log(Level.FINER, bean + ": " + e.toString(), e);
    }
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actor)
  {
    send(headers, actor, null);
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actor,
                   Object arg1,
                   Object arg2)
  {
    send(headers, actor, arg1);
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actor,
                   Object arg1,
                   Object arg2,
                   Object arg3)
  {
    send(headers, actor, arg1);
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actor,
                   Object []args)
  {
    int len = args != null ? args.length : 0;
    
    switch (len) {
    case 0:
      send(headers, actor, (Object) null);
      break;
    case 1:
      send(headers, actor, args[0]);
      break;
    default:
      send(headers, actor, args[0]);
      break;
    }
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp workerActor,
                    Object arg1)
  {
    Object bean = workerActor.bean();

    if (log.isLoggable(Level.FINEST)) {
      log.finest("amp-query " + name() + "[" + bean + "] (" + arg1 + ")"
          + "\n  " + result);
    }

    try {
      Object value = _methodHandle.invokeExact(bean, arg1);

      ((Result) result).ok(value);
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);

      result.fail(e);
    }
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp actor)
  {
    query(headers, result, actor, null);
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp actor,
                    Object arg1,
                    Object arg2)
  {
    query(headers, result, actor, arg1);
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp actor,
                    Object arg1,
                    Object arg2,
                    Object arg3)
  {
    query(headers, result, actor, arg1);
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp actor,
                    Object []args)
  {
    int len = args != null ? args.length : 0;
    
    switch (len) {
    case 0:
      query(headers, result, actor, (Object) null);
      break;
    case 1:
      query(headers, result, actor, args[0]);
      break;
    default:
      query(headers, result, actor, args[0]);
      break;
    }
  }
}
