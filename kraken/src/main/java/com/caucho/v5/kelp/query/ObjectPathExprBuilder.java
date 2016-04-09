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

import com.caucho.v5.h3.QueryBuilderH3.PathBuilderH3;

/**
 * Query based on SQL.
 */
public class ObjectPathExprBuilder extends ExprBuilderKelp
{ 
  private ExprBuilderKelp _parent;
  private String _name;
  private PathMapHessian _pathMap;
  
  ObjectPathExprBuilder(ExprBuilderKelp parent,
                        String name)
  {
    _parent = parent;
    _name = name;
  }
  
  @Override
  public ExprBuilderKelp field(String name)
  {
    return new ObjectPathExprBuilder(this, name);
  }
  
  @Override
  protected String getPathName()
  {
    String parentPathName = _parent.getPathName();
    
    if (parentPathName == null) {
      return _name;
    }
    else {
      return parentPathName + "." + _name;
    }
  }
  
  @Override
  public PathMapHessian buildPathMap(QueryBuilder builder)
  {
    if (_pathMap == null) {
      //_pathMap = new PathMapHessian();
      throw new UnsupportedOperationException();
    }
    
    return _pathMap;
  }
  
  @Override
  public PathMapHessian getPathMap()
  {
    return _pathMap;
  }
  
  @Override
  protected PathHessian buildPath(QueryBuilder builder)
  {
    PathMapHessian pathMap = _parent.buildPathMap(builder);
    System.out.println("PM: " + pathMap + " " + _parent);
    
    return pathMap.field(_name);
    /*
    PathHessian subPath = pathMap.get(_name);
    
    if (subPath == null) {
      PathHessian pathParent = _parent.buildParentPath(builder);
      
      int index = builder.allocateValue();
      
      subPath = new PathFieldHessian(this, _name, index);
      
      pathMap.put(_name, subPath);
    }
    
    return subPath;
    */
  }

  /*
  @Override
  public PathBuilderH3 buildPathH3(QueryBuilder builder)
  {
    PathMapHessian pathMap = _parent.buildPathMap(builder);
    System.out.println("PM: " + pathMap + " " + _parent);
    
    return pathMap.field(_name);
  }
  */
  
  @Override
  protected PathHessian buildParentPath(QueryBuilder builder)
  {
    PathMapHessian pathMap = _parent.buildPathMap(builder);
    
    PathHessian subPath = pathMap.get(_name);
    
    if (subPath == null) {
      PathMapHessian pathMapSelf = buildPathMap(builder);
      
      subPath = new PathHessianFieldParent(this, pathMapSelf, _name);
      
      pathMap.put(_name, subPath);
    }
    
    return subPath;
  }

  @Override
  public ExprKelp build(QueryBuilder builder)
  {
    //PathHessian subPath = buildPath(builder);

    //  System.out.println("SP: " + subPath);

    PathBuilderH3 pathBuilder = _parent.pathH3(_name, builder);
    pathBuilder.build();
    
    return new ValueExprKelp(pathBuilder.index());
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _name + "]";
  }
}
