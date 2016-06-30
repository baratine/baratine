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

package com.caucho.v5.amp.stream;

import io.baratine.function.BiConsumerAsync;
import io.baratine.function.BiConsumerSync;
import io.baratine.function.BiFunctionAsync;
import io.baratine.function.BiFunctionSync;
import io.baratine.function.BinaryOperatorAsync;
import io.baratine.function.BinaryOperatorSync;
import io.baratine.function.ConsumerAsync;
import io.baratine.function.ConsumerSync;
import io.baratine.function.FunctionAsync;
import io.baratine.function.FunctionSync;
import io.baratine.function.PredicateAsync;
import io.baratine.function.PredicateSync;
import io.baratine.function.SupplierSync;
import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionCancelled;
import io.baratine.service.ServiceRef;
import io.baratine.stream.ResultStream;
import io.baratine.stream.ResultStreamBuilderSync;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.message.StreamCallMessage;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;

@SuppressWarnings("serial")
public class StreamBuilderImpl<T,U> implements ResultStreamBuilderSync<T>, Serializable
{
  private transient ServiceRefAmp _serviceRef;
  private transient MethodAmp _method;
  private Object[] _args;
  
  // private SupplierSync<?> _init;
  
  // private StreamBuilderFilter<T,U> _filter;
  
  // private Object _value;

  public StreamBuilderImpl(ServiceRefAmp serviceRef,
                           MethodAmp method, 
                           Object []args)
  {
    Objects.requireNonNull(serviceRef);
    Objects.requireNonNull(method);
    
    _serviceRef = serviceRef;
    _method = method;
    _args = args;
    
    // this(serviceRef, method, args, StreamBuilderFilter.identity());
  }

  /*
  private StreamBuilderImpl(ServiceRefAmp serviceRef,
                            MethodAmp method, 
                            Object []args,
                            StreamBuilderFilter<T,U> filter)
  {
    Objects.requireNonNull(serviceRef);
    Objects.requireNonNull(method);
    Objects.requireNonNull(filter);
    
    _serviceRef = serviceRef;
    _method = method;
    _args = args;

    _filter = filter;
  }
  */

  private StreamBuilderImpl(StreamBuilderImpl<?,U> next)
  {
    Objects.requireNonNull(next);
    
    _serviceRef = next._serviceRef;
    _method = next._method;
    _args = next._args;

    // _filter = StreamBuilderFilter.identity();
  }
  
  // 
  // builder
  //
  
  protected ResultStream<U> build(ResultStream<? super T> next)
  {
    return (ResultStream) next;
  }
  
  //
  // terminals
  //
  
  @Override
  public Cancel exec()
  {
    ResultStreamExecute<T> resultStream = new ResultStreamExecute<>(Result.ignore());
    
    offer(resultStream);
    
    return resultStream;
  }
  
  @Override
  public void result(Result<? super T> result)
  {
    ResultStream<T> resultStream = new ResultStreamExecute<T>(result);
    
    offer(resultStream);
    
    // return resultStream;
  }
  
  @Override
  public void to(ResultStream<? super T> result)
  {
    Objects.requireNonNull(result);
    
    local().offer(result);
  }
  
  @Override
  public T result()
  {
    /*
    ResultFuture<T> future = new ResultFuture<>();
    
    return _serviceRef.getManager().run(future, 60, TimeUnit.SECONDS, 
                                        ()->result(future));
                                        */
    //System.err.println("CURR: " + OutboxAmp.getCurrent());
    return _serviceRef.services().run(60, TimeUnit.SECONDS, 
                                        r->result(r));
  }
  
  @Override
  public StreamBuilderImpl<Void,U> forEach(ConsumerSync<? super T> consumer)
  {
    return new ForEachSync<>(this, consumer);
  }
  
  @Override
  public StreamBuilderImpl<Void,U> forEach(ConsumerAsync<? super T> consumer)
  {
    return new ForEachAsync<>(this, consumer);
  }
  
  @Override
  public StreamBuilderImpl<T,U> first()
  {
    return new First<T,U>(this);
  }
  
  @Override
  public StreamBuilderImpl<Void,U> ignore()
  {
    return new Ignore<T,U>(this);
  }
  
  //
  // reduce
  //

  /**
   * Reduce with a binary function
   * 
   * <code><pre>
   * s = init;
   * s = op(s, t1);
   * s = op(s, t2);
   * result = s;
   * </pre></code>
   */
  @Override
  public StreamBuilderImpl<T,U> reduce(T init,
                                       BinaryOperatorSync<T> op)
  {
    return new ReduceOpInitSync<>(this, init, op);
  }

  /**
   * reduce((x,y)->op) with a sync binary function
   * 
   * <code><pre>
   * s = t1;
   * s = op(s, t2);
   * s = op(s, t3);
   * result = s;
   * </pre></code>
   */
  @Override
  public StreamBuilderImpl<T,U> reduce(BinaryOperatorSync<T> op)
  {
    return new ReduceOpSync<>(this, op);
  }
  
  /**
   * Reduce with an accumulator and combiner
   */
  @Override
  public <R> StreamBuilderImpl<R,U> 
  reduce(R identity, 
         BiFunctionSync<R, ? super T, R> accumulator,
         BinaryOperatorSync<R> combiner)
  {
    return new ReduceAccumSync<>(this, identity, accumulator, combiner);
  }
  
  /**
   * Async Reduce with combiner
   */
  /*
  @Override
  public <R> StreamBuilderImpl<R,U>
  reduce(R identity, 
         BiFunctionAsync<R, ? super T, R> accumulator,
         BinaryOperatorAsync<R> combiner)
  {
    return new ReduceAccumAsync<>(this, identity, accumulator, combiner);
  }
  */

  @Override
  public <R> StreamBuilderImpl<R,U>
  collect(SupplierSync<R> init,
          BiConsumerSync<R,T> accum,
          BiConsumerSync<R,R> combiner)
  {
    return new CollectSync<>(this, init, accum, combiner);
  }

  /*
  @Override
  public <R> StreamBuilderImpl<R,U>
  collect(SupplierSync<R> init,
          BiConsumerAsync<R,T> accum,
          BiConsumerAsync<R,R> combiner)
  {
    return new CollectAsync<>(this, init, accum, combiner);
  }
  */
  
  //
  // map
  //
  
  @Override
  public <R> StreamBuilderImpl<R,U> map(FunctionSync<? super T,? extends R> map)
  {
    return new MapSync<R,T,U>(this, map);
  }
  
  @Override
  public <R> StreamBuilderImpl<R,U> map(FunctionAsync<? super T,? extends R> map)
  {
    return new MapAsync<R,T,U>(this, map);
  }
  
  /**
   * chain(s->builder) - builds a ResultStream chain with custom builder
   */
  @Override
  public <S> StreamBuilderImpl<S,U> 
  custom(FunctionSync<ResultStream<S>,ResultStream<T>> builder)
  {
    return new Chain<S,T,U>(this, builder);
  }
  
  /**
   * peek(consumer) - accepts a copy of the item and passes it on.
   */
  @Override
  public StreamBuilderImpl<T,U> peek(ConsumerSync<? super T> consumer)
  {
    return new Peek<T,U>(this, consumer);
  }
  
  /**
   * local() - switch to local (caller) methods from service (remote) methods.
   */
  @Override
  public StreamBuilderImpl<T,U> local()
  {
    return new Local<T,U>(this);
  }
  
  /**
   * prefetch() - limit the flow prefetch credit
   */
  // @Override
  public StreamBuilderImpl<T,U> prefetch(int prefetch)
  {
    return new Prefetch<T,U>(this, prefetch);
  }
  
  //
  // filter
  //

  /**
   * filter() sync 
   */
  @Override
  public StreamBuilderImpl<T,U> filter(PredicateSync<? super T> test)
  {
    return new FilterSync<T,U>(this, test);
  }
  
  /**
   * filter() async
   */
  @Override
  public StreamBuilderImpl<T,U> filter(PredicateAsync<? super T> test)
  {
    return new FilterAsync<T,U>(this, test);
  }
  
  //
  // blocking versions
  //

  /**
   * reduce(init, (x,y)->op) sync
   */
  /*
  @Override
  public T reduce(T init,
                  BinaryOperatorSync<T> op)
  {
    ResultFuture<T> future = new ResultFuture<>();
  
    return doFuture(future, 
                    ()->reduce(init, op, future));
  }
  */
  
  /*
  @Override
  public <R> R collect(SupplierSync<R> init,
                       BiConsumerSync<R,T> accum,
                       BiConsumerSync<R,R> combiner)
  {
    ResultFuture<R> future = new ResultFuture<>();
    
    return doFuture(future,
                    ()->collect(init, accum, combiner, future));
  }
  */
  
  @Override
  public StreamBuilderImpl<Iterable<T>,U> iter()
  {
    return new Iter<>(this);
    /*
    ResultFuture<Iterable<T>> future = new ResultFuture<>();
    
    ResultStreamIter<T> resultIter = new ResultStreamIter<>(future);
    
    return doFuture(future, ()->offer(resultIter));
    */
  }
  
  @Override
  public StreamBuilderImpl<Stream<T>,U> stream()
  {
    return new StreamFactory<>(this);
    /*
    ResultFuture<Iterable<T>> future = new ResultFuture<>();
    
    ResultStreamIter<T> resultIter = new ResultStreamIter<>(future);
    
    return doFuture(future, ()->offer(resultIter));
    */
  }

  /*
  @Override
  public <V> V reduce(V identity, 
                      BiFunctionSync<V, ? super T, V> accumulator,
                      BinaryOperatorSync<V> combiner)
  {
    ResultFuture<V> future = new ResultFuture<>();
    
    return doFuture(future, 
                     ()->reduce(identity, accumulator, combiner, future));
  }
  */
  
  /*
  private <R> R doFuture(ResultFuture<R> future, Runnable task)
  {
    return _serviceRef.getManager().run(future, 60, TimeUnit.SECONDS, task);
  }
  */
  
  /*
  private <R> R doFuture(Consumer<Result<R>> task)
  {
    return _serviceRef.getManager().run(60, TimeUnit.SECONDS, task);
  }
  */

  private void offer(ResultStream<? super T> resultStream)
  {
    long expires = 600 * 1000L;

    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_serviceRef.services())) {
      HeadersAmp headers = HeadersNull.NULL;
    
      ResultStream<U> stream = build(resultStream);
    
      StreamCallMessage<U> msg;
      msg = new StreamCallMessage<U>(outbox, outbox.inbox(), headers,
                                     _serviceRef, _method, 
                                     stream,
                                     expires, _args);
      
      long timeout = 10000;

      msg.offer(timeout);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _method + "]";
  }
  
  //
  // inner classes
  //
  
  abstract private static class Next<S,T,U> extends StreamBuilderImpl<S,U> {
    private StreamBuilderImpl<T,U> _next;
    
    Next(StreamBuilderImpl<T,U> next)
    {
      super(next);
      
      Objects.requireNonNull(next);
      
      _next = next;
    }
    
    protected StreamBuilderImpl<T,U> getNext()
    {
      return _next;
    }
  }
  
  //
  // chain
  //
  
  /**
   * chain() builder
   */
  private static class Chain<S,T,U> extends Next<S,T,U> {
    private FunctionSync<ResultStream<S>,ResultStream<T>> _builder;
    
    Chain(StreamBuilderImpl<T,U> next,
          FunctionSync<ResultStream<S>,ResultStream<T>> builder)
    {
      super(next);
      
      Objects.requireNonNull(builder);
      
      _builder = builder;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super S> result)
    {
      ResultStream<T> chainResult = new ResultStreamChain<>(result, _builder);
      
      return getNext().build(chainResult);
    }
  }
  
  /**
   * chain() ResultStream
   */
  private static class ResultStreamChain<T,S> extends ResultStream.Wrapper<T,T>
  {
    private ResultStream<? super S> _prev;
    private FunctionSync<ResultStream<S>,ResultStream<T>> _builder;
    
    ResultStreamChain(ResultStream<? super S> prev,
                      FunctionSync<ResultStream<S>,ResultStream<T>> builder)
    {
      super(builder.apply((ResultStream) prev));
      
      _prev = prev;
      _builder = builder;
    }
    
    @Override
    public void accept(T value)
    {
      next().accept(value);
    }
    
    @Override
    public ResultStream<?> createJoin()
    {
      ResultStream<?> reduce = _prev.createJoin();
      
      return reduce;
    }
    
    @Override
    public ResultStream<T> createFork(ResultStream<Object> resultReduce)
    {
      ResultStream<? super S> prev = _prev.createFork(resultReduce);
      
      return new ResultStreamChain<>(prev, _builder);
    }
  }
  
  /**
   * peek() builder
   */
  private static class Peek<T,U> extends Next<T,T,U> {
    private ConsumerSync<? super T> _consumer;
    
    Peek(StreamBuilderImpl<T,U> next,
         ConsumerSync<? super T> consumer)
    {
      super(next);
      
      Objects.requireNonNull(consumer);
      
      _consumer = consumer;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super T> result)
    {
      ResultStream<T> peekResult = new ResultStreamPeek<T>(result, _consumer);
      
      return getNext().build(peekResult);
    }
  }
  
  /**
   * peek() ResultStream
   */
  private static class ResultStreamPeek<T> extends ResultStreamServiceBase<T,T>
  {
    private ConsumerSync<? super T> _consumer;
    
    ResultStreamPeek(ResultStream<? super T> next,
                     ConsumerSync<? super T> consumer)
    {
      super(next);
      
      _consumer = consumer;
    }
    
    @Override
    public void accept(T value)
    {
      _consumer.accept(value);
      
      next().accept(value);
    }
    
    @Override
    public ResultStream<T> createMapSelf(ResultStream<? super T> next)
    {
      return new ResultStreamPeek<>(next, _consumer);
    }
  }
  
  /**
   * filter() sync builder
   */
  private static class FilterSync<T,U> extends Next<T,T,U> {
    private PredicateSync<? super T> _test;
    
    FilterSync(StreamBuilderImpl<T,U> next,
               PredicateSync<? super T> test)
    {
      super(next);
      
      Objects.requireNonNull(test);
      
      _test = test;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super T> result)
    {
      ResultStream<T> filterResult = new ResultStreamFilterSync<>(result, _test);
      
      return getNext().build(filterResult);
    }
  }
  
  /**
   * filter() sync ResultStream
   */
  private static class ResultStreamFilterSync<T>
    extends ResultStreamServiceBase<T,T>
  {
    private PredicateSync<? super T> _test;
    
    ResultStreamFilterSync(ResultStream<? super T> next,
                           PredicateSync<? super T> test)
    {
      super(next);
      
      _test = test;
    }
    
    @Override
    public void accept(T value)
    {
      if (_test.test(value)) {
        next().accept(value);
      }
    }
    
    @Override
    public ResultStream<T> createMapSelf(ResultStream<? super T> next)
    {
      return new ResultStreamFilterSync<>(next, _test);
    }
  }
  
  /**
   * filter() async builder
   */
  private static class FilterAsync<T,U> extends Next<T,T,U> {
    private PredicateAsync<? super T> _test;
    
    FilterAsync(StreamBuilderImpl<T,U> next,
               PredicateAsync<? super T> test)
    {
      super(next);
      
      Objects.requireNonNull(test);
      
      _test = test;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super T> result)
    {
      ResultStream<T> filterResult = new ResultStreamFilterAsync<>(result, _test);
      
      return getNext().build(filterResult);
    }
  }
  
  /**
   * filter() async ResultStream
   */
  private static class ResultStreamFilterAsync<T>
    extends ResultStreamServiceBaseAsync<T,T>
  {
    private PredicateAsync<? super T> _test;
    
    ResultStreamFilterAsync(ResultStream<? super T> next,
                            PredicateAsync<? super T> test)
    {
      super(next);
      
      _test = test;
    }
    
    @Override
    public void accept(T value)
    {
      addPending();
      
      _test.test(value, (x,e)->{
        if (e != null) {
          next().fail(e);
          return;
        }
        
        if (Boolean.TRUE.equals(x)) {
          next().accept(value); 
        }
        
        subPending();
      });
    }
    
    @Override
    public ResultStream<T> createMapSelf(ResultStream<? super T> next)
    {
      return new ResultStreamFilterAsync<>(next, _test);
    }
  }
  
  /**
   * map() sync builder
   */
  private static class MapSync<S,T,U> extends Next<S,T,U> {
    private FunctionSync<? super T,? extends S> _map;
    
    MapSync(StreamBuilderImpl<T,U> next,
             FunctionSync<? super T,? extends S> map)
    {
      super(next);
      
      Objects.requireNonNull(map);
      
      _map = map;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super S> result)
    {
      ResultStream<T> mapResult = new ResultStreamMapSync<>(result, _map);
      
      return getNext().build(mapResult);
    }
  }
  
  /**
   * map() sync ResultStream
   */
  private static class ResultStreamMapSync<T,U>
    extends ResultStreamServiceBase<T,U>
  {
    private FunctionSync<? super T,? extends U> _map;
    
    ResultStreamMapSync(ResultStream<U> next,
                        FunctionSync<? super T,? extends U> map)
    {
      super(next);
      
      _map = map;
    }
    
    @Override
    public void accept(T value)
    {
      next().accept(_map.apply(value));
    }
    
    @Override
    public ResultStream<T> createMapSelf(ResultStream<? super U> next)
    {
      return new ResultStreamMapSync<>(next, _map);
    }
  }
  
  /**
   * map() async builder
   */
  private static class MapAsync<R,T,U> extends Next<R,T,U> {
    private FunctionAsync<? super T,? extends R> _map;
    
    MapAsync(StreamBuilderImpl<T,U> next,
             FunctionAsync<? super T,? extends R> map)
    {
      super(next);
      
      Objects.requireNonNull(map);
      
      _map = map;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super R> result)
    {
      ResultStream<T> mapResult = new ResultStreamMapAsync<>(result, _map);
      
      return getNext().build(mapResult);
    }
  }
  
  /**
   * map() async ResultStream
   */
  private static class ResultStreamMapAsync<T,U>
    extends ResultStreamServiceBaseAsync<T,U>
  {
    private FunctionAsync<? super T,? extends U> _map;
    
    ResultStreamMapAsync(ResultStream<U> next,
                         FunctionAsync<? super T,? extends U> test)
    {
      super(next);
      
      _map = test;
    }
    
    @Override
    public void accept(T value)
    {
      addPending();
      
      _map.apply(value, Result.of(x->{ 
        next().accept(x);
        
        subPending();
      },
      e->{ 
        next().fail(e);
        subPending();
      }));
    }
    
    @Override
    public ResultStream<T> createMapSelf(ResultStream<? super U> next)
    {
      return new ResultStreamMapAsync<>(next, _map);
    }
  }
  
  //
  // local() implementation classes
  //
  
  /**
   * local() StreamBuilder
   */
  private static class Local<T,U> extends Next<T,T,U> {
    Local(StreamBuilderImpl<T,U> next)
    {
      super(next);
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super T> result)
    {
      ResultStream<T> chainResult = new ResultStreamLocal<T>(result);
      
      return getNext().build(chainResult);
    }
  }
  
  /**
   * local() ResultStream
   */
  private static class ResultStreamLocal<T> extends ResultStream.Wrapper<T,T>
  {
    ResultStreamLocal(ResultStream<? super T> next)
    {
      super(next);
    }
    
    @Override
    public void accept(T value)
    {
      next().accept(value);
    }
  }
  
  //
  // first() implementation classes
  //
  
  /**
   * first() StreamBuilder
   */
  private static class First<T,U> extends Next<T,T,U> {
    First(StreamBuilderImpl<T,U> next)
    {
      super(next);
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super T> result)
    {
      ResultStream<T> chainResult = new ResultStreamServiceFirst<T>(result);
      
      return getNext().build(chainResult);
    }
  }
  
  //
  // ignore() implementation classes
  //
  
  /**
   * ignore() StreamBuilder
   */
  private static class Ignore<T,U> extends Next<Void,T,U> {
    Ignore(StreamBuilderImpl<T,U> next)
    {
      super(next);
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super Void> result)
    {
      ResultStream<T> chainResult = new ResultStreamServiceIgnore<T>(result);
      
      return getNext().build(chainResult);
    }
  }
  
  //
  // iter() implementation classes
  //
  
  /**
   * iter() StreamBuilder
   */
  private static class Iter<T,U> extends Next<Iterable<T>,T,U> {
    Iter(StreamBuilderImpl<T,U> next)
    {
      super(next);
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super Iterable<T>> result)
    {
      ResultStreamIter<T> resultIter = new ResultStreamIter<>(result);
      
      return getNext().build(resultIter);
    }
  }
  
  //
  // stream() implementation classes
  //
  
  /**
   * stream() StreamBuilder
   */
  private static class StreamFactory<T,U> extends Next<Stream<T>,T,U> {
    StreamFactory(StreamBuilderImpl<T,U> next)
    {
      super(next);
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super Stream<T>> result)
    {
      ResultStreamJdkStream<T> resultStream
        = new ResultStreamJdkStream<>(result);
      
      return getNext().build(resultStream);
    }
  }
  
  //
  // local() implementation classes
  
  /**
   * prefetch() StreamBuilder
   */
  private static class Prefetch<T,U> extends Next<T,T,U> {
    private int _prefetch;
    
    Prefetch(StreamBuilderImpl<T,U> next, int prefetch)
    {
      super(next);
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super T> result)
    {
      ResultStream<T> prefetchResult
        = new ResultStreamServicePrefetch<T>(result, _prefetch);
      
      return getNext().build(prefetchResult);
    }
  }
  
  /**
   * forEach() sync builder
   */
  private static class ForEachSync<T,U> extends Next<Void,T,U> {
    private ConsumerSync<? super T> _consumer;
    
    ForEachSync(StreamBuilderImpl<T,U> next,
                ConsumerSync<? super T> consumer)
    {
      super(next);
      
      Objects.requireNonNull(consumer);
      
      _consumer = consumer;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super Void> result)
    {
      ResultStream<T> forEachResult
        = new ResultStreamForEachSync<>(result, _consumer);
      
      return getNext().build(forEachResult);
    }
  }
  
  /**
   * forEach() async builder
   */
  private static class ForEachAsync<T,U> extends Next<Void,T,U> {
    private ConsumerAsync<? super T> _consumer;
    
    ForEachAsync(StreamBuilderImpl<T,U> next,
                ConsumerAsync<? super T> consumer)
    {
      super(next);
      
      Objects.requireNonNull(consumer);
      
      _consumer = consumer;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super Void> result)
    {
      ResultStream<T> forEachResult
        = new ResultStreamForEachAsync<>(result, _consumer);
      
      return getNext().build(forEachResult);
    }
  }
  
  /**
   * reduce(op) sync builder
   */
  private static class ReduceOpSync<T,U> extends Next<T,T,U> {
    private BinaryOperatorSync<T> _op;
    
    ReduceOpSync(StreamBuilderImpl<T,U> next,
                 BinaryOperatorSync<T> op)
    {
      super(next);
      
      Objects.requireNonNull(op);
      
      _op = op;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super T> result)
    {
      ResultStream<T> resultReduce
        = new ResultStreamLocalFunSync<T>(result, _op);

      ResultStream<T> resultAccum
        = new ResultStreamServiceFunSync<T>(resultReduce, _op);
      
      return getNext().build(resultAccum);
    }
  }
  
  /**
   * reduce(op) async builder
   */
  private static class ReduceOpAsync<T,U> extends Next<T,T,U> {
    private BinaryOperatorAsync<T> _op;
    
    ReduceOpAsync(StreamBuilderImpl<T,U> next,
                 BinaryOperatorAsync<T> op)
    {
      super(next);
      
      Objects.requireNonNull(op);
      
      _op = op;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super T> result)
    {
      ResultStream<T> resultReduce
        = new ResultStreamLocalFunAsync<T>(result, _op);

      ResultStream<T> resultAccum
        = new ResultStreamServiceFunAsync<T>(resultReduce, _op);
      
      return getNext().build(resultAccum);
    }
  }
  
  /**
   * reduce(op,init) sync builder
   */
  private static class ReduceOpInitSync<T,U> extends Next<T,T,U> {
    private T _init;
    private BinaryOperatorSync<T> _op;
    
    ReduceOpInitSync(StreamBuilderImpl<T,U> next,
                     T init,
                     BinaryOperatorSync<T> op)
    {
      super(next);
      
      _init = init;
      
      Objects.requireNonNull(op);
      
      _op = op;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super T> result)
    {
      ResultStream<T> resultReduce
        = new ResultStreamLocalFunSync<T>(result, _op);

      ResultStream<T> resultAccum
        = new ResultStreamServiceFunInitSync<T,T>(resultReduce, _init, _op);

      return getNext().build(resultAccum);
    }
  }
  
  /**
   * reduce(op,init) async builder
   */
  private static class ReduceOpInitAsync<T,U> extends Next<T,T,U> {
    private T _init;
    private BinaryOperatorAsync<T> _op;
    
    ReduceOpInitAsync(StreamBuilderImpl<T,U> next,
                      T init,
                      BinaryOperatorAsync<T> op)
    {
      super(next);
      
      _init = init;
      
      Objects.requireNonNull(op);
      
      _op = op;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super T> result)
    {
      ResultStream<T> resultReduce
        = new ResultStreamLocalFunAsync<T>(result, _op);

      ResultStream<T> resultAccum
        = new ResultStreamServiceFunInitAsync<T,T>(resultReduce, _init, _op);

      return getNext().build(resultAccum);
    }
  }
  
  /**
   * reduce(init,accum,op) sync builder
   */
  private static class ReduceAccumSync<R,T,U> extends Next<R,T,U> {
    private R _init;
    private BiFunctionSync<R, ? super T, R> _accumulator;
    private BinaryOperatorSync<R> _combiner;
    
    ReduceAccumSync(StreamBuilderImpl<T,U> next,
                      R init,
                      BiFunctionSync<R, ? super T, R> accumulator,
                      BinaryOperatorSync<R> combiner)
    {
      super(next);
      
      Objects.requireNonNull(accumulator);
      Objects.requireNonNull(combiner);
      
      _init = init;
      _accumulator = accumulator;
      _combiner = combiner;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super R> result)
    {
      ResultStream<R> resultReduce
        = new ResultStreamLocalFunSync<R>(result, _combiner);
  
      ResultStream<T> resultAccum
        = new ResultStreamServiceFunInitSync<T,R>(resultReduce, _init, _accumulator);

      return getNext().build(resultAccum);
    }
  }
  
  /**
   * reduce(init,accum,op) async builder
   */
  private static class ReduceAccumAsync<R,T,U> extends Next<R,T,U> {
    private R _init;
    private BiFunctionAsync<R, ? super T, R> _accumulator;
    private BinaryOperatorAsync<R> _combiner;
    
    ReduceAccumAsync(StreamBuilderImpl<T,U> next,
                      R init,
                      BiFunctionAsync<R, ? super T, R> accumulator,
                      BinaryOperatorAsync<R> combiner)
    {
      super(next);
      
      Objects.requireNonNull(accumulator);
      Objects.requireNonNull(combiner);
      
      _init = init;
      _accumulator = accumulator;
      _combiner = combiner;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super R> result)
    {
      ResultStream<R> resultReduce
        = new ResultStreamLocalFunAsync<R>(result, _combiner);
  
      ResultStream<T> resultAccum
        = new ResultStreamServiceFunInitAsync<T,R>(resultReduce, _init, _accumulator);

      return getNext().build(resultAccum);
    }
  }
  
  /**
   * collect(init,accum,op) sync builder
   */
  private static class CollectSync<R,T,U> extends Next<R,T,U> {
    private SupplierSync<R> _init;
    private BiConsumerSync<R,T> _accum;
    private BiConsumerSync<R,R> _combiner;
    
    private CollectSync(StreamBuilderImpl<T,U> next,
                        SupplierSync<R> init,
                        BiConsumerSync<R,T> accum,
                        BiConsumerSync<R,R> combiner)
    {
      super(next);
      
      Objects.requireNonNull(accum);
      Objects.requireNonNull(combiner);
      
      _init = init;
      _accum = accum;
      _combiner = combiner;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super R> result)
    {
      ResultStreamLocalAccumSync<R, R> reduce;
      reduce = new ResultStreamLocalAccumSync<R,R>(result, _combiner, x->x);
      
      ResultStream<T> resultAccum;
      resultAccum = new ResultStreamServiceAccumSync<T,R>(reduce, _init, _accum);
      
      return getNext().build(resultAccum);
    }
  }
  
  /**
   * collect(init,accum,op) async builder
   */
  private static class CollectAsync<R,T,U> extends Next<R,T,U> {
    private SupplierSync<R> _init;
    private BiConsumerAsync<R,T> _accum;
    private BiConsumerAsync<R,R> _combiner;
    
    private CollectAsync(StreamBuilderImpl<T,U> next,
                         SupplierSync<R> init,
                         BiConsumerAsync<R,T> accum,
                         BiConsumerAsync<R,R> combiner)
    {
      super(next);
      
      Objects.requireNonNull(accum);
      Objects.requireNonNull(combiner);
      
      _init = init;
      _accum = accum;
      _combiner = combiner;
    }
    
    @Override
    public ResultStream<U> build(ResultStream<? super R> result)
    {
      ResultStreamLocalAccumAsync<R, R> reduce;
      reduce = new ResultStreamLocalAccumAsync<R,R>(result, _combiner, x->x);
      
      ResultStream<T> resultAccum;
      resultAccum = new ResultStreamServiceAccumAsync<T,R>(reduce, _init, _accum);
      
      return getNext().build(resultAccum);
    }
  }

  static class ResultStreamExecute<V>
    extends ResultStream.ResultWrapper<V,V>
    implements Cancel
  {
    private boolean _isCancelled;
    
    private V _lastValue;
    
    ResultStreamExecute(Result<? super V> result)
    {
      super(result);
    }

    @Override
    public void accept(V value)
    {
      _lastValue = value;
    }
    
    @Override
    public boolean isCancelled()
    {
      return _isCancelled;
    }
    
    @Override
    public void cancel()
    {
      _isCancelled = true;
      
      getNext().fail(new ServiceExceptionCancelled(getClass().getName()));
    }
    
    @Override
    public void ok()
    {
      _isCancelled = true;
      
      getNext().ok(_lastValue);
    }
    
    @Override
    public void fail(Throwable exn)
    {
      _isCancelled = true;
      
      getNext().fail(exn);
    }
  }
  
  private static class ResultStreamForEachSync<V> extends ResultStream.Wrapper<V,Void>
  {
    private ConsumerSync<? super V> _consumer;
    
    ResultStreamForEachSync(ResultStream<? super Void> result, 
                            ConsumerSync<? super V> consumer)
    {
      super(result);
      
      _consumer = consumer;
    }
    
    @Override
    public void accept(V value)
    {
      _consumer.accept(value);
    }

    @Override
    public void ok()
    {
      super.ok();
    }
  }
  
  private static class ResultStreamForEachAsync<V> extends ResultStreamLocalBaseAsync<V,Void>
  {
    private ConsumerAsync<? super V> _consumer;
    
    ResultStreamForEachAsync(ResultStream<? super Void> result, 
                             ConsumerAsync<? super V> consumer)
    {
      super(result);
      
      _consumer = consumer;
    }
    
    @Override
    public void accept(V value)
    {
      addPending();
      
      _consumer.accept(value, Result.of(x->{ subPending(); },
                                          e->{
                                            next().fail(e); 
                                            subPending(); 
                                          }));
    }
  }
  
  private static class ResultStreamIter<V> 
    extends ResultStream.Wrapper<V,Iterable<V>>
  {
    private ArrayList<V> _list;
    
    ResultStreamIter(ResultStream<? super Iterable<V>> result)
    {
      super(result);
    }
    
    @Override
    public void start()
    {
      _list = new ArrayList<V>();
    }
    
    @Override
    public void accept(V value)
    {
      _list.add(value);
    }
    
    @Override
    public void ok()
    {
      next().accept(_list);
      next().ok();
    }
  }
  
  private static class ResultStreamJdkStream<V> 
    extends ResultStream.Wrapper<V,Stream<V>>
  {
    private ArrayList<V> _list;
    
    ResultStreamJdkStream(ResultStream<? super Stream<V>> result)
    {
      super(result);
    }
    
    @Override
    public void start()
    {
      _list = new ArrayList<V>();
    }
    
    @Override
    public void accept(V value)
    {
      _list.add(value);
    }
    
    @Override
    public void ok()
    {
      next().accept(_list.stream());
      next().ok();
    }
  }
  
  private static class ResultStreamAccumFun<V> implements ResultStream<V>
  {
    private ResultStream<V> _result;
    private V _value;
    private BinaryOperatorSync<V> _fun;
    
    ResultStreamAccumFun(ResultStream<V> result,
                         V initValue,
                          BinaryOperatorSync<V> fun)
    {
      _result = result;
      _value = initValue;
      _fun = fun;
    }
    
    @Override
    public boolean isFuture()
    {
      return _result.isFuture();
    }
    
    @Override
    public void accept(V value)
    {
      _value = _fun.apply(_value, value);
    }
    
    @Override
    public void ok()
    {
      _result.ok();
    }
    
    @Override
    public void fail(Throwable exn)
    {
      _result.fail(exn);
    }
    
    @Override
    public void handle(V value, Throwable exn, boolean ok)
    {
      if (ok) {
        ok();
      }
      else if (exn != null) {
        fail(exn);
      }
      else {
        accept(value);
      }
    }
    
    @Override
    public ResultStream<?> createJoin()
    {
      return _result.createJoin();
    }
    
    @Override
    public ResultStream<V> createFork(ResultStream<Object> resultReduce)
    {
      ResultStream<V> result = _result.createFork(resultReduce);
      
      return new ResultStreamAccumFun<V>(result, _value, _fun);
    }
  }
}
