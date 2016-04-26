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

import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.ExprKelp;
import com.caucho.v5.kraken.table.TableKraken;

import io.baratine.db.BlobReader;
import io.baratine.db.Cursor;


public class CursorKraken implements Cursor
{
  private EnvKelp _envKelp;
  private RowCursor _rowCursor;
  private ExprKelp[] _results;
  
  public CursorKraken(TableKraken table,
                      EnvKelp envKelp, 
                      RowCursor rowCursor,
                      ExprKelp[] results)
  {
    _envKelp = envKelp;
    _rowCursor = rowCursor;
    _results = results;
    
    _rowCursor.serializer(table.serializer());
  }
  
  public RowCursor getRowCursor()
  {
    return _rowCursor;
  }
  
  @Override
  public long getVersion()
  {
    return _rowCursor.getVersion();
  }
  
  @Override
  public long getUpdateTime()
  {
    return _rowCursor.getStateTime();
  }
  
  @Override
  public long getTimeout()
  {
    return _rowCursor.getStateTimeout();
  }
  
  @Override
  public int getInt(int index)
  {
    return _results[index - 1].evalInt(_envKelp);
  }
  
  @Override
  public long getLong(int index)
  {
    return _results[index - 1].evalLong(_envKelp);
  }
  
  @Override
  public double getDouble(int index)
  {
    return _results[index - 1].evalDouble(_envKelp);
  }
  
  @Override
  public String getString(int index)
  {
    return _results[index - 1].evalString(_envKelp);
  }
  
  @Override
  public Object getObject(int index)
  {
    return _results[index - 1].evalObject(_envKelp);
  }
  
  @Override
  public byte []getBytes(int index)
  {
    return _results[index - 1].evalBytes(_envKelp);
  }
  
  @Override
  public InputStream getInputStream(int index)
  {
    return _results[index - 1].evalInputStream(_envKelp);
  }
  
  @Override
  public BlobReader getBlobReader(int index)
  {
    return _results[index - 1].evalBlobReader(_envKelp);
  }

  @Override
  public int getColumnCount()
  {
    return _results.length;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _rowCursor + "]";
  }
}
