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

import io.baratine.pipe.Pipe.FlowOut;
import io.baratine.pipe.Pipes.PipeOutBuilder;
import io.baratine.service.Result;
import io.baratine.service.ServiceException;


/**
 * {@code OutPipe} sends a sequence of values from a source to a sink.
 */
class PipesImpl<T>
{
  static class ResultPipeInHandlerImpl<T>
    implements ResultPipeIn<T>, Pipe<T>
  {
    private Pipe.InHandler<T> _handler;
    
    ResultPipeInHandlerImpl(Pipe.InHandler<T> handler)
    {
      Objects.requireNonNull(handler);
      
      _handler = handler;
    }
    
    @Override
    public Pipe<T> pipe()
    {
      return this;
    }

    @Override
    public void next(T value)
    {
      _handler.handle(value, null, false);
    }

    @Override
    public void close()
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
  
  static class PipeInResultImpl<T> implements Pipe<T>
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
    public void close()
    {
      _pipeIn.handle(null, null, true);
    }

    @Override
    public void fail(Throwable exn)
    {
      _pipeIn.handle(null, exn, false);
    }
  }
  
  static class PipeOutFlowImpl<T> implements FlowOut<T>
  {
    private ResultPipeOut<T> _result;
    
    PipeOutFlowImpl(ResultPipeOut<T> result)
    {
      Objects.requireNonNull(result);
      
      _result = result;
    }

    @Override
    public void ready(Pipe<T> pipe)
    {
      try {
        _result.handle(pipe, null);
      } catch (Exception e) {
        throw ServiceException.createAndRethrow(e);
      }
    }

    @Override
    public void fail(Throwable exn)
    {
      try {
        _result.handle((Pipe<T>) null, exn);
      } catch (Exception e) {
        throw ServiceException.createAndRethrow(e);
      }
    }
  }
  
  static class PipeOutResultImpl<T> implements PipeOutBuilder<T>
  {
    private Result<Pipe<T>> _result;
    private FlowOut<T> _flow;
    
    PipeOutResultImpl(FlowOut<T> flow)
    {
      Objects.requireNonNull(flow);
      
      _flow = flow;
    }
    
    PipeOutResultImpl(Result<Pipe<T>> result)
    {
      Objects.requireNonNull(result);
      
      _result = result;
    }

    @Override
    public FlowOut<T> flow()
    {
      return _flow;
    }
    
    @Override
    public void ok(Pipe<T> pipe)
    {
      if (_result != null) {
        _result.ok(pipe);
      }
    }
    
    @Override
    public void fail(Throwable exn)
    {
      if (_result != null) {
        _result.fail(exn);
      }
    }
    
    public void handle(Pipe<T> pipe, Throwable exn)
    {
      throw new IllegalStateException();
    }

    @Override
    public PipeOutBuilder<T> fail(Consumer<Throwable> exn)
    {
      return this;
    }
  }
  
  static class ResultPipeInImpl<T> implements ResultPipeIn<T>
  {
    private Pipe<T> _pipe;
    
    ResultPipeInImpl(Pipe<T> pipe)
    {
      Objects.requireNonNull(pipe);
      
      _pipe = pipe;
    }
    
    /**
     * The subscriber's {@code PipeIn} handler will be registered as
     * the pipe consumer.
     */
    @Override
    public Pipe<T> pipe()
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
