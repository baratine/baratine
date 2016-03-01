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
 * @author Scott Ferguson
 */

package com.caucho.v5.profile;

import java.util.logging.Logger;

import javax.management.JMException;

import com.caucho.v5.util.MemoryPoolAdapter;

public class MemoryUtil //extends ManagedObjectBase 
  //implements MemoryMXBean
{
  private static final Logger log = Logger.getLogger(MemoryUtil.class.getName());
  
  private static MemoryUtil _admin;
  
  private MemoryPoolAdapter _memoryPoolAdapter;
  
  private MemoryUtil()
  {
    //super(MemoryAdmin.class.getClassLoader());
    
    _memoryPoolAdapter = new MemoryPoolAdapter();
    
    //registerSelf();
  }

  public static MemoryUtil create()
  {
    synchronized (MemoryUtil.class) {
      if (_admin == null) {
        _admin = new MemoryUtil();
      }
      
      return _admin;
    }
  }

  //@Override
  public String getName()
  {
    return null;
  }

  public long getCodeCacheCommitted()
  {
    try {
      return _memoryPoolAdapter.getCodeCacheCommitted();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getCodeCacheMax()
  {
    try {
      return _memoryPoolAdapter.getCodeCacheMax();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getCodeCacheUsed()
  {
    try {
      return _memoryPoolAdapter.getCodeCacheUsed();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getCodeCacheFree()
  {
    try {
      return _memoryPoolAdapter.getCodeCacheFree();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getEdenCommitted()
  {
    try {
      return _memoryPoolAdapter.getEdenCommitted();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getEdenMax()
  {
    try {
      return _memoryPoolAdapter.getEdenMax();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getEdenUsed()
  {
    try {
      return _memoryPoolAdapter.getEdenUsed();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getEdenFree()
  {
    try {
      return _memoryPoolAdapter.getEdenFree();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getSurvivorCommitted()
  {
    try {
      return _memoryPoolAdapter.getSurvivorCommitted();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getSurvivorMax()
  {
    try {
      return _memoryPoolAdapter.getSurvivorMax();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getSurvivorUsed()
  {
    try {
      return _memoryPoolAdapter.getSurvivorUsed();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getSurvivorFree()
  {
    try {
      return _memoryPoolAdapter.getSurvivorFree();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getTenuredCommitted()
  {
    try {
      return _memoryPoolAdapter.getTenuredCommitted();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getTenuredMax()
  {
    try {
      return _memoryPoolAdapter.getTenuredMax();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getTenuredUsed()
  {
    try {
      return _memoryPoolAdapter.getTenuredUsed();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getTenuredFree()
  {
    try {
      return _memoryPoolAdapter.getTenuredFree();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getGarbageCollectionTime()
  {
    try {
      return _memoryPoolAdapter.getGarbageCollectionTime();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }

  public long getGarbageCollectionCount()
  {
    try {
      return _memoryPoolAdapter.getGarbageCollectionCount();
    } catch (JMException e) {
      throw new RuntimeException(e);
    }
  }
}
