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

import java.io.IOException;

import com.caucho.v5.h3.QueryBuilderH3;
import com.caucho.v5.h3.QueryBuilderH3.PathBuilderH3;

/**
 * Building a program for hessian.
 */
public class PathHessianNode extends PathHessian
{
  private PathBuilderH3 _pathBuilder;
  private int _index;
  
  PathHessianNode(PathBuilderH3 pathBuilder)
  {
    _pathBuilder = pathBuilder;
  }

  void leaf()
  {
    _pathBuilder.build();
    
    _index = _pathBuilder.index();
  }
  /*
  public String getName()
  {
    return _name;
  }
  
  @Override
  public PathMapHessian getPathMap()
  {
    PathMapHessian pathMap = _pathBuilder.getPathMap();
    
    if (pathMap != null) {
      return pathMap;
    }
    else {
      return super.getPathMap();
    }
  }
  
  @Override
  void scan(EnvKelp query, Object []values)
      throws IOException
  {
    values[_index] = query.readObject();
  }
  */
  
  @Override
  public int getIndex()
  {
    return _index;
  }
  
  /*
  @Override
  public String toString()
  {
    return _name;
  }
  */
}
