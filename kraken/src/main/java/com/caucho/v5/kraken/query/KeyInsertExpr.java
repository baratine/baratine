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
import com.caucho.v5.kelp.query.QueryBuilderKelp;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Murmur64;

public class KeyInsertExpr extends ExprKraken
{
  private TableKraken _table;
  private ExprKraken _value;

  public KeyInsertExpr(TableKraken table, ExprKraken value)
  {
    _table = table;
    _value = value;
  }
  
  //
  // builder methods
  //
  
  /*
  @Override
  public int resultInt(RowCursor rowCursor)
  {
    byte []key = rowCursor.getKey();
    
    //int hash = Integer.reverse((key[0] << 24) | (key[1] << 16));
    
    int len = key.length;
    
    int hash = ((key[len - 2] & 0xff) << 8) | (key[len - 1] & 0xff);
    
    TablePod pod = _table.getTablePod();
    
    return hash;
  }
  */

  /*
  @Override
  public ExprBuilderKelp buildKelp(QueryBuilderKelp builder)
  {
    return builder.field(_column.getName());
  }
  */

  @Override
  public ExprBuilderKelp buildKelp(QueryBuilderKraken builder)
  {
    return new WrapperBuilder(this);
  }

  @Override
  public byte []evalBytes(Object []args)
  {
    String value = _value.evalString(args);
    
    byte []key = new byte[16];
    
    if (value == null) {
      return null;
    }
    
    int sublen = Math.min(value.length(), 8);
    
    for (int i = 0; i < sublen; i++) {
      key[i] = (byte) value.charAt(i);
    }

    long hash = Murmur64.generate(Murmur64.SEED, value);
    
    BitsUtil.writeLong(key, 8, hash);
    
    return key;
  }

  /*
  @Override
  public long resultLong(RowCursor rowCursor)
  {
    return rowCursor.getLong(_column.getIndex());
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
  public String resultString(RowCursor rowCursor)
  {
    return rowCursor.getString(_column.getIndex());
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
  */
}
