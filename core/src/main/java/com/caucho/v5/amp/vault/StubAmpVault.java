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

package com.caucho.v5.amp.vault;

import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.stub.StubAmpBean;
import com.caucho.v5.amp.stub.StubClass;

import io.baratine.service.Result;

/**
 * Stub for a vault bean.
 */
public class StubAmpVault extends StubAmpBean
{
  private StubClass _stubClassAsset;

  public StubAmpVault(StubClass stubClassVault,
                      StubClass stubClassAsset,
                      Object bean,
                      String path,
                      ServiceConfig config)
  {
    super(stubClassVault, bean, path, null, config);
    
    _stubClassAsset = stubClassAsset;
  }
  
  @Override
  public void onActive(Result<? super Boolean> result)
  {
    super.onActive(result);
    
    if (_stubClassAsset != null) {
      _stubClassAsset.onActive(container());
    }
  }
  
  @Override
  public boolean isLifecycleAware()
  {
    return true;
  }
}
