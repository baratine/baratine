/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
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

package com.caucho.v5.health.meter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;


public final class AverageTimeMeter extends MeterBase {
  private final AtomicLong _sum = new AtomicLong();
  private final AtomicInteger _count = new AtomicInteger();
  
  private double _value;

  public AverageTimeMeter(String name)
  {
    super(name);
  }

  public final void addData(long time)
  {
    long oldValue;

    do {
      oldValue = _sum.get();
    } while (! _sum.compareAndSet(oldValue, oldValue + time));

    _count.incrementAndGet();
  }
  
  /**
   * Return the probe's next sample.
   */
  public final void sample()
  {
    long sum = _sum.getAndSet(0);
    int count = _count.getAndSet(0);

    if (count != 0)
      _value =sum / (double) count;
    else
      _value = 0;
  }
  
  public final double calculate()
  {
    return _value;
  }
}
