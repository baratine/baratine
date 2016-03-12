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

package com.caucho.v5.amp.deliver;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import com.caucho.v5.amp.queue.CounterBuilder;
import com.caucho.v5.amp.queue.QueueRingFixed;
import com.caucho.v5.amp.queue.QueueRingResizing;
import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.util.L10N;

/**
 * Interface for an actor queue
 */
public class QueueDeliverBuilderImpl<M> // extends MessageOutbox<M>>
  implements QueueDeliverBuilder<M>
{
  private static final L10N L = new L10N(QueueDeliverBuilderImpl.class);
  
  private static final int DEFAULT_INITIAL = 16;
  private static final int DEFAULT_CAPACITY = 1024;
  
  //private DeliverOutbox<M> []_processors;
  //private int _initial = 64;
  private int _initial = -1;
  private int _capacity = -1;
  private boolean _isMultiworker;
  private int _multiworkerOffset = 1;
  
  private Executor _executor; // = ThreadPool.getCurrent();
  //private long _workerIdleTimeout; // = 500L;
  private int _threadMax = 64 * 1024;
  private ClassLoader _classLoader
    = Thread.currentThread().getContextClassLoader();

  //private Supplier<Outbox<M,C>> _outboxFactory;

  private Object _outboxContext;
  
  public QueueDeliverBuilderImpl()
  {
    //_outboxFactory = ()->new OutboxImpl<>();
  }

  @Override
  public QueueDeliverBuilderImpl<M> sizeMax(int capacity)
  {
    _capacity = capacity;

    return this;
  }
  
  @Override
  public int sizeMax()
  {
    if (_capacity > 0) {
      return _capacity;
    }
    else {
      return DEFAULT_CAPACITY;
    }
  }
  
  @Override
  public QueueDeliverBuilderImpl<M> size(int initial)
  {
    _initial = initial;
    
    return this;
  }

  @Override
  public int size()
  {
    if (_initial > 0) {
      return _initial;
    }
    else if (_capacity > 0) {
      return sizeMax();
    }
    else {
      return DEFAULT_INITIAL;
    }
  }
  
  @Override
  public QueueDeliverBuilderImpl<M> multiworker(boolean isMultiworker)
  {
    _isMultiworker = isMultiworker;
    
    return this;
  }
  
  @Override
  public boolean isMultiworker()
  {
    return _isMultiworker;
  }
  
  @Override
  public QueueDeliverBuilderImpl<M> multiworkerOffset(int offset)
  {
    _multiworkerOffset = offset;
    
    return this;
  }

  @Override
  public int multiworkerOffset()
  {
    return _multiworkerOffset;
  }

  protected void validateFullBuilder()
  {
    validateBuilder();
  }

  protected void validateBuilder()
  {
  }
  
  public Object getOutboxContext()
  {
    return _outboxContext;
  }
  
  public void setOutboxContext(Object context)
  {
    Objects.requireNonNull(context);
    
    _outboxContext = context;
  }
  
  public Executor getExecutor()
  {
    return _executor;
  }
  
  public void setExecutor(Executor executor)
  {
    Objects.requireNonNull(executor);
    
    _executor = executor;
  }
  
  //@Override
  public ClassLoader getClassLoader()
  {
    return _classLoader;
  }
  
  public void setClassLoader(ClassLoader loader)
  {
    Objects.requireNonNull(loader);
    
    _classLoader = loader;
  }
  
  /*
  public void setWorkerIdleTimeout(long timeout)
  {
    _workerIdleTimeout = timeout;
  }
  */
  
  public void setThreadMax(int max)
  {
    _threadMax = max;
  }
  
  public int getThreadMax()
  {
    return _threadMax;
  }

  /*
  @Override
  public OutboxDeliver<M> createOutbox(Deliver<M> deliver)
  {
    return _outboxFactory.createOutbox(deliver);
  }
  */
  
  @Override
  public QueueDeliver<M> build(Deliver<M> deliver)
  {
    validateBuilder();
    
    if (deliver == null) {
      throw new IllegalArgumentException(L.l("'processors' is required"));
    }
    
    QueueRing<M> queue = buildQueue();
    
    Executor executor = createExecutor();
    ClassLoader loader = getClassLoader();
    
    // OutboxDeliver<M> outbox = _outboxFactory.createOutbox(deliver);
    
    WorkerDeliverSingleThread<M> worker
      = new WorkerDeliverSingleThread<M>(deliver,
                                        _outboxContext,
                                        executor,
                                        loader,
                                        queue);
    
    return new QueueDeliverImpl<M>(queue, worker);
  }
  
  protected QueueRing<M> buildQueue()
  {
    int initial = size();
    int capacity = sizeMax();

    if (initial < capacity && initial > 0) {
      return new QueueRingResizing<>(initial, capacity);
    }
    else {
      return new QueueRingFixed<>(capacity);
    }
  }
  
  //@Override
  public QueueRing<M> buildQueue(CounterBuilder counterBuilder)
  {
    int initial = size();
    int capacity = sizeMax();
    
    if (initial < capacity && initial > 0) {
      return new QueueRingResizing<>(initial, capacity, counterBuilder);
    }
    else {
      return new QueueRingFixed<>(capacity, counterBuilder);
    }
  }
  
  /*
  public QueueService<M> build(DeliverOutbox<M> ...processors)
  {
    return buildMultiworker(processors);
  }
  */
  
  @Override
  public QueueDeliver<M> build(Supplier<Deliver<M>> factory, 
                               int workerCount)
  {
    Objects.requireNonNull(factory);
    validateBuilder();
    
    if (workerCount <= 0) {
      throw new IllegalArgumentException();
    }
    
    if (workerCount == 1) {
      return build(factory.get());
    }

    QueueRing<M> queueDeliver = buildQueue();
    
    //Objects.requireNonNull(processors);
      
    @SuppressWarnings("unchecked")
    WorkerDeliver<M>[] workers = new WorkerDeliver[workerCount];
      
    Executor executor = createExecutor();
    ClassLoader loader = getClassLoader();
      
    for (int i = 0; i < workers.length; i++) {
      Deliver<M> deliver = factory.get();
      
      workers[i] = new WorkerDeliverMultiThread<M>(deliver,
                                                   _outboxContext,
                                                   executor, loader, 
                                                   queueDeliver);
    }
      
    WorkerDeliver<M> worker
      = new WorkerDeliverMultiCoordinator<>(queueDeliver, 
                                          workers,
                                          multiworkerOffset());
    
    return new QueueDeliverImpl<M>(queueDeliver, worker);
  }
  
  @Override
  @SuppressWarnings("unchecked")
  public QueueDeliver<M> disruptor(Deliver<M> ...deliver)
  {
    Objects.requireNonNull(deliver);
    
    int count = deliver.length;
    
    if (count == 0) {
      throw new IllegalArgumentException();
    }
    
    if (count == 1) {
      return build(deliver[0]);
    }
    
    CounterBuilder counter = CounterBuilder.create(count + 1);
    
    QueueRing<M> queue = buildQueue(counter);
    
    // Executor executor = queueBuilder.createExecutor();
    
    WorkerDeliver<M> nextTask = WorkerDeliver.createNull();
    
    WorkerDeliverDisruptor<M> []workers = new WorkerDeliverDisruptor[count];
    
    for (int i = count - 1; i >= 0; i--) {
      boolean isTail = i == count - 1;

      workers[i] = new WorkerDeliverDisruptor<M>(deliver[i],
                                               _outboxContext,
                                               createExecutor(),
                                               _classLoader,
                                               queue,
                                               i,
                                               i + 1,
                                               isTail,
                                               nextTask);
      
      nextTask = workers[i];
    }
    
    workers[count - 1].setHeadWorker(workers[0]);

    return new QueueDeliverImpl<M>(queue, workers[0]);
  }
  
  /*
  public <X extends Runnable> QueueService<X> 
  buildSpawnTask(SpawnThreadManager threadManager)
  {
    validateBuilder();
    
    return new QueueServiceSpawn(buildQueue(), 
                              createBlockingExecutor(),
                              threadManager);
  }
  */
  
  //@Override
  public Executor createExecutor()
  {
    
    Executor executor = _executor;
    
    if (executor == null) {
      ThreadPool threadPool = ThreadPool.current();
          
      //executor = threadPool.getThrottleExecutor();
       executor = threadPool;
    }
    
    return executor;
  }
  
  /*
  private Executor createBlockingExecutor()
  {
    Executor executor = _executor;
    
    if (executor == null) {
      ThreadPool threadPool = ThreadPool.current();
          
      executor = threadPool.throttleExecutor();
      // executor = threadPool;
    }
    
    return executor;
  }
  */
}
