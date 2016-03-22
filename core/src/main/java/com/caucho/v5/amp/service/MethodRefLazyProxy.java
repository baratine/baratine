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

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.stub.MethodAmp;

/**
 * Sender for an actor ref.
 */
public class MethodRefLazyProxy extends MethodRefLazy
{
  private ServiceRefAmp _serviceRef;
  private String _methodName;
  
  public MethodRefLazyProxy(ServiceRefAmp serviceRef,
                            String methodName)
  {
    _serviceRef = serviceRef;
    _methodName = methodName;
  }
  
  @Override
  protected MethodRefAmp createDelegate()
  {
    MethodRefAmp methodRef = _serviceRef.getMethod(_methodName);

    if (methodRef != null && ! methodRef.isClosed()) {
      return methodRef;
    }
    else {
      return null;//new MethodRefNull(_serviceRef, _methodName);
    }
  }
  
  @Override
  public ServiceRefAmp getService()
  {
    return _serviceRef;
  }

  @Override
  public MethodAmp method()
  {
    MethodRefAmp delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.method();
    }
    else {
      return new MethodAmpLazyProxy(this);
    }
  }
  
  public String toString()
  {
    MethodRefAmp delegate = getDelegateLazy();
    
    if (delegate != null) {
      return getClass().getSimpleName() + "[" + delegate + "]";
    }
    else {
      return getClass().getSimpleName() + "[" + _methodName + "," + _serviceRef + "]";
    }
  }
}
