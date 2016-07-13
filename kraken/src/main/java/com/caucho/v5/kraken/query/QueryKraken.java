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

import io.baratine.db.Cursor;
import io.baratine.db.CursorPrepareSync;
import io.baratine.db.DatabaseWatch;
import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.stream.ResultStream;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.MethodRef;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.util.L10N;


public class QueryKraken
{
  private static final L10N L = new L10N(QueryKraken.class);
  
  private final String _sql;

  protected QueryKraken(String sql)
  {
    Objects.requireNonNull(sql);
    
    _sql = sql;
  }
  
  public String getSql()
  {
    return _sql;
  }
  
  public TableKraken table()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  public void exec(Result<Object> result,
                   Object ...args)
  {
    throw new UnsupportedOperationException(L.l("{0} does not implement exec()", 
                                                getClass().getSimpleName()));
  }
  
  public Object execSync(Object ...args)
  {
    ServicesAmp manager = table().getManager();
    
    return manager.run(10, TimeUnit.SECONDS, r->exec(r, args));
  }
  
  /*
  public void exec(Object ...args)
  {
    exec(null, args);
  }
  */

  public boolean isStaticNode()
  {
    // TODO Auto-generated method stub
    return false;
  }
  
  /*
  public int partitionHash(Object ...args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
  
  public int calculateHash(RowCursor cursor)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void findOne(Result<Cursor> result, Object ...args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void findOneDirect(Result<Cursor> result, Object ...args)
  {
    findOne(result, args);
  }

  public Cursor findOneFuture(Object ...args)
  {
    ServicesAmp manager = table().getManager();
    
    return manager.run(10, TimeUnit.SECONDS, r->findOne(r, args));
  }

  public void findAll(Result<Iterable<Cursor>> result, Object ...args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void findAllLocal(Result<Iterable<Cursor>> result, Object ...args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void map(MethodRef method, Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public Iterable<Cursor> findAll(Object ...args)
  {
    ResultFuture<Iterable<Cursor>> future = new ResultFuture<>();
    
    findAll(future, args);
    
    return future.get(10, TimeUnit.SECONDS);
  }

  public void findStream(ResultStream<Cursor> result, Object[] args)
  {
    findAll(Result.of(iter->fillStream(result, iter), 
                        e->result.fail(e)), 
            args);
  }
  
  private void fillStream(ResultStream<Cursor> result,
                          Iterable<Cursor> iter)
  {
    try {
      for (Cursor cursor : iter) {
        result.accept(cursor);
      }
      
      result.ok();
    } catch (Throwable e) {
      result.fail(e);
    }
  }

  public void findAllLocalKeys(Result<Iterable<byte[]>> result, Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void fillKey(RowCursor cursor, Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void watch(DatabaseWatch watch, Result<Cancel> result, Object  ...args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /*
  public void unwatch(DatabaseWatch watch, Object...args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  public CursorPrepareSync prepare()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _sql + "]";
  }
}
