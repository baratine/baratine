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

package com.caucho.v5.amp;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Type;

import com.caucho.v5.amp.proxy.ProxyHandleAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.ServiceExceptionIllegalArgument;
import io.baratine.service.ServiceRef;

/**
 * Sender for an actor ref.
 */
public interface ServiceRefAmp extends ServiceRef
{
  static ServiceRefAmp current()
  {
    return (ServiceRefAmp) ServiceRef.current();
  }
  
  @Override
  String address();
  
  @Override
  ServicesAmp manager();
  
  boolean isUp();

  InboxAmp inbox();
  
  StubAmp stub();

  default ClassLoader classLoader()
  {
    return manager().classLoader();
  }
  
  void offer(MessageAmp message);
  
  boolean isPublic();
  
  MethodRefAmp method(String methodName, Type returnType, Class<?> ...params);
  
  MethodRefAmp methodByName(String methodName);
  
  MethodRefAmp methodByName(String methodName, Type returnType);
  
  AnnotatedType api();
  
  //@Override
  Iterable<? extends MethodRefAmp> getMethods();

  QueryRefAmp getQueryRef(long id);
  
  QueryRefAmp removeQueryRef(long id);

  @Override
  ServiceRefAmp pin(Object listener);

  ServiceRefAmp pin(Object listener, String address);
  
  // @Override
  ServiceRefAmp bind(String address);
  
  // creates new instance for scoping
  ServiceRefAmp lookup();
  
  //@Override
  ServiceRefAmp onLookup(String path);
  
  ServiceRefAmp start();
  
  @Override
  default ServiceRefAmp pinNode(int hash)
  {
    return this;
  }
  
  // @Override ServiceRefAmp unsubscribe(Object service);

  void shutdown(ShutdownModeAmp mode);
  void close();
  
  static ServiceRefAmp toRef(Object proxy)
  {
    return (ServiceRefAmp) ServiceRef.toRef(proxy);
  }
  
  static void requireService(Object listener)
  {
    if (listener instanceof ServiceRefAmp) {
    }
    else if (listener instanceof ProxyHandleAmp) {
    }
    else {
      throw new ServiceExceptionIllegalArgument(String.valueOf(listener));
    }
  }

}
