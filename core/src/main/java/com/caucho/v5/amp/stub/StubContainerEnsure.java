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

package com.caucho.v5.amp.stub;

import java.util.HashMap;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ensure.MethodEnsureAmp;

import io.baratine.service.ResultChain;

/**
 * Baratine stub container for reliable messaging
 */
class StubContainerEnsure
{
  private StubContainerBase _container;

  private HashMap<MethodAmp, MethodEnsureAmp> _ensureMap;
  
  public StubContainerEnsure(StubContainerBase container)
  {
    _container = container;
    
    _ensureMap = new HashMap<>();
  }

  public ResultChain<?> ensure(StubAmpBean stub, 
                               MethodAmp method,
                               ResultChain<?> result, 
                               Object[] args)
  {
    return methodEnsure(method).ensure(stub, result, args);
  }

  public void onActive(MethodAmp method)
  {
    if (method instanceof FilterMethodEnsure) {
      FilterMethodEnsure ensure = (FilterMethodEnsure) method;
      
      method = ensure.delegate();
    }
    
    MethodEnsureAmp methodEnsure = methodEnsure(method);
    
    methodEnsure.onActive(_container.stub());
  }
  
  private MethodEnsureAmp methodEnsure(MethodAmp method)
  {
    HashMap<MethodAmp, MethodEnsureAmp> ensureMap = _ensureMap;
    
    if (ensureMap == null) {
      _ensureMap = ensureMap = new HashMap<>();
    }
    
    MethodEnsureAmp methodEnsure = ensureMap.get(method);
    
    if (methodEnsure == null) {
      ServicesAmp services = _container.stub().services();
    
      methodEnsure = services.ensureMethod(method);
      
      ensureMap.put(method, methodEnsure);
    }
    
    return methodEnsure;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
