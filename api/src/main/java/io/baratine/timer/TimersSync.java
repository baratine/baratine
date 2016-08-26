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

/**
 * Timer service local to the JVM. The timer can be obtained by
 * injection (CDI):
 *
 * <pre>
 *     &#64;Inject &#64;Service("timer:") Timers _timer;
 * </pre>
 *
 * <p> or with the <code>{@link io.baratine.service.Services}</code>:
 *
 * <pre>
 *     Services.current().service("timer:").as(Timers.class);
 * </pre>
 *
 * <p> Service name: "timer:"
 *
 * @see io.baratine.service.Services
 */
public interface TimersSync extends Timers
{
  /**
   * Run the task at the given time.
   *
   * The task implements {@code Consumer} to accept a cancel.
   *
   * <blockquote><pre>
   *     // run 5 seconds from now
   *     timers.runAt(task, System.currentTimeMillis() + 5000);
   * </pre></blockquote>
   *
   * @param task the task to execute
   * @param time millisecond time since epoch to run
   * @return cancel handler
   */
  Cancel runAt(@Pin Consumer<? super Cancel> task, 
               long time);

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
   * @param unit unit type specifier
   * @return cancel handler
   */
  Cancel runAfter(@Pin Consumer<? super Cancel> task, 
                  long delay, 
                  TimeUnit unit);

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
   * @param task task to execute
   * @param delay run period
   * @param unit run period time units
   * @return cancel handler
   */
  Cancel runEvery(@Pin Consumer<? super Cancel> task, 
                  long delay, 
                  TimeUnit unit);

  /**
   * Schedule a <code>Runnable</code> where scheduling is controlled by a
   * scheduler.  {@link LongUnaryOperator#applyAsLong(long)}} is run first
   * to determine the initial execution of the task.
   *
   * <p> <b>Run every 2 seconds, starting 2 seconds from now:</b>
   * <blockquote><pre>
   *     timerService.schedule(task, (t) -&gt; t + 2000, Result.ignore());
   * </pre></blockquote>
   *
   * <p> <b>Run exactly 5 times, then unregister this task:</b>
   * <blockquote><pre>
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
   *     }, Result.ignore());
   * </pre></blockquote>
   *
   * @param task task to execute
   * @param nextTime specifies next time function
   * @return result Cancel handler for the task
   */
  Cancel schedule(@Pin Consumer<? super Cancel> task,
                        LongUnaryOperator nextTime);
}
