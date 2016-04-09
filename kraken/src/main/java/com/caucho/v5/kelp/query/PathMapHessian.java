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

package com.caucho.v5.kelp.query;

import java.util.HashMap;
import java.util.Objects;

import com.caucho.v5.h3.QueryBuilderH3;
import com.caucho.v5.h3.QueryBuilderH3.PathBuilderH3;

/**
 * A map of paths from a given node.
 */
public class PathMapHessian
{
  private final HashMap<String,PathHessian> _pathMap = new HashMap<>();
  
  private QueryBuilderH3 _queryBuilder;
  
  PathMapHessian(QueryBuilderH3 queryBuilder)
  {
    Objects.requireNonNull(queryBuilder);
    
    _queryBuilder = queryBuilder;
  }
  
  QueryBuilderH3 queryBuilder()
  {
    return _queryBuilder;
  }
  
  void put(String key, PathHessian path)
  {
    Thread.dumpStack();
    _pathMap.put(key, path);
  }
  
  PathHessian get(String key)
  {
    return _pathMap.get(key);
  }

  public PathHessian field(String name)
  {
    PathBuilderH3 subPath = queryBuilder().field(name);
    
    // TODO Auto-generated method stub
    return null;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
