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

import java.lang.reflect.Type;
import java.util.Objects;

import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.kelp.MapKelp;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.ExprKelp;
import com.caucho.v5.kraken.table.TableKraken;

import io.baratine.service.MethodRef;
import io.baratine.spi.Headers;


public class MapQuery extends QueryKraken
{
  private final TableKraken _table;
  private ExprKraken _keyExpr;
  private ExprKraken _whereKraken;
  private EnvKelp _envKelp;
  private ExprKelp[] _results;
  
  MapQuery(String sql,
           MapQueryBuilder builder,
           TableKraken tableKraken,
           ExprKraken keyExpr,
           ExprKraken whereKraken,
           EnvKelp envKelp,
           ExprKelp [] results)
  {
    super(sql);
    
    Objects.requireNonNull(whereKraken);
    Objects.requireNonNull(envKelp);
    
    _table = tableKraken;
    _keyExpr = keyExpr;
    _whereKraken = whereKraken;
    _envKelp = envKelp;
    _results = results;
  }
  
  @Override
  public TableKraken table()
  {
    return _table;
  }

  ExprKraken getWhere()
  {
    return _whereKraken;
  }
  
  @Override
  public void fillKey(RowCursor cursor, Object []args)
  {
    _whereKraken.fillMinCursor(cursor, args);
  }
  
  @Override
  public int calculateHash(RowCursor cursor)
  {
    return table().getPodHash(cursor);
  }

  @Override
  public void map(MethodRef methodRef, Object []args)
  {
    MethodRefAmp methodRefAmp = (MethodRefAmp) methodRef;
    
    TableKelp tableKelp = _table.getTableKelp();
    
    RowCursor minCursor = tableKelp.cursor();
    RowCursor maxCursor = tableKelp.cursor();
    
    minCursor.clear();
    maxCursor.setKeyMax();

    _whereKraken.fillMinCursor(minCursor, args);
    _whereKraken.fillMaxCursor(maxCursor, args);
    
    //QueryKelp whereKelp = _whereExpr.bind(args);
    // XXX: binding should be with unique
    EnvKelp envKelp = new EnvKelp(_envKelp, args);

    MarshalMap []marshal = createMarshal(methodRefAmp);
    
    tableKelp.map(minCursor, maxCursor, envKelp,
                  new MapResult(envKelp, methodRef, marshal));

    // result.completed(null);
  }
  
  private MarshalMap []createMarshal(MethodRefAmp method)
  {
    ParameterAmp []param = method.parameters();
    
    MarshalMap []map = new MarshalMap[param.length];
    
    for (int i = 0; i < map.length; i++) {
      ExprKelp result = null;
      
      if (i < _results.length) {
        result = _results[i];
      }
      
      map[i] = createMarshalArg(param[i].type(), result); 
    }
    
    return map;
  }
  
  private MarshalMap createMarshalArg(Type type, ExprKelp exprResult)
  {
    if (type == null || exprResult == null) {
      return new MarshalNull();
    }
    else if (int.class.equals(type) || Integer.class.equals(type)) {
      return new MarshalInt32(exprResult);
    }
    else if (long.class.equals(type) || Long.class.equals(type)) {
      return new MarshalInt64(exprResult);
    }
    else if (String.class.equals(type)) {
      return new MarshalString(exprResult);
    }
    else {
      return new MarshalNull();
    }
  }
  
  private interface MarshalMap {
    Object marshal(EnvKelp env);
  }
  
  private class MarshalNull implements MarshalMap {
    @Override
    public Object marshal(EnvKelp env)
    {
      return null;
    }
  }
  
  private class MarshalInt32 implements MarshalMap {
    private ExprKelp _expr;
    
    MarshalInt32(ExprKelp expr)
    {
      _expr = expr;
    }
    
    @Override
    public Object marshal(EnvKelp env)
    {
      return _expr.evalInt(env);
    }
  }
  
  private class MarshalInt64 implements MarshalMap {
    private ExprKelp _expr;
    
    MarshalInt64(ExprKelp expr)
    {
      _expr = expr;
    }
    
    @Override
    public Object marshal(EnvKelp env)
    {
      return _expr.evalLong(env);
    }
  }
  
  private class MarshalString implements MarshalMap {
    private ExprKelp _expr;
    
    MarshalString(ExprKelp expr)
    {
      _expr = expr;
    }
    
    @Override
    public Object marshal(EnvKelp env)
    {
      return _expr.evalString(env);
    }
  }
  
  private class MapResult implements MapKelp
  {
    private MethodRef _methodRef;
    private MarshalMap []_marshal;
    private EnvKelp _envKelp;
    
    MapResult(EnvKelp envKelp,
              MethodRef methodRef, 
              MarshalMap []marshal)
    {
      _methodRef = methodRef;
      _marshal = marshal;
      _envKelp = envKelp;
    }
    
    @Override
    public void onRow(RowCursor rowCursor)
    {
      MarshalMap[] marshal = _marshal;
      Object []args = new Object[marshal.length];
      
      Headers headers = HeadersNull.NULL;
      
      for (int i = 0; i < args.length; i++) {
        args[i] = marshal[i].marshal(_envKelp);
      }
      
      _methodRef.send(headers, args);
    }
  }
}
