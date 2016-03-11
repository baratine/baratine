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

import java.util.Objects;

/**
 * Counter builder for a sequence of delivery workers.
 */
public class CounterBuilderArray extends CounterBuilderBase
{
  private final int _length;

  CounterBuilderArray(int length)
  {
    if (length < 2) {
      throw new IllegalArgumentException();
    }
    
    _length = length;
  }
  
  @Override
  public final int getHeadIndex()
  {
    return 0;
  }
  
  @Override
  public final int getTailIndex()
  {
    return _length - 1;
  }

  @Override
  public final CounterBuilder getTail()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public CounterRingGroup build(long initialIndex)
  {
    CounterRing []counters = new CounterRing[_length];
    
    for (int i = 0; i < _length; i++) {
      counters[i] = new CounterAtomic();
      counters[i].set(initialIndex);
    }
    
    return new CounterGroupSequence(counters);
  }
  
  @Override
  public CounterRing build(CounterRing[] counters, boolean isTail)
  {
    throw new UnsupportedOperationException();
  }
}
