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

package com.caucho.v5.amp.queue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.util.CurrentTime;

/**
 * Ring blocking algorithm.
 */
public class RingBlockerBasic implements RingBlocker
{
  private static final Logger log
    = Logger.getLogger(RingBlockerBasic.class.getName());

  private final AtomicLong _offerWaitSequence = new AtomicLong();
  private final AtomicLong _offerWakeSequence = new AtomicLong();
  
  // true if the offer has ever needed to wait, used to avoid slow reads
  // on queues that never fill.
  private boolean _isOfferWait;

  private final AtomicLong _pollWaitSequence = new AtomicLong();
  private final AtomicLong _pollWakeSequence = new AtomicLong();
  
  // true if the poll has ever needed to wait, used to avoid slow reads
  // on queues that never fill.
  private boolean _isPollWait;

  // private static final AtomicLong _debugIdGen = new AtomicLong();
  // private final long _id = _debugIdGen.incrementAndGet();

  @Override
  public final long nextOfferSequence()
  {
    return _offerWaitSequence.incrementAndGet();
  }

  @Override
  public final boolean offerWait(long sequence,
                                 long timeout,
                                 TimeUnit unit)
  {
    if (timeout <= 0) {
      return false;
    }
    
    if (! _isOfferWait) {
      // avoid memory write if already set
      _isOfferWait = true;
    }

    long now = CurrentTime.getCurrentTimeActual();
    long expires = now + unit.toMillis(timeout);

    pollWake();

    synchronized (_offerWaitSequence) {
      while (_offerWakeSequence.get() < sequence
             && (now = CurrentTime.getCurrentTimeActual()) < expires) {
        try {
          // long nanos = unit.toNanos(timeout);

          long millis = Math.max(0, expires - now);
          // nanos = nanos % 1000000L;
          // long start = System.currentTimeMillis();

          _offerWaitSequence.wait(millis);
        } catch (Exception e) {
          log.log(Level.FINER, e.toString(), e);

          Thread.interrupted();
        }
      }
    }

    return sequence <= _offerWakeSequence.get();
  }

  @Override
  public final void offerWake()
  {
    if (! _isOfferWait) {
      return;
    }
    
    long wakeSequence = _offerWakeSequence.get();
    long waitSequence = _offerWaitSequence.get();

    while (wakeSequence < waitSequence) {
      if (_offerWakeSequence.compareAndSet(wakeSequence, waitSequence)) {
        synchronized (_offerWaitSequence) {
          _offerWaitSequence.notifyAll();
        }
        break;
      }

      wakeSequence = _offerWakeSequence.get();
    }
  }

  @Override
  public final boolean wake()
  {
    offerWake();
    
    return true;
  }

  @Override
  public final void wakeAll()
  {
    wake();
  }

  @Override
  public final long nextPollSequence()
  {
    return _pollWaitSequence.incrementAndGet();
  }

  @Override
  public boolean pollWait(long sequence,
                                long timeout,
                                TimeUnit unit)
  {
    if (timeout <= 0) {
      return false;
    }
    
    if (! _isPollWait) {
      _isPollWait = true;
    }

    long expires = CurrentTime.getCurrentTimeActual() + unit.toMillis(timeout);

    offerWake();

    synchronized (_pollWaitSequence) {
      long now;

      while (_pollWakeSequence.get() < sequence
             && (now = CurrentTime.getCurrentTimeActual()) < expires) {
        try {
          // long nanos = unit.toNanos(timeout);

          long millis = Math.max(0, expires - now);
          // nanos = nanos % 1000000L;

          _pollWaitSequence.wait(millis);
        } catch (Exception e) {
          log.log(Level.FINER, e.toString(), e);

          Thread.interrupted();
        }
      }
    }

    return sequence <= _pollWakeSequence.get();
  }

  @Override
  public final boolean isPollWait()
  {
    if (! _isPollWait) {
      return false;
    }
    else {
      return _pollWakeSequence.get() < _pollWaitSequence.get();
    }
  }

  @Override
  public void pollWake()
  {
    if (isPollWait()) {
      pollWakeImpl();
    }
  }

  private void pollWakeImpl()
  {
    synchronized (_pollWaitSequence) {
      if (isPollWait()) {
        _pollWakeSequence.set(_pollWaitSequence.get());
        _pollWaitSequence.notifyAll();
      }
    }
  }
}
