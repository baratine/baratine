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

package com.caucho.v5.lifecycle;

/**
 * Lifecycle constants.
 */
public enum LifecycleState {
  NEW {
    @Override
    public boolean isBeforeInit() { return true; }
  },
  
  INITIALIZING {
    @Override
    public boolean isBeforeInit() { return true; }
    
    @Override
    public boolean isInitializing() { return true; }
  },
  
  INIT {
    @Override
    public boolean isInit() { return true; }
    
    @Override
    public boolean isIdle() { return true; }
  },
  
  STARTING {
    @Override
    public boolean isStarting() { return true; }
  },
  
  STANDBY {
    @Override
    public boolean isAllowStopFromRestart() { return true; }
  },
  
  STOPPED_IDLE {
    @Override
    public boolean isStoppedIdle() { return true; }
    
    @Override
    public boolean isIdle() { return true; }
    
    @Override
    public boolean isStopped() { return true; }
  },
  
  WARMUP {
    @Override
    public boolean isWarmup() { return true; }
    
    @Override
    public boolean isRunnable() { return true; }
  },
  
  ACTIVE {
    @Override
    public boolean isActive() { return true; }
    @Override
    public boolean isRunnable() { return true; }
    @Override
    public boolean isAllowStopFromRestart() { return true; }
  },
  
  FAILED {
    @Override
    public boolean isError() { return true; }
    @Override
    public boolean isAllowStopFromRestart() { return true; }
  },
  
  STOPPING {
    @Override
    public boolean isStopping() { return true; }
  },
  
  STOPPED {
    @Override
    public boolean isStopped() { return true; }
  },
  
  DESTROYING {
    @Override
    public boolean isDestroying() { return true; }
  },
  
  DESTROYED {
    @Override
    public boolean isDestroyed() { return true; }
  };

  //
  // State predicates.
  //
  
  public int getState() 
  {
    return ordinal(); 
  }

  public String getStateName()
  {
    return toString();
  }

  public boolean isBeforeInit()
  {
    return false;
  }

  public boolean isInitializing()
  {
    return false;
  }

  public boolean isInit()
  {
    return false;
  }

  public boolean isAfterInit()
  {
    return INIT.ordinal() <= ordinal();
  }

  public boolean isStoppedIdle()
  {
    return false;
  }

  public boolean isIdle()
  {
    return false;
  }

  public boolean isStarting()
  {
    return false;
  }

  public boolean isAfterStarting()
  {
    return STARTING.ordinal() <= ordinal();
  }

  public boolean isBeforeActive()
  {
    return ordinal() < ACTIVE.ordinal();
  }

  public boolean isWarmup()
  {
    return false;
  }    

  public boolean isAfterActive()
  {
    return ACTIVE.ordinal() <= ordinal();
  }

  public boolean isActive()
  {
    return false;
  }
  
  public boolean isRunnable()
  {
    return false;
  }

  public boolean isError()
  {
    return false;
  }

  public boolean isStopping()
  {
    return false;
  }

  public boolean isAfterStopping()
  {
    return STOPPING.ordinal() <= ordinal();
  }

  public boolean isAllowStopFromRestart()
  {
    return false;
  }

  public boolean isStopped()
  {
    return false;
  }

  public boolean isDestroying()
  {
    return false;
  }

  public boolean isAfterDestroying()
  {
    return DESTROYING.ordinal() <= ordinal();
  }

  public boolean isDestroyed()
  {
    return false;
  }
}
