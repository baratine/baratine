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

import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.query.ExprBuilderKelp;
import com.caucho.v5.kelp.query.QueryBuilderKelp;

public class LiteralExpr extends ExprKraken
{
  private final Object _value;
  
  public LiteralExpr(Object value)
  {
    _value = value;
  }

  @Override
  public int evalInt(RowCursor cursor, Object []args)
  {
    if (_value instanceof Number) {
      Number number = (Number) _value;
      
      return number.intValue();
    }
    else {
      throw new UnsupportedOperationException(String.valueOf(_value));
    }
  }

  @Override
  public long evalLong(RowCursor cursor, Object []args)
  {
    if (_value instanceof Number) {
      Number number = (Number) _value;
      
      return number.longValue();
    }
    else {
      throw new UnsupportedOperationException(String.valueOf(_value));
    }
  }

  @Override
  public String evalString(Object []params)
  {
    if (_value == null) {
      return null;
    }
    else {
      return String.valueOf(_value);
    }
  }

  @Override
  public Object evalObject(RowCursor cursor, Object []params)
  {
    return _value;
  }
  
  /**
   * Object expr support.
   */
  
  @Override
  public String toObjectExpr(String columnName)
  {
    if (_value == null) {
      return "null";
    }
    else if (_value instanceof String) {
      return "'" + _value + "'";
    }
    else {
      return String.valueOf(_value);
    }
  }
  
  //
  // build methods
  //
  
  @Override
  public boolean isConstant()
  {
    return true;
  }

  @Override
  public ExprBuilderKelp buildKelp(QueryBuilderKraken builder)
  {
    return builder.getBuilderKelp().literal(_value);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _value + "]";
  }
}
