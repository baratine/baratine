/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is software; you can redistribute it and/or modify
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

package com.caucho.v5.network.port;

import java.util.Objects;

/**
 * Result request for the next state.
 */
public enum StateConnection
{
  FREE { // free is unallocated, either new or saved for reuse
    @Override
    StateConnection toAccepted() { return ACTIVE; }
    
    @Override
    public boolean isFree() { return true; }
  },
  
  // change to the idle state
  IDLE {
  },
  
  // accept a new request
  ACCEPT {
    @Override
    public boolean isRead() { return true; }
  },
  
  // handler is ready
  ACTIVE {
  },
  
  // wait for read data
  READ {
    @Override
    public boolean isRead() { return true; }
  },
  
  // wait for polled data
  POLL {
    @Override
    public boolean isPoll() { return true; }
  },
  
  // suspend the connection until a wake
  SLEEP {
    @Override
    public boolean isSuspend() { return true; }

    @Override
    public StateConnection toWake() { return ACTIVE; }
  },
  
  TIMEOUT {
  },
  
  CLOSE_READ_A {
  },
  
  CLOSE_READ_S {
    @Override
    public StateConnection toWake() { return CLOSE_READ_A; }
    
    @Override
    public StateConnection toCloseRead() { return this; }
  },
  
  // close the connection
  CLOSE {
    @Override
    public boolean isAcceptAllowed() { return true; }

    @Override
    public boolean isClose() { return true; }

    @Override
    public StateConnection onServiceNext(StateConnection result)
    {
      return this;
    }

    @Override
    public StateConnection toCloseRead()
    {
      return this;
    }

    @Override
    public StateConnection toFree()
    {
      return FREE;
    }  
  },
  
  // destroy the connection
  DESTROY {
    @Override
    public boolean isClose() { return true; }

    @Override
    public boolean isDestroy() { return true; }

    @Override
    public StateConnection onServiceNext(StateConnection result)
    {
      return this;
    }

    @Override
    public StateConnection toCloseRead()
    {
      return this;
    }

    @Override
    public StateConnection toFree()
    {
      return this;
    }  
  },
  ;

  public boolean isIdle()
  {
    return false;
  }

  public boolean isRead()
  {
    return false;
  }

  public boolean isPoll()
  {
    return false;
  }

  public boolean isClose()
  {
    return false;
  }

  public boolean isAcceptAllowed()
  {
    return false;
  }

  public boolean isSuspend()
  {
    return false;
  }
  
  public boolean isDestroy()
  {
    return false;
  }
  
  StateConnection toAccepted()
  {
    throw new IllegalStateException(toString());
  }

  public StateConnection toWake()
  {
    return this;
  }

  public StateConnection onServiceNext(StateConnection result)
  {
    Objects.requireNonNull(result);
    
    return result;
  }

  public StateConnection toClose()
  {
    return CLOSE;
  }

  public StateConnection toFree()
  {
    throw new IllegalStateException(toString());
  }

  public boolean isActive()
  {
    return false;
  }

  public boolean isFree()
  {
    return false;
  }

  public StateConnection toCloseRead()
  {
    return CLOSE_READ_A;
  }

  public boolean isTimeoutCapable()
  {
    // TODO Auto-generated method stub
    return false;
  }
}
