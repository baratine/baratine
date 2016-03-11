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

import java.util.ArrayList;

/**
 * Abstract counter for a multi-processor queue.
 */
public class CounterBuilderParallel extends CounterBuilderBase
{
  private final CounterBuilder[] _children;
  private final int _tailIndex;

  CounterBuilderParallel(ArrayList<CounterBuilder> children, 
                              int tailIndex)
  {
    if (children.size() <= 1) {
      throw new IllegalArgumentException();
    }
    
    _children = new CounterBuilder[children.size()];
    
    children.toArray(_children);
    
    _tailIndex = tailIndex;
  }
  
  @Override
  public int getHeadIndex()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public int getTailIndex()
  {
    return _tailIndex;
  }

  @Override
  public final CounterBuilder getTail()
  {
    return this;
  }

  @Override
  public CounterRing build(CounterRing[] counters, boolean isTail)
  {
    CounterRing []prev = new CounterRing[_children.length];
    int i = 0;
    
    for (CounterBuilder child : _children) {
      prev[i++] = child.build(counters, false);
    }
    
    CounterRing join = new CounterJoin(prev);
    
    counters[getTailIndex()] = join;
    
    return join;
  }

  public CounterBuilder[] getChildren()
  {
    return _children;
  }
}
