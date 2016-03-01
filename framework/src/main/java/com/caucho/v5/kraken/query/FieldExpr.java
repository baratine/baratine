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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.kraken.query;

import com.caucho.v5.kelp.query.ExprBuilderKelp;
import com.caucho.v5.kelp.query.ExprKelp;

public class FieldExpr extends ExprKraken
{
  private ExprKraken _left;
  private String _fieldName;
  private ExprKelp _exprKelp;

  public FieldExpr(ExprKraken left,
                   String fieldName)
  {
    _left = left;
    _fieldName = fieldName;
  }

  @Override
  public ExprKraken field(String name)
  {
    return new FieldExpr(this, name);
  }
  
  //
  // kelp builder
  //

  @Override
  public ExprBuilderKelp buildKelp(QueryBuilderKraken builder)
  {
    return _left.buildKelp(builder).field(_fieldName);
  }
  
  /*
  @Override
  public ExprKraken bind(QueryBuilderKraken builderQuery)
  {
    QueryBuilderKelp builderKelp;
    builderKelp = new QueryBuilderKelp(builderQuery.getTable().getTableKelp());
    
    ExprBuilderKelp builderResult = buildKelp(builderKelp);
  
    _exprKelp = builderResult.build(builderKelp);
    
    return this;
  }
  */

  @Override
  public String toObjectExpr(String columnName)
  {
    return _left.toObjectExpr(columnName) + '.' + _fieldName;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _left + "," + _fieldName + "]";
  }
}
