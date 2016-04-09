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

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.query.ExprBuilderKelp;
import com.caucho.v5.kelp.query.ExprKelp;
import com.caucho.v5.kelp.query.QueryBuilderKelp;
import com.caucho.v5.kraken.table.PodHashGenerator;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.KrakenImpl;

public class ReplaceQueryBuilder extends QueryBuilderKraken
{
  private TableKraken _table;
  private ArrayList<Column> _columns;
  private ArrayList<ExprKraken> _values;
  
  private ArrayList<Column> _updateColumns;
  private ArrayList<ExprKraken> _updateValues;
  
  private KrakenImpl _tableManager;
  private QueryBuilderKelp _builderKelp;
  
  public ReplaceQueryBuilder(KrakenImpl tableManager,
                            String sql,
                            TableKraken table, 
                            ArrayList<Column> columns)
  {
    super(sql);
    
    _tableManager = tableManager;
    _table = table;
    _columns = new ArrayList<>(columns);
  }

  public void setParams(ParamExpr[] params)
  {
  }

  public void setValues(ArrayList<ExprKraken> values)
  {
    _values = values;
  }
  
  @Override
  public TableKraken getTable()
  {
    return _table;
  }
  
  @Override
  public QueryBuilderKelp getBuilderKelp()
  {
    return _builderKelp;
  }
  
  @Override
  public void build(Result<QueryKraken> result)
  {
    result.ok(build());
  }
  
  @Override
  public ReplaceQuery build()
  {
    // boolean isKeyHash = false;
    
    for (int i = 0; i < _columns.size(); i++) {
      Column column = _columns.get(i);

      String name = column.name();
      
      /*
      if (name.equals(":pod_hash")) {
        isKeyHash = true;
        continue;
      }
      else
      */ 
      if (name.startsWith(":")) {
        continue;
      }
      
      String key = ":key_" + name;
      
      Column colKey = _table.getColumn(key);

      if (colKey != null) {
        _columns.add(colKey);
        _values.add(new KeyInsertExpr(_table, _values.get(i)));
      }
    }
    
    Column hashColumn = _table.getColumn(HashExprGenerator.HASH_COLUMN);
    
    if (hashColumn != null) {
      PodHashGenerator hashGen = _table.getHashGenerator();
      
      ExprKraken hashExpr = hashGen.buildInsertExpr(_columns, _values);
      
      _columns.add(hashColumn);
      _values.add(hashExpr);
    }
    
    // XXX: verify all keys assigned
    
    /*
    if (! isKeyHash) {
      Column hashColumn = _table.getTableKelp().getColumn(":pod_hash");
      _columns.add(hashColumn);
      _values.add(new KeyHashInsertExpr(_table));
    }
    */
    
    _builderKelp = new QueryBuilderKelp(_table.getTableKelp());
    
    ArrayList<ExprKelp> values = new ArrayList<>();
    ArrayList<Column> updateColumns = new ArrayList<>();
    ArrayList<ExprKelp> updateValues = new ArrayList<>();
    
    for (int i = 0; i < _columns.size(); i++) {
      Column col = _columns.get(i);
      ExprKraken expr = _values.get(i);
      
      ExprBuilderKelp exprKelpBuilder = expr.buildKelp(this);
      
      values.add(exprKelpBuilder.build(_builderKelp));
      
      if (! _table.isKeyColumn(col)) {
        updateColumns.add(col);
        
        // ExprBuilderKelp exprKelpBuilder = expr.buildKelp(this);
        
        updateValues.add(exprKelpBuilder.build(_builderKelp));
      }
    }

    return new ReplaceQuery(sql(), _table, _columns, values,
                            updateColumns, updateValues);
  }
}
