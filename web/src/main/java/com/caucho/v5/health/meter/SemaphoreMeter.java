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


public final class SemaphoreMeter extends MeterBase implements SemaphoreSensor {
  // sample data
  private final AtomicLong _acquireCount = new AtomicLong();
  private final AtomicLong _releaseCount = new AtomicLong();
  
  // min/max
  private final AtomicLong _min = new AtomicLong();
  private final AtomicLong _max = new AtomicLong();

  // for sample
  private long _lastAcquireCount;
  
  private long _value;
  
  public SemaphoreMeter(String name)
  {
    super(name);
  }

  public MeterBase createCount(String name)
  {
    return new CountProbe(name);
  }

  public MeterBase createMin(String name)
  {
    return new MinProbe(name);
  }

  public MeterBase createMax(String name)
  {
    return new MaxProbe(name);
  }

  public final void acquire()
  {
    long acquire = _acquireCount.incrementAndGet();
    long release = _releaseCount.get();

    long count = acquire - release;
    long max;

    while ((max = _max.get()) < count) {
      _max.compareAndSet(max, count);
    }
  }

  public final void release()
  {
    long acquire = _acquireCount.get();
    long release = _releaseCount.incrementAndGet();

    long count = acquire - release;
    
    long min;

    while (count < (min = _min.get())) {
      _min.compareAndSet(min, count);
    }
  }
  
  /**
   * Return the probe's next average.
   */
  @Override
  public final void sample()
  {
    _value = _acquireCount.get() - _releaseCount.get();
  }
  
  @Override
  public final double calculate()
  {
    return _value;
  }
  
  /**
   * Return the probe's next sample.
   */
  public final double sampleCount()
  {
    long lastAcquireCount = _lastAcquireCount;
    _lastAcquireCount = _acquireCount.get();
    
    return _lastAcquireCount - lastAcquireCount;
  }
  
  /**
   * Return the probe's next sample.
   */
  public final double sampleMax()
  {
    long count = _acquireCount.get() - _releaseCount.get();
    long max = _max.getAndSet(count);

    return max;
  }
  
  /**
   * Return the probe's next sample.
   */
  public final double sampleMin()
  {
    long count = _acquireCount.get() - _releaseCount.get();
    long min = _min.getAndSet(count);

    return min;
  }

  class CountProbe extends MeterBase {
    private double _value;
    
    CountProbe(String name)
    {
      super(name);
    }

    @Override
    public void sample()
    {
      _value = sampleCount();
    }
    
    @Override
    public double calculate()
    {
      return _value;
    }
  }

  class MaxProbe extends MeterBase {
    private double _value;
    
    MaxProbe(String name)
    {
      super(name);
    }

    @Override
    public void sample()
    {
      _value = sampleMax();
    }
    
    @Override
    public double calculate()
    {
      return _value;
    }
  }

  class MinProbe extends MeterBase {
    private double _value;
    
    MinProbe(String name)
    {
      super(name);
    }

    @Override
    public void sample()
    {
      _value = sampleMin();
    }
    
    @Override
    public double calculate()
    {
      return _value;
    }
  }
}
