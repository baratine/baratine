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

import com.caucho.v5.util.L10N;

import io.baratine.service.Shim;
import io.baratine.service.Result;

/**
 * Counter/marker for handling multiple saves, e.g. with multiple resources.
 */
class ResultShim<V,T> extends Result.Wrapper<V,V>
{
  private static final L10N L = new L10N(ResultShim.class);
  
  private TransferAsset<T,V> _shim;
  private StubAmp _stub;

  public ResultShim(Result<V> delegate, 
                    StubAmp stub,
                    TransferAsset<T,V> shim)
  {
    super(delegate);
    
    _stub = stub;
    _shim = shim;
  }

  @Override
  public void ok(V value)
  {
    if (value != null) {
      throw new IllegalArgumentException(L.l("Only null is allowed for a @{0} result.ok, but received {1}'",
                                             Shim.class.getSimpleName(),
                                             value));
    }
    T bean = (T) _stub.bean();

    delegate().ok(_shim.toTransfer(bean));
  }
}
