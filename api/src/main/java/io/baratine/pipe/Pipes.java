/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package io.baratine.pipe;

import java.util.Objects;
import java.util.function.Consumer;


/**
 * {@code OutPipe} sends a sequence of values from a source to a sink.
 */
public class Pipes
{
  public static <T> ResultPipeOut<T> flow(PipeOut.Flow<T> flow)
  {
    return new PipeOutResultImpl<>(flow);
  }
  
  /*
  public static <T> PipeOutBuilder<T> out(Consumer<PipeOut<T>> ready)
  {
    throw new UnsupportedOperationException();
  }
  */
  
  public static <T> ResultPipeIn<T> in(PipeIn<T> pipe)
  {
    return new ResultPipeInImpl<>(pipe);
  }
  
  public static <T> PipeIn<T> in(PipeIn.InHandler<T> handler)
  {
    return new ResultPipeInHandlerImpl<>(handler);
  }
  
  public interface PipeOutBuilder<T> extends ResultPipeOut<T>
  {
    PipeOutBuilder<T> fail(Consumer<Throwable> exn);
  }
  
  public interface PipeInBuilder<T> extends ResultPipeIn<T>
  {
    PipeInBuilder<T> credit(int initialCredit);
    
    PipeInBuilder<T> prefetch(int prefetch);
    
    PipeInBuilder<T> capacity(int size);
    
    PipeInBuilder<T> pause();
  }
  
  private static class ResultPipeInHandlerImpl<T>
    implements ResultPipeIn<T>, PipeIn<T>
  {
    private PipeIn.InHandler<T> _handler;
    
    ResultPipeInHandlerImpl(PipeIn.InHandler<T> handler)
    {
      Objects.requireNonNull(handler);
      
      _handler = handler;
    }
    
    @Override
    public PipeIn<T> pipe()
    {
      return this;
    }

    @Override
    public void next(T value)
    {
      _handler.handle(value, null, false);
    }

    @Override
    public void ok()
    {
      _handler.handle(null, null, true);
    }

    @Override
    public void fail(Throwable exn)
    {
      _handler.handle(null, exn, false);
    }

    @Override
    public void handle(T next, Throwable fail, boolean ok)
    {
      throw new IllegalStateException(getClass().getName());
    }
    
  }
  
  static class PipeInResultImpl<T> implements PipeIn<T>
  {
    private ResultPipeIn<T> _pipeIn;
    
    PipeInResultImpl(ResultPipeIn<T> pipeIn)
    {
      Objects.requireNonNull(pipeIn);
      
      _pipeIn = pipeIn;
    }

    @Override
    public void next(T value)
    {
      _pipeIn.handle(value, null, false);
    }

    @Override
    public void ok()
    {
      _pipeIn.handle(null, null, true);
    }

    @Override
    public void fail(Throwable exn)
    {
      _pipeIn.handle(null, exn, false);
    }
  }
  
  static class PipeOutFlowImpl<T> implements PipeOut.Flow<T>
  {
    private ResultPipeOut<T> _result;
    
    PipeOutFlowImpl(ResultPipeOut<T> result)
    {
      Objects.requireNonNull(result);
      
      _result = result;
    }

    @Override
    public void ready(PipeOut<T> pipe)
    {
      _result.handle(pipe, null);
    }

    @Override
    public void fail(Throwable exn)
    {
      _result.handle(null, exn);
    }
  }
  
  static class PipeOutResultImpl<T> implements ResultPipeOut<T>
  {
    private PipeOut.Flow<T> _flow;
    
    PipeOutResultImpl(PipeOut.Flow<T> flow)
    {
      Objects.requireNonNull(flow);
      
      _flow = flow;
    }

    @Override
    public PipeOut.Flow<T> flow()
    {
      return _flow;
    }
    
    @Override
    public void handle(PipeOut<T> pipe, Throwable exn)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }
  }
  
  static class ResultPipeInImpl<T> implements ResultPipeIn<T>
  {
    private PipeIn<T> _pipe;
    
    ResultPipeInImpl(PipeIn<T> pipe)
    {
      Objects.requireNonNull(pipe);
      
      _pipe = pipe;
    }
    
    /**
     * The subscriber's {@code PipeIn} handler will be registered as
     * the pipe consumer.
     */
    @Override
    public PipeIn<T> pipe()
    {
      return _pipe;
    }

    /**
     * Subscription lambda for basic clients.
     * 
     * Clients that need more control over the flow should use the pipe().
     */
    public void handle(T next, Throwable fail, boolean ok)
    {
      throw new IllegalStateException(getClass().getName());
    }
  }
}
