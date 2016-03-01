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

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Interface for an actor queue
 */
public class DisruptorBuilderQueueNode<M extends MessageDeliver>
  extends DisruptorBuilderQueueBase<M>
{
  private final DisruptorBuilderQueueTop<M> _top;
  private final DeliverFactory<M> _actorFactory;
  
  private ArrayList<DisruptorBuilderQueueNode<M>> _peers = new ArrayList<>();
  
  private DisruptorBuilderQueueNode<M> _next;
  
  DisruptorBuilderQueueNode(DisruptorBuilderQueueTop<M> top,
                       DeliverFactory<M> actorFactory)
  {
    Objects.requireNonNull(top);
    Objects.requireNonNull(actorFactory);
    
    _top = top;
    _actorFactory = actorFactory;
  }
  
  private DisruptorBuilderQueueNode(DisruptorBuilderQueueTop<M> top,
                               DeliverFactory<M> actorFactory,
                               ArrayList<DisruptorBuilderQueueNode<M>> peers,
                               DisruptorBuilderQueueNode<M> next)
  {
    Objects.requireNonNull(top);
    Objects.requireNonNull(actorFactory);
    
    _top = top;
    _actorFactory = actorFactory;
    _peers = peers;
    _next = next;
  }
  
  @Override
  protected DisruptorBuilderQueueTop<M> getTop()
  {
    return _top;
  }
  
  protected ArrayList<DisruptorBuilderQueueNode<M>> getPeers()
  {
    return _peers;
  }
  
  protected void setNext(DisruptorBuilderQueueNode<M> next)
  {
    if (_next != null) {
      throw new IllegalStateException();
    }
    
    _next = next;
  }
  
  protected DisruptorBuilderQueueNode<M> getNext()
  {
    return _next;
  }
  
  @Override
  public DisruptorBuilderQueueNode<M> peer(DeliverFactory<M> actorFactory)
  {
    DisruptorBuilderQueueNode<M> peer = new DisruptorBuilderQueueNode<>(_top, actorFactory);
    
    _peers.add(peer);
    
    return peer;
  }
  
  @Override
  public DisruptorBuilderQueueNode<M> next(DeliverFactory<M> actorFactory)
  {
    if (_next != null) {
      throw new IllegalStateException();
    }
    
    DisruptorBuilderQueueNode<M> next = new DisruptorBuilderQueueNode<>(_top, actorFactory);
    
    _next = next;
    
    return next;
  }
  
  private DisruptorBuilderQueueNode<M> normalize()
  {
    ArrayList<DisruptorBuilderQueueNode<M>> peers = new ArrayList<>();
    
    for (DisruptorBuilderQueueNode<M> peer : _peers) {
      peer.normalize(peers);
    }
    
    return new DisruptorBuilderQueueNode<M>(_top, _actorFactory, 
                                       peers,
                                       _next.normalize());
  }
  
  private void normalize(ArrayList<DisruptorBuilderQueueNode<M>> peers)
  {
    if (_next != null) {
      peers.add(normalize());
    }
    else {
      peers.add(new DisruptorBuilderQueueNode<M>(_top, _actorFactory));
      
      for (DisruptorBuilderQueueNode<M> peer : _peers) {
        peer.normalize(peers);
      }
    }
  }
  
  @Override
  public CounterBuilder createCounterBuilder(CounterBuilder head,
                                                  int index)
  {
    CounterBuilder self;
    
    int workers = _actorFactory.getMaxWorkers();
    
    if (workers > 1) {
      self = new CounterBuilderMultiWorker(index++, workers); 
    }
    else {
      self = new CounterBuilderAtomic(index++);
    }
    
    CounterBuilder first;
    
    if (_peers.size() == 0) {
      first = self; 
    }
    else {
      ArrayList<CounterBuilder> counters = new ArrayList<>();

      counters.add(self);
      
      for (DisruptorBuilderQueueNode<M> peer : _peers) {
        CounterBuilder counter = peer.createCounterBuilder(head, index);
        
        counters.add(counter);
        
        index = counter.getTail().getTailIndex() + 1;
      }
      
      first = new CounterBuilderParallel(counters, index++);
    }
    
    if (_next != null) {
      CounterBuilder rest = _next.createCounterBuilder(first, index);
      
      return new CounterBuilderSequence(first, rest);
    }
    else if (_peers.size() > 0) {
      // dummy counter for the dummy tail join()
      CounterBuilder rest = new CounterBuilderAtomic(index);
      
      return new CounterBuilderSequence(first, rest);
    }
    else {
      return first;
    }
  }

  @Override
  public WorkerDeliverLifecycle<M> build(QueueDeliver<M> queue,
                                         CounterBuilder headBuilder,
                                         CounterBuilder tailBuilder,
                                         WorkerDeliverLifecycle<M> nextTask,
                                         QueueDeliverBuilder<M> queueBuilder,
                                         boolean isTail)
  {
    if (_next != null) {
      CounterBuilderSequence seqBuilder
        = (CounterBuilderSequence) tailBuilder;
    
      CounterBuilder first = seqBuilder.getFirst();
      CounterBuilder rest = seqBuilder.getRest();
      
      nextTask = _next.build(queue,
                             first,
                             rest,
                             nextTask,
                             queueBuilder,
                             isTail);

      tailBuilder = first;
      
      isTail = false;
    }
    
    if (_peers.size() > 0) {
      if (isTail) {
        // dummy tail worker to consume the entry if a fork is
        // specified, but no followers exist.
        
        CounterBuilderSequence seqBuilder
          = (CounterBuilderSequence) tailBuilder;
      
        CounterBuilder first = seqBuilder.getFirst();
        CounterBuilder rest = seqBuilder.getRest();
        
        nextTask = buildDummyTailImpl(queue, 
                                      first,
                                      rest,
                                      nextTask,
                                      queueBuilder.createExecutor(),
                                      queueBuilder.getClassLoader());

        tailBuilder = first;
        isTail = false;
      }
      
      CounterBuilderParallel builder
        = (CounterBuilderParallel) tailBuilder;
      
      CounterBuilder[] children = builder.getChildren();
      
      WorkerDeliverLifecycle<M> self;
      
      self = buildImpl(queue, 
                       headBuilder, 
                       children[0],
                       nextTask,
                       queueBuilder, 
                       false);
      
      ArrayList<WorkerDeliverLifecycle<M>> workers = new ArrayList<>();
      workers.add(self);
      
      for (int i = 0; i < _peers.size(); i++) {
        DisruptorBuilderQueueNode<M> peer = _peers.get(i);
        
        WorkerDeliverLifecycle<M> worker;
        
        worker = peer.build(queue, 
                            headBuilder, 
                            children[i + 1],
                            nextTask,
                            queueBuilder, 
                            false);
        
        workers.add(worker);
      }
      
      WorkerDeliverLifecycle<M> joinWorker = new WorkerDeliverDisruptorJoin<>(workers);

      return joinWorker;
    }
    else {
      return buildImpl(queue, 
                       headBuilder, 
                       tailBuilder, 
                       nextTask,
                       queueBuilder, 
                       isTail);
    }
  }

  WorkerDeliverLifecycle<M>
  buildImpl(QueueDeliver<M> queue,
            CounterBuilder prev,
            CounterBuilder next,
            WorkerDeliverLifecycle<M> nextTask,
            QueueDeliverBuilder<M> queueBuilder,
            boolean isTail)
  {
    Executor executor = queueBuilder.createExecutor();
    ClassLoader loader = queueBuilder.getClassLoader();
    
    int workers = _actorFactory.getMaxWorkers();
    
    WorkerDeliverMessage<M> worker;
    
    Supplier<OutboxDeliver<M>> outboxFactory = queueBuilder.getOutboxFactory();
    OutboxContext<M> outboxContext = queueBuilder.getOutboxContext();
    
    
    if (workers <= 1) {
      Deliver<M> deliver = _actorFactory.get();
      
      worker = new WorkerDeliverDisruptor<M>(_actorFactory.get(),
                                             outboxFactory,
                                             outboxContext,
                                             executor,
                                             loader,
                                             queue,
                                             prev.getTailIndex(),
                                             next.getHeadIndex(), 
                                             isTail,
                                             nextTask);
    }
    else if (isTail) {
      WorkerDeliverMessage<M>[]subworkers
        = new WorkerDeliverMessage[workers];
    
      for (int i = 0; i < workers; i++) { 
        Deliver<M> deliver = _actorFactory.get();
        
        subworkers[i] = new WorkerDeliverDisruptorMultiTail<M>(_actorFactory.get(),
                                                               outboxFactory,
                                                               outboxContext,
                                                               executor,
                                                               loader,
                                                               queue,
                                                               prev.getTailIndex(),
                                                               next.getHeadIndex(),
                                                               nextTask);
      }
    
      worker = new WorkerDeliverDisruptorMultiWorker<M>(queue, subworkers);
    }

    else {
      WorkerDeliverMessage<M> []subworkers
        = new WorkerDeliverMessage[workers];

      for (int i = 0; i < workers; i++) {
        Deliver<M> deliver = _actorFactory.get();
        
        subworkers[i] = new WorkerDeliverDisruptorMulti<M>(deliver,
                                                           outboxFactory,
                                                           outboxContext,
                                                           executor,
                                                           loader,
                                                           queue,
                                                           prev.getTailIndex(),
                                                           next.getHeadIndex(),
                                                           isTail,
                                                           nextTask);
      }
    
      worker = new WorkerDeliverDisruptorMultiWorker<M>(queue, subworkers);
    }
    
    return worker;
  }

  WorkerDeliverLifecycle<M>
  buildDummyTailImpl(QueueDeliver<M> queue,
                     CounterBuilder prev,
                     CounterBuilder next,
                     WorkerDeliverLifecycle<M> nextTask,
                     Executor executor,
                     ClassLoader loader)
  {
    // ContextOutbox<M> threadManager = new ContextOutbox<>();
    
    DeliverAmpBase<M> deliver = new DeliverAmpBase<M>();
    
    Supplier<OutboxDeliver<M>> outboxFactory = null;
    OutboxContext<M> outboxContext = null;
    
    boolean isTail = true;
    
    WorkerDeliverDisruptor<M> worker;

    worker = new WorkerDeliverDisruptor<M>(deliver,
                                           outboxFactory,
                                           outboxContext,
                                           executor,
                                           loader,
                                           queue,
                                           prev.getTailIndex(),
                                           next.getHeadIndex(), 
                                           isTail,
                                           nextTask);
    
    return worker;
  }

  public WorkerDeliverLifecycle<M>
  buildSingle(QueueDeliver<M> queue,
              QueueDeliverBuilder<M> queueBuilder)
  {
    Executor executor = queueBuilder.createExecutor();
    ClassLoader loader = queueBuilder.getClassLoader();
    
    WorkerDeliverLifecycle<M> worker;
    
    Supplier<OutboxDeliver<M>> outboxFactory = queueBuilder.getOutboxFactory();
    OutboxContext<M> outboxContext = queueBuilder.getOutboxContext();
    
    int workers = _actorFactory.getMaxWorkers();
    
    if (workers <= 1) {
      Deliver<M> deliver = _actorFactory.get();
      
      worker = new WorkerDeliverSingleThread<M>(deliver,
                                                outboxFactory,
                                                outboxContext,
                                                executor,
                                                loader,
                                                queue);
    }
    else {
      WorkerDeliverMessage<M> []subworkers = new WorkerDeliverMessage[workers];
      
      for (int i = 0; i < workers; i++) {
        Deliver<M> deliver = _actorFactory.get();
        
        subworkers[i] = new WorkerDeliverMultiThread<M>(deliver,
                                                        outboxFactory,
                                                        outboxContext,
                                                        executor,
                                                        loader,
                                                        queue);
      }
      
      worker = new WorkerDeliverDisruptorMultiWorker<M>(queue, subworkers);
    }
    
    return worker;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _actorFactory + "]";
  }
}
