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
import java.util.function.Function;

import io.baratine.pipe.Credits.OnAvailable;
import io.baratine.pipe.Pipe.PipeInBuilder;
import io.baratine.pipe.Pipe.PipeOutBuilder;
import io.baratine.service.Result;


/**
 * {@code OutPipe} sends a sequence of values from a source to a sink.
 */
class PipeStatic<T>
{
  static class ResultPipeInHandlerImpl<T>
    implements ResultPipeIn<T>, Pipe<T>
  {
    private InHandler<T> _handler;
    
    ResultPipeInHandlerImpl(InHandler<T> handler)
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
  
  /*
  static class PipeOutFlowImpl<T> implements FlowOut<Pipe<T>>
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
  */
  
  static class PipeOutResultImpl<T> extends Result.Wrapper<Pipe<T>, Pipe<T>>
    implements PipeOutBuilder<T>
  {
    private Function<Pipe<T>,OnAvailable> _onOk;
    
    private Consumer<Throwable> _onFail;
    
    private OnAvailable _flow;
    
    PipeOutResultImpl(OnAvailable flow)
    {
      super(Result.ignore());
      
      Objects.requireNonNull(flow);
      
      _flow = flow;
    }
    
    PipeOutResultImpl(Function<Pipe<T>,OnAvailable> onOk)
    {
      super(Result.ignore());
      
      Objects.requireNonNull(onOk);
      
      _onOk = onOk;
    }
    
    PipeOutResultImpl(Result<Pipe<T>> result)
    {
      super(result);
    }

    @Override
    public PipeOutResultImpl<T> flow(OnAvailable flow)
    {
      Objects.requireNonNull(flow);
      
      _flow = flow;
      
      return this;
    }

    @Override
    public PipeOutBuilder<T> fail(Consumer<Throwable> onFail)
    {
      Objects.requireNonNull(onFail);
      
      _onFail = onFail;
      
      return this;
    }
    
    @Override
    public void ok(Pipe<T> pipe)
    {
      if (_onOk != null) {
        OnAvailable flow = _onOk.apply(pipe);
        pipe.credits().onAvailable(flow);
      }
      
      delegate().ok(pipe);

      if (_flow != null) {
        pipe.credits().onAvailable(_flow);
      }

      /*
      if (_flow != null) {
        _flow.ready(pipe);
      }
      */
    }
    
    @Override
    public void fail(Throwable exn)
    {
      if (_flow != null) {
        _flow.fail(exn);
      }
      
      if (_onFail != null) {
        _onFail.accept(exn);
      }
      
      delegate().fail(exn);
    }
  }
  
  static class PipeInBuilderImpl<T> implements Pipe<T>
  {
    private final Consumer<T> _onNext;
    
    PipeInBuilderImpl(Consumer<T> onNext)
    {
      _onNext = onNext;
    }

    @Override
    public void next(T value)
    {
      _onNext.accept(value);
    }

    @Override
    public void close()
    {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void fail(Throwable exn)
    {
      // TODO Auto-generated method stub
      
    }
  }
  
  static class ResultPipeInImpl<T>
  implements ResultPipeIn<T>, PipeInBuilder<T>, Pipe<T>, OnAvailable
  {
    private final Pipe<T> _pipe;
    
    private PipeInBuilderImpl<T> _pipeBuilder;
    
    private Pipe<T> _builtPipe;
    
    private Credits _flowIn;
    
    private Credits _flowNext;
    
    private Long _credits;
    private Integer _prefetch;
    private Integer _capacity;
    
    private long _sequenceIn;
    
    ResultPipeInImpl(Pipe<T> pipe)
    {
      Objects.requireNonNull(pipe);
      
      _pipe = pipe;
    }
    
    ResultPipeInImpl(Consumer<T> onNext)
    {
      Objects.requireNonNull(onNext);
      
      _pipeBuilder = new PipeInBuilderImpl<>(onNext);
      _pipe = _pipeBuilder;
    }
    
    /**
     * The subscriber's {@code PipeIn} handler will be registered as
     * the pipe consumer.
     */
    @Override
    public Pipe<T> pipe()
    {
      if (_builtPipe != null) {
        return _builtPipe;
      }
      else {
        return _pipe;
      }
    }
    
    //
    // pipe filter methods
    //

    @Override
    public void next(T value)
    {
      _sequenceIn++;
      
      _pipe.next(value);
      
      updateCredits();
    }

    @Override
    public void close()
    {
      _pipe.close();
    }

    @Override
    public void fail(Throwable exn)
    {
      _pipe.fail(exn);
    }
    
    //
    // filter pipe init methods
    //
    
    @Override
    public void credits(Credits flow)
    {
      _flowIn = flow;
      
      if (_flowNext != null) {
        _flowIn.set(_flowNext.get());
      }
    }
    
    @Override
    public int prefetch()
    {
      if (_flowNext != null) {
        return PREFETCH_DISABLE;
      }
      else if (_prefetch != null) {
        return _prefetch;
      }
      else {
        return Pipe.PREFETCH_DEFAULT;
      }
    }
    
    @Override
    public long creditsInitial()
    {
      if (_flowNext != null) {
        return _flowNext.get();
      }
      else if (_credits != null) {
        return _credits;
      }
      else {
        return Pipe.CREDIT_DISABLE;
      }
    }
    
    //
    // builder methods
    //

    @Override
    public PipeInBuilder<T> credits(long credits)
    {
      _credits = credits;
      
      return this;
    }

    @Override
    public PipeInBuilder<T> prefetch(int prefetch)
    {
      _prefetch = prefetch;
      
      return this;
    }

    @Override
    public PipeInBuilder<T> capacity(int size)
    {
      _capacity = size;
      
      return this;
    }

    @Override
    public int capacity()
    {
      if (_capacity != null) {
        return _capacity;
      }
      else {
        return 0;
      }
    }

    @Override
    public ResultPipeIn<T> chain(Credits flowNext)
    {
      _flowNext = flowNext;
      
      _builtPipe = this;
      
      _flowNext.onAvailable(this);
      
      return this;
    }

    @Override
    public void available()
    {
      updateCredits();
    }
    
    private void updateCredits()
    {
      if (_flowNext != null) {
        int available = _flowNext.available();
      
        if (_flowIn != null) {
          _flowIn.set(_sequenceIn + available);
        }
      }
    }
    
    // 
    // illegal state methods

    @Override
    public void handle(T next, Throwable fail, boolean ok)
    {
      throw new IllegalStateException(getClass().getName());
    }

    @Override
    public void handle(Void value, Throwable fail)
    {
      throw new IllegalStateException();
    }

    @Override
    public PipeInBuilder<T> ok(Consumer<Void> onOkSubscription)
    {
      throw new IllegalStateException();
    }

    @Override
    public PipeInBuilder<T> fail(Consumer<Throwable> onFail)
    {
      throw new IllegalStateException();
    }

    @Override
    public PipeInBuilder<T> close(Runnable onClose)
    {
      throw new IllegalStateException();
    }
  }
}
