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

package com.caucho.v5.amp.service;

import io.baratine.service.Result;
import io.baratine.spi.Headers;
import io.baratine.stream.ResultStream;

import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.message.MessageWrapperClassLoader;
import com.caucho.v5.amp.spi.MessageAmp;

/**
 * Sender for an actor ref.
 */
abstract public class MethodRefWrapperClassLoader extends MethodRefWrapper
{
  abstract protected ClassLoader getDelegateClassLoader();

  @Override
  public void offer(MessageAmp msg)
  {
    ClassLoader loader = getDelegateClassLoader();
    
    MessageAmp msgLoader = new MessageWrapperClassLoader(msg, loader);
    
    getDelegate().offer(msgLoader);
  }
  
  @Override
  public void send(Headers headers, Object... args)
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(getDelegateClassLoader());
      
      getDelegate().send(headers, args);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  @Override
  public <T> void query(Headers headers,
                        Result<T> result,
                        Object... args)
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(getDelegateClassLoader());
      
      getDelegate().query(headers, result, args);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  @Override
  public <T> void query(Headers headers,
                        Result<T> cb, 
                        long timeout, TimeUnit timeUnit,
                        Object... args)
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(getDelegateClassLoader());
      
      getDelegate().query(headers, cb, timeout, timeUnit, args);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  @Override
  public <T> void stream(Headers headers,
                         ResultStream<T> result,
                         Object... args)
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(getDelegateClassLoader());

      getDelegate().stream(headers, result, args);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getDelegate() + "]";
  }
}
