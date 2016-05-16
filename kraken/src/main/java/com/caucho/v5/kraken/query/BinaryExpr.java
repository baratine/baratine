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

public class BinaryExpr extends ExprKraken
{
  private BinaryOp _op;
  private ExprKraken _left;
  private ExprKraken _right;

  public BinaryExpr(BinaryOp op,
                    ExprKraken left,
                    ExprKraken right)
  {
    _op = op;
    _left = left;
    _right = right;
  }
  
  protected ExprKraken getRight()
  {
    return _right;
  }
  
  //
  // bind
  //
  
  @Override
  public ExprKraken bind(QueryBuilderKraken builder)
  {
    ExprKraken left = _left.bind(builder);
    ExprKraken right = _right.bind(builder);
    
    ExprKraken keyExpr = bindKey(builder, left, right);
    
    if (keyExpr != null) {
      return keyExpr;
    }
    
    if (left == _left && right == _right) {
      return this;
    }
    
    return new BinaryExpr(_op, left, right);
  }
  
  private BinaryExpr bindKey(QueryBuilderKraken builder,
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
        Column keyColumn = builder.getTable().getColumn(":key_" + leftCol.getColumn().name());
        
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
  
  /*
  @Override
  public ExprKraken bindKey(QueryBuilderKraken builder)
  {
    if (_op != BinaryOp.EQ) {
      return null;
    }
    
    if (_left instanceof ColumnExpr) {
      ColumnExpr left = (ColumnExpr) _left;
      
      if (isKey(builder.getTable(), left)) {
        return new PodKeyExpr(left.getColumn(), _right); 
      }
    }
    
    if (_right instanceof ColumnExpr) {
      ColumnExpr right = (ColumnExpr) _right;
      
      if (isKey(builder.getTable(), right)) {
        return new PodKeyExpr(right.getColumn(), _left); 
      }
    }
    
    return null;
  }
  */
  
  private boolean isHashKey(TableKraken table,
                            ColumnExpr columnExpr)
  {
    String keyName = ":key_" + columnExpr.getColumn().name();
    
    Column keyColumn = table.getColumn(keyName);
    
    return (keyColumn != null && isKey(table, keyColumn));
  }
  
  private boolean isKey(TableKraken table,
                        ColumnExpr columnExpr)
  {
    Column col = columnExpr.getColumn();
    
    return isKey(table, col);
  }
  
    
  private boolean isKey(TableKraken table,
                        Column column)
  {
    Row row = table.getTableKelp().row();
    
    int keyOffset = row.getKeyOffset();
    int keyLength = row.getKeyLength();
    
    int colOffset = column.getOffset();

    return keyOffset <= colOffset && colOffset < keyOffset + keyLength;
  }

  @Override
  public ExprBuilderKelp buildKelp(QueryBuilderKraken builder)
  {
    ExprBuilderKelp left = _left.buildKelp(builder);
    ExprBuilderKelp right = _right.buildKelp(builder);
    
    return _op.buildKelp(left, right);
  }

  @Override
  public void fillCursor(RowCursor rowCursor, Object []args)
  {
    if (_op != BinaryOp.EQ) {
      return;
    }
    
    if (! (_left instanceof ColumnExpr)) {
      return;
    }
    
    Column column = ((ColumnExpr) _left).getColumn();
    
    switch (column.type()) {
    case INT16:
    case INT32:
      rowCursor.setInt(column.getIndex(), _right.evalInt(rowCursor, args));
      break;
      
    case INT64:
      rowCursor.setLong(column.getIndex(), _right.evalLong(rowCursor, args));
      break;
      
    case STRING:
      rowCursor.setString(column.getIndex(), _right.evalString(args));
      break;
      
    case BYTES:
      rowCursor.setBytes(column.getIndex(), _right.evalBytes(args), 0);
      break;
      
    default:
      break;
    }
  }

  @Override
  public long evalLong(RowCursor cursor, Object []args)
  {
    Object leftValue = _left.evalObject(cursor, args);
    Object rightValue = _right.evalObject(cursor, args);
    
    return _op.evalLong(leftValue, rightValue);
  }
  
  public Object evalObject(RowCursor cursor, Object []args)
  {
    Object leftValue = _left.evalObject(cursor, args);
    Object rightValue = _right.evalObject(cursor, args);
    
    return _op.evalObject(leftValue, rightValue);
  }
  
  @Override
  public String toObjectExpr(String columnName)
  {
    return ("(" + _left.toObjectExpr(columnName)
            + " " + _op
            + " " + _right.toObjectExpr(columnName) + ")");
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _op + "," + _left + "," + _right + "]";
  }
}
