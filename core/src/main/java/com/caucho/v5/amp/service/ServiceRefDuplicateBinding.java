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

import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;

/**
 * Abstract implementation for a service ref.
 */
public class ServiceRefDuplicateBinding extends ServiceRefBase
{
  private static final Logger log
    = Logger.getLogger(ServiceRefDuplicateBinding.class.getName());
  
  private ServicesAmp _manager;
  private String _address;
  private RuntimeException _exn;
  
  public ServiceRefDuplicateBinding(ServicesAmp manager,
                             String address, 
                             RuntimeException exn)
  {
    _manager = manager;
    _address = address;
    _exn = exn;
    
    exn.printStackTrace();
  }
  
  @Override
  public String address()
  {
    return _address;
  }
  
  @Override
  public boolean isUp()
  {
    return false;
  }
  
  @Override
  public boolean isClosed()
  {
    return true;
  }
  
  @Override
  public InboxAmp inbox()
  {
    throw _exn;
  }

  @Override
  public MethodRefAmp methodByName(String methodName)
  {
    return new MethodRefException(this, methodName, _exn);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + address() + "]";
  }
}
