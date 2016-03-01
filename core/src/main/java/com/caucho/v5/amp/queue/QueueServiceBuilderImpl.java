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

package com.caucho.v5.amp.queue;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import com.caucho.v5.amp.queue.DisruptorBuilderQueue.DeliverFactory;
import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.util.L10N;

/**
 * Interface for an actor queue
 */
public class QueueServiceBuilderImpl<M extends MessageDeliver>
  extends QueueServiceBuilderBase<M>
{
  private static final L10N L = new L10N(QueueServiceBuilderImpl.class);
  
  private Executor _executor; // = ThreadPool.getCurrent();
  private long _workerIdleTimeout; // = 500L;
  private int _threadMax = 64 * 1024;
  private ClassLoader _classLoader
    = Thread.currentThread().getContextClassLoader();

  private Supplier<OutboxDeliver<M>> _outboxFactory;

  private OutboxContext<M> _outboxContext;
  
  public QueueServiceBuilderImpl()
  {
    _outboxFactory = ()->new OutboxDeliverImpl<>();
  }
  
  @Override
  public Supplier<OutboxDeliver<M>> getOutboxFactory()
  {
    return _outboxFactory;
  }
  
  public void setOutboxFactory(Supplier<OutboxDeliver<M>> factory)
  {
    Objects.requireNonNull(factory);
    
    _outboxFactory = factory;
  }
  
  public OutboxContext<M> getOutboxContext()
  {
    return _outboxContext;
  }
  
  public void setOutboxContext(OutboxContext<M> context)
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
  
  @Override
  public ClassLoader getClassLoader()
  {
    return _classLoader;
  }
  
  public void setClassLoader(ClassLoader loader)
  {
    Objects.requireNonNull(loader);
    
    _classLoader = loader;
  }
  
  public void setWorkerIdleTimeout(long timeout)
  {
    _workerIdleTimeout = timeout;
  }
  
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
  public QueueService<M> build(Deliver<M> deliver)
  {
    validateBuilder();
    
    if (deliver == null) {
      throw new IllegalArgumentException(L.l("'processors' is required"));
    }
    
    QueueDeliver<M> queue = buildQueue();
    
    Executor executor = createExecutor();
    ClassLoader loader = getClassLoader();
    
    // OutboxDeliver<M> outbox = _outboxFactory.createOutbox(deliver);
    
    WorkerDeliverSingleThread<M> worker
      = new WorkerDeliverSingleThread<>(deliver,
                                        _outboxFactory,
                                        _outboxContext,
                                        executor,
                                        loader,
                                        queue);
    
    return new QueueServiceImpl<M>(queue, worker);
  }
  
  protected QueueDeliver<M> buildQueue()
  {
    int initial = getInitial();
    int capacity = getCapacity();

    if (initial < capacity && initial > 0) {
      return new QueueRingResizing<>(initial, capacity);
    }
    else {
      return new QueueRing<>(capacity);
    }
  }
  
  @Override
  public QueueDeliver<M> buildQueue(CounterBuilder counterBuilder)
  {
    int initial = getInitial();
    int capacity = getCapacity();
    
    if (initial < capacity && initial > 0) {
      return new QueueRingResizing<>(initial, capacity, counterBuilder);
    }
    else {
      return new QueueRing<>(capacity, counterBuilder);
    }
  }
  
  public QueueService<M> build(Deliver<M> ...processors)
  {
    return buildMultiworker(processors);
  }
  
  public QueueService<M> buildMultiworker(Deliver<M> ...processors)
  {
    validateBuilder();
    
    if (processors.length == 1) {
      return build(processors[0]);
    }

    QueueDeliver<M> queueDeliver = buildQueue();
    Objects.requireNonNull(processors);
      
    WorkerDeliverLifecycle[] workers
      = new WorkerDeliverLifecycle[processors.length];
      
    Executor executor = createExecutor();
    ClassLoader loader = getClassLoader();
      
    for (int i = 0; i < workers.length; i++) {
      Deliver<M> deliver = processors[i];
      
      //OutboxDeliver<M> outbox = _outboxFactory.createOutbox(deliver);
      
      workers[i] = new WorkerDeliverMultiThread<M>(deliver,
                                                   _outboxFactory,
                                                   _outboxContext,
                                                   executor, loader, 
                                                   queueDeliver);
    }
      
    WorkerDeliverLifecycle worker
      = new WorkerDeliverMultiCoordinator(queueDeliver, 
                                          workers,
                                          getMultiworkerOffset());
    
    return new QueueServiceImpl<M>(queueDeliver, worker);
  }
  
  public QueueService<M> buildSpawn(Deliver<M> processor)
  {
    validateBuilder();
    
    Deliver<M> spawnProcessor
      = new DeliverAmpSpawn<>(processor, createBlockingExecutor());
    
    return build(spawnProcessor);
  }
  
  @Override
  public DisruptorBuilderQueue<M> 
  disruptorBuilder(DeliverFactory<M> actorFactory)
  {
    return new DisruptorBuilderQueueTop<M>(this, actorFactory);
  }
  
  @Override
  public DisruptorBuilderQueue<M> 
  disruptorBuilder(final Deliver<M> deliver)
  {
    return new DisruptorBuilderQueueTop<M>(this, new DeliverFactory<M>() {
      public Deliver<M> get() { return deliver; }
      public int getMaxWorkers() { return 1; }
    });
  }
  
  public <X extends Runnable> QueueService<X> 
  buildSpawnTask(SpawnThreadManager threadManager)
  {
    validateBuilder();
    
    return new QueueServiceSpawn(buildQueue(), 
                              createBlockingExecutor(),
                              threadManager);
  }
  
  @Override
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
  
  private Executor createBlockingExecutor()
  {
    Executor executor = _executor;
    
    if (executor == null) {
      ThreadPool threadPool = ThreadPool.current();
          
      executor = threadPool.getThrottleExecutor();
      // executor = threadPool;
    }
    
    return executor;
  }
}
