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
import com.caucho.v5.kelp.Row;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.query.BinaryOpKelp;
import com.caucho.v5.kelp.query.ExprBuilderKelp;
import com.caucho.v5.kelp.query.QueryBuilderKelp;
import com.caucho.v5.kraken.table.TableKraken;

public class BetweenExpr extends ExprKraken
{
  private ExprKraken _expr;
  private ExprKraken _min;
  private ExprKraken _max;
  private boolean _isNot;

  public BetweenExpr(ExprKraken expr,
                     ExprKraken min,
                     ExprKraken max,
                     boolean isNot)
  {
    _expr = expr;
    _min = min;
    _max = max;
    _isNot = isNot;
  }
  
  //
  // bind
  //
  
  @Override
  public ExprKraken bind(QueryBuilderKraken builder)
  {
    ExprKraken expr = _expr.bind(builder);
    ExprKraken min = _min.bind(builder);
    ExprKraken max = _max .bind(builder);

    /*
    ExprKraken keyExpr = bindKey(builder, left, right);
    
    if (keyExpr != null) {
      return keyExpr;
    }
    
    if (left == _left && right == _right) {
      return this;
    }
    */
    
    return new BetweenExpr(expr, min, max, _isNot);
  }
  
  /*
  private BetweenExpr bindKey(QueryBuilderKraken builder,
                                ExprKraken left,
                                ExprKraken right)
  {
    if (_op != BinaryOp.EQ) {
      return null;
    }
    
    if (left instanceof ColumnExpr) {
      ColumnExpr leftCol = (ColumnExpr) left;
      
      if (isKey(builder.getTable(), leftCol) && right.isConstant()) {
        return new BinaryKeyExpr(leftCol, right);
      }
      else if (isHashKey(builder.getTable(), leftCol) && right.isConstant()) {
        Column keyColumn = builder.getTable().getColumn(":key_" + leftCol.getColumn().getName());
        
        return new BinaryHashKeyExpr(builder.getTable(), leftCol, keyColumn, right);
      }
    }
    
    if (right instanceof ColumnExpr) {
      ColumnExpr rightCol = (ColumnExpr) right;
      
      if (isKey(builder.getTable(), rightCol) && left.isConstant()) {
        return new BinaryKeyExpr(rightCol, left);
      }
    }
    
    return null;
  }
  */

  @Override
  public ExprBuilderKelp buildKelp(QueryBuilderKraken builder)
  {
    ExprBuilderKelp expr = _expr.buildKelp(builder);
    ExprBuilderKelp min = _min.buildKelp(builder);
    ExprBuilderKelp max = _max.buildKelp(builder);
    
    return expr.between(min, max);
  }
  
  @Override
  public String toObjectExpr(String columnName)
  {
    return ("(" + _expr.toObjectExpr(columnName)
            + " BETWEEN "
            + " " + _min.toObjectExpr(columnName)
            + " " + _max.toObjectExpr(columnName)
            + ")");
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _expr + "," + _min + "," + _max + "]";
  }
}
