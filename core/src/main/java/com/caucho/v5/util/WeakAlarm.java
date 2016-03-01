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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.util;

import java.lang.ref.WeakReference;

/**
 * The alarm class provides a lightweight event scheduler.  This allows
 * an objects to schedule a timeout without creating a new thread.
 *
 * <p>A separate thread periodically tests the queue for alarms ready.
 *
 * <p>You should use Cron for slow requests.  Alarm is only
 * appropriate for very short jobs.
 */
public class WeakAlarm extends Alarm
{
  private WeakReference<AlarmListener> _listenerRef;
  private WeakReference<ClassLoader> _loaderRef;

    
  /**
   * Create a new wakeup alarm with a designated listener as a callback.
   * The alarm is not scheduled.
   */
  public WeakAlarm(AlarmListener listener) 
  {
    super(listener);
  }
    
  /**
   * Create a new wakeup alarm with a designated listener as a callback.
   * The alarm is not scheduled.
   */
  public WeakAlarm(String name, AlarmListener listener) 
  {
    super(name, listener);
  }

  /**
   * Creates a named alarm and schedules its wakeup.
   *
   * @param name the object prepared to receive the callback
   * @param listener the object prepared to receive the callback
   * @param delta the time in milliseconds to wake up
   */
  public WeakAlarm(String name, AlarmListener listener, long delta) 
  {
    super(name, listener, delta);
  }
  /**
   * Creates a new alarm and schedules its wakeup.
   *
   * @param listener the object prepared to receive the callback
   * @param delta the time in milliseconds to wake up
   */
  public WeakAlarm(AlarmListener listener, long delta) 
  {
    this(listener);

    runAfter(delta);
  }
  
  /**
   * Return the alarm's listener.
   */
  @Override
  public AlarmListener getListener()
  {
    return _listenerRef.get();
  }
  
  /**
   * Sets the alarm's listener.
   */
  @Override
  public void setListener(AlarmListener listener)
  {
    _listenerRef = new WeakReference<AlarmListener>(listener);
  }

  @Override
  public ClassLoader getContextLoader()
  {
    WeakReference<ClassLoader> loaderRef = _loaderRef;
    
    if (loaderRef != null)
      return loaderRef.get();
    else
      return null;
  }
  
  /**
   * Sets the class loader.
   */
  @Override
  public void setContextLoader(ClassLoader loader)
  {
    if (loader != null)
      _loaderRef = new WeakReference<ClassLoader>(loader);
    else
      _loaderRef = null;
  }
}
