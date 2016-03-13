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

import io.baratine.service.MethodRef;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.spi.Headers;
import io.baratine.stream.ResultStream;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;

/**
 * handle to an actor method.
 */
public interface MethodRefAmp extends MethodRef
{
  @Override
  ServiceRefAmp getService();
  
  boolean isUp();
  
  boolean isClosed();
  
  InboxAmp getInbox();

  MethodAmp method();
  
  void offer(MessageAmp message);

  Class<?> []getParameterClasses();

  ActorAmp getActor(ActorAmp actorDeliver);
  
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
  
  default MethodRefAmp getActive()
  {
    return this;
  }
  
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
  Type []getParameterTypes();
  
  /**
   * Annotations for each method parameter.
   */
  Annotation [][]getParameterAnnotations();

  /**
   * True if the final argument is variable-length.
   */
  boolean isVarArgs();
  
}
