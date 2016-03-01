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


/**
 * Abstract counter for a multi-processor queue.
 */
public class CounterBuilderMultiWorker extends CounterBuilderBase
{
  private final int _index;
  private final int _workers;

  CounterBuilderMultiWorker(int index, int workers)
  {
    _index = index;
    _workers = workers;
    
    if (workers <= 1) {
      throw new IllegalStateException(getClass().getName());
    }
  }

  @Override
  public final int getHeadIndex()
  {
    return _index;
  }
  
  @Override
  public final int getTailIndex()
  {
    return _index;
  }

  @Override
  public final CounterBuilder getTail()
  {
    return this;
  }

  @Override
  public CounterActor build(CounterActor[] counters, boolean isTail)
  {
    CounterActor counter;
    
    if (isTail) {
      counter = new CounterAtomic();
    }
    else {
      counter = new CounterMultiTail();
    }
    
    counters[getTailIndex()] = counter;
    
    return counter;
  }
}
