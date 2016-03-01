/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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
 * @author Alex Rojkov
 */
package com.caucho.v5.config.util;

import sun.misc.Unsafe;

class ObjectFactoryBuilderUnsafe extends ObjectFactoryBuilder
{
  private Unsafe _unsafe;

  ObjectFactoryBuilderUnsafe()
  {
    if (!UnsafeUtil.isEnabled())
      throw new IllegalStateException();

    _unsafe = UnsafeUtil.getUnsafe();
  }

  @Override
  public <X> ObjectFactory<X> build(Class<X> c)
  {
    return new UnsafeObjectFactory(_unsafe, c);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  static class UnsafeObjectFactory<X> extends ObjectFactory<X>
  {
    private final Unsafe _unsafe;
    private final Class<X> _cl;
    
    UnsafeObjectFactory(Unsafe unsafe, Class<X> cl)
    {
      _unsafe = unsafe;
      _cl = cl;
    }
  
    @Override
    public X allocate() throws InstantiationException
    {
      return (X) _unsafe.allocateInstance(_cl);
    }
  }
}
