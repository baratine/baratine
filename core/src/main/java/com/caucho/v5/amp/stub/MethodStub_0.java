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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.HeadersAmp;

import io.baratine.service.Result;
import io.baratine.service.ResultChain;

/**
 * Creates MPC skeletons and stubs.
 */
class MethodStub_0 extends MethodStubBase {
  private static final Logger log
  = Logger.getLogger(MethodStub_0.class.getName());

  private final MethodHandle _queryMethodHandle;
  private final MethodHandle _sendMethodHandle;

  MethodStub_0(ServicesAmp rampManager,
                   Method method)
    throws IllegalAccessException
  {
    super(method);
    
    method.setAccessible(true);
    //_method = method;
    MethodHandle mh = MethodHandles.lookup().unreflect(method);
    
    mh = mh.asType(MethodType.genericMethodType(1));

    mh = filterMethod(rampManager,
                      mh,
                      method);

    MethodType sendType = MethodType.methodType(void.class, Object.class);
    _sendMethodHandle = mh.asType(sendType);

    MethodType queryType = MethodType.methodType(Object.class, Object.class);
    _queryMethodHandle = mh.asType(queryType);
  }
  
  /*
  @Override
  public RampActor getActor()
  {
    return _actor;
  }
  */

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor)
  {
    Object bean = actor.loadBean();
    
    if (log.isLoggable(Level.FINEST)) {
      log.finest("amp-send " + name() + "[" + bean + "] ()");
    }

    try {
      _sendMethodHandle.invokeExact(bean);
    } catch (Throwable e) {
      log.log(Level.FINER, bean + ": " + e.toString(), e);
    }
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object arg1)
  {
    send(headers, actor);
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object arg1,
                   Object arg2)
  {
    send(headers, actor);
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object arg1,
                   Object arg2,
                   Object arg3)
  {
    send(headers, actor);
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object []args)
  {
    send(headers, actor);
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor)
  {
    try {
      Object bean = actor.bean();

      if (log.isLoggable(Level.FINEST)) {
        log.finest("amp-query " + name() + "[" + bean + "] ()"
            + "\n  " + result);
      }

      Object value = _queryMethodHandle.invokeExact(bean);

      ((Result) result).ok(value);
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
      result.fail(e);
    }
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1)
  {
    query(headers, result, actor);
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1,
                    Object arg2)
  {
    query(headers, result, actor);
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1,
                    Object arg2,
                    Object arg3)
  {
    query(headers, result, actor);
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object []args)
  {
    query(headers, result, actor);
  }
}
