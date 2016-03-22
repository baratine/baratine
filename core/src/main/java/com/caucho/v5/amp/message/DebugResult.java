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

package com.caucho.v5.amp.message;

import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionTimeout;
import io.baratine.service.ServiceRef;

import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * Debug/timeout query result.
 */
public final class DebugResult<V> implements Result<V>
{
  private static final L10N L = new L10N(DebugResult.class);
  
  private final Result<V> _result;
  private final DebugQueryMap _queryMap;
  private final long _id;
  
  private final ServiceRef _serviceRef;
  private final MethodAmp _method;
  private final long _expireTime;
  
  private DebugResult<?> _next;
  private DebugResult<?> _prev;
  
  public DebugResult(Result<V> result,
                     ServiceRef serviceRef,
                     MethodAmp method,
                     DebugQueryMap queryMap,
                     long id)
  {
    _result = result;
    _queryMap = queryMap;
    _id = id;
    
    _serviceRef = serviceRef;
    _method = method;
    
    long timeout = 2 * 60 * 1000;
    _expireTime = timeout + CurrentTime.getCurrentTime();
  }
  
  @Override
  public void ok(V value)
  {
    _queryMap.removeQuery(this);
    
    _result.ok(value);
  }
  
  @Override
  public void fail(Throwable exn)
  {
    _queryMap.removeQuery(this);
    
    System.out.println("FAIL: " + _result + " " + exn);
    _result.fail(exn);
  }
  
  @Override
  public void handle(V value, Throwable exn)
  {
    _queryMap.removeQuery(this);
    
    if (exn != null) {
      _result.fail(exn);
    }
    else {
      _result.ok(value);
    }
  }

  public long getId()
  {
    return _id;
  }

  public void setNext(DebugResult<?> next)
  {
    _next = next;
  }

  public DebugResult<?> getNext()
  {
    return _next;
  }

  public void setPrev(DebugResult<?> prev)
  {
    _prev = prev;
  }

  public DebugResult<?> getPrev()
  {
    return _prev;
  }

  public void timeout(long now)
  {
    if (_expireTime < now) {
      ServiceExceptionTimeout exn
        = new ServiceExceptionTimeout(L.l("query timeout calling {0} in {1}",
                                          _method.name(),
                                          _serviceRef.address()));
      
      exn.fillInStackTrace();
      
      fail(exn);
    }
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _serviceRef.address()
            + "," + _method.name()
            + "," + _id + "]");
  }
}
