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

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * Queue with an attached processor.
 */
abstract public class QueueServiceBase<M> implements QueueService<M>
{
  private final QueueDeliver<M> _queue;
 
  public QueueServiceBase(QueueDeliver<M> queue)
  {
    Objects.requireNonNull(queue);
    
    _queue = queue;
  }
  
  /*
  public int getOfferReserve()
  {
    return getQueue().getOfferReserve();
  }
  */
  
  protected final QueueDeliver<M> getQueue()
  {
    return _queue;
  }
  
  @Override
  public boolean isSingleWorker()
  {
    return false;
  } 

  @Override
  public final boolean isEmpty()
  {
    return _queue.isEmpty();
  }

  @Override
  public final int size()
  {
    return _queue.size();
  }

  @Override
  public final int remainingCapacity()
  {
    return _queue.remainingCapacity();
  }

  @Override
  public final boolean contains(Object o)
  {
    return _queue.contains(o);
  }

  @Override
  public final boolean containsAll(Collection<?> c)
  {
    return _queue.containsAll(c);
  }

  @Override
  public final boolean offer(M item)
  {
    boolean result = _queue.offer(item);
    
    return result;
  }

  @Override
  public final boolean offer(M item, long timeout, TimeUnit unit)
  {
    boolean result = _queue.offer(item, 0, TimeUnit.SECONDS);

    if (result || timeout <= 0) {
      return result;
    }
    else {
      wake();

      boolean isValid = _queue.offer(item, timeout, unit);

      return isValid;
    }
  }
  
  @Override
  public WorkerDeliverLifecycle getWorker()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
  }

  @Override
  public final boolean add(M item)
  {
    return _queue.add(item);
  }

  @Override
  public final void put(M item) throws InterruptedException
  {
    if (_queue.offer(item)) {
      return;
    }
      
    wake();
      
    _queue.put(item);
  }

  @Override
  public final boolean addAll(Collection<? extends M> c)
  {
    return _queue.addAll(c);
  }

  @Override
  public final M peek()
  {
    return _queue.peek();
  }

  @Override
  public final M poll()
  {
    return _queue.poll();
  }
  
  @Override
  public final M poll(long timeout, TimeUnit unit) throws InterruptedException
  {
    return _queue.poll(timeout, unit);
  }


  @Override
  public M element()
  {
    return _queue.element();
  }

  @Override
  public M take() throws InterruptedException
  {
    return _queue.take();
  }

  @Override
  public M remove()
  {
    return _queue.remove();
  }

  @Override
  public Iterator<M> iterator()
  {
    return _queue.iterator();
  }

  @Override
  public Object[] toArray()
  {
    return _queue.toArray();
  }

  @Override
  public <X> X[] toArray(X[] array)
  {
    return _queue.toArray(array);
  }

  @Override
  public boolean removeAll(Collection<?> c)
  {
    return _queue.removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c)
  {
    return _queue.retainAll(c);
  }

  @Override
  public void clear()
  {
    _queue.clear();
  }

  @Override
  public boolean remove(Object o)
  {
    return _queue.remove(o);
  }

  @Override
  public int drainTo(Collection<? super M> c)
  {
    return _queue.drainTo(c);
  }

  @Override
  public int drainTo(Collection<? super M> c, int maxElements)
  {
    return _queue.drainTo(c, maxElements);
  }
  
  @Override
  abstract public boolean wake();
  
  @Override
  public void wakeAll()
  {
    wake();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
