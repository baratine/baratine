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

package com.caucho.v5.amp.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.Result;
import io.baratine.spi.Headers;
import io.baratine.stream.ResultStream;

/**
 * handle to an actor method.
 */
public interface MethodRefAmp // extends MethodRef
{
  /**
   * Name of the method.
   */
  String getName();
  
  /**
   * Annotations on the method
   */
  Annotation []getAnnotations();
  
  /**
   * The return type of this method.
   */
  Type getReturnType();
  
  /** 
   * Types of the method arguments.
   */
  ParameterAmp []parameters();

  /**
   * True if the final argument is variable-length.
   */
  boolean isVarArgs();
  
  /**
   * The owning service.
   */
  ServiceRefAmp serviceRef();
  
  boolean isUp();
  
  boolean isClosed();
  
  InboxAmp inbox();

  MethodAmp method();
  
  void offer(MessageAmp message);
  
  default MethodRefAmp getActive()
  {
    return this;
  }
  

  //Class<?> []getParameterClasses();

  StubAmp stubActive(StubAmp actorDeliver);
  
  
  /**
   * Call a method without a result value.
   * 
   * @param args arguments for this method invocation
   */
  void send(Object ...args);

  /**
   * Invoke a method with a result.
   * 
   * @param result Callback that handles success and failure cases for the query operation.
   * @param args arguments for this method invocation
   */
  <T> void query(Result<T> result,
                 Object ...args);
  
  void send(Headers headers, Object ...args);
  
  <T> void query(Headers headers, Result<T> result, Object ...args);
  
  <T> void query(Headers headers, 
                 Result<T> result, 
                 long timeout, 
                 TimeUnit unit, 
                 Object ...args);

  <T> void stream(Headers headers,
                  ResultStream<T> result,
                  Object ...args);
}
