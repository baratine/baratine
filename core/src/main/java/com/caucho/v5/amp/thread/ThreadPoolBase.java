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

package com.caucho.v5.amp.thread;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.queue.QueueRingFixed;
import com.caucho.v5.health.HealthSystemFacade;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.ConcurrentArrayList;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.Friend;
import com.caucho.v5.util.L10N;

/**
 * A generic pool of threads available for Alarms and Work tasks.
 */
public class ThreadPoolBase implements Executor, RunnableItemScheduler
{
  private static final L10N L = new L10N(ThreadPoolBase.class);

  private static final Logger log
    = Logger.getLogger(ThreadPoolBase.class.getName());

  public static final String THREAD_FULL_EVENT = "caucho.thread.schedule.full";

  private static final long MAX_EXPIRE = Long.MAX_VALUE / 2;
  
  private static final int THREAD_IDLE_MIN = 8;
  private static final int THREAD_IDLE_MAX = 1024;

  private static final int THREAD_THROTTLE_LIMIT = 256;
  // private static final int THREAD_THROTTLE_LIMIT = 1000;
  private static final long THREAD_THROTTLE_SLEEP = 1;

  private static final AtomicReference<ThreadPoolBase> _globalThreadPool
    = new AtomicReference<>();

  private final String _name;

  private final ThreadLauncher _launcher;
  private final Lifecycle _lifecycle = new Lifecycle();

  // configuration items

  private int _idleMin = THREAD_IDLE_MIN;
  private int _idleMax = THREAD_IDLE_MAX;

  //
  // array of all active threads
  //
  
  private final ConcurrentArrayList<ThreadAmp> _threadList
    = new ConcurrentArrayList<>(ThreadAmp.class);

  //
  // lifecycle count to drain on environment change
  //

  private final AtomicLong _resetCount = new AtomicLong();

  //
  // thread max and thread lifetime counts
  //
  private final AtomicLong _overflowCount = new AtomicLong();

  //
  // the idle queue
  //

  private final QueueRingFixed<ThreadAmp> _idleThreadRing
    = new QueueRingFixed<>(8192);

  //
  // task queues
  //

  // private final ThreadTaskRing2 _taskQueue = new ThreadTaskRing2();
  private final QueueRingFixed<RunnableItem> _taskQueue
    = new QueueRingFixed<>(16 * 1024);
    
  private final AtomicLong _threadWakeHead = new AtomicLong();
  private final AtomicLong _threadWakeTail = new AtomicLong();
  
  private long _spinTimeoutCount;
  
  private final AtomicInteger _taskCount = new AtomicInteger();
  private final AtomicInteger _spinIdleCount = new AtomicInteger();
  
  private final int _spinCpuMax;

  private int _waitCount;

  public ThreadPoolBase()
  {
    this("system");
  }

  public ThreadPoolBase(String name)
  {
    _name = name;

    _launcher = new ThreadLauncher(this);
    _launcher.setIdleMax(THREAD_IDLE_MAX);
    _launcher.setIdleMin(THREAD_IDLE_MIN);

    _launcher.setThrottleLimit(THREAD_THROTTLE_LIMIT);
    _launcher.setThrottleSleepTime(THREAD_THROTTLE_SLEEP);
    // initialize default values
    
    int cpus = Runtime.getRuntime().availableProcessors();
    
    _spinCpuMax = Math.min(4, (cpus + 2) / 4);
    // _spinCpuMax = Math.min(4, cpus / 2);

    //_spinCpuMax = Math.min(4, Runtime.getRuntime().availableProcessors() / 2);
    //System.out.println("SPIN_CPU: " + _spinCpuMax);
    // long timeout = TimeUnit.MICROSECONDS.toNanos(50);
    //long timeout = TimeUnit.MICROSECONDS.toNanos(200);
    
    _spinTimeoutCount = 1;
    // _spinTimeoutCount = 1;
    
    init();
    //System.out.println("SPIN_TIMEOUT: " + _spinTimeoutCount);
  }

  public static ThreadPoolBase current()
  {
    ThreadPoolBase threadPool = _globalThreadPool.get();

    if (threadPool != null)
      throw new IllegalStateException();

    return threadPool;
  }

  protected void setAsGlobal(ThreadPoolBase pool)
  {
    _globalThreadPool.set(pool);
  }

  //
  // Configuration properties
  //

  /**
   * Sets the maximum number of threads.
   */
  public void setThreadMax(int max)
  {
    _launcher.setThreadMax(max);
  }

  /**
   * Gets the maximum number of threads.
   */
  public int getThreadMax()
  {
    return _launcher.getThreadMax();
  }

  /**
   * Sets the minimum number of idle threads.
   */
  public void setIdleMin(int min)
  {
    if (min < 1)
      throw new IllegalArgumentException(L.l("idle-min must be greater than zero."));

    if (_idleMax <= min) {
      throw new IllegalArgumentException(L.l("idle-min '{0}' must be less than idle-max '{1}'.",
                                             min, _idleMax));
    }

    _idleMin = min;

    _launcher.setIdleMin(_idleMin);
  }

  /**
   * Gets the minimum number of idle threads.
   */
  public int getIdleMin()
  {
    return _idleMin;
  }

  /**
   * Returns the thread idle max.
   */
  public int getIdleMax()
  {
    return _idleMax;
  }

  /**
   * Returns the thread idle max.
   */
  public void setIdleMax(int idleMax)
  {
    if (idleMax <= _idleMin) {
      throw new IllegalArgumentException(L.l("idle-max '{0}' must be greater than idle-min '{1}'.",
                                             idleMax, _idleMin));
    }

    _launcher.setIdleMax(_idleMax);
  }

  /**
   * Sets the minimum number of free threads reserved for priority tasks.
   */
  public void setPriorityIdleMin(int min)
  {
  }

  public int getPriorityIdleMin()
  {
    return 0;
  }

  /**
   * Sets the idle timeout
   */
  public void setIdleTimeout(long timeout)
  {
    _launcher.setIdleTimeout(timeout);
  }

  /**
   * Returns the idle timeout.
   */
  public long getIdleTimeout()
  {
    return _launcher.getIdleTimeout();
  }

  //
  // launcher throttle configuration
  //


  /**
   * Sets the throttle period.
   */
  public void setThrottlePeriod(long period)
  {
    _launcher.setThrottlePeriod(period);
  }

  /**
   * Sets the throttle limit.
   */
  public void setThrottleLimit(int limit)
  {
    _launcher.setThrottleLimit(limit);
  }

  /**
   * Sets the throttle sleep time.
   */
  public void setThrottleSleepTime(long period)
  {
    _launcher.setThrottleSleepTime(period);
  }

  public long getSpinCount()
  {
    return _spinTimeoutCount;
  }

  //
  // statistics
  //

  /**
   * Returns the total thread count.
   */
  public int getThreadCount()
  {
    return _launcher.getThreadCount();
  }

  /**
   * Returns the active thread count.
   */
  public int getThreadActiveCount()
  {
    return (getThreadCount()
            - getThreadIdleCount());

    /*
    return (getThreadCount()
            - getThreadIdleCount()
            - getPriorityIdleCount());
            */
  }

  /**
   * Returns the starting thread count.
   */
  public int getThreadStartingCount()
  {
    return _launcher.getStartingCount();
  }

  /**
   * Returns the idle thread count.
   */
  public int getThreadIdleCount()
  {
    return _launcher.getIdleCount();
  }

  /**
   * Returns the priority idle thread count.
   */
  /*
  public int getPriorityIdleCount()
  {
    return _launcher.getPriorityIdleCount();
  }
  */

  /**
   * Returns the waiting thread count.
   */
  public int getThreadWaitCount()
  {
    return _waitCount;
  }

  /**
   * Returns the free thread count.
   */
  public int getFreeThreadCount()
  {
    return getThreadMax() - getThreadCount() - _launcher.getStartingCount();
  }

  /**
   * Returns the total created thread count.
   */
  public long getThreadCreateCountTotal()
  {
    return _launcher.getCreateCountTotal();
  }

  /**
   * Returns the total created overflow thread count.
   */
  public long getThreadOverflowCountTotal()
  {
    return _overflowCount.get();
  }

  /**
   * Returns priority queue size
   */
  public int getThreadPriorityQueueSize()
  {
    return 0;
  }

  /**
   * Returns task queue size
   */
  public int getThreadTaskQueueSize()
  {
    return _taskQueue.size();
  }

  //
  // initialization
  //

  private void init()
  {
    update();
    
    if (_spinCpuMax == 0) {
      _spinTimeoutCount = 1;
      return;
    }
    
    long timeout = TimeUnit.MICROSECONDS.toNanos(50);
    
    _spinTimeoutCount = Math.max(1, calculateTimeout(timeout));
  }
  
  private long calculateTimeout(long timeout)
  {
    long testTimeTarget = TimeUnit.MILLISECONDS.toNanos(50);
   
    long testCount = 1000L * 1000L;
   
    long testTime = 0;
   
    while (testTime < testTimeTarget) {
      long startTime = System.nanoTime();
     
      timeTask(testCount);
     
      long endTime = System.nanoTime();
     
      testTime = endTime - startTime;
      
      testCount *= Math.max(2, 2 * testTimeTarget / Math.max(1, testTime));
    }
   
    return (long) ((double) testCount * timeout / testTime);
  }
 
  private void timeTask(long testCount)
  {
    while (testCount-- > 0) {
      // _taskQueue.beginPoll();
      _taskQueue.poll();
    }
  }

  private void update()
  {
    _launcher.update();
  }

  public void start()
  {
    _launcher.start();
  }

  //
  // Scheduling methods
  //

  /**
   * Schedules a new task.
   */
  public boolean schedule(Runnable task)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    boolean isPriority = false;
    boolean isQueue = true;
    boolean isWake = true;

    return scheduleImpl(task, loader, MAX_EXPIRE, isPriority, isQueue, isWake);
  }

  /**
   * Schedules a new task.
   */
  public boolean schedule(Runnable task, ClassLoader loader)
  {
    boolean isPriority = false;
    boolean isQueue = true;
    boolean isWake = true;

    return scheduleImpl(task, loader, MAX_EXPIRE, isPriority, isQueue, isWake);
  }

  /**
   * Adds a new task.
   */
  public boolean schedule(Runnable task, long timeout)
  {
    long expire;

    if (timeout < 0 || MAX_EXPIRE < timeout)
      expire = MAX_EXPIRE;
    else
      expire = CurrentTime.getCurrentTimeActual() + timeout;

    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    boolean isPriority = false;
    boolean isQueue = true;
    boolean isWake = true;

    return scheduleImpl(task, loader, expire, isPriority, isQueue, isWake);
  }

  /**
   * Adds a new task.
   */
  public void schedulePriority(Runnable task)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    // long expire = CurrentTime.getCurrentTimeActual() + PRIORITY_TIMEOUT;
    long expire = MAX_EXPIRE;

    boolean isPriority = true;
    boolean isQueue = true;
    boolean isWake = true;

    if (! scheduleImpl(task, loader, expire, isPriority, isQueue, isWake)) {
      String msg = (this + " unable to schedule priority thread " + task
                    + " pri-min=" + getPriorityIdleMin()
                    + " thread=" + getThreadCount()
                    + " idle=" + getThreadIdleCount()
                    // + " pri-idle=" + getPriorityIdleCount()
                    + " starting=" + getThreadStartingCount()
                    + " max=" + getThreadMax());

      log.warning(msg);

      OverflowThread item = new OverflowThread(task);
      item.start();

      HealthSystemFacade.fireEvent(THREAD_FULL_EVENT, msg);
    }
  }

  /**
   * Adds a new task.
   */
  public boolean start(Runnable task)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    boolean isPriority = false;
    boolean isQueue = false;
    boolean isWake = true;

    return scheduleImpl(task, loader, MAX_EXPIRE, isPriority, isQueue, isWake);
  }

  /**
   * Adds a new task.
   */
  public boolean start(Runnable task, long timeout)
  {
    long expire;

    if (timeout < 0 || timeout > MAX_EXPIRE)
      expire = MAX_EXPIRE;
    else
      expire = CurrentTime.getCurrentTimeActual() + timeout;

    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    boolean isPriority = false;
    boolean isQueue = false;
    boolean isWake = true;

    return scheduleImpl(task, loader, expire, isPriority, isQueue, isWake);
  }

  /**
   * Adds a new task.
   */
  public void startPriority(Runnable task)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    // long expire = CurrentTime.getCurrentTimeActual() + PRIORITY_TIMEOUT;
    long expire = MAX_EXPIRE;

    boolean isPriority = true;
    boolean isQueue = true;
    boolean isWake = true;

    if (! scheduleImpl(task, loader, expire, isPriority, isQueue, isWake)) {
      String msg = (this + " unable to start priority thread " + task
                    + " pri-min=" + getPriorityIdleMin()
                    + " thread=" + getThreadCount()
                    + " idle=" + getThreadIdleCount()
                    // + " pri-idle=" + getPriorityIdleCount()
                    + " starting=" + getThreadStartingCount()
                    + " max=" + getThreadMax());

      log.warning(msg);

      HealthSystemFacade.fireEvent(THREAD_FULL_EVENT, msg);

      OverflowThread item = new OverflowThread(task);
      item.start();
    }
  }

  /**
   * Adds a new task.
   */
  public boolean startPriority(Runnable task, long timeout)
  {
    long expire;

    if (timeout < 0 || timeout > MAX_EXPIRE)
      expire = MAX_EXPIRE;
    else
      expire = CurrentTime.getCurrentTimeActual() + timeout;

    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    boolean isPriority = true;
    boolean isQueue = false;
    boolean isWake = true;

    return scheduleImpl(task, loader, expire, isPriority, isQueue, isWake);
  }

  /**
   * Submit a task, but do not wake the scheduler
   */
  public boolean submitNoWake(Runnable task)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    boolean isPriority = false;
    boolean isQueue = true;
    boolean isWake = false;

    return scheduleImpl(task, loader, MAX_EXPIRE, isPriority, isQueue, isWake);
  }

  /**
   * Submit a task, but do not wake the scheduler
   */
  public boolean submitNoWake(Runnable task, ClassLoader loader)
  {
    boolean isPriority = false;
    boolean isQueue = true;
    boolean isWake = false;

    return scheduleImpl(task, loader, MAX_EXPIRE, isPriority, isQueue, isWake);
  }

  /**
   * main scheduling implementation class.
   */
  protected boolean scheduleImpl(Runnable task,
                                 ClassLoader loader,
                                 long expireTime,
                                 boolean isPriority,
                                 boolean isQueueIfFull,
                                 boolean isWakeScheduler)
  {
    Objects.requireNonNull(task);
    
    RunnableItem taskItem = new RunnableItem(task, loader);
    
    return schedule(taskItem);
  }

  @Override
  public final void execute(Runnable task)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    schedule(new RunnableItem(task, loader));
  }

  /**
   * main scheduling implementation class.
   */
  boolean schedule(Runnable task,
                   ClassLoader loader,
                   long timeout)
  {
    return schedule(new RunnableItem(task, loader, timeout));
  }
  
  @Override
  public final boolean schedule(RunnableItem taskItem)
  {
    if (_taskQueue.offer(taskItem)) {
      _taskCount.incrementAndGet();
      
      wakeIdle();
      
      return true;
    }
    
    System.out.println("TASK_FULL");
    
    return false;
  }
  
  void processTask()
  {
    _taskCount.decrementAndGet();
  }

  @Friend(ThreadAmp.class)
  void offerIdle(ThreadAmp thread)
  {
    _idleThreadRing.offer(thread);
  }
  
  public String getStatus()
  {
    return ("ThreadPool[queue:" + _taskQueue.size()
            + ",task:" + _taskCount.get()
            + ",spin:" + _spinIdleCount.get()
            + ",idle:" + _idleThreadRing.size()
            + ",start:" + (_threadWakeHead.get() - _threadWakeTail.get())
            + "]");
  }
  
  //
  // task methods
  //

  @Friend(ThreadAmp.class)
  RunnableItem poll(boolean isWake)
  {
    QueueRingFixed<RunnableItem> taskQueue = _taskQueue;
    
    RunnableItem item = null;
    
    if (startSpinIdle()) {
      if (isWake) {
        onWakeThread();
      }
      
      for (long i = getSpinCount(); i >= 0 && item == null; i--) {
        wakeThreadsFromSpin();
        item = taskQueue.poll();
      }
      
      finishSpinIdle();
      
      // need to poll after the spin idle completes for timing, because the
      // caller might see this thread as spinning just as it completes
      if (item == null) {
        item = taskQueue.poll();
      }
    }
    else {
      item = taskQueue.poll();
      
      if (isWake) {
        onWakeThread();
      }
    }
    
    if (item != null) {
      _taskCount.decrementAndGet();
      wakeIdle();
    }
    
    return item;
  }

  private void onWakeThread()
  {
    _threadWakeTail.incrementAndGet();
  }

  private boolean startSpinIdle()
  {
    int idle;
    int spinIdleMax = _spinCpuMax;
    
    do {
      idle = _spinIdleCount.get();

      if (spinIdleMax <= idle) {
        return false;
      }
    } while (! _spinIdleCount.compareAndSet(idle, idle + 1));
    
    return true;
  }
  
  private void finishSpinIdle()
  {
    _spinIdleCount.decrementAndGet();
  }
  
  /**
   * Wakes an idle thread from a scheduler.
   */
  private void wakeIdle()
  {
    wakeThreads(1);
  }
  
  /**
   * Wakes an idle thread from the spin loop. Wake as many threads as
   * needed to support the waiting tasks.
   */
  private void wakeThreadsFromSpin()
  {
    // XXX: this is not actually faster
    // wakeThreads(8);
  }

  /**
   * wake enough threads to process the tasks 
   */
  private void wakeThreads(int count)
  {
    while (true) {
      int taskCount = Math.max(0, _taskCount.get());
    
      int spinIdleCount = _spinIdleCount.get();
      long threadWakeHead = _threadWakeHead.get();
      long threadWakeTail = _threadWakeTail.get();
    
      long threadCount = spinIdleCount + threadWakeHead - threadWakeTail;
      
      if (taskCount <= threadCount) {
        return;
      }
      
      if (count <= threadCount) {
        return;
      }
      
      ThreadAmp thread = _idleThreadRing.poll();
      
      if (thread == null) {
        return;
      }
      
      if (_threadWakeHead.compareAndSet(threadWakeHead, threadWakeHead + 1)) {
        thread.setWakeThread();
        LockSupport.unpark(thread);
      }
      else {
        // avoid duplicate wake
        _idleThreadRing.offer(thread);
      }
    }
  }
  
  final void addThread(ThreadAmp thread)
  {
    _threadList.add(thread);
  }
  
  final void removeThread(ThreadAmp thread)
  {
    _threadList.remove(thread);
  }
  
  public final int countSlowThreads(ExecutorThrottle executor)
  {
    long now = CurrentTime.getCurrentTimeActual();
    
    int count = 0;
    
    for (ThreadAmp thread : _threadList) {
      if (thread != null && thread.isSlow(executor, now)) {
        count++;
      }
    }
    
    return count;
  }

  //
  // lifecycle methods
  //

  boolean isActive()
  {
    return _lifecycle.isActive();

  }

  /**
   * Resets the thread pool, letting old threads drain.
   */
  public void reset()
  {
    _resetCount.incrementAndGet();
  }

  /**
   * Resets the thread pool, letting old threads drain.
   */
  public void closeEnvironment(ClassLoader env)
  {
    // XXX: incorrect
    reset();
  }

  /**
   * interrupts all the idle threads.
   */
  public void clearIdleThreads()
  {
    _launcher.resetThrottle();
    
    int idleCount = _idleThreadRing.size();
    
    ThreadAmp thread;

    while (idleCount-- > 0 && (thread = _idleThreadRing.poll()) != null) {
      thread.close();
    }
  }

  public void close()
  {
    if (this == _globalThreadPool.get()) {
      throw new IllegalStateException(L.l("Cannot close global thread pool"));
    }

    _lifecycle.toDestroy();
    _launcher.close();

    clearIdleThreads();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _name + "]";
  }

  final class OverflowThread extends Thread {
    private Runnable _task;
    private ClassLoader _loader;

    OverflowThread(Runnable task)
    {
      super("amp-overflow-" + task.getClass().getSimpleName());
      setDaemon(true);

      _task = task;
      _loader = Thread.currentThread().getContextClassLoader();
    }

    /**
     * The main thread execution method.
     */
    @Override
    public void run()
    {
      Thread thread = Thread.currentThread();
      thread.setContextClassLoader(_loader);

      try {
        _overflowCount.incrementAndGet();

        _task.run();
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);
      }
    }
  }
}
