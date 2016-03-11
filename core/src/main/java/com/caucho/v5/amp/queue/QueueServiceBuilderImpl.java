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

import com.caucho.v5.amp.outbox.DeliverOutbox;
import com.caucho.v5.amp.outbox.MessageOutbox;
import com.caucho.v5.amp.outbox.QueueOutbox;
import com.caucho.v5.amp.outbox.QueueService;
import com.caucho.v5.amp.outbox.WorkerOutbox;
import com.caucho.v5.amp.outbox.WorkerOutboxMultiCoordinator;
import com.caucho.v5.amp.outbox.WorkerOutboxMultiThread;
import com.caucho.v5.amp.outbox.WorkerOutboxSingleThread;
import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.util.L10N;

/**
 * Interface for an actor queue
 */
public class QueueServiceBuilderImpl<M extends MessageOutbox<M>>
  extends QueueServiceBuilderBase<M>
{
  private static final L10N L = new L10N(QueueServiceBuilderImpl.class);
  
  private Executor _executor; // = ThreadPool.getCurrent();
  private long _workerIdleTimeout; // = 500L;
  private int _threadMax = 64 * 1024;
  private ClassLoader _classLoader
    = Thread.currentThread().getContextClassLoader();

  //private Supplier<Outbox<M,C>> _outboxFactory;

  private Object _outboxContext;
  
  public QueueServiceBuilderImpl()
  {
    //_outboxFactory = ()->new OutboxImpl<>();
  }
  
  /*
  @Override
  public Supplier<Outbox<M,C>> getOutboxFactory()
  {
    return _outboxFactory;
  }
  
  public void setOutboxFactory(Supplier<Outbox<M,C>> factory)
  {
    Objects.requireNonNull(factory);
    
    _outboxFactory = factory;
  }
  */
  
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
  public QueueService<M> build(DeliverOutbox<M> deliver)
  {
    validateBuilder();
    
    if (deliver == null) {
      throw new IllegalArgumentException(L.l("'processors' is required"));
    }
    
    QueueOutbox<M> queue = buildQueue();
    
    Executor executor = createExecutor();
    ClassLoader loader = getClassLoader();
    
    // OutboxDeliver<M> outbox = _outboxFactory.createOutbox(deliver);
    
    WorkerOutboxSingleThread<M> worker
      = new WorkerOutboxSingleThread<M>(deliver,
                                        _outboxContext,
                                        executor,
                                        loader,
                                        queue);
    
    return new QueueServiceImpl<M>(queue, worker);
  }
  
  protected QueueOutbox<M> buildQueue()
  {
    int initial = initial();
    int capacity = capacity();

    if (initial < capacity && initial > 0) {
      return new QueueRingResizing<>(initial, capacity);
    }
    else {
      return new QueueRing<>(capacity);
    }
  }
  
  @Override
  public QueueOutbox<M> buildQueue(CounterBuilder counterBuilder)
  {
    int initial = initial();
    int capacity = capacity();
    
    if (initial < capacity && initial > 0) {
      return new QueueRingResizing<>(initial, capacity, counterBuilder);
    }
    else {
      return new QueueRing<>(capacity, counterBuilder);
    }
  }
  
  /*
  public QueueService<M> build(DeliverOutbox<M> ...processors)
  {
    return buildMultiworker(processors);
  }
  */
  
  @Override
  public QueueService<M> build(Supplier<DeliverOutbox<M>> factory, 
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

    QueueOutbox<M> queueDeliver = buildQueue();
    
    //Objects.requireNonNull(processors);
      
    WorkerOutbox<M>[] workers = new WorkerOutbox[workerCount];
      
    Executor executor = createExecutor();
    ClassLoader loader = getClassLoader();
      
    for (int i = 0; i < workers.length; i++) {
      DeliverOutbox<M> deliver = factory.get();
      
      workers[i] = new WorkerOutboxMultiThread<M>(deliver,
                                                   _outboxContext,
                                                   executor, loader, 
                                                   queueDeliver);
    }
      
    WorkerOutbox<M> worker
      = new WorkerOutboxMultiCoordinator<>(queueDeliver, 
                                          workers,
                                          multiworkerOffset());
    
    return new QueueServiceImpl<M>(queueDeliver, worker);
  }
  
  /*
  public QueueService<M> buildSpawn(DeliverOutbox<M> processor)
  {
    validateBuilder();
    
    DeliverOutbox<M> spawnProcessor
      = new DeliverAmpSpawn<>(processor, createBlockingExecutor());
    
    return build(spawnProcessor);
  }
  */
  /*
  @Override
  public DisruptorBuilderQueue<M> 
  disruptorBuilder(DeliverFactory<M> actorFactory)
  {
    return new DisruptorBuilderQueueTop<M>(this, actorFactory);
  }
  */
  
  /*
  @Override
  public DisruptorBuilderQueue<M> 
  disruptorBuilder(final DeliverOutbox<M> deliver)
  {
    return new DisruptorBuilderQueueTop<M>(this, new DeliverFactory<M>() {
      public DeliverOutbox<M> get() { return deliver; }
      public int getMaxWorkers() { return 1; }
    });
  }
  */
  
  /*
  //@Override
  public QueueService<M> disruptor2(DeliverOutbox<M> first,
                                    DeliverOutbox<M> ...rest)
  {
    DisruptorBuilderQueue<M> builder = disruptorBuilder(first);
    
    DisruptorBuilderQueue<M> ptr = builder;
    for (DeliverOutbox<M> deliver : rest) {
      ptr = ptr.next(deliver);
    }
    
    return builder.build();
  }
  */
  
  @Override
  public QueueService<M> disruptor(DeliverOutbox<M> ...deliver)
  {
    Objects.requireNonNull(deliver);
    
    int count = deliver.length;
    
    if (count == 0) {
      throw new IllegalArgumentException();
    }
    
    if (count == 1) {
      return build(deliver[0]);
    }
    CounterBuilder counter = new CounterBuilderArray(count + 1);
    
    QueueOutbox<M> queue = buildQueue(counter);
    
    // Executor executor = queueBuilder.createExecutor();
    
    WorkerOutbox<M> nextTask = WorkerOutbox.createNull();
    
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

    return new QueueServiceImpl<M>(queue, workers[0]);
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
