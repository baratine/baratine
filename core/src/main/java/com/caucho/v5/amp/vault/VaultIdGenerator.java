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

package com.caucho.v5.amp.vault;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.RandomUtil;

import io.baratine.service.Ids;

public class VaultIdGenerator
{
  static Supplier<String> create(Class<?> idType)
  {
    if (idType.equals(long.class) || idType.equals(Long.class)) {
      return new IdGeneratorLong();
    }
    else {
      return new IdGeneratorUnsupported(idType);
    }
  }
  
  private static class IdGeneratorUnsupported implements Supplier<String>
  {
    private Class<?> _type;
    
    IdGeneratorUnsupported(Class<?> type)
    {
      _type = type;
    }

    @Override
    public String get()
    {
      throw new UnsupportedOperationException(_type.toString());
    }
  }
  
  private static class IdGeneratorLong implements Supplier<String>
  {
    private long _node;
    
    private AtomicLong _sequence = new AtomicLong();
    private int _nodeBits = 10; 
    private long _sequenceMask;
    
    IdGeneratorLong()
    {
      _sequence.set(RandomUtil.getRandomLong());
      
      _sequenceMask = (1L << (64 - Ids.TIME_BITS - _nodeBits)) - 1;
    }

    @Override
    public String get()
    {
      long now = CurrentTime.getCurrentTime() / 1000;
      long sequence = _sequence.incrementAndGet();
      
      long id = ((now << (64 - Ids.TIME_BITS))
                | (_node << (64 - Ids.TIME_BITS - _nodeBits))
                | (sequence & _sequenceMask));
      
      return Ids.encode(id);
    }
  }
}
