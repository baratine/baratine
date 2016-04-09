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

import com.caucho.v5.h3.QueryBuilderH3;
import com.caucho.v5.h3.QueryBuilderH3.PathBuilderH3;
import com.caucho.v5.kelp.Column;

/**
 * Query based on SQL.
 */
public class ObjectColumnExprBuilder extends ColumnExprBuilder
{
  private QueryBuilderH3 _queryBuilder;
  
  ObjectColumnExprBuilder(Column column)
  {
    super(column);
  }
  
  @Override
  public ExprBuilderKelp field(String name)
  {
    return new ObjectPathExprBuilder(this, name);
  }

  @Override
  public PathMapHessian buildPathMap(QueryBuilder builder)
  {
    PathKelp path = builder.addPath(getColumn());
    
    return path.pathMap();
  }
  
  public PathBuilderH3 pathH3(String field, QueryBuilder builder)
  {
    if (_queryBuilder == null) {
      _queryBuilder = builder.pathH3(getColumn());
    }
    
    return _queryBuilder.field(field);
  }
}
