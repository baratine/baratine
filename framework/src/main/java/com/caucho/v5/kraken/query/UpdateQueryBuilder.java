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

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.UpdateKelp;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.ExprBuilderKelp;
import com.caucho.v5.kelp.query.ExprKelp;
import com.caucho.v5.kelp.query.QueryBuilderKelp;
import com.caucho.v5.kraken.fun.IsshardExpr;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.TableManagerKraken;
import com.caucho.v5.util.L10N;

public class UpdateQueryBuilder extends QueryBuilderKraken
{
  private static final L10N L = new L10N(UpdateQueryBuilder.class);
  
  private final TableManagerKraken _tableManager;
  
  private String _tableName;

  private ExprKraken _whereKraken;
  private UpdateBuilder _updateBuilder;

  private EnvKelp _whereKelp;

  private TableKraken _table;
  
  private ArrayList<SetItem> _setList = new ArrayList<>();

  private QueryBuilderKelp _builderKelp;
  
  public UpdateQueryBuilder(TableManagerKraken tableManager,
                            String sql)
  {
    super(sql);
    
    _tableManager = tableManager;
  }
  
  /**
   * Checks if the table meta-data is available to the current server, i.e.
   * is in the local store.
   */
  @Override
  public boolean isTableLoaded()
  {
    return _tableManager.getTable(_tableName) != null;
  }

  @Override
  public TableKraken getTable()
  {
    return _table;
  }
  
  @Override
  public String getTableName()
  {
    return _tableName;
  }
  
  @Override
  public QueryBuilderKelp getBuilderKelp()
  {
    return _builderKelp;
  }
  
  public void setUpdateBuilder(UpdateBuilder update)
  {
    Objects.requireNonNull(update);
    
    _updateBuilder = update;
  }
  
  @Override
  public void setLocal(int node)
  {
    FunExpr localExpr = new IsshardExpr();
    localExpr.addArg(new LiteralExpr(node));
    
    ExprKraken whereBuilder = _whereKraken;
    
    if (whereBuilder != null) {
      whereBuilder = new AndExpr().add(whereBuilder).add(localExpr);
    }
    else {
      whereBuilder = localExpr;
    }

    _whereKraken = whereBuilder;
  }

  @Override
  public void build(Result<QueryKraken> result)
  {
    TableKraken table = _tableManager.getTable(_tableName);
    
    if (table != null) {
      result.ok(build(table));
    }
    else {
      _tableManager.loadTable(_tableName, result.of(t->build(t)));
    }
  }
    
  public UpdateQuery build(TableKraken table)
  {
    if (table == null) {
      throw new QueryException(L.l("'{0}' is an unknown table\n  {1}",
                                   _tableName, sql()));
    }
    
    _table = table;
    
    if (_setList.size() > 0) {
      _updateBuilder = new SetUpdateBuilder(_setList);
    }
    //query.setUpdateBuilder(new SetUpdateBuilder(setItems));
    
    if (_updateBuilder == null) {
      throw new QueryException(L.l("Update {0} requires an action", _tableName));
      
    }
    
    ExprKraken whereBuilder = _whereKraken;
    
    if (whereBuilder == null) {
      whereBuilder = new TrueExpr();
    }
    
    ExprKraken whereExpr = whereBuilder.bind(this);
    
    ExprKraken keyExpr = whereExpr.bindKey(this);
    
    _builderKelp = new QueryBuilderKelp(_table.getTableKelp());
    
    UpdateKelp update = _updateBuilder.build(this);
    
    ExprBuilderKelp whereKelpBuilder = whereExpr.buildKelp(this);
    
    EnvKelp whereKelp = _builderKelp.build(whereKelpBuilder);
    
    return new UpdateQuery(sql(), _table, keyExpr, whereExpr, update, whereKelp);
  }

  @Override
  public void setTableName(String tableName)
  {
    _tableName = tableName;
  }

  @Override
  public ExprKraken bind(String tableName, String columnName)
  {
    Column column = _table.getTableKelp().getColumn(columnName);
    
    if (column == null) {
      throw new QueryException(L.l("'{0}' is an unknown column of '{1}'",
                                   columnName, _table.getName()));
    }
    
    return new ColumnExpr(column);
  }

  @Override
  public void setWhereExpr(ExprKraken whereExpr)
  {
    _whereKraken = whereExpr;
  }

  public void setKelpExpr(EnvKelp whereKelp)
  {
    _whereKelp = whereKelp;
  }
  
  public EnvKelp getKelpExpr()
  {
    return _whereKelp;
  }
  
  public void addItem(String columnName, ExprKraken expr)
  {
    _setList.add(new SetItem(columnName, expr));
  }

  public void setParams(ParamExpr[] params)
  {
    // TODO Auto-generated method stub
    
  }
  
  public interface UpdateBuilder
  {
    UpdateKelp build(QueryBuilderKraken builderKelp);
  }
  
  private static class SetItem {
    private String _columnName;
    private ExprKraken _expr;
    
    SetItem(String columnName, ExprKraken expr)
    {
      _columnName = columnName;
      _expr = expr;
    }
    
    String getColumnName()
    {
      return _columnName;
    }
    
    ExprKraken getExpr()
    {
      return _expr;
    }
  }
  
  private class SetUpdateBuilder implements UpdateBuilder
  {
    private ArrayList<SetItem> _setItems;
    
    SetUpdateBuilder(ArrayList<SetItem> setItems)
    {
      _setItems = setItems;
    }

    @Override
    public UpdateKelp build(QueryBuilderKraken builder)
    {
      // TODO Auto-generated method stub
      SetUpdateImpl update = new SetUpdateImpl();
      
      for (SetItem setItem : _setItems) {
        String columnName = setItem.getColumnName();
        ExprKraken exprKraken = setItem.getExpr();
        
        Column column = builder.getTable().getColumn(columnName);
        
        ExprBuilderKelp exprBuilder = exprKraken.buildKelp(builder);
        ExprKelp expr = exprBuilder.build(builder.getBuilderKelp());
        
        update.addSet(column, expr);
      }
      
      return update;
    }
    
  }
  
  private class SetUpdateImpl implements UpdateKelp
  {
    private ArrayList<Column> _columns = new ArrayList<>();
    private ArrayList<ExprKelp> _exprs = new ArrayList<>();
    
    public void addSet(Column column, ExprKelp expr)
    {
      _columns.add(column);
      _exprs.add(expr);
    }
    
    @Override
    public boolean onRow(RowCursor cursor, EnvKelp env)
    {
      int size = _columns.size();
      
      for (int i = 0; i < size; i++) {
        Column column = _columns.get(i);
        ExprKelp expr = _exprs.get(i);

        switch (column.getType()) {
        case INT32:
          cursor.setInt(column.getIndex(), expr.evalInt(env));
          break;
          
        case INT64:
          cursor.setLong(column.getIndex(), expr.evalLong(env));
          break;
          
        case STRING:
          cursor.setString(column.getIndex(), expr.evalString(env));
          break;
          
        case OBJECT:
          cursor.setObject(column.getIndex(), expr.evalObject(env));
          break;
          
        default:
          throw new UnsupportedOperationException(String.valueOf(column.getType()));
        }
      }
      
      return true;
    }
  }
}
