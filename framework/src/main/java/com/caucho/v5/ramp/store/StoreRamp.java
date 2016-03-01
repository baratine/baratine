/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.ramp.store;

import com.caucho.v5.ramp.keyvalue.StoreSync;

import io.baratine.service.OnLoad;
import io.baratine.service.Result;
import io.baratine.service.Service;

/*
 * Entry to the store.
 */
@Service
public class StoreRamp
{
  private StoreRootRamp _root;
  private String _path;
  
  public StoreRamp(StoreRootRamp root, String path)
  {
    _root = root;
    _path = path;
  }
  
  @OnLoad
  void onLoad(Result<Boolean> result)
  {
    _root.onLoad(result);
  }

  public void get(String key, Result<Object> result)
  {
    _root.get(_path + key, result);
  }
  
  public StoreSync<?> lookup(String path)
  {
    return _root.lookup(_path + path);
  }

  /*
  public void findOne(String sql, Result<Object> result, Object ...args)
  {
    _root.findOneImpl(_path, sql, result, args);
  }

  public void find(ResultStream result, String sql, Object ...args)
  {
    System.out.println("RAND: " + result);
    _root.findImpl(_path, sql, args, result);
  }

  public void findLocal(ResultStream result, String sql, Object ...args)
  {
    _root.findLocalImpl(_path, sql, args, result);
  }
  */

  public void put(String key, Object value, Result<Void> result)
  {
    _root.put(_path + key, value, result);
  }

  public void remove(String key, Result<Void> result)
  {
    _root.remove(_path + key, result);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
