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

package com.caucho.v5.amp.service;

import java.io.Serializable;
import java.util.Objects;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServiceManagerAmp;

/**
 * Abstract implementation for a service ref.
 */
public class ServiceRefHandle implements Serializable
{
  private String _address;
  private transient ServiceManagerAmp _manager;
  
  private ServiceRefHandle()
  {
  }
  
  public ServiceRefHandle(String address,
                          ServiceManagerAmp manager)
  {
    Objects.requireNonNull(address);
    
    if (address.startsWith("public:///")) {
      ClassLoader loader = manager.classLoader();
      
      /*
      PodBartender pod = BartenderSystem.getCurrentPod(loader);
      
      address = address.substring("public://".length());
      
      if (pod != null) {
        address = "pod://" + pod.getName() + address;
      }
      else {
        address = "remote://" + address;
      }
      */
      address = "remote://" + address;
    }
    
    _address = address;
    _manager = manager;
  }

  public String getAddress()
  {
    return _address;
  }
  
  private Object readResolve()
  {
    
    String address = _address;
    
    // baratine/3320
    //ServiceManagerAmp manager = Amp.getContextManager();
    ServiceManagerAmp manager = _manager;
    
    if (manager == null || ! _address.startsWith("session:")) {
      manager = Amp.getCurrentManager();
    }

    // cloud/0516
    /*
    if (address.startsWith("public:")) {
      address = "remote:" + address.substring("public:".length());
      
      return manager.lookup(address);
    }
    */
    
    return new ServiceRefLazyContext(manager, address);
  }
    
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _address + "]";
  }
}
