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

package com.caucho.v5.bartender;

/**
 * Represents the current state of a cloud server.
 */
public enum ServerBartenderState {
  unknown {
    // treat unknown as active so initial pings/requests will complete
    // baratine/a014
    //@Override
    //public boolean isActive() { return true; }

    @Override
    public ServerBartenderState toKnown()
    {
      return down;
    }
  },
  
  down {
    @Override
    public boolean isKnown()
    { 
      return true;
    }
  },
  
  disabled {
    @Override
    public ServerBartenderState onHeartbeatStart()
    {
      return disabled;
    }
    
    @Override
    public ServerBartenderState onHeartbeatStop()
    {
      return disabled;
    }
    
    @Override
    public boolean isDisabled()
    {
      return true;
    }
    @Override
    public boolean isKnown()
    { 
      return true;
    }
  },
  
  disabled_soft {
    @Override
    public ServerBartenderState onHeartbeatStart()
    {
      return disabled_soft;
    }
    
    @Override
    public ServerBartenderState onHeartbeatStop()
    {
      return disabled_soft;
    }

    @Override
    public boolean isDisableSoft()
    {
      return true;
    }
    
    @Override
    public boolean isKnown()
    { 
      return true;
    }
  },
  
  failed_heartbeat {
    @Override
    public boolean isKnown()
    { 
      return true;
    }
  },
  
  up {
    @Override
    public boolean isActive()
    {
      return true;
    }
    
    @Override
    public boolean isKnown()
    { 
      return true;
    }
  };

  public ServerBartenderState onHeartbeatStart()
  {
    return up;
  }

  public ServerBartenderState toKnown()
  {
    return this;
  }
  
  public ServerBartenderState onHeartbeatStop()
  {
    return down;
  }

  public boolean isDisableSoft()
  {
    return false;
  }
  
  public boolean isDisabled()
  {
    return false;
  }

  public boolean isActive()
  {
    return false;
  }

  public boolean isKnown()
  {
    return false;
  }
}
