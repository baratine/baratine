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

import java.io.InputStream;

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.Column.ColumnType;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.ExprBuilderKelp;
import com.caucho.v5.kelp.query.QueryBuilderKelp;

public class ColumnExpr extends ExprKraken
{
  private final Column _column;
  
  public ColumnExpr(Column column)
  {
    _column = column;
  }

  public Column getColumn()
  {
    return _column;
  }
  //
  // builder methods
  //
  
  @Override
  public ExprKraken field(String name)
  {
    if (_column.type() == ColumnType.OBJECT) {
      return new FieldExpr(this, name);
    }
    else {
      throw new UnsupportedOperationException(String.valueOf(_column));
    }
  }
  

  @Override
  public ExprBuilderKelp buildKelp(QueryBuilderKraken builder)
  {
    return builder.getBuilderKelp().field(_column.name());
  }

  @Override
  public int evalInt(RowCursor cursor, Object []args)
  {
    throw new UnsupportedOperationException(String.valueOf(_column));
  }

  @Override
  public int resultInt(EnvKelp env)
  {
    return env.getCursor().getInt(_column.getIndex());
  }

  @Override
  public long resultLong(EnvKelp env)
  {
    return env.getCursor().getLong(_column.getIndex());
  }

  @Override
  public double resultDouble(RowCursor rowCursor)
  {
    return rowCursor.getDouble(_column.getIndex());
  }

  @Override
  public byte []resultBytes(RowCursor rowCursor)
  {
    return rowCursor.getBytes(_column.getIndex());
  }

  @Override
  public String resultString(EnvKelp env)
  {
    return env.getCursor().getString(_column.getIndex());
  }

  @Override
  public Object resultObject(RowCursor rowCursor)
  {
    return rowCursor.getObject(_column.getIndex());
  }

  @Override
  public InputStream resultInputStream(RowCursor rowCursor)
  {
    return rowCursor.openInputStream(_column.getIndex());
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _column + "]";
  }
}
