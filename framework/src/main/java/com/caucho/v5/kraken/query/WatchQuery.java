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

import io.baratine.db.DatabaseWatch;
import io.baratine.service.Cancel;
import io.baratine.service.Result;

import java.util.Objects;

import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kraken.table.TableKraken;


public class WatchQuery extends QueryKraken
{
  private final TableKraken _table;
  private ExprKraken _keyExpr;
  private ExprKraken _whereKraken;
  private EnvKelp _whereKelp;
  
  WatchQuery(String sql,
              TableKraken tableKraken,
              ExprKraken keyExpr,
              ExprKraken whereKraken,
              EnvKelp whereKelp)
  {
    super(sql);
    
    Objects.requireNonNull(whereKraken);
    Objects.requireNonNull(whereKelp);
    
    _table = tableKraken;
    _keyExpr = keyExpr;
    _whereKraken = whereKraken;
    _whereKelp = whereKelp;
  }
  
  @Override
  public TableKraken table()
  {
    return _table;
  }

  /**
   * Returns the hash code for the owning partition. If the hash is negative,
   * the query does not have an owning partition.
   */
  /*
  @Override
  public int partitionHash(Object[] args)
  {
    if (_keyExpr == null) {
      return -1;
    }
    
    return _keyExpr.partitionHash(args);
  }
  */

  @Override
  public void watch(DatabaseWatch watch, 
                    Result<Cancel> result, 
                    Object... args)
  {
    TableKelp tableKelp = _table.getTableKelp();
    
    RowCursor minCursor = tableKelp.cursor();
    RowCursor maxCursor = tableKelp.cursor();
    
    minCursor.clear();
    maxCursor.setKeyMax();
    
    _whereKraken.fillMinCursor(minCursor, args);
    _whereKraken.fillMaxCursor(minCursor, args);
    
    //QueryKelp whereKelp = _whereExpr.bind(args);
    // XXX: binding should be with unique
    //EnvKelp whereKelp = new EnvKelp(_whereKelp, args);
    
    //tableKelp.findOne(minCursor, maxCursor, whereKelp,
    //                  new FindDeleteResult(result));
    
    _table.addWatch(watch, minCursor.getKey(), result);
    
    // result.completed(null);
  }

  /*
  @Override
  public void unwatch(DatabaseWatch watch, Object... args)
  {
    TableKelp tableKelp = _table.getTableKelp();
    
    RowCursor minCursor = tableKelp.cursor();
    RowCursor maxCursor = tableKelp.cursor();
    
    minCursor.clear();
    maxCursor.setKeyMax();
    
    _whereKraken.fillMinCursor(minCursor, args);
    _whereKraken.fillMaxCursor(minCursor, args);
    
    //QueryKelp whereKelp = _whereExpr.bind(args);
    // XXX: binding should be with unique
    //EnvKelp whereKelp = new EnvKelp(_whereKelp, args);
    
    //tableKelp.findOne(minCursor, maxCursor, whereKelp,
    //                  new FindDeleteResult(result));
    
    _table.removeWatch(watch, minCursor.getKey());
    
    // result.completed(null);
  }
  */
 
  @Override
  public void fillKey(RowCursor cursor, Object []args)
  {
    _whereKraken.fillMinCursor(cursor, args);
  }
  
  /*
  private class FindDeleteResult extends Result.Wrapper<RowCursor,Object>
  {
    FindDeleteResult(Result<Object> result)
    {
      super(result);
    }
    
    @Override
    public void complete(RowCursor rowCursor)
    {
      if (rowCursor == null) {
        getNext().complete(false);
        return;
      }
      
      rowCursor.setVersion(0);
      
      // XXX: needs to be a callback inside the find
      BackupKelp backup = _table.getBackupCallback();
      
      _table.getTableKelp().remove(rowCursor, backup, (Result) getNext());
    }
  }
  */
}
