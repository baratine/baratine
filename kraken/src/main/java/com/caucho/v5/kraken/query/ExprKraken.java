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
import java.util.Set;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.ExprBuilderKelp;
import com.caucho.v5.kelp.query.QueryBuilderKelp;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.util.L10N;

public class ExprKraken
{
  private static final L10N L = new L10N(ExprKraken.class);
  
  //
  // eval methods
  //
  
  public int evalInt(RowCursor cursor, Object []args)
  {
    return (int) evalLong(cursor, args);
  }
  
  public long evalLong(RowCursor cursor, Object []args)
  {
    Object value = evalObject(cursor, args);
    
    if (value == null) {
      return 0;
    }
    else if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    else {
      throw error("'{0}' (1) is an invalid long value",
                  value,
                  value.getClass().getName());
    }
  }
  
  public double evalDouble(RowCursor cursor, Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public Object evalObject(RowCursor cursor, Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName() + " " + toString());
  }
  
  protected RuntimeException error(String msg, Object ...args)
  {
    return new ConfigException(L.l(msg, args));
  }

  public byte []evalBytes(Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public String evalString(Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public InputStream evalInputStream(Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void fillCursor(RowCursor rowCursor, Object []args)
  {
  }

  public int resultInt(EnvKelp rowCursor)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public long resultLong(EnvKelp rowCursor)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public double resultDouble(RowCursor rowCursor)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public byte []resultBytes(RowCursor rowCursor)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public String resultString(EnvKelp env)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public Object resultObject(RowCursor rowCursor)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public InputStream resultInputStream(RowCursor rowCursor)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void fillMinCursor(RowCursor minCursor, Object []args)
  {
    fillCursor(minCursor, args);
  }

  public void fillMaxCursor(RowCursor maxCursor, Object []args)
  {
    fillCursor(maxCursor, args);
  }

  public int fillNodeHash(TableKraken table,
                          RowCursor cursor, 
                          Object[] args)
  {
    fillMinCursor(cursor, args);
    
    return table.getPodHash(cursor);
  }
  
  
  //
  // builder methods
  //
  
  public ExprKraken field(String name)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  //
  // binding
  //
  
  /**
   * True if the expression is a constant for all rows, e.g. params.
   */
  public boolean isConstant()
  {
    return false;
  }
  
  /**
   * Returns all the keys assigned statically, e.g. by param or literal.
   */
  public void fillAssignedKeys(Set<String> keys)
  {
    keys.clear();
  }

  /**
   * Returns the assigned key expression
   */
  public ExprKraken getKeyExpr(String name)
  {
    return null;
  }

  public ExprKraken bind(QueryBuilderKraken builder)
  {
    return this;
  }

  public ExprKraken bindKey(QueryBuilderKraken builder)
  {
    return this;
  }

  public ExprBuilderKelp buildKelp(QueryBuilderKraken builder)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  //
  // sub-query
  //
  
  /**
   * Returns a valid expression from an earlier object-expression, used
   * to implement object queries where the caller doesn't know the table
   * but does know the object structure.
   */
  
  public String toObjectExpr(String columnName)
  {
    throw new UnsupportedOperationException(getClass().getSimpleName());
  }
  
  //
  // exec methods
  // 

  public EnvKelp bind(Object[] args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /*
  public final int partitionHash(Object[] args)
  {
    return -1;
  }
  */
}
