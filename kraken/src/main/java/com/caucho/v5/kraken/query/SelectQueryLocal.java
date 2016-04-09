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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.ExprKelp;
import com.caucho.v5.kraken.table.TableKraken;

import io.baratine.db.Cursor;
import io.baratine.service.Result;


public class SelectQueryLocal extends SelectQueryBase
{
  private final TableKraken _table;
  private ExprKraken _keyExpr;
  private ExprKraken _whereKraken;
  private EnvKelp _whereKelp;
  private ExprKelp[] _results;
  private boolean _isStaticNode;
  
  SelectQueryLocal(String sql,
              SelectQueryBuilder builder,
              TableKraken tableKraken,
              ExprKraken keyExpr,
              ExprKraken whereKraken,
              EnvKelp whereKelp,
              ExprKelp [] results)
  {
    super(sql);
    
    Objects.requireNonNull(whereKraken);
    Objects.requireNonNull(whereKelp);
    
    _table = tableKraken;
    _keyExpr = keyExpr;
    _whereKraken = whereKraken;
    _whereKelp = whereKelp;
    _results = results;
    
    _isStaticNode = builder.isStaticNode(whereKraken);
  }
  
  @Override
  public TableKraken table()
  {
    return _table;
  }

  @Override
  ExprKraken getWhere()
  {
    return _whereKraken;
  }

  @Override
  ExprKraken getKeyExpr()
  {
    return _keyExpr;
  }

  @Override
  public boolean isStaticNode()
  {
    return _isStaticNode;
  }

  @Override
  public boolean isLocal()
  {
    return true;
  }

  @Override
  public void fillKey(RowCursor cursor, Object []args)
  {
    _keyExpr.fillMinCursor(cursor, args);
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
  public int calculateHash(RowCursor cursor)
  {
    return table().getPodHash(cursor);
  }

  @Override
  public void findOne(Result<Cursor> result, Object ...args)
  {
    TableKelp tableKelp = _table.getTableKelp();
    
    RowCursor minCursor = _table.cursor();
    RowCursor maxCursor = _table.cursor();
    
    minCursor.clear();
    maxCursor.setKeyMax();
    
    _keyExpr.fillMinCursor(minCursor, args);
    _keyExpr.fillMaxCursor(maxCursor, args);
    
    EnvKelp envKelp = new EnvKelp(_whereKelp, args);

    /*
    if (isLocal()) {
      tableKelp.get(minCursor,
                    result.from(v->findOneGetResult(v, envKelp, minCursor)));
    }
    else
    */ 
    if (isStaticNode()) {
      // key is filled in
      tableKelp.get(minCursor, 
                    result.of(v->findOneGetResult(v, envKelp, minCursor)));
    }
    else {
      // QueryKelp whereKelp = _whereExpr.bind(args);
      // XXX: binding should be with unique

      tableKelp.findOne(minCursor, maxCursor, envKelp,
                        result.of(cursor->findOneResult(cursor, envKelp)));

      // result.completed(null);
    }
  }

  @Override
  public void findOneDirect(Result<Cursor> result, Object ...args)
  {
    TableKelp tableKelp = _table.getTableKelp();
    
    RowCursor minCursor = _table.cursor();
    RowCursor maxCursor = _table.cursor();
    
    minCursor.clear();
    maxCursor.setKeyMax();
    
    _keyExpr.fillMinCursor(minCursor, args);
    _keyExpr.fillMaxCursor(maxCursor, args);
    
    EnvKelp envKelp = new EnvKelp(_whereKelp, args);
    
    if (isLocal()) {
      getDirect(result, minCursor, args);
      /*
      tableKelp.getDirect(minCursor,
                    result.from(v->findOneGetResult(v, envKelp, minCursor)));
                    */
    }
    else if (isStaticNode()) {
      getDirect(result, minCursor, args);
      /*
      tableKelp.getDirect(minCursor, 
                    result.from(v->findOneGetResult(v, envKelp, minCursor)));
                    */
    }
    else {
      // QueryKelp whereKelp = _whereExpr.bind(args);
      // XXX: binding should be with unique

      tableKelp.findOne(minCursor, maxCursor, envKelp,
                        result.of(cursor->findOneResult(cursor, envKelp)));

      // result.completed(null);
    }
  }

  public void getDirect(Result<Cursor> result, RowCursor cursor, Object []args)
  {
    TableKelp tableKelp = _table.getTableKelp();
    
    EnvKelp envKelp = new EnvKelp(_whereKelp, args);
    
    tableKelp.getDirect(cursor,
                        result.of(v->findOneGetResult(v, envKelp, cursor)));
  }
  
  private Cursor findOneGetResult(Boolean value, 
                               EnvKelp envKelp, 
                               RowCursor rowCursor)
  {
    if (Boolean.TRUE.equals(value)) {
      envKelp.test(rowCursor);

      return new CursorKraken(table(), envKelp, rowCursor, _results);
    }
    else {
      return null;
    }
  }
  
  private Cursor findOneResult(RowCursor value, 
                               EnvKelp envKelp)
  {
    if (value != null) {
      //envKelp.test(rowCursor);
      envKelp.test(value);
      
      return new CursorKraken(table(), envKelp, value, _results);
    }
    else {
      return null;
    }
  }

  public void getOne(Result<Cursor> result, byte[] key, Object[] args)
  {
    TableKelp tableKelp = _table.getTableKelp();
    
    RowCursor cursor = _table.cursor();
    
    cursor.setKey(key, 0);
    
    EnvKelp envKelp = createEnv(args);
    
    tableKelp.getDirect(cursor, result.of(x->onGet(envKelp, cursor, x)));
  }

  @Override
  public void findAll(Result<Iterable<Cursor>> result, Object ...args)
  {
    TableKelp tableKelp = _table.getTableKelp();
    
    RowCursor minCursor = _table.cursor();
    RowCursor maxCursor = _table.cursor();
    
    minCursor.clear();
    maxCursor.setKeyMax();

    _whereKraken.fillMinCursor(minCursor, args);
    _whereKraken.fillMaxCursor(maxCursor, args);
    
    //QueryKelp whereKelp = _whereExpr.bind(args);
    // XXX: binding should be with unique
    EnvKelp whereKelp = createEnv(args);

    tableKelp.findAll(minCursor, maxCursor, whereKelp,
                      result.of(x->onFindAll(whereKelp, x)));

    // result.completed(null);
  }

  @Override
  public void findAllLocalKeys(Result<Iterable<byte[]>> result, Object []args)
  {
    TableKelp tableKelp = _table.getTableKelp();
    
    RowCursor minCursor = _table.cursor();
    RowCursor maxCursor = _table.cursor();
    
    minCursor.clear();
    maxCursor.setKeyMax();

    _whereKraken.fillMinCursor(minCursor, args);
    _whereKraken.fillMaxCursor(maxCursor, args);
    
    //QueryKelp whereKelp = _whereExpr.bind(args);
    // XXX: binding should be with unique
    EnvKelp whereKelp = createEnv(args);

    tableKelp.findAll(minCursor, maxCursor, whereKelp,
                      result.of(x->onFindAllKeys(x)));
  }
  
  private EnvKelp createEnv(Object []args)
  {
    EnvKelp env = new EnvKelp(_whereKelp, args);
    
    env.setAttribute("krakenTable", _table);
    
    return env;
  }
  
  /*
  private class FindResult extends Result.Wrapper<RowCursor,Cursor>
  {
    private EnvKelp _envKelp;
    
    FindResult(Result<Cursor> result, EnvKelp envKelp)
    {
      super(result);
      
      _envKelp = envKelp;
    }
    
    @Override
    public void complete(RowCursor rowCursor)
    {
      if (rowCursor == null) {
        getNext().complete(null);
        return;
      }
      
      CursorKraken cursor = new CursorKraken(_envKelp, rowCursor, _results);
      
      getNext().complete(cursor);
    }
  }
  */
  
  /*
  private class GetResult extends Result.Wrapper<Boolean,Cursor>
  {
    private EnvKelp _envKelp;
    private RowCursor _rowCursor;
    
    GetResult(Result<Cursor> result, 
              EnvKelp envKelp, 
              RowCursor rowCursor)
    {
      super(result);
      
      _envKelp = envKelp;
      _rowCursor = rowCursor;
    }
    
    @Override
    public void complete(Boolean isFound)
    {
      if (Boolean.TRUE.equals(isFound)) {
        _envKelp.test(_rowCursor);

        CursorKraken cursor = new CursorKraken(_envKelp, _rowCursor, _results);
        
        getNext().complete(cursor);
      }
      else {
        getNext().complete(null);
      }
    }
  }
  */
  
  private Cursor onGet(EnvKelp envKelp, RowCursor rowCursor, Boolean isFound)
  {
    if (Boolean.TRUE.equals(isFound)) {
      envKelp.test(rowCursor);

      CursorKraken cursor = new CursorKraken(table(), envKelp, rowCursor, _results);
      
      return cursor;
    }
    else {
      return null;
    }
  }
  
  /*
  private class FindAllResult extends Result.Wrapper<Iterable<RowCursor>,Iterable<Cursor>>
  {
    private EnvKelp _envKelp;
    
    FindAllResult(EnvKelp envKelp, Result<Iterable<Cursor>> result)
    {
      super(result);
      
      _envKelp = envKelp;
    }
    
    @Override
    public void complete(Iterable<RowCursor> rowIter)
    {
      if (rowIter == null) {
        getNext().complete(null);
        return;
      }
      
      getNext().complete(new CursorIterable(_envKelp, rowIter, _results));
    }
  }
  */
  
  private Iterable<Cursor> onFindAll(EnvKelp envKelp, 
                                     Iterable<RowCursor> rowIter)
  {
    if (rowIter == null) {
      return null;
    }
    
    return new CursorIterable(envKelp, rowIter, _results);
  }
  
  /*
  private class FindAllKeysResult extends Result.Wrapper<Iterable<RowCursor>,Iterable<byte[]>>
  {
    private ArrayList<byte[]> _keys = new ArrayList<>();
    
    FindAllKeysResult(Result<Iterable<byte[]>> result)
    {
      super(result);
    }
    
    @Override
    public void complete(Iterable<RowCursor> rowIter)
    {
      if (rowIter == null) {
        getNext().complete(null);
        return;
      }
      
      for (RowCursor cursor : rowIter) {
        _keys.add(cursor.getKey());
      }
      
      getNext().complete(_keys);
    }
  }
  */
  
  private ArrayList<byte []> onFindAllKeys(Iterable<RowCursor> rowIter)
  {
    if (rowIter == null) {
      return null;
    }
    
    ArrayList<byte[]> keys = new ArrayList<>();
    
    for (RowCursor cursor : rowIter) {
      keys.add(cursor.getKey());
    }
    
    return keys;
    
  }
  
  private class CursorIterable implements Iterable<Cursor>
  {
    private EnvKelp _envKelp;
    private Iterable<RowCursor> _rowIter;
    private ExprKelp []_results;
    
    CursorIterable(EnvKelp envKelp, 
                   Iterable<RowCursor> rowIter,
                   ExprKelp []results)
    {
      _envKelp = envKelp;
      _rowIter = rowIter;
      _results = results;
    }

    @Override
    public Iterator<Cursor> iterator()
    {
      return new CursorIterator(_envKelp, _rowIter.iterator(), _results);
    }
  }
  
  private class CursorIterator implements Iterator<Cursor>
  {
    private EnvKelp _envKelp;
    private Iterator<RowCursor> _rowIter;
    private ExprKelp []_results;
    
    CursorIterator(EnvKelp envKelp,
                   Iterator<RowCursor> rowIter,
                   ExprKelp []results)
    {
      _envKelp = envKelp;
      _rowIter = rowIter;
      _results = results;
    }

    @Override
    public boolean hasNext()
    {
      return _rowIter.hasNext();
    }

    @Override
    public Cursor next()
    {
      RowCursor rowCursor = _rowIter.next();

      if (rowCursor != null) {
        return new CursorKraken(table(), _envKelp, rowCursor, _results);
      }
      else {
        return null;
      }
    }

    @Override
    public void remove()
    {
      _rowIter.remove();
    }
  }
}
