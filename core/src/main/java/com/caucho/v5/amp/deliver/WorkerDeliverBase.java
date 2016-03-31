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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.thread.RunnableItem;
import com.caucho.v5.amp.thread.RunnableItemScheduler;
import com.caucho.v5.amp.thread.ThreadAmp;
import com.caucho.v5.util.CurrentTime;

import io.baratine.service.ResultFuture;

/**
 * Base worker for delivering messages.
 * 
 * The worker is a single Runnable that can be launched with a wake() call.
 * A new thread will be assigned if the worker is currently idle, otherwise
 * the current thread is used.
 */
public abstract class WorkerDeliverBase<M> // extends MessageOutbox<M>>
  implements WorkerDeliver<M>, Runnable
{
  private static final Logger log
    = Logger.getLogger(WorkerDeliverBase.class.getName());
  
  private final Deliver<M> _deliver;
  
  //private final Supplier<Outbox> _outboxFactory;  
  private final Object _context;
  
  /*
  private final AtomicReference<OutboxDeliver<M>> _outboxRef
    = new AtomicReference<>();
    */
  
  private final AtomicLong _startCount = new AtomicLong();
  private final AtomicLong _launchCount = new AtomicLong();
  private final AtomicLong _endCount = new AtomicLong();
  
  private final AtomicReference<State> _stateRef
    = new AtomicReference<>(State.IDLE);

  private final ClassLoader _classLoader;
  private final Launcher _launcher;

  protected WorkerDeliverBase(Deliver<M> deliver,
                             Object context,
                             Executor executor,
                             ClassLoader classLoader)
  {
    Objects.requireNonNull(deliver);
    Objects.requireNonNull(executor);
    Objects.requireNonNull(classLoader);
    
    _deliver = deliver;
    
    _classLoader = classLoader;
    
    _launcher = createLauncher(executor, this);
    
    _context = context;
  }
  
  protected final Object context()
  {
    return _context;
  }
    
  abstract protected void runImpl(Outbox outbox, M tailMsg)
    throws Exception;
  
  protected boolean isEmpty()
  {
    return false;
  }
  
  private boolean isDebug()
  {
    return log.isLoggable(Level.FINER) || ! CurrentTime.isTest();
  }
  
  private static Launcher createLauncher(Executor executor,
                                         Runnable task)
  {
    if (executor instanceof RunnableItemScheduler) {
      RunnableItemScheduler scheduler = (RunnableItemScheduler) executor;
      
      return new SchedulerLauncher(scheduler, task);
    }
    else {
      return new ExecutorLauncher(executor, task);
    }
  }
  
  public final boolean isTaskActive()
  {
    return _stateRef.get().isActive();
  }
  
  public final String getState()
  {
    return (_stateRef.get().toString() + " " + _launchCount.get()
            + ":" + _startCount.get() + ":" + _endCount.get());
  }
  
  public boolean isClosed()
  {
    return _stateRef.get().isClosed();
  }
  
  /*
  @Override
  public void onInit()
  {
    
  }
  
  @Override
  public void onActive()
  {
    
  }
  */

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    if (mode == ShutdownModeAmp.IMMEDIATE) {
      _stateRef.set(State.CLOSED);
    }
  }

  @Override
  public final void run()
  {
    try {
      _startCount.incrementAndGet();
      
      _stateRef.set(State.ACTIVE);
      
      ThreadAmp thread = (ThreadAmp) Thread.currentThread();
      /// XXX: check outbox type
      Outbox outbox = thread.outbox();
      
      runStarted(outbox, null);
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
      System.out.println(getClass().getSimpleName() + ": " + e);
    } finally {
      _endCount.incrementAndGet();
    }
  }
  
  @Override
  public boolean runAs(Outbox outbox, M tailMsg)
  {
    if (toStart()) {
      runStarted(outbox, tailMsg);
      return true;
    }
    else {
      return false;
      /*
      long timeout = 10000;
      
      tailMsg.offerQueue(timeout);
      
      if (wakeSelf()) {
        _stateRef.set(State.ACTIVE);
        
        runStarted(outbox, null);
      }
      */
    }
  }
  
  @Override
  public boolean runOne(Outbox outbox, M tailMsg)
  {
    Objects.requireNonNull(tailMsg);
    
    if (isRunOneValid() && toStart()) {
      runOneStarted(outbox, tailMsg);
      return true;
    }
    else {
      return false;
    }
  }
  
  protected boolean isRunOneValid()
  {
    return false;
  }
  
  protected void runOneImpl(Outbox outbox, M tailMsg) throws Exception
  {
    throw new IllegalStateException(getClass().getName());
  }
    
  private void runStarted(Outbox outbox, M tailMsg)
  {
    ClassLoader classLoader = _classLoader;
    Thread thread = Thread.currentThread();
    boolean isDebug = false;
    String oldThreadName = null;
    
    Object oldContext = outbox.getAndSetContext(context());
    
    try {
      thread.setContextClassLoader(classLoader);
      isDebug = isDebug();
      
      if (isDebug) {
        oldThreadName = thread.getName();
        thread.setName(_deliver.getName());
      }
      
      AtomicReference<State> stateRef = _stateRef;
      
      while (true) {
        runImpl(outbox, tailMsg);
        
        tailMsg = null;
        // tailMsg = outbox.flushAfterTask();
        
        State state = stateRef.get();
        State stateIdle = state.toIdle();
        
        if (state.isClosed() || stateIdle.isIdle()) {
          return;
        }
          
        stateRef.compareAndSet(state, State.ACTIVE);
        //thread.setContextClassLoader(classLoader);
      }
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
      return;
    } finally {
      outbox.getAndSetContext(oldContext);
      // ContextOutbox.setCurrent(null);
      
      toIdle();
      
      if (isDebug) {
        thread.setName(oldThreadName);
      }
    }
  }
  
  private void runOneStarted(Outbox outbox, M tailMsg)
  {
    ClassLoader classLoader = _classLoader;
    Thread thread = Thread.currentThread();
    boolean isDebug = false;
    String oldThreadName = null;
    
    ClassLoader oldLoader = thread.getContextClassLoader();
    //OutboxDeliver<Object> oldOutbox = ContextOutbox.getCurrent();
    Object oldContext = outbox.getAndSetContext(context());
  
    try {
      thread.setContextClassLoader(classLoader);
      isDebug = isDebug();
    
      if (isDebug) {
        oldThreadName = thread.getName();
        thread.setName(_deliver.getName());
      }
    
      //OutboxDeliverMessage<M> outbox = getOutbox();
    
      //ContextOutbox.setCurrent(outbox);
      
      

      runOneImpl(outbox, tailMsg);
      
      // outbox.flush();
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
      
      outbox.flush();
    } finally {
      thread.setContextClassLoader(oldLoader);

      outbox.getAndSetContext(oldContext);
      //ContextOutbox.setCurrent(oldOutbox);
    
      toIdle();
    
      if (isDebug) {
        thread.setName(oldThreadName);
      }
    }
  }
  
  private boolean toStart()
  {
    final AtomicReference<State> stateRef = _stateRef;
    
    State oldState;
    State newState;
    
    do {
      oldState = stateRef.get();
      
      if (! oldState.isIdle()) {
        return false;
      }
      
      newState = oldState.toStart();
    } while (! stateRef.compareAndSet(oldState, newState));

    return true;
  }

  @Override
  public final boolean wake()
  {
    if (wakeSelf()) {
      launch();

      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public final void wakeAll()
  {
    wake();
  }
  
  /**
   * Wakes all threads, waiting for the task to complete before returning.
   * 
   * Used on shutdown to avoid spiking threads.
   * 
   * The wait is timed out to avoid delays for slow tasks.
   */
  @Override
  public final void wakeAllAndWait()
  {
    if (wakeSelf()) {
      launchAndWait();
    }
  }

  private boolean wakeSelf()
  {
    AtomicReference<State> stateRef = _stateRef;
    
    State oldState;
    State newState;
    
    do {
      oldState = stateRef.get();
      newState = oldState.toWake();
    } while (! stateRef.compareAndSet(oldState, newState));

    return (oldState.isIdle() && newState.isActive());
  }

  private void toIdle()
  {
    final AtomicReference<State> stateRef = _stateRef;
    
    State oldState;
    State newState;
    
    do {
      oldState = stateRef.get();
      newState = oldState.toIdle();
    } while (! stateRef.compareAndSet(oldState, newState));

    if (newState.isActive()) {
      launch();
    }
  }
  
  private void launch()
  {
    try {
      _launchCount.incrementAndGet();
      _launcher.execute();
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  /**
   * Launch a thread, waiting for the task to complete, with a timeout to
   * avoid bogging down for slow tasks. Used to minimize thread spikes on
   * multiworker shutdown.
   */
  private void launchAndWait()
  {
    try {
      _launchCount.incrementAndGet();
      _launcher.executeAndWait();
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  static enum State {
    IDLE {
      @Override
      State toWake() { return ACTIVE_WAKE; }
      
      @Override
      State toStart() { return ACTIVE; }
      
      @Override
      boolean isIdle() { return true; }
    },
    
    ACTIVE {
      @Override
      State toWake() { return ACTIVE_WAKE; }
      
      @Override
      State toStart() { return ACTIVE_WAKE; }
      
      @Override
      State toIdle() { return IDLE; }
      
      @Override
      boolean isActive() { return true; }
    },
    
    ACTIVE_WAKE {
      @Override
      State toWake() { return this; }
      
      @Override
      State toStart() { return this; }
      
      @Override
      State toIdle() { return this; }
      
      @Override
      boolean isActive() { return true; }
    },
      
    CLOSED {
        @Override
        State toIdle() { return this; }
        
        @Override
        boolean isClosed() { return true; }
    };
    
    State toWake() { return this; }
    State toStart() { return this; }
    
    State toIdle() { System.out.println("BAD: " + toString());
                     throw new IllegalStateException(toString()); }
    
    boolean isIdle() { return false; }
    boolean isActive() { return false; }
    boolean isWake() { return false; }
    boolean isClosed() { return false; }
    
  }
  
  abstract static class Launcher {
    abstract void execute();

    void executeAndWait()
    {
      execute();
    }
  }
  
  final static class ExecutorLauncher extends Launcher
  {
    private final Executor _executor;
    private final Runnable _task;
    
    ExecutorLauncher(Executor executor, Runnable task)
    {
      Objects.requireNonNull(executor);
      Objects.requireNonNull(task);
      
      _executor = executor;
      _task = task;
    }
    
    @Override
    final void execute()
    {
      _executor.execute(_task);
    }
    
    @Override
    final void executeAndWait()
    {
      WaitTask task = new WaitTask(_task);
      
      _executor.execute(task);
      
      task.waitFor(10, TimeUnit.MILLISECONDS);
    }
  }
  
  final static class SchedulerLauncher extends Launcher
  {
    private final RunnableItemScheduler _scheduler;
    private final Runnable _task;
    private final RunnableItem _taskItem;
    
    SchedulerLauncher(RunnableItemScheduler scheduler, Runnable task)
    {
      Objects.requireNonNull(scheduler);
      Objects.requireNonNull(task);
      
      _scheduler = scheduler;
      _task = task;
      _taskItem = new RunnableItem(task, task.getClass().getClassLoader());
    }
    
    @Override
    final void execute()
    {
      _scheduler.schedule(_taskItem);
    }
    
    @Override
    final void executeAndWait()
    {
      WaitTask task = new WaitTask(_task);
      
      RunnableItem taskItem;
      
      taskItem = new RunnableItem(task, _task.getClass().getClassLoader());
      
      _scheduler.schedule(taskItem);
      
      task.waitFor(10, TimeUnit.MILLISECONDS);
    }
  }
  
  final static class WaitTask implements Runnable
  {
    private ResultFuture<Boolean> _future = new ResultFuture<>();
    private Runnable _task;
    
    WaitTask(Runnable task)
    {
      _task = task;
    }
    
    void waitFor(long time, TimeUnit unit)
    {
      try {
        _future.get(time, unit);
      } catch (Exception e) {
      }
    }
    
    @Override
    public void run()
    {
      try {
        _task.run();
      } finally {
        _future.ok(true);
      }
    }
  }
}
