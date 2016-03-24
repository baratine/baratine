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

import com.caucho.v5.health.meter.ActiveTimeSensor;
import com.caucho.v5.health.meter.MeterBase;
import com.caucho.v5.util.Alarm;
import com.caucho.v5.util.CurrentTime;

public final class MeterActiveTime extends MeterBase implements ActiveTimeSensor {
  private final double _scale;

  private final Object _lock = new Object();

  // sample data
  private final AtomicLong _activeCount = new AtomicLong();
  private final AtomicLong _activeCountMax = new AtomicLong();
  private final AtomicLong _totalCount = new AtomicLong();
  private final AtomicLong _sum = new AtomicLong();
  private final AtomicLong _max = new AtomicLong();
  private double _sumSquare;

  private long _lastTotalCount;

  private long _lastAvgTotalCount;
  private long _lastAvgSum;

  // for 95%
  private long _lastStdTotalCount;
  private double _lastStdSum;
  
  private double _avg;

  public MeterActiveTime(String name)
  {
    super(name);

    _scale = 1.0;
  }

  @Override
  public final long start()
  {
    long startTime = CurrentTime.currentTime();

    long activeCount = _activeCount.incrementAndGet();

    long max;

    while ((max = _activeCountMax.get()) < activeCount
           && ! _activeCountMax.compareAndSet(max, activeCount)) {
    }

    return startTime;
  }

  @Override
  public final long end(long startTime)
  {
    _totalCount.incrementAndGet();
    _activeCount.decrementAndGet();

    long endTime = CurrentTime.currentTime();

    long value = endTime - startTime;

    double sqValue = value * value;
    _sum.addAndGet(value);
    _sumSquare += sqValue;

    long max;
    while ((max = _max.get()) < value
           && ! _max.compareAndSet(max, value)) {
    }
    
    return value;
  }

  public MeterBase createActiveCount(String name)
  {
    return new ActiveCountProbe(name);
  }

  public MeterBase createActiveCountMax(String name)
  {
    return new ActiveCountMaxProbe(name);
  }

  public MeterBase createTotalCount(String name)
  {
    return new TotalCountProbe(name);
  }

  public MeterBase createMax(String name)
  {
    return new MaxProbe(name);
  }

  public MeterBase createSigma(String name, int n)
  {
    return new SigmaProbe(name, n);
  }

  /**
   * Return the probe's next average.
   */
  public final void sample()
  {
    synchronized (_lock) {
      long count = _totalCount.get();
      long lastCount = _lastAvgTotalCount;
      _lastAvgTotalCount = count;

      long sum = _sum.get();
      double lastSum = _lastAvgSum;
      _lastAvgSum = sum;

      if (count == lastCount) {
        _avg =  0;
      }
      else {
        _avg = _scale * (sum - lastSum) / (double) (count - lastCount);
      }
    }
  }
  
  @Override
  public final double calculate()
  {
    return _avg;
  }

  /**
   * Sample the active count
   */
  public final double sampleActiveCount()
  {
    return _activeCount.get();
  }

  /**
   * Sample the active count
   */
  public final double sampleActiveCountMax()
  {
    return _activeCountMax.getAndSet(_activeCount.get());
  }

  /**
   * Sample the total count
   */
  public final double sampleTotalCount()
  {
    long count = _totalCount.get();
    long lastCount = _lastTotalCount;
    _lastTotalCount = count;

    return count - lastCount;
  }

  /**
   * Return the probe's next 2-sigma
   */
  public final double sampleSigma(int n)
  {
    synchronized (_lock) {
      long count = _totalCount.get();
      long lastCount = _lastStdTotalCount;
      _lastStdTotalCount = count;

      double sum = _sum.get();
      double lastSum = _lastStdSum;
      _lastStdSum = sum;

      double sumSquare = _sumSquare;
      _sumSquare = 0;

      if (count == lastCount)
        return 0;

      double avg = (sum - lastSum) / (count - lastCount);
      double part = (count - lastCount) * sumSquare - sum * sum;

      if (part < 0)
        part = 0;

      double std = Math.sqrt(part) / (count - lastCount);

      return _scale * (avg + n * std);
    }
  }

  /**
   * Return the probe's next sample.
   */
  public final double sampleMax()
  {
    long max = _max.getAndSet(0);

    return _scale * max;
  }

  class ActiveCountProbe extends MeterBase {
    private double _value;
    
    ActiveCountProbe(String name)
    {
      super(name);
    }

    @Override
    public void sample()
    {
      _value = sampleActiveCount();
    }
    
    @Override
    public double calculate()
    {
      return _value;
    }
  }

  class ActiveCountMaxProbe extends MeterBase {
    private double _value;
    
    ActiveCountMaxProbe(String name)
    {
      super(name);
    }

    @Override
    public void sample()
    {
      _value = sampleActiveCountMax();
    }
    
    @Override
    public double calculate()
    {
      return _value;
    }
  }

  class TotalCountProbe extends MeterBase {
    private double _value;
    
    TotalCountProbe(String name)
    {
      super(name);
    }

    @Override
    public void sample()
    {
      _value = sampleTotalCount();
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

  class SigmaProbe extends MeterBase {
    private final int _n;
    
    private double _value;

    SigmaProbe(String name, int n)
    {
      super(name);

      _n = n;
    }

    @Override
    public void sample()
    {
      _value = sampleSigma(_n);
    }
    
    @Override
    public double calculate()
    {
      return _value;
    }
  }
}
