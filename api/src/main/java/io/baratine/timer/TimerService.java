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

package io.baratine.timer;

import io.baratine.service.Cancel;
import io.baratine.service.Direct;
import io.baratine.service.Result;
import io.baratine.service.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Timer service local to the JVM. The timer can be obtained by
 * injection (CDI):
 *
 * <pre>
 *     &#64;Inject &#64;Lookup("timer:") TimerServicer _timer;
 * </pre>
 *
 * <p> or with the <code>{@link io.baratine.service.ServiceManager}</code>:
 *
 * <pre>
 *     ServiceManager.current().lookup("timer:").as(TimerService.class);
 * </pre>
 *
 * <p> Service name: "timer:"
 *
 * @see io.baratine.service.ServiceManager
 */
@Service("timer:")
public interface TimerService
{
  /**
   * Returns the current time.  <code>&#64;{@link Direct}</code> indicates that this
   * call bypasses the inbox for this service (i.e. callers call this method
   * directly).
   *
   * @return the current time
   */
  @Direct
  long getCurrentTime();

  /**
   * Run the <code>Runnable</code> at the given time.
   *
   * <pre>
   *     // run 5 seconds from now
   *     timeService.runAt(task, System.currentTimeMillis() + 5000);
   * </pre>
   *
   * @param task the task to execute
   * @param time millisecond time since epoch to run
   * @param result holder for the cancel result
   */
  void runAt(@Service Consumer<? super Cancel> task, 
             long time,
             Result<? super Cancel> result);

  /**
   * Run the <code>Runnable</code> <b>once</b> after the given delay.
   *
   * <pre>
   *     MyRunnable task = new MyRunnable();
   *
   *     // run once 10 seconds from now
   *     timerService.runAfter(task, 10, TimeUnit.SECONDS);
   * </pre>
   *
   * @param task the executable timer task
   * @param delay time to delay in units
   * @param unit timeunit to delay
   * @param result holder for the timer 
   */
  void runAfter(@Service Consumer<? super Cancel> task, 
                long delay, 
                TimeUnit unit,
                Result<? super Cancel> result);

  /**
   * Run the <code>Runnable</code> periodically after the given delay.
   *
   * <pre>
   *     MyRunnable task = new MyRunnable();
   *
   *     // run every 10 seconds
   *     timerService.runEvery(task, 10, TimeUnit.SECONDS);
   * </pre>
   *
   * @param task
   * @param delay
   * @param unit
   */
  void runEvery(@Service Consumer<? super Cancel> task, 
                long delay, 
                TimeUnit unit,
                Result<? super Cancel> result);

  /**
   * Schedule a <code>Runnable</code> where scheduling is controlled by a
   * scheduler.  {@link TimerScheduler#nextRunTime(long)} is run first
   * to determine the initial execution of the task.
   *
   * <p> <b>Run every 2 seconds, starting 2 seconds from now:</b>
   * <pre>
   *     timerService.schedule(task, new TimerScheduler() {
   *         public long nextRunTime(long now) {
   *           return now + 2000;
   *         }
   *     };
   * </pre>
   *
   * <p> <b>Run exactly 5 times, then unregister this task:</b>
   * <pre>
   *     timerService.schedule(task, new TimerScheduler() {
   *         int count = 0;
   *
   *         public long nextRunTime(long now) {
   *           if (count++ &gt;= 5) {
   *             return -1; // negative value to cancel
   *           }
   *           else {
   *             return now + 2000;
   *           }
   *         }
   *     };
   * </pre>
   *
   * @param task
   * @param scheduler
   */
  void schedule(@Service Consumer<? super Cancel> task, 
                TimerScheduler scheduler,
                Result<? super Cancel> result);

  /**
   * Schedule a <code>Runnable</code> that is controlled by a cron scheduler.
   *
   * <p> <b>Run every 2 seconds:</b>
   * <pre>
   *     timerService.cron(task, "*&#47;2 * * *");
   * </pre>
   *
   * <p> <b>Run on the 5th second of the 6th minute of the 7th hour everyday:</b>
   * <pre>
   *     timerService.cron(task, "5 6 7 *");
   * </pre>
   *
   * @param task
   * @param cron basic cron syntax
   */
  void cron(@Service Consumer<? super Cancel> task, 
            String cron,
            Result<? super Cancel> result);

  /**
   * Unregisters the <code>Runnable</code> from this timer.
   * <pre>
   *     timerService.unregister(task);
   * </pre>
   *
   * @param task
   */
  // void cancel(CancelHandle timerHandle);

  /**
   * Returns the task info associated with this Runnable.
   */
  //TaskInfo getTask(@Service Runnable task);
  //void getTask(@Service Runnable task, Result<TaskInfo> result);

  /**
   * Returns the list of scheduled tasks.
   */
  //List<TaskInfo> getTasks();
  //void getTasks(Result<List<TaskInfo>> task);
}
