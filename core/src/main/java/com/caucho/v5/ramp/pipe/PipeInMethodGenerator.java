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

package com.caucho.v5.ramp.pipe;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.MethodOnInitGenerator;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.util.L10N;

import io.baratine.convert.Convert;
import io.baratine.pipe.Message;
import io.baratine.pipe.Pipe;
import io.baratine.pipe.PipeIn;
import io.baratine.pipe.Pipes;
import io.baratine.service.ResultChain;

/**
 * Provider for pipes.
 */
class PipeInMethodGenerator implements MethodOnInitGenerator
{
  private static final L10N L = new L10N(PipeInMethodGenerator.class);
  
  private static final Logger log
    = Logger.getLogger(PipeInMethodGenerator.class.getName());
  
  @Override
  public MethodAmp createMethod(Method method, 
                                Annotation ann,
                                ServicesAmp services)
  {
    PipeIn pipeIn = (PipeIn) ann;
    String path = pipeIn.value();

    if (path.isEmpty()) {
      path = "pipe:///" + method.getName();
    }

    Pipes<?> pipes = services.service(path).as(Pipes.class);
    
    Parameter []params = method.getParameters();
    
    if (params.length != 1) {
      throw new IllegalArgumentException(L.l("@{0} method {1}.{2} must have a single value",
                                             PipeIn.class.getSimpleName(),
                                             method.getDeclaringClass().getSimpleName(),
                                             method.getName()));
    }
    
    MessageConverter converter = new MessageConverter(String.class);
    
    return new PipeInMethod(pipes, method, converter);
  }

  private static class PipeInMethod<T> implements MethodAmp
  {
    private Pipes<T> _pipes;
    private Method _method;
    private MessageConverter<T> _converter;

    PipeInMethod(Pipes<T> pipes,
                 Method method,
                 MessageConverter<T> converter)
    {
      _pipes = pipes;
      _method = method;
      _converter = converter;
    }

    @Override
    public String name()
    {
      return "onInit";
    }

    @Override
    public void query(HeadersAmp headers, 
                      ResultChain<?> result, 
                      StubAmp stub,
                      Object[] args)
    {
      PipeArgValue<T> arg = new PipeArgValue<>(_converter);
      
      PipeSubscriber<T> sub
        = new PipeSubscriber<>(stub.bean(), _method, arg);

      _pipes.subscribe(Pipe.in(sub));

      result.ok(null);
    }
  }
  
  private static class MessageConverter<T> extends ClassValue<Convert<?,T>>
  {
    private Class<T> _target;
    
    MessageConverter(Class<T> target)
    {
      _target = target;
    }
    
    @Override
    protected Convert<?, T> computeValue(Class<?> type)
    {
      return x->(T) x;
    }
    
  }
  
  private interface PipeArg
  {
    Object arg(Object message);
  }
  
  private class PipeArgNull implements PipeArg
  {
    @Override
    public Object arg(Object message)
    {
      return null;
    }
  }
  
  private static class PipeArgValue<T> implements PipeArg
  {
    private ClassValue<Convert<?,T>> _converterMap;
    
    PipeArgValue(ClassValue<Convert<?,T>> converterMap)
    {
      _converterMap = converterMap;
    }
    
    @Override
    public T arg(Object message)
    {
      if (message == null) {
        return null;
      }
      else if (message instanceof Message) {
        Message<?> msg = (Message<?>) message;
        
        message = msg.value();
        
        if (message == null) {
          return null;
        }
      }
      
      Convert converter = _converterMap.get(message.getClass());
      
      return (T) converter.convert(message);
    }
  }

  private static class PipeSubscriber<T> implements Pipe<T>
  {
    private Object _bean;
    private Method _method;
    private PipeArg _arg;

    PipeSubscriber(Object bean,
                   Method method,
                   PipeArg arg)
    {
      _bean = bean;
      _method = method;
      _arg = arg;
    }

    @Override
    public void next(T message)
    {
      try {
        _method.invoke(_bean, _arg.arg(message));
      } catch (Exception e) {
        String loc = _bean.getClass().getSimpleName() + "." + _method.getName();

        log.log(Level.FINER, loc + ": " + e, e); ;
      }
    }

    @Override
    public void close()
    {

    }

    @Override
    public void fail(Throwable exn)
    {
      exn.printStackTrace();
    }
  }
}
