/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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
 * @author Alex Rojkov
 */

package com.caucho.v5.util;

import java.util.concurrent.atomic.AtomicLong;

import io.baratine.vault.IdAsset;

/**
 * The id can be used for sequence-based tables like log files because
 * the ids are strictly increasing, and the sequence bits are sufficient to
 * avoid rollover.
 */
public final class IdentityGenerator
{
  private static final L10N L = new L10N(IdentityGenerator.class);
  
  private static int TIME_BITS = 34;
  
  private long _node;
    
  private int _timeOffset;
  private int _sequenceBits;
  private int _sequenceIncrement;
  
  private long _sequenceMask;
  
  private AtomicLong _sequence = new AtomicLong();

  private long _sequenceRandomMask;
  
  /**
   * Incrementing generator with a node index, used for database ids.
   */
  public IdentityGenerator(int nodeIndex)
  {
    _timeOffset = 64 - TIME_BITS;
    
    _node = Long.reverse(nodeIndex) >>> TIME_BITS;
    
    _sequenceBits = _timeOffset;
    _sequenceMask = (1L << _sequenceBits) - 1;
    
    int nodeBits = 12;
    _sequenceRandomMask = (1L << (_sequenceBits - nodeBits - 2)) - 1;
    
    _sequenceIncrement = 1;
  }

  /**
   * Id generator used for session id generation.
   * 
   * This id may not be strictly increasing because the sequence increment
   * may cause wrap-around of the sequence bits. The wrap-around will not
   * caused collisions because the increment is relatively prime to the
   * sequence space, i.e. all the sequence bits will be used, just not in a
   * simple increment order.
   */
  public IdentityGenerator(int nodeIndex, 
                          int sequenceIncrement)
  {
    if (sequenceIncrement < 0 || sequenceIncrement % 2 == 0) {
      throw new IllegalArgumentException(L.l("'{0}' is an invalid sequence increment",
                                             sequenceIncrement));
    }
    
    _timeOffset = 64 - TIME_BITS;    
    
    _node = Long.reverse(nodeIndex) >>> TIME_BITS;
    
    int nodeBits = 10;

    _sequenceBits = _timeOffset - nodeBits;
    _sequenceMask = (1L << _sequenceBits) - 1;
    _sequenceRandomMask = (1L << (_sequenceBits - 2)) - 1;
    
    _sequenceIncrement = sequenceIncrement;
  }
  
  /**
   * Returns the next id.
   */
  public long get()
  {
    long now = CurrentTime.currentTime() / 1000;
    
    long oldSequence;
    long newSequence;
    
    do {
      oldSequence = _sequence.get();
      
      long oldTime = oldSequence >>> _timeOffset;
    
      if (oldTime != now) {
        newSequence = ((now << _timeOffset)
                      + (randomLong() & _sequenceRandomMask));
      }
      else {
        // relatively prime increment will use the whole sequence space
        newSequence = oldSequence + _sequenceIncrement;
      }
    } while (! _sequence.compareAndSet(oldSequence, newSequence));
      
    long id = ((now << _timeOffset)
               | _node
               | (newSequence & _sequenceMask));
      
    return id;
  }
  
  protected long randomLong()
  {
    return RandomUtil.getRandomLong();
  }
}
