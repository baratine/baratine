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

import io.baratine.db.BlobReader;

import java.io.InputStream;

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.Column.ColumnType;

/**
 * Building a program for a kraken/kelp query
 */
public class ColumnExprKelp extends ExprKelp
{
  private final Column _column;
  
  public ColumnExprKelp(Column column)
  {
    _column = column;
  }

  @Override
  public Object eval(EnvKelp cxt)
  {
    switch (_column.getType()) {
    case INT16:
      return cxt.getCursor().getInt(_column.getIndex());
      
    case INT32:
      return cxt.getCursor().getInt(_column.getIndex());
      
    case INT64:
      return cxt.getCursor().getLong(_column.getIndex());
      
    case DOUBLE:
      return cxt.getCursor().getDouble(_column.getIndex());
      
    case STRING:
      return cxt.getCursor().getString(_column.getIndex());
      
    case BYTES:
      return cxt.getCursor().getBytes(_column.getIndex());
      
    case OBJECT:
      return cxt.getCursor().getObject(_column.getIndex());
      
    default:
      throw new UnsupportedOperationException(String.valueOf(_column.getType()));
    }
  }

  @Override
  public String evalString(EnvKelp env)
  {
    if (_column.getType() == ColumnType.STRING) {
      return env.getCursor().getString(_column.getIndex());
    }
    else {
      return String.valueOf(eval(env));
    }
  }

  @Override
  public Object evalObject(EnvKelp env)
  {
    return eval(env);
  }

  @Override
  public byte[] evalBytes(EnvKelp env)
  {
    return env.getCursor().getBytes(_column.getIndex());
  }

  @Override
  public InputStream evalInputStream(EnvKelp env)
  {
    return env.getCursor().openInputStream(_column.getIndex());
  }

  @Override
  public BlobReader evalBlobReader(EnvKelp env)
  {
    return env.getCursor().openBlobReader(_column.getIndex());
  }

  @Override
  public String toString()
  {
    return _column.name();
  }
}
