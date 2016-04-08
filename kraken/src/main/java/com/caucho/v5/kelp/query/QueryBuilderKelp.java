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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.caucho.v5.h3.QueryBuilderH3;
import com.caucho.v5.h3.QueryH3;
import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.Column.ColumnType;
import com.caucho.v5.kelp.Row;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.util.L10N;

/**
 * Building a program for hessian.
 */
public class QueryBuilderKelp extends QueryBuilder
{
  private static final L10N L = new L10N(QueryBuilderKelp.class);
  
  private final Row _row;
  
  private ExprKelp _expr;
  
  private HashMap<String,PathKelp> _pathMap = new HashMap<>();
  
  private HashMap<Column,QueryBuilderH3> _pathH3Map = new HashMap<>();
  private AtomicInteger _pathCount = new AtomicInteger();

  private TableKelp _table;
  
  public QueryBuilderKelp(TableKelp table)
  {
    _table = table;
    _row = table.getRow();
  }

  public ExprBuilderKelp literal(Object value)
  {
    return new LiteralBuilder(value);
  }

  public ExprBuilderKelp param(int index)
  {
    return new ParamBuilder(index);
  }
  
  @Override
  public ExprBuilderKelp field(String name)
  {
    Column column = _row.findColumn(name);
    
    if (column == null) {
      throw new RuntimeException(L.l("'{0}' is an unknown column of {1}",
                                     name, _table));
    }
    
    if (column.getType() == ColumnType.OBJECT) {
      return new ObjectColumnExprBuilder(column);
    }
    else {
      return new ColumnExprBuilder(column);
    }
    /*
    else {
      
      throw new RuntimeException(L.l("'{0}' is an invalid type", column));
    }
    */
  }
  
  /*
  public PathBuilder field(String name)
  {
    PathTopBuilder top = new PathTopBuilder(this);
    
    return new PathFieldBuilder(this, top, name);
  }
  */
  
  /*
  @Override
  protected PathMapHessian getPathMap()
  {
    return _pathMap;
  }
  */
  
  @Override
  protected PathKelp addPath(Column column)
  {
    PathKelp path = _pathMap.get(column.name());
    
    if (path == null) {
      switch (column.getType()) {
      case BLOB:
        path = new PathObjectKelp(column, _table.serializer());
        break;
        
      case OBJECT:
        path = new PathObjectKelp(column, _table.serializer());
        break;
        
      default:
        path = new PathKelp(column);
        break;
      }
      
      _pathMap.put(column.name(), path);
    }
    
    return path;
  }
  
  @Override
  protected QueryBuilderH3 pathH3(Column column)
  {
    QueryBuilderH3 path = _pathH3Map.get(column);
    
    if (path == null) {
      switch (column.getType()) {
      case BLOB:
        path = _table.serializer().newQuery().count(_pathCount);
        break;
        
      case OBJECT:
        path = _table.serializer().newQuery().count(_pathCount);
        break;
        
      default:
        throw new UnsupportedOperationException(String.valueOf(column));
      }
      
      _pathH3Map.put(column, path);
    }
    
    return path;
  }
  
  protected void setWhere(ExprKelp expr)
  {
    _expr = expr;
  }
  
  public EnvKelp build()
  {
    if (_expr == null) {
      throw new IllegalStateException(L.l("where() expression is required"));
    }
    
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public EnvKelp build(ExprBuilderKelp exprBuilder)
  {
    ExprKelp expr = exprBuilder.build(this);
    
    /*
    PathKelp []paths = new PathKelp[_pathMap.size()];
    _pathMap.values().toArray(paths);
    */
    
    PathKelp []paths = new PathKelp[_pathH3Map.size()];
    
    int i = 0;
    for (Map.Entry<Column,QueryBuilderH3> entry : _pathH3Map.entrySet()) {
      QueryBuilderH3 builderH3 = entry.getValue();
      QueryH3 query = builderH3.build();
      
      paths[i++] = new PathH3Kelp(entry.getKey(), _table.serializer(), query);
    }
    
    //return new EnvKelp(paths, expr, getValueLength());
    return new EnvKelp(_table, paths, expr, _pathCount.get());
    //return new EnvKelp(queries, expr, _pathCount.get());
  }
}
