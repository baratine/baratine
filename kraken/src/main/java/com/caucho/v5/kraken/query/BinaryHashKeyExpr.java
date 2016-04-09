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

import java.util.Set;

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kraken.table.TableKraken;

public class BinaryHashKeyExpr extends BinaryExpr
{
  private final TableKraken _table;
  private final ColumnExpr _column;
  private final Column _keyColumn;
  
  public BinaryHashKeyExpr(TableKraken table,
                           ColumnExpr column,
                           Column keyColumn,
                           ExprKraken value)
  {
    super(BinaryOp.EQ, column, value);
    
    _table = table;
    _column = column;
    _keyColumn = keyColumn;
  }

  /*
  @Override
  public ExprBuilderKelp buildKelp(QueryBuilderKelp builder)
  {
    return new LiteralBuilder(true);
  }
  */
  
  /**
   * Returns all the keys assigned statically, e.g. by param or literal.
   */
  @Override
  public void fillAssignedKeys(Set<String> keys)
  {
    keys.add(_keyColumn.name());
  }

  /**
   * Returns the assigned key expression
   */
  @Override
  public ExprKraken getKeyExpr(String name)
  {
    if (name.equals(_column.getColumn().name())) {
      return getRight();
    }
    else {
      return null;
    }
  }

  @Override
  public void fillCursor(RowCursor rowCursor, Object []args)
  {
    Column column = _column.getColumn();
    
    switch (column.type()) {
    case STRING: {
      String value = getRight().evalString(args);
      
      _table.fillHashKey(rowCursor, _keyColumn, value);
      
      //rowCursor.setString(column.getIndex(), value);
      break;
    }
      
    default:
      throw new UnsupportedOperationException(String.valueOf(column));
    }
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _column.getColumn().name()
            + "," + _keyColumn.name()
            + "," + getRight() + "]");
  }
}
