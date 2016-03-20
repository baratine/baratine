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

package com.caucho.v5.amp.inbox;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.deliver.Deliver;
import com.caucho.v5.amp.deliver.QueueDeliver;
import com.caucho.v5.amp.deliver.QueueDeliverBuilderImpl;
import com.caucho.v5.amp.deliver.WorkerDeliver;
import com.caucho.v5.amp.deliver.WorkerDeliverMultiThread;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.message.InboxMessage;
import com.caucho.v5.amp.message.OnActiveMessage;
import com.caucho.v5.amp.message.OnActiveReplayMessage;
import com.caucho.v5.amp.message.OnInitMessage;
import com.caucho.v5.amp.message.OnShutdownMessage;
import com.caucho.v5.amp.message.ReplayMessage;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.service.ServiceRefCore;
import com.caucho.v5.amp.service.ServiceRefPublic;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.L10N;

import io.baratine.service.QueueFullHandler;
import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionClosed;

/**
 * inbox for a service.
 */
public class InboxQueue extends InboxBase
{
  private static final L10N L = new L10N(InboxQueue.class);
  private static final Logger log
    = Logger.getLogger(InboxQueue.class.getName());

  private final ServiceRefAmp _serviceRef;

  private final String _anonAddress;
  private String _bindAddress;

  private final QueueDeliver<MessageAmp> _queue;
  private final ActorAmp _actor;
  private final WorkerDeliver<MessageAmp> _worker;
  
  private final boolean _isLifecycleAware;
  
  private final Lifecycle _lifecycle = new Lifecycle();

  private final long _sendTimeout;
  private final QueueFullHandler _fullHandler;

  private boolean _isFiner;

  private InboxQueueReplyOverflow _replyOverflowQueue;

  private final InboxMessage _inboxMessage;

  public InboxQueue(ServiceManagerAmp manager,
                    QueueDeliverBuilderImpl<MessageAmp> queueBuilder,
                    QueueServiceFactoryInbox serviceQueueFactory,
                    ServiceConfig config)
  {
    super(manager);

    _isFiner = log.isLoggable(Level.FINER);

    _inboxMessage = new InboxMessage(this);
    
    ActorAmp actor = serviceQueueFactory.getMainActor();

    // String address = "anon:" + actor.getApiClass().getName();
    String name = actor.getName();
    // String address = serviceQueueFactory.getName();

    if (name != null) {
    }
    else if (config.address() != null) {
      name = config.address();
    }
    else if (config.name() != null) {
      name = config.name();
    }
    else {
      name = "anon:" + actor.getApiClass().getSimpleName();
    }

    _anonAddress = name;

    if (config.isPublic()) {
      _serviceRef = new ServiceRefPublic(actor, this);
    }
    else {
      _serviceRef = new ServiceRefCore(actor, this);
    }
    
    // queueBuilder.setOutboxContext(new OutboxContextAmpImpl(this));
    queueBuilder.setOutboxContext(this);

    _queue = serviceQueueFactory.build(queueBuilder, this);

    _worker = worker(_queue);

    _actor = actor;
    
    _isLifecycleAware = actor.isLifecycleAware() || ! _queue.isSingleWorker();

    long timeout = config.queueTimeout();

    if (timeout < 0) {
      timeout = 60 * 1000L;
    }

    _sendTimeout = timeout;

    QueueFullHandler handler = config.queueFullHandler();

    if (handler == null) {
      handler = manager.getQueueFullHandler();
    }

    _fullHandler = handler;

    // start(actor);
  }
  
  
  @SuppressWarnings("unchecked")
  private WorkerDeliver<MessageAmp> worker(QueueDeliver<MessageAmp> queue)
  {
    return (WorkerDeliver<MessageAmp>) queue.worker();
  }

  MessageAmp getMessageInbox()
  {
    return _inboxMessage;
  }

  /*
  @Override
  public OutboxAmp getContextOutbox()
  {
    return (OutboxAmp) _worker.getContextOutbox();
  }
  */

  @Override
  public boolean isLifecycleAware()
  {
    return _isLifecycleAware;
  }
  
  /**
   * Start is called lazily when a service is first used.
   */
  @Override
  public void start()
  {
    if (_lifecycle.isActive()) {
      return;
    }
    
    synchronized (_lifecycle) {
      if (! _lifecycle.toInit()) {
        return;
      }
      
      init(_actor);
    }
    
    start(_actor);
  }
  
  /**
   * init is called lazily when a service is first used.
   */
  @Override
  public void init()
  {
    if (_lifecycle.isAfterInit()) {
      return;
    }
    
    start();
    
    _lifecycle.waitForActive(10000);
  }

  /**
   * Init calls the @OnInit methods.
   */
  private void init(ActorAmp actor)
  {
    //BuildMessageAmp buildMessage = new BuildMessageAmp(this);

    OnInitMessage onInitMsg = new OnInitMessage(this);
      
    _queue.offer(onInitMsg);
    _queue.wake();
  }

  private void start(ActorAmp actor)
  {
    if (! _lifecycle.toStarting()) {
      return;
    }
    
    // OutboxAmpBase buildContext = new OutboxAmpBase();

    //BuildMessageAmp buildMessage = new BuildMessageAmp(this);
    // MessageAmp oldMessage = ContextMessageAmp.getAndSet(buildMessage);

    // Outbox<MessageAmp> oldContext = OutboxThreadLocal.getCurrent();

    try {
      // OutboxThreadLocal.setCurrent(buildContext);
      
      MessageAmp onActiveMsg = new OnActiveMessage(this, isSingle());
      
      JournalAmp journal = actor.getJournal();
      
      // buildContext.setMessage(onActiveMsg);
      
      if (journal != null) {
        if (log.isLoggable(Level.FINER)) {
          log.finer(L.l("journal replay {0} ({1})",
                        serviceRef().address(),
                        serviceRef().apiClass().getName()));
        }

        onActiveMsg = new OnActiveReplayMessage(this, isSingle());

        ActiveResult activeResult = new ActiveResult(onActiveMsg);

        ReplayMessage replayMsg
          = new ReplayMessage(this, _queue, activeResult);

        // buildContext.setMessage(replayMsg);

        _queue.offer(replayMsg);
        _queue.wake();
      }
      else {
        // _worker.onActive();
        //_queue.offer(onActiveMsg);
        _lifecycle.toActive();
        //_queue.wake();
      }
    } finally {
      // OutboxThreadLocal.setCurrent(oldContext);
      // ContextMessageAmp.set(oldMessage);
    }
  }
  
  @Override
  protected boolean isSingle()
  {
    return ! (_worker instanceof WorkerDeliverMultiThread);
  }

  public Supplier<Deliver<MessageAmp>>
  createDeliverFactory(Supplier<ActorAmp> supplierActor,
                       ServiceConfig config)
  {
    return new DeliverInboxFactory(this,
                                       supplierActor,
                                       config);
  }
  
  public Deliver<MessageAmp> createDeliver(ActorAmp actor)
  {
    boolean isDebug = manager().isDebug() || log.isLoggable(Level.FINE);

    if (isDebug) {
      return new DeliverInboxDebug(this, actor);
    }
    else {
      return new DeliverInbox(this, actor);
    }
  }

  @Override
  public final ServiceRefAmp serviceRef()
  {
    return _serviceRef;
  }

  @Override
  public String getAddress()
  {
    if (_bindAddress != null) {
      return _bindAddress;
    }
    else {
      return _anonAddress;
    }
  }
  
  public String getDebugName()
  {
    return _actor.getApiClass().getSimpleName() + "-" + manager().getDebugId();
  }

  @Override
  public boolean bind(String address)
  {
    if (_bindAddress == null) {
      _bindAddress = address;
      
      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public final long getSize()
  {
    int size = _queue.size();
    
    return size;
  }

  protected final QueueDeliver<MessageAmp> getQueue()
  {
    return _queue;
  }

  @Override
  public final HeadersAmp createHeaders(HeadersAmp headers,
                                        ServiceRefAmp serviceRef,
                                        MethodAmp method)
  {
    if (_isFiner || headers.getSize() > 0) {
      int size = headers.getSize();

      if (size > 100 && size < 120) {
        log.warning("Possible cycle: " + method+ " " + this + " " + headers);
      }

      int count = 2;

      int index = (size / count) + 1;
      headers = headers.add("service." + index, serviceRef.address());
      headers = headers.add("method." + index, method.name());
    }

    return headers;
  }

  @Override
  public ActorAmp getDirectActor()
  {
    return _actor;
  }

  @Override
  public boolean offer(MessageAmp message, long callerTimeout)
  {
    if (_lifecycle.isAfterStopping()) {
      message.fail(new ServiceExceptionClosed(L.l("closed {0} for message {1}",
                                                    serviceRef(), message)));
      
      return true;
    }
    
    QueueDeliver<MessageAmp> queue = _queue;
    
    long timeout = Math.min(_sendTimeout, callerTimeout);

    boolean result = queue.offer(message,
                                 timeout, TimeUnit.MILLISECONDS);

    if (! result) {
      _fullHandler.onQueueFull(serviceRef(),
                               queue.size(),
                               timeout, TimeUnit.MILLISECONDS,
                               message);

      return false;
    }

    return result;
  }

  @Override
  public boolean offerResult(MessageAmp message)
  {
    if (_lifecycle.isAfterStopping()) {
      message.fail(new ServiceExceptionClosed(L.l("closed {0} for message {1} and inbox {2}",
                                                  serviceRef(), message, this)));
      
      return true;
    }
    
    QueueDeliver<MessageAmp> queue = _queue;

    boolean value = queue.offer(message, 0, TimeUnit.MILLISECONDS);
    //boolean value = queue.offer(message, 10, TimeUnit.MILLISECONDS, 0);

    // XXX: offer shouldn't automatically wake
    // queue.wake();

    if (! value) {
      InboxQueueReplyOverflow overflowQueue;

      synchronized (this) {
        overflowQueue = _replyOverflowQueue;

        if (overflowQueue == null) {
          _replyOverflowQueue = overflowQueue = new InboxQueueReplyOverflow(queue);
        }
      }

      return overflowQueue.offer(message);
    }


    return value;

    // offerAndWake(message);

    // return true;
  }

  @Override
  public final void offerAndWake(MessageAmp message, long callerTimeout)
  {
    if (isClosed()) { // _lifecycle.isAfterStopping()) {
      message.fail(new ServiceExceptionClosed(L.l("Closed {0}, actor {1} for message {2}",
                                                    serviceRef(),
                                                    _actor,
                                                    message)));
      
      return;
    }
    
    QueueDeliver<MessageAmp> queue = _queue;
    
    long timeout = Math.min(callerTimeout, _sendTimeout);

    if (! queue.offer(message, timeout, TimeUnit.MILLISECONDS)) {
      // ThreadDump.create().dumpThreads();
      _fullHandler.onQueueFull(serviceRef(),
                               queue.size(),
                               timeout, TimeUnit.MILLISECONDS,
                               message);
    }

    queue.wake();
  }

  @Override
  public final WorkerDeliver<MessageAmp> worker()
  {
    return _worker;
  }

  @Override
  public boolean isClosed()
  {
    return _lifecycle.isDestroyed();
  }
  
  /**
   * Closes the inbox
   */
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    if (! _lifecycle.toStopping()) {
      return;
    }
    
    _lifecycle.toDestroy();
    
    OnShutdownMessage shutdownMessage = new OnShutdownMessage(this, mode, isSingle());
    
    _queue.offer(shutdownMessage);
    
    // _queue.close();
    /*
    for (Actor<?> actorProcessor : _queueActors) {
      actorProcessor.close();
    }
    */
    
    _queue.wakeAllAndWait();

    shutdownMessage.waitFor(1, TimeUnit.SECONDS);
    
    super.shutdown(mode);

    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(manager())) {
      Object ctx = outbox.getAndSetContext(this);
      
      try {
        outbox.flush();
        
        if (! isSingle()) {
          _worker.shutdown(mode);
        }
      } finally {
        outbox.getAndSetContext(ctx);
      }
    }

    // XXX: _worker.shutdown(ShutdownModeAmp.IMMEDIATE);
  }

  void wakeAll()
  {
    _queue.wakeAll();
  }

  public void wakeAllAndWait()
  {
    _queue.wakeAllAndWait();
  }

  @Override
  public void onActive()
  {
    // _worker.onActive();
  }

  @Override
  public void onInit()
  {
    // _worker.onInit();
  }

  @Override
  public void shutdownActors(ShutdownModeAmp mode)
  {
    _worker.shutdown(mode);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + serviceRef().address() + "]";
  }
  
  private class ActiveResult implements Result<Boolean> {
    private MessageAmp _onActiveMsg;
    
    ActiveResult(MessageAmp onActiveMsg)
    {
      _onActiveMsg = onActiveMsg;
    }
    
    @Override
    public boolean isFuture()
    {
      return true;
    }

    @Override
    public void ok(Boolean result)
    {
      //_worker.onActive();
      _queue.offer(_onActiveMsg);
      _lifecycle.toActive();
      _queue.wake();
    }

    @Override
    public void fail(Throwable exn)
    {
      //_worker.onActive();
      _lifecycle.toActive();
      _onActiveMsg.fail(exn);
    }

    @Override
    public void handle(Boolean result, Throwable exn)
    {
      if (exn != null) {
        fail(exn);
      }
      else {
        ok(result);
      }
    }
  }

  @Override
  public MessageAmp getMessage()
  {
    return _inboxMessage;
  }

  /*
  @Override
  public InboxAmp getInbox()
  {
    return this;
  }

  @Override
  public void setInbox(InboxAmp inbox)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void setMessage(MessageAmp message)
  {
    // TODO Auto-generated method stub
    
  }
  */
}
