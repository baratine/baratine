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

import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * Lazy init proxy
 */
abstract public class ServiceRefLazyBase extends ServiceRefWrapper
{
  private final ServicesAmp _manager;
  
  private final AtomicReference<ServiceRefAmp> _delegateRef
    = new AtomicReference<>();
    
  protected ServiceRefLazyBase(ServicesAmp manager)
  {
    _manager = manager;
  }

  @Override
  public ServicesAmp manager()
  {
    return _manager;
  }
  
  protected ServiceRefAmp getDelegateLazy()
  {
    return _delegateRef.get();
  }
  
  @Override
  public boolean isClosed()
  {
    ServiceRefAmp delegate = _delegateRef.get();
    
    return delegate == null || delegate.isClosed();
  }
  
  @Override
  protected ServiceRefAmp delegate()
  {
    ServiceRefAmp delegate = _delegateRef.get();
    
    if (delegate == null) {
      synchronized (_delegateRef) {
        delegate = _delegateRef.get();
        
        if (delegate == null) {
          delegate = newDelegate();
          
          if (delegate != null && ! delegate.isClosed()) {
            _delegateRef.set(delegate);
          }
        }
      }
    }
    
    return delegate;
  }
  
  abstract protected ServiceRefAmp newDelegate();
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    ServiceRefAmp delegate = getDelegateLazy();
    
    if (delegate != null) {
      delegate.shutdown(mode);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _delegateRef.get() + "]";
  }
}
