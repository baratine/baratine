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

package com.caucho.v5.h3.query;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.caucho.v5.h3.QueryBuilderH3;
import com.caucho.v5.h3.context.ContextH3;

/**
 * H3 output interface
 */
public class QueryBuilderH3Impl implements QueryBuilderH3
{
  private ContextH3 _context;
  
  private ArrayList<PathBuilderH3Impl> _paths = new ArrayList<>();
  
  private AtomicInteger _count = new AtomicInteger();
  
  public QueryBuilderH3Impl(ContextH3 context)
  {
    Objects.requireNonNull(context);
    _context = context;
  }
  
  @Override
  public PathBuilderH3 field(String fieldName)
  {
    return new PathBuilderH3Impl(fieldName); 
  }
  
  @Override
  public QueryBuilderH3 count(AtomicInteger count)
  {
    Objects.requireNonNull(count);
    
    if (_count.get() != 0) {
      throw new IllegalStateException();
    }
    
    _count = count;
    
    return this;
  }
  
  private int addPath(PathBuilderH3Impl pathBuilder)
  {
    pathBuilder.index(_count.getAndIncrement());
    
    _paths.add(pathBuilder);
    
    return pathBuilder.index();
  }
  
  @Override
  public QueryH3Impl build()
  {
    PathH3Node root = new PathH3Node();
    
    int index = 0;
    for (PathBuilderH3Impl builder : _paths) {
      builder.build(root);
    }
    
    return new QueryH3Impl(_context, root, _count.get());
  }
  
  /**
   * Path builder
   */
  private class PathBuilderH3Impl implements PathBuilderH3
  {
    private String _fieldName;
    private int _index = -1;
    
    PathBuilderH3Impl(String fieldName)
    {
      Objects.requireNonNull(fieldName);
      
      _fieldName = fieldName;
    }

    public void build(PathH3Node root)
    {
      root.leaf(_fieldName, index());
    }
    
    public void index(int index)
    {
      if (_index >= 0) {
        throw new IllegalStateException();
      }
      
      _index = index;
    }
    
    @Override
    public int index()
    {
      if (_index < 0) {
        throw new IllegalStateException();
      }
      
      return _index;
    }

    @Override
    public QueryBuilderH3Impl build()
    {
      if (_index < 0) {
        _index = addPath(this);
      }
      
      return QueryBuilderH3Impl.this;
    }
  }
}
