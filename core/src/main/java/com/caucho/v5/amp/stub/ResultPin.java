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

import com.caucho.v5.amp.ServiceRefAmp;

import io.baratine.service.Result;
import io.baratine.service.ResultChain;

/**
 * Counter/marker for handling multiple saves, e.g. with multiple resources.
 */
class ResultPin<V> extends Result.Wrapper<V,V>
{
  private final Class<V> _api;
  
  public ResultPin(ResultChain<V> delegate, Class<V> api)
  {
    super(delegate);
    
    _api = api;
  }

  @Override
  public void ok(V value)
  {
    if (value == null) {
      delegate().ok(null);
      return;
    }
    
    ServiceRefAmp serviceRef = ServiceRefAmp.current();

    delegate().ok(serviceRef.pin(value).as(_api));
  }
}
