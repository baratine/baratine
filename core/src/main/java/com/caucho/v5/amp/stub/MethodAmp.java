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
import java.lang.reflect.Method;
import java.util.ArrayList;

import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.StubContainerAmp;

import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.Result;
import io.baratine.service.ResultChain;
import io.baratine.stream.ResultStream;


/**
 * handle to an actor method.
 */
public interface MethodAmp
{
  String name();
  
  default boolean isClosed()
  {
    return false;
  }
  
  default boolean isValid()
  {
    return true;
  }
  
  default boolean isDirect()
  {
    return false;
  }

  default boolean isModify()
  {
    return false;
  }
  
  default Annotation[] getAnnotations()
  {
    return null;
  }
  
  default Class<?> declaringClass()
  {
    return Object.class;
  }
  
  default Class<?> getReturnType()
  {
    return Object.class;
  }

  default ParameterAmp []parameters()
  {
    return new ParameterAmp[0];
  }
  
  /*
  Class<?> []getParameterTypes();
  
  Type []getGenericParameterTypes();
  
  Annotation [][]getParameterAnnotations();
  */
  
  default boolean isVarArgs()
  {
    return false;
  }

  default void onActive(StubContainerAmp container)
  {
  }
  
  //
  // send methods
  //
  
  default void send(HeadersAmp headers,
                    StubAmp stub)
  {
    send(headers, stub, new Object[0]);
  }

  default void send(HeadersAmp headers,
                    StubAmp stub,
                    Object arg1)
  {
    send(headers, stub, new Object[] { arg1 });
  }

  default void send(HeadersAmp headers,
                    StubAmp stub,
                    Object arg1, 
                    Object arg2)
  {
    send(headers, stub, new Object[] { arg1, arg2 });
  }

  default void send(HeadersAmp headers,
                    StubAmp stub,
                    Object arg1,
                    Object arg2, 
                    Object arg3)
  {
    send(headers, stub, new Object[] { arg1, arg2, arg3 });
  }

  default void send(HeadersAmp headers,
                    StubAmp stub,
                    Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  //
  // query methods
  //
  
  default void query(HeadersAmp headers,
                     ResultChain<?> result,
                     StubAmp stub)
  {
    query(headers, result, stub, new Object[0]);
  }
  
  default void query(HeadersAmp headers,
                     ResultChain<?> result,
                     StubAmp stub,
                     Object arg1)
  {
    query(headers, result, stub, new Object[] { arg1 });
  }
  
  default void query(HeadersAmp headers,
                     ResultChain<?> result,
                     StubAmp stub,
                     Object arg1,
                     Object arg2)
  {
    query(headers, result, stub, new Object[] { arg1, arg2 });
  }
  
  default void query(HeadersAmp headers,
                     ResultChain<?> result,
                     StubAmp stub,
                     Object arg1,
                     Object arg2,
                     Object arg3)
  {
    query(headers, result, stub, new Object[] { arg1, arg2, arg3 });
  }
  
  default void query(HeadersAmp headers,
                     ResultChain<?> result,
                     StubAmp stub,
                     Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default Object shim(Object value)
  {
    return value;
  }
  
  //
  // map-reduce/stream methods
  //
  
  default <T> void stream(HeadersAmp headers,
                          ResultStream<T> result,
                          StubAmp stub,
                          Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  //
  // pipe methods
  //
  
  default <T> void outPipe(HeadersAmp headers,
                           ResultPipeOut<T> result,
                           StubAmp stub,
                           Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default <T> void inPipe(HeadersAmp headers,
                          ResultPipeIn<T> result,
                          StubAmp stub,
                          Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  static Class<?>[] paramTypes(Method targetMethod)
  {
    ArrayList<Class<?>> paramList = new ArrayList<>();
    
    for (Class<?> param : targetMethod.getParameterTypes()) {
      if (Result.class.equals(param)
          || ResultChain.class.equals(param)
          || ResultStream.class.equals(param)
          || ResultPipeIn.class.equals(param)
          || ResultPipeOut.class.equals(param)) {
        continue;
      }
      
      paramList.add(param);
    }
    
    Class<?> []paramArray = new Class<?>[paramList.size()];
    paramList.toArray(paramArray);
    
    return paramArray;
  }
}
