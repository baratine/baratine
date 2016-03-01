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

package com.caucho.v5.amp.manager;

import java.util.Objects;
import java.util.function.Supplier;

import com.caucho.v5.amp.spi.ActorAmp;

/**
 * Supplier of ActorAmp created from a bean supplier.
 */
public class SupplierActor implements Supplier<ActorAmp>
{
  private final AmpManager _manager;
  private final Supplier<?> _supplierBean;
  private final ServiceConfig _config;
  
  private ActorAmp _firstActor;
  
  public SupplierActor(AmpManager manager,
                       Supplier<?> supplierBean,
                       ServiceConfig config)
  {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(supplierBean);
    
    _manager = manager;
    _supplierBean = supplierBean;
    _config = config;
  }
  
  public SupplierActor(AmpManager manager,
                       Supplier<?> supplierBean,
                       ActorAmp firstActor,
                       ServiceConfig config)
  {
    this(manager, supplierBean, config);
    
    _firstActor = firstActor;
  }
  
  @Override
  public ActorAmp get()
  {
    ActorAmp firstActor = _firstActor;
    
    if (firstActor != null) {
      _firstActor = null;

      return firstActor;
    }
    
    ActorAmp actor = _manager.createActor(_supplierBean.get(), _config);
    
    return actor;
  }
  
  @Override
  public String toString()
  {
    if (_firstActor != null) {
      return getClass().getSimpleName() + "[" + _firstActor + "]";
    }
    else {
      return getClass().getSimpleName() + "[" + _supplierBean + "]";
    }
  }
}
