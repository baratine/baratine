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

import io.baratine.service.Result;

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.UpdateKelp;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.TablePod;


public class UpdateQuery extends QueryKraken
{
  private final TableKraken _table;
  private ExprKraken _keyExpr;
  private ExprKraken _whereKraken;
  private EnvKelp _whereKelp;
  
  private UpdateKelp _update;
  
  UpdateQuery(String sql,
              TableKraken tableKraken,
              ExprKraken keyExpr,
              ExprKraken whereKraken,
              UpdateKelp update,
              EnvKelp whereKelp)
  {
    super(sql);
    
    Objects.requireNonNull(whereKraken);
    Objects.requireNonNull(whereKelp);
    Objects.requireNonNull(update);
    
    _table = tableKraken;
    _keyExpr = keyExpr;
    _whereKraken = whereKraken;
    _update = update;
    _whereKelp = whereKelp;
  }
  
  @Override
  public TableKraken table()
  {
    return _table;
  }

  @Override
  public void exec(Result<Object> result, Object ...args)
  {
    if (isStaticNode()) {
      updateSelf(result, args);
      return;
    }
    
    TablePod tablePod = _table.getTablePod();
    
    int nodeCount = tablePod.getNodeCount();
    
    if (nodeCount <= 1) {
      updateSelf(result, args);
      return;
    }
    
    ArrayList<ServerBartender> servers = tablePod.getUpdateServers();
    
    if (servers.size() <= 1) {
      // validate against self
      updateSelf(result, args);
      return;
    }
    
    UpdateReduceResult resultUpdate
      = new UpdateReduceResult(result, servers.size());

    for (int i = 0; i < nodeCount; i++) {
      tablePod.update(resultUpdate, i, getSql(), args);
    }
  }
  
  private void updateSelf(Result<Object> result, Object []args)
  {
    TableKelp tableKelp = _table.getTableKelp();
    
    RowCursor minCursor = tableKelp.cursor();
    RowCursor maxCursor = tableKelp.cursor();
    
    minCursor.clear();
    maxCursor.setKeyMax();
    
    _keyExpr.fillMinCursor(minCursor, args);
    _keyExpr.fillMaxCursor(minCursor, args);
    
    //QueryKelp whereKelp = _whereExpr.bind(args);
    // XXX: binding should be with unique
    EnvKelp whereKelp = new EnvKelp(_whereKelp, args);
    
    whereKelp.setAttribute("krakenTable", _table);
    
    tableKelp.update(minCursor, maxCursor, whereKelp,
                     _update,
                     _table.getBackupCallback(),
                     result.of(x->x));
    
    // result.completed(null);
  }

  public void execLocal(Result<Object> result, Object[] args)
  {
    updateSelf(result, args);
  }
  
  @Override
  public void fillKey(RowCursor cursor, Object []args)
  {
    _keyExpr.fillMinCursor(cursor, args);
  }

  /*
  private class FindUpdateResult extends Result.Wrapper<Integer,Object>
  {
    FindUpdateResult(Result<Object> result)
    {
      super(result);
    }
    
    @Override
    public void complete(Integer value)
    {
      getNext().complete(value);
    }
  }
  */
  
  private static class UpdateReduceResult extends Result.Wrapper<Integer,Object>
  {
    private int _count;
    private int _countResult;
    
    UpdateReduceResult(Result<Object> result, int count)
    {
      super(result);
      
      _count = count;
    }
    
    @Override
    public void ok(Integer value)
    {
      if (value != null) {
        _countResult += value;
      }
      
      if (--_count == 0) {
        delegate().ok(_countResult);
      }
    }
    
    @Override
    public void fail(Throwable exn)
    {
      _count = -1;
      
      delegate().fail(exn);
    }
    
    /*
    @Override
    public void fail(Throwable exn)
    {
      _count = -1;
    }
    */
  }
}
