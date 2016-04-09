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

import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.kelp.Column;
import com.caucho.v5.kraken.table.PodHashGenerator;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.TableManagerKraken;

import io.baratine.service.Result;

public class InsertQueryBuilder extends QueryBuilderKraken
{
  private TableKraken _table;
  private ArrayList<Column> _columns;
  private ArrayList<ExprKraken> _values;
  private TableManagerKraken _tableManager;
  private QueryParserKraken _parser;
  
  public InsertQueryBuilder(QueryParserKraken parser,
                            String sql,
                            TableKraken table, 
                            ArrayList<Column> columns)
  {
    super(sql);
    
    _parser = parser;
    _tableManager = parser.tableManager();
    _table = table;
    _columns = new ArrayList<>(columns);
  }

  /*
  public SerializerFactory serializerFactory()
  {
    return _parser.serializerFactory();
  }
  */
  
  /*
  public OutFactoryH3 serializerFactory()
  {
    return _parser.serializerFactory();
  }
  */

  public TableKraken table()
  {
    return _table;
  }

  public void setParams(ParamExpr[] params)
  {
  }

  public void setValues(ArrayList<ExprKraken> values)
  {
    _values = values;
  }
  
  @Override
  public void build(Result<QueryKraken> result)
  {
    result.ok(build());
  }
  
  @Override
  public InsertQuery build()
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

    return new InsertQuery(sql(), this, _table, _columns, _values);
  }
}
