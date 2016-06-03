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

/**
 * The id can be used for sequence-based tables like log files because
 * the ids are strictly increasing, and the sequence bits are sufficient to
 * avoid rollover.
 */
public final class IdentityGenerator
{
  private static final L10N L = new L10N(IdentityGenerator.class);
  
  private long _node;
    
  private final int  _timeBits;
  private final int _timeOffset;
  private int _sequenceBits;
  private int _sequenceIncrement;
  
  private long _sequenceMask;
  
  private AtomicLong _sequence = new AtomicLong();

  private long _sequenceRandomMask;
  private boolean _isRandom = true;

  
  /**
   * Incrementing generator with a node index, used for database ids.
   */
  private IdentityGenerator(IdentityBuilder builder)
  {
    _timeBits = builder.timeBits();
    _isRandom = builder.isRandom();
    int nodeBits = builder.nodeBits();
    
    _timeOffset = 64 - _timeBits;
    
    _node = Long.reverse(builder.node()) >>> _timeBits;
    
    _sequenceBits = _timeOffset;
    _sequenceMask = (1L << _sequenceBits) - 1;
    
    _sequenceRandomMask = (1L << (_sequenceBits - nodeBits - 2)) - 1;
    
    _sequenceIncrement = 1;
  }
  
  public static IdentityBuilder newGenerator()
  {
    return new IdentityBuilder();
  }
  
  public long time(long id)
  {
    return (id >> _timeOffset) * 1000;
  }
  
  public long sequence(long id)
  {
    return id & ((1L << _timeOffset) - 1);
  }

  /**
   * Returns the current id.
   */
  public long current()
  {
    return _sequence.get();
  }
  
  public void update(long sequence)
  {
    if (_sequence.get() < sequence) {
      _sequence.set(sequence + 1);
    }
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
    if (_isRandom) {
      return RandomUtil.getRandomLong();
    }
    else {
      return 0;
    }
  }
  
  public static class IdentityBuilder
  {
    private int _timeBits = 34;
    private int _sequenceIncrement = 1;
    private int _node = 0;
    private boolean _isRandom = true;
    
    public int timeBits()
    {
      return _timeBits;
    }
    
    public IdentityBuilder timeBits(int timeBits)
    {
      _timeBits = timeBits;
      
      return this;
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
    public IdentityBuilder increment(int increment)
    {
      if (increment < 0 || increment % 2 == 0) {
        throw new IllegalArgumentException(L.l("'{0}' is an invalid sequence increment",
                                               increment));
      }
      
      _sequenceIncrement = increment;
      
      return this;
    }
    
    public int increment()
    {
      return _sequenceIncrement;
    }
    
    public int node()
    {
      return _node;
    }
    
    public IdentityBuilder node(int node)
    {
      if (_node < 0) {
        throw new IllegalArgumentException();
      }
      
      _node = node;
      
      return this;
    }
    
    public int nodeBits()
    {
      if (_sequenceIncrement == 1) {
        return 12;
      }
      else {
        return 10;
      }
    }
    
    public boolean isRandom()
    {
      return _isRandom;
    }
    
    public IdentityBuilder random(boolean isRandom)
    {
      _isRandom = isRandom;
      
      return this;
    }
    
    public IdentityGenerator get()
    {
      return new IdentityGenerator(this);
    }
  }
}
