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

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.query.ExprBuilderKelp;
import com.caucho.v5.kelp.query.ExprHandleBuilderKelp;
import com.caucho.v5.util.L10N;

/**
 * expression identifier at the top level, which will usually bind to a
 * column.
 */
public class IdExprBuilder extends ExprKraken
{
  private static final L10N L = new L10N(IdExprBuilder.class);
  
  private final String _name;
  
  public IdExprBuilder(String name)
  {
    _name = name;
  }
  //
  // builder methods
  //
  
  @Override
  public ExprKraken field(String name)
  {
    return new FieldExpr(this, name);
  }
  

  @Override
  public ExprBuilderKelp buildKelp(QueryBuilderKraken builder)
  {
    TableKelp table = builder.getTable().getTableKelp();
    
    Column column = table.getColumn(_name);
    
    if (column != null) {
      ExprBuilderKelp exprBuilder = builder.getBuilderKelp().field(_name);
      
      return exprBuilder;
    }
    
    switch (_name) {
    case ":pod_hash":
      return new ExprHandleBuilderKelp(new PodHashExprKelp(builder.getTable()));
      
    case ":pod_node":
      throw new UnsupportedOperationException();
//      return new ExprHandleBuilderKelp(new PodNodeExprKelp(builder.getTable()));

    default:
      break;
    }
    
    
    throw new QueryException(L.l("'{0}' is an unknown column of '{1}' in {2}",
                                 _name, table.getName(), builder.sql()));
  }
  
  //
  // bind methods
  //
  
  @Override
  public ExprKraken bind(QueryBuilderKraken builder)
  {
    return builder.bind(null, _name);
  }
  
  //
  // object-expr methods
  //
  
  @Override
  public String toObjectExpr(String columnName)
  {
    if (_name.equals("this")) {
      return columnName;
    }
    else {
      return columnName + "." + _name;
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _name + "]";
  }
}
