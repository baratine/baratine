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

package io.baratine.service;

import io.baratine.function.TriConsumer;
import io.baratine.service.Result.Fork;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation classes for Result.fork and Result.from.
 *
 */
class ResultImpl
{
  static final Logger log = Logger.getLogger(ResultImpl.class.getName());
  
  abstract static class ResultJoinBase
  {
    private final AtomicInteger _counter;
    
    ResultJoinBase(int count)
    {
      _counter = new AtomicInteger(count);
    }
    
    ResultJoinBase()
    {
      _counter = new AtomicInteger(1);
    }
    
    void addFork()
    {
      _counter.incrementAndGet();
    }
    
    void completeFork()
    {
      if (_counter.decrementAndGet() == 0) {
        complete();
      }
    }
    
    void failFork(Throwable exn)
    {
      logFail(exn);
      
      if (_counter.decrementAndGet() == 0) {
        complete();
      }
    }
    
    abstract protected void complete();
    
    private void logFail(Throwable exn)
    {
      Logger log = Logger.getLogger(Result.class.getName());

      if (log.isLoggable(Level.FINER)) {
        log.finer(exn.toString());
      }
      
      log.log(Level.FINEST, exn.toString(), exn);
    }
    
    abstract boolean isFuture();
  }
  
  /**
   * A fork branch for a Result.fork call.
   */
  final static class ResultFork<U> implements Result<U>
  {
    private final ResultJoinBase _join;
    
    private U _value;
    private Throwable _failure;
    
    private Result<Object> _chain;
    private Object _chainValue;
    
    private boolean _isDone;
    
    ResultFork(ResultJoinBase join)
    {
      _join = join;
    }

    /**
     * The completion value.
     */
    public final U getValue()
    {
      if (_chain != null) {
        Result<Object> chain = _chain;
        Object chainValue = _chainValue;
        
        _chain = null;
        _chainValue = null;
        
        chain.completeFuture(chainValue);
      }
      
      return _value;
    }
    
    public final Throwable getFailure()
    {
      return _failure;
    }
    
    public void handle(U value, Throwable exn)
    {
      if (exn != null) {
        fail(exn);
      }
      else {
        ok(value);
      }
    }

    /**
     * Completes the branch result. 
     */
    @Override
    public void ok(U value)
    {
      _value = value;
      
      boolean isDonePrev = _isDone;
      
      if (! isDonePrev) {
        _isDone = true;

        _join.completeFork();
      }
    }
    
    @Override
    public boolean isFuture()
    {
      return _join.isFuture();
    }
    
    @Override
    public <V> void completeFuture(Result<V> result, V value)
    {
      _chain = (Result) result;
      _chainValue = value;
      
      boolean isDonePrev = _isDone;
      if (! isDonePrev) {
        _isDone = true;

        _join.completeFork();
      }
    }
    
    @Override
    public void completeFuture(U value)
    {
      ok(value);
    }

    @Override
    public void fail(Throwable exn)
    {
      boolean isDonePrev = _isDone;
      
      _isDone = true;
      
      if (! isDonePrev) {
        _isDone = true;
        
        _failure = exn;
        
        _join.failFork(exn);
      }
    }
  }
  
  final static class ResultJoinFunction<U,T> extends ResultJoinBase
    implements Result<Void>
  {
    private final Result<T> _result;
    private final ResultFork<U> []_resultsFork;
    
    private final Function<List<U>,T> _finisher;
    
    private Throwable _fail;
    
    ResultJoinFunction(Result<T> result,
                       ResultFork<U> []resultsFork,
                       Function<List<U>,T> finisher)
    {
      super(resultsFork.length);
      
      _result = result;
      _resultsFork = resultsFork;
      
      _finisher = finisher;
    }
    
    @Override
    protected void failFork(Throwable exn)
    {
      _fail = exn;
      
      super.failFork(exn);
    }
    
    @Override
    public void handle(Void value, Throwable exn)
    {
      if (exn != null) {
        fail(exn);
      }
      else {
        ok(null);
      }
    }
    
    @Override
    protected void complete()
    {
      try {
        if (_fail != null) {
          _result.fail(_fail);
          return;
        }
        
        if (_result.isFuture()) {
          _result.completeFuture(this, null);
        }
        else {
          completeFuture(null);
        }
      } catch (Throwable e) {
        _result.fail(e);
      }
    }
    
    @Override
    public boolean isFuture()
    {
      return _result.isFuture();
    }
    
    @Override
    public void ok(Void value)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }
    
    @Override
    public void completeFuture(Void value)
    {
      ResultFork<U>[] resultsFork = _resultsFork;
      int count = resultsFork.length;
  
      List<U> values = new ArrayList<>();
  
      for (int i = 0; i < count; i++) {
        values.add(resultsFork[i].getValue());
      }

      _result.ok(_finisher.apply(values));
    }
  }
  
  final static class ResultJoinConsumer<U,T> extends ResultJoinBase
    implements Result<Void>
  {
    private final Result<T> _result;
    private final ResultFork<U> []_resultsFork;
    
    private final BiConsumer<List<U>,Result<T>> _finisher;
    
    private Throwable _fail;
    
    ResultJoinConsumer(Result<T> result,
                       ResultFork<U> []resultsFork,
                       BiConsumer <List<U>,Result<T>> finisher)
    {
      super(resultsFork.length);
      
      _result = result;
      _resultsFork = resultsFork;
      
      _finisher = finisher;
    }

    @Override
    protected void failFork(Throwable exn)
    {
      if (_fail == null) {
        _fail = exn;
      }
      
      super.failFork(exn);
    }
    
    @Override
    protected void complete()
    {
      try {
        if (_fail != null) {
          _result.fail(_fail);
          return;
        }
        
        if (_result.isFuture()) {
          _result.completeFuture(this, null);
        }
        else {
          completeFuture(null);
        }
      } catch (Throwable e) {
        _result.fail(e);
      }
    }
    
    @Override
    public boolean isFuture()
    {
      return _result.isFuture();
    }

    @Override
    public void ok(Void value)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    public void handle(Void value, Throwable exn)
    {
      if (exn != null) {
        fail(exn);
      }
      else {
        ok(value);
      }
    }

    @Override
    public void completeFuture(Void value)
    {
      ResultFork<U>[] resultsFork = _resultsFork;
      int count = resultsFork.length;
    
      List<U> values = new ArrayList<>();
    
      for (int i = 0; i < count; i++) {
        values.add(resultsFork[i].getValue());
      }
      
      _finisher.accept(values, _result);
    }
  }
  
  final static class ResultJoinConsumerFails<U,T> extends ResultJoinBase
    implements Result<Void>
  {
    private final Result<T> _result;
    private final ResultFork<U> []_resultsFork;
    
    private final BiConsumer<List<U>,Result<T>> _finisher;
    private final TriConsumer<List<U>,List<Throwable>,Result<T>> _fails;
    
    private boolean _isFail;
    
    ResultJoinConsumerFails(Result<T> result,
                       ResultFork<U> []resultsFork,
                       BiConsumer <List<U>,Result<T>> finisher,
                       TriConsumer <List<U>,List<Throwable>,Result<T>> fails)
    {
      super(resultsFork.length);
      
      _result = result;
      _resultsFork = resultsFork;
      
      _finisher = finisher;
      _fails = fails;
    }

    @Override
    protected void failFork(Throwable exn)
    {
      _isFail = true;

      super.failFork(exn);
    }
    
    @Override
    protected void complete()
    {
      if (_result.isFuture()) {
        _result.completeFuture(this, null);
      }
      else {
        completeFuture(null);
      }
    }
    
    @Override
    public boolean isFuture()
    {
      return _result.isFuture();
    }

    @Override
    public void ok(Void value)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    public void handle(Void value, Throwable exn)
    {
      if (exn != null) {
        fail(exn);
      }
      else {
        ok(value);
      }
    }
    
    @Override
    public void completeFuture(Void value)
    {
      try {
        ResultFork<U>[] resultsFork = _resultsFork;
        int count = resultsFork.length;
        
        List<U> values = new ArrayList<>();
      
        for (int i = 0; i < count; i++) {
          values.add(resultsFork[i].getValue());
        }

        if (! _isFail) {
          _finisher.accept(values, _result);
        }
        else {
          List<Throwable> fails = new ArrayList<>();
        
          for (int i = 0; i < count; i++) {
            fails.add(resultsFork[i].getFailure());
          }
          
          _fails.accept(values, fails, _result);
        }
      } catch (Throwable e) {
        _result.fail(e);
      }
    }
  }
  
  final static class ResultJoinBuilder<U,T> extends ResultJoinBase
    implements Result<Void>, Fork<U,T>
  {
    private final Result<T> _result;
    private final List<ResultFork<? extends U>> _forkList = new ArrayList<>();
    
    private Function<List<U>,T> _finisherFunction;
    private BiConsumer<List<U>,Result<T>> _finisherConsumer;
    
    private TriConsumer<List<U>,List<Throwable>,Result<T>> _fails;
    
    private boolean _isFail;
    
    ResultJoinBuilder(Result<T> result)
    {
      _result = result;
    }
    
    @Override
    public <V extends U> ResultFork<V> branch()
    {
      validateBuilder();
      
      ResultFork<V> resultFork = new ResultFork<>(this);
      
      _forkList.add(resultFork);
    
      super.addFork();

      return resultFork;
    }
  

    @Override
    public Fork<U,T> fail(TriConsumer<List<U>, List<Throwable>, Result<T>> fails)
    {
      validateBuilder();
      
      _fails = (TriConsumer) fails;

      return this;
    }

    @Override
    public void join(Function<List<U>, T> finisher)
    {
      validateBuilder();
      
      _finisherFunction = finisher;
      
      completeFork();
    }

    @Override
    public void join(BiConsumer<List<U>, Result<T>> finisher)
    {
      validateBuilder();
      
      _finisherConsumer = finisher;
      
      completeFork();
    }
    
    private void validateBuilder()
    {
      if (_finisherFunction != null || _finisherConsumer != null) {
        throw new IllegalStateException();
      }
    }

    @Override
    protected void failFork(Throwable exn)
    {
      _isFail = true;

      super.failFork(exn);
    }
    
    @Override
    protected void complete()
    {
      if (_result.isFuture()) {
        _result.completeFuture(this, null);
      }
      else {
        completeFuture(null);
      }
    }
    
    @Override
    public boolean isFuture()
    {
      return _result.isFuture();
    }

    @Override
    public void ok(Void value)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    public void handle(Void value, Throwable exn)
    {
      if (exn != null) {
        fail(exn);
      }
      else {
        ok(value);
      }
    }
    
    @Override
    public void completeFuture(Void value)
    {
      try {
        List<U> values = new ArrayList<>();
      
        for (int i = 0; i < _forkList.size(); i++) {
          values.add(_forkList.get(i).getValue());
        }

        if (! _isFail) {
          if (_finisherFunction != null) {
            _result.ok(_finisherFunction.apply(values));
          }
          else if (_finisherConsumer != null) {
            _finisherConsumer.accept(values, _result);
          }
          else {
            throw new IllegalStateException();
          }
        }
        else {
          List<Throwable> fails = new ArrayList<>();
          
          Throwable failFirst = null;
        
          for (int i = 0; i < _forkList.size(); i++) {
            Throwable fail = _forkList.get(i).getFailure();
            
            if (failFirst == null) {
              failFirst = fail;
            }
            
            fails.add(fail);
          }

          if (_fails != null) {
            _fails.accept(values, fails, _result);
          }
          else {
            _result.fail(failFirst);
          }
        }
      } catch (Throwable e) {
        _result.fail(e);
      }
    }
  }
  
  final static class ChainResult<T,U> implements Result<T>
  {
    private Result<U> _next;
    private BiConsumer<T,Result<U>> _consumer;
    
    public ChainResult(Result<U> next,
                       BiConsumer<T,Result<U>> consumer)
    {
      Objects.requireNonNull(next);
      _next = next;
      
      Objects.requireNonNull(consumer);
      _consumer = consumer;
    }
  
    @Override
    public void ok(T value)
    {
      try {
        _consumer.accept(value, _next);
      } catch (Throwable e) {
        fail(e);
      }
    }
    
    @Override
    public void fail(Throwable exn)
    {
      _next.fail(exn);
    }
    
    @Override
    public void handle(T value, Throwable exn)
    {
      try {
        if (exn != null) {
          _next.fail(exn);
        }
        else {
          _consumer.accept(value, _next);
        }
      } catch (Throwable e) {
        _next.fail(e);
      }
    }
  }
  
  final static class ChainResultAsync<T,U> extends ChainResultBase<T,U>
  {
    private BiConsumer<T,Result<U>> _consumer;
    
    public ChainResultAsync(Result<U> next,
                            BiConsumer<T,Result<U>> consumer)
    {
      super(next);
      
      Objects.requireNonNull(consumer);
      _consumer = consumer;
    }
    
    @Override
    public boolean isFuture()
    {
      return true;
    }
  
    @Override
    public void ok(T value)
    {
      completeFuture(value);
    }
    
    @Override
    public <V> void completeFuture(Result<V> result, V value)
    {
      delegate().completeFuture(result, value);
    }
    
    @Override
    public void completeFuture(T value)
    {
      //Object oldContext = beginAsyncContext();
      
      try {
        _consumer.accept(value, delegate());
      } catch (Throwable e) {
        fail(e);
      } finally {
        // endAsyncContext(oldContext);
      }
    }
  }
  
  /**
   * Chained Result using a function to map the returned value to 
   * the Result.
   */
  static class ChainResultFun<T,U> extends ChainResultBase<T,U>
  {
    private Function<T,U> _fun;
    
    public ChainResultFun(Result<U> next,
                          Function<T,U> fun)
    {
      super(next);
      
      Objects.requireNonNull(fun);
      _fun = fun;
    }
    
    @Override
    public boolean isFuture()
    {
      return false;
    }
  
    @Override
    public void ok(T value)
    {
      try {
        U nextValue = _fun.apply(value);

        delegate().ok(nextValue);
      } catch (Throwable e) {
        fail(e);
      }
    }
  }
  
  /**
   * Chained Result using a function to map the returned value to 
   * the Result.
   */
  static class ChainResultFunExn<T,U> extends ChainResultFun<T,U>
  {
    private BiConsumer<Throwable,Result<U>> _exnHandler;
    
    public ChainResultFunExn(Result<U> next,
                             Function<T,U> fun,
                             BiConsumer<Throwable,Result<U>> exnHandler)
    {
      super(next, fun);
      
      Objects.requireNonNull(exnHandler);
      _exnHandler = exnHandler;
    }
  
    @Override
    public void fail(Throwable exn)
    {
      try {
        _exnHandler.accept(exn, delegate());
      } catch (Throwable e) {
        delegate().fail(e);
      }
    }
  }
  
  abstract static class ChainResultBase<T,U> extends Result.Wrapper<T,U>
  {
    public ChainResultBase(Result<U> next)
    {
      super(next);
    }
    
    @Override
    public abstract void ok(T value);
  }
  
  /**
   * Chained Result using a function to map the returned value to 
   * the Result.
   */
  static class ChainResultFunFuture<T,U> extends ChainResultBase<T,U>
  {
    private Function<T,U> _fun;
    
    public ChainResultFunFuture(Result<U> next,
                                Function<T,U> fun)
    {
      super(next);
      
      Objects.requireNonNull(fun);
      _fun = fun;
    }
    
    @Override
    public boolean isFuture()
    {
      return true;
    }
  
    @Override
    public void ok(T value)
    {
      completeFuture(value);
    }
    
    @Override
    public void completeFuture(T value)
    {
      // Object oldContext = beginAsyncContext();
      
      try {
        U nextValue = _fun.apply(value);
        
        delegate().completeFuture(nextValue);
      } catch (Throwable e) {
        fail(e);
      } finally {
        // endAsyncContext(oldContext);
      }
    }
  }
  
  /**
   * Chained Result using a function to map the returned value to 
   * the Result.
   */
  static class ChainResultFunFutureExn<T,U> extends ChainResultFunFuture<T,U>
  {
    private BiConsumer<Throwable,Result<U>> _exnHandler;
    
    public ChainResultFunFutureExn(Result<U> next,
                             Function<T,U> fun,
                             BiConsumer<Throwable,Result<U>> exnHandler)
    {
      super(next, fun);
      
      Objects.requireNonNull(exnHandler);
      _exnHandler = exnHandler;
    }
  
    @Override
    public void fail(Throwable exn)
    {
      try {
        _exnHandler.accept(exn, delegate());
      } catch (Throwable e) {
        delegate().fail(e);
      }
    }
  }

  static class Ignore<T> implements Result<T>
  {
    private static final Logger log = Logger.getLogger(ResultImpl.class.getName());
    private static final Ignore<?> NULL_ADAPTER = new Ignore<Object>();
    
    public static <T> Result<T> create()
    {
      return (Result) NULL_ADAPTER;
    }
    
    @Override
    public void handle(T result, Throwable exn)
    {
      if (exn != null) {
        if (log.isLoggable(Level.FINER)) {
          log.finer(this + " failed " + exn);
          exn.printStackTrace();
        }
        else if (log.isLoggable(Level.FINEST)) {
          log.log(Level.FINEST, exn.toString(), exn);
        }
        
      }
      else {
        if (log.isLoggable(Level.FINEST)) {
          log.finest(this + " completed " + result);
        }
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[]";
    }
  }

  static class AdapterMake<T> implements Result<T>
  {
    private Consumer<T> _result;
    private Consumer<Throwable> _exn;
    
    public AdapterMake(Consumer<T> result, Consumer<Throwable> exn)
    {
      Objects.requireNonNull(result);
      Objects.requireNonNull(exn);
      
      _result = result;
      _exn = exn;
    }
    
    @Override
    public void handle(T result, Throwable exn)
    {
      try {
        if (exn != null) {
          _exn.accept(exn);
        }
        else {
          _result.accept(result);
        }
      } catch (Throwable exn2) {
        _exn.accept(exn);
      }
    }
  }

  static class ResultBuilder<T> implements Result<T>, Result.Builder<T>
  {
    private Consumer<T> _onComplete;
    private Consumer<Throwable> _onFail;
    
    public ResultBuilder(Consumer<T> result, Consumer<Throwable> fail)
    {
      _onComplete = result;
      _onFail = fail;
    }
    
    public Result<T> onOk(Consumer<T> onComplete)
    {
      Objects.requireNonNull(onComplete);
      
      _onComplete = onComplete;
      
      return this;
    }
    
    @Override
    public void handle(T result, Throwable exn)
    {
      try {
        if (exn != null) {
          Consumer<Throwable> onFail = _onFail;
        
          if (onFail != null) {
            onFail.accept(exn);
          }
          else {
            if (log.isLoggable(Level.FINER)) {
              log.finer(exn.toString());
            }
          }
        }
        else {
          _onComplete.accept(result);
        }
      } catch (Throwable exn2) {
        Consumer<Throwable> onFail = _onFail;
        
        if (onFail != null) {
          onFail.accept(exn);
        }
        else {
          log.log(Level.FINER, exn2.toString(), exn2);
        }
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _onComplete + "," + _onFail + "]";
    }
  }
}
