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
public final class CounterSingle implements CounterGroup
{
  private final CounterActor _head = new CounterAtomic();
  private final CounterActor _tail = new CounterAtomic();
  
  public CounterSingle(long initialIndex)
  {
    _head.set(initialIndex);
    _tail.set(initialIndex);
  }

  @Override
  public final int getSize()
  {
    return 2;
  }

  @Override
  public final CounterActor getCounter(int index)
  {
    switch (index) {
    case 0:
      return _head;
    case 1:
      return _tail;
    default:
      throw new IllegalArgumentException();
    }
  }

  @Override
  public CounterMultiTail getMultiCounter(int tailIndex)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
