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

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

import io.baratine.service.Cancel;
import io.baratine.service.Pin;
import io.baratine.service.Result;
import io.baratine.service.Service;

/**
 * Timer service local to the JVM. The timer can be obtained by
 * injection (CDI):
 *
 * <pre>
 *     &#64;Inject Timers _timers;
 * </pre>
 *
 * <p> or with the <code>{@link io.baratine.service.Services}</code>:
 *
 * <pre>
 *     ServiceManager.current().service(Timers.class);
 * </pre>
 *
 * <p> Service name: "timer:"
 *
 * @see io.baratine.service.Services
 */
@Service("timer:")
public interface Timers
{
  /**
   * Run the task at the given time.
   * 
   * The task implements {@code Consumer} to accept a cancel.
   *
   * <pre>
   *     // run 5 seconds from now
   *     timers.runAt(task, System.currentTimeMillis() + 5000);
   * </pre>
   *
   * @param task the task to execute
   * @param time millisecond time since epoch to run
   * @param result holder for the cancel result
   */
  void runAt(@Pin Consumer<? super Cancel> task, 
             long time,
             Result<? super Cancel> result);

  /**
   * Run the task <b>once</b> after the given delay.
   *
   * <pre>
   *     MyRunnable task = new MyRunnable();
   *
   *     // run once 10 seconds from now
   *     timers.runAfter(task, 10, TimeUnit.SECONDS);
   * </pre>
   *
   * @param task the executable timer task
   * @param delay time to delay in units
   * @param unit timeunit to delay
   * @param result holder for the timer 
   */
  void runAfter(@Pin Consumer<? super Cancel> task, 
                long delay, 
                TimeUnit unit,
                Result<? super Cancel> result);

  /**
   * Run the task periodically after the given delay.
   *
   * <pre>
   *     MyRunnable task = new MyRunnable();
   *
   *     // run every 10 seconds
   *     timers.runEvery(task, 10, TimeUnit.SECONDS);
   * </pre>
   *
   * @param task
   * @param delay
   * @param unit
   */
  void runEvery(@Pin Consumer<? super Cancel> task, 
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
   *     timerService.schedule(task, new LongUnaryOperator() {
   *         int count = 0;
   *
   *         public long applyAsLong(long now) {
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
  void schedule(@Pin Consumer<? super Cancel> task, 
                LongUnaryOperator nextTime,
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
  void cron(@Pin Consumer<? super Cancel> task, 
            String cron,
            Result<? super Cancel> result);
}
