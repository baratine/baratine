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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.MethodOnInitGenerator;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.inject.Injector.InjectorBuilder;
import io.baratine.inject.Key;
import io.baratine.pipe.Message;
import io.baratine.pipe.Pipe;
import io.baratine.pipe.PipeIn;
import io.baratine.pipe.Pipes;
import io.baratine.service.ResultChain;
import io.baratine.service.ServiceInitializer;
import io.baratine.service.Services.ServicesBuilder;

/**
 * Provider for pipes.
 */
public class ServiceInitPipe implements ServiceInitializer
{
  private static final Logger log
    = Logger.getLogger(ServiceInitPipe.class.getName());
  
  @Override
  public void init(ServicesBuilder builder)
  {
    SchemePipeImpl pipeScheme = new SchemePipeImpl("pipe:");
    
    builder.service(pipeScheme)
           .address("pipe:")
           .start();
    
    InjectorBuilder injector = builder.injector();
    
    
    injector.bean(PipeInGenerator.class)
            .to(Key.of(MethodOnInitGenerator.class,
                       PipeIn.class));

    // builder.in
    //pipeRef.start();
    
    //services.
  }
  
  private static class PipeInGenerator implements MethodOnInitGenerator
  {
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

      return new PipeInMethod(pipes, method);
    }
    
  }
  
  private static class PipeInMethod<T> implements MethodAmp
  {
    private Pipes<T> _pipes;
    private Method _method;
    
    PipeInMethod(Pipes<T> pipes,
                 Method method)
    {
      _pipes = pipes;
      _method = method;
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
      PipeSubscriber<T> sub = new PipeSubscriber<>(stub.bean(), _method);
      
      _pipes.subscribe(Pipe.in(sub));
      
      result.ok(null);
    }
  }
  
  private static class PipeSubscriber<T> implements Pipe<T>
  {
    private Object _bean;
    private Method _method;
    
    PipeSubscriber(Object bean, Method method)
    {
      _bean = bean;
      _method = method;
    }
    
    @Override
    public void next(T value)
    {
      try {
        Message<?> msg = (Message) value;
        
        _method.invoke(_bean, msg.value());
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
