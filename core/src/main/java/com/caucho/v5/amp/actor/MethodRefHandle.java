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

package com.caucho.v5.amp.actor;

import java.io.Serializable;
import java.util.Objects;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServiceRefAmp;

/**
 * Serialization handle for a MethodRef.
 */
public class MethodRefHandle implements Serializable
{
  private String _address;
  private String _name;
  
  @SuppressWarnings("unused")
  private MethodRefHandle()
  {
  }
  
  public MethodRefHandle(String address, String name)
  {
    Objects.requireNonNull(address);
    Objects.requireNonNull(name);
    
    _address = address;
    _name= name;
  }

  public String getAddress()
  {
    return _address;
  }
  
  private Object readResolve()
  {
    //ServiceRefAmp serviceRef = (ServiceRefAmp) Services.getCurrentManager().lookup(_address);
    ServiceRefAmp serviceRef = new ServiceRefLazyContext(Amp.getContextManager(), _address);
    
    return serviceRef.getMethod(_name);
  }
    
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _address + "," + _name + "]";
  }
}
