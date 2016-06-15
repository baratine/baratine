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
 * @author Alex Rojkov
 */

package com.caucho.v5.ramp.vault;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.caucho.v5.amp.vault.MethodVault;
import com.caucho.v5.amp.vault.VaultDriver;

import io.baratine.db.Cursor;
import io.baratine.service.Result;

abstract class FindQueryVault<ID,T,V> implements MethodVault<V>
{
  private String _where;
  private VaultDriver<ID,T> _driver;

  protected FindQueryVault(VaultDriver<ID,T> driver, String where)
  {
    _driver = driver;
    _where = where;
  }

  public String getWhere()
  {
    return _where;
  }

  public VaultDriver<ID,T> driver()
  {
    return _driver;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + '[' + _where + ']';
  }

  /**
   * Find a single value and return an id
   */
  public static class FindOneId<ID,T> extends FindQueryVault<ID,T,ID>
  {
    private String _sql;
    private Function<Cursor,ID> _cursorToId;
    
    FindOneId(VaultDriverDataImpl<ID,T> driver, String where)
    {
      super(driver, where);
      
      _cursorToId = driver.cursorToId();
      
      StringBuilder sb = new StringBuilder();
      sb.append("SELECT ");
      sb.append(driver.selectId());
      sb.append(" FROM ");
      sb.append(driver.entityInfo().tableName());
      
      if (where != null) {
        sb.append(" ");
        sb.append(where);
      }
      
      _sql = sb.toString();
    }

    @Override
    public void invoke(Result<ID> result, Object[] args)
    {
      driver().findOne(_sql,
                             args,
                             result.of(_cursorToId));
    }
  }

  /**
   * Find a single value and return a proxy
   */
  public static class FindOneProxy<ID,T,V> extends FindQueryVault<ID,T,V>
  {
    private String _sql;
    private Function<Cursor,ID> _cursorToId;
    private Class<V> _api;
        
    FindOneProxy(VaultDriverDataImpl<ID,T> driver, 
                String where,
                Class<V> api)
    {
      super(driver, where);
      
      Objects.requireNonNull(api);
      
      _api = api;
      
      _cursorToId = driver.cursorToId();
      
      StringBuilder sb = new StringBuilder();
      sb.append("SELECT ");
      sb.append(driver.selectId());
      sb.append(" FROM ");
      sb.append(driver.entityInfo().tableName());
      
      if (where != null) {
        sb.append(" ");
        sb.append(where);
      }
      
      _sql = sb.toString();
    }

    @Override
    public void invoke(Result<V> result, Object []args)
    {
      driver().findOne(_sql,
                             args,
                             result.of(this::toProxy));
    }
    
    private V toProxy(Cursor cursor)
    {
      if (cursor == null) {
        return null;
      }
      
      ID id = _cursorToId.apply(cursor);
      
      return driver().lookup(id).as(_api);
    }
  }

  /**
   * Find a single value and returns a bean
   */
  public static class FindOneBean<ID,T,V> extends FindQueryVault<ID,T,V>
  {
    private Function<Cursor,V> _cursorToBean;
    private String _sql;
    
    FindOneBean(VaultDriverDataImpl<ID,T> driver, 
                String where,
                Class<V> api)
    {
      super(driver, where);
      
      Objects.requireNonNull(api);
      
      _cursorToBean = driver.cursorToBean(api);
      
      String select = driver.selectBean(api);
      
      StringBuilder sb = new StringBuilder();
      sb.append("SELECT ");
      sb.append(select);
      sb.append(" FROM ");
      sb.append(driver.entityInfo().tableName());
      
      if (where != null) {
        sb.append(" ");
        sb.append(where);
      }
      
      _sql = sb.toString();
    }

    @Override
    public void invoke(Result<V> result, Object []args)
    {
      driver().findOne(_sql,
                             args,
                             result.of(_cursorToBean));
    }
  }

  /**
   * Find a list of keys, List&lt;ID&gt;. 
   */
  static class FindListIds<ID,T> extends FindQueryVault<ID,T,List<ID>>
  {
    private String _sql;
    private Function<Cursor,ID> _cursorToId;
    
    FindListIds(VaultDriverDataImpl<ID,T> driver, String where)
    {
      super(driver, where);
      
      _cursorToId = driver.cursorToId();
      
      StringBuilder sb = new StringBuilder();
      sb.append("SELECT ");
      sb.append(driver.selectId());
      sb.append(" FROM ");
      sb.append(driver.entityInfo().tableName());
      
      if (where != null) {
        sb.append(" ");
        sb.append(where);
      }
      
      _sql = sb.toString();
    }

    @Override
    public void invoke(Result<List<ID>> result, Object[] args)
    {
      driver().findAll(_sql,
                              args,
                              result.of(this::listResult));
    }
    
    private List<ID> listResult(Iterable<Cursor> iter)
    {
      ArrayList<ID> list = new ArrayList<>();
      
      for (Cursor cursor : iter) {
        list.add(_cursorToId.apply(cursor));
      }
      
      return list;
    }
  }

  /**
   * Result is a list of proxies, List&lt;MyApi&gt;. 
   */
  public static class FindListProxy<ID,T,V> extends FindQueryVault<ID,T,List<V>>
  {
    private Class<V> _api;
    
    private String _sql;
    private Function<Cursor,ID> _cursorToId;
    
    FindListProxy(VaultDriverDataImpl<ID,T> driver, 
                    String where,
                    Class<V> api)
    {
      super(driver, where);
      
      Objects.requireNonNull(api);
      
      _api = api;
      
      _cursorToId = driver.cursorToId();
      
      StringBuilder sb = new StringBuilder();
      sb.append("SELECT ");
      sb.append(driver.selectId());
      sb.append(" FROM ");
      sb.append(driver.entityInfo().tableName());
      
      if (where != null) {
        sb.append(" ");
        sb.append(where);
      }
      
      _sql = sb.toString();
    }

    @Override
    public void invoke(Result<List<V>> result, Object []args)
    {
      driver().findAll(_sql,
                              args,
                              result.of(this::toProxies));
    }
    
    private List<V> toProxies(Iterable<Cursor> results)
    {
      List<V> list = new ArrayList<>();
      
      for (Cursor cursor : results) {
        ID id = _cursorToId.apply(cursor);
        list.add(driver().lookup(id).as(_api));
      }
      
      return list;
    }
  }

  /**
   * Result is a list of value beans, List&lt;V&gt;. 
   */
  public static class FindListBean<ID,T,V> extends FindQueryVault<ID,T,List<V>>
  {
    private Function<Cursor,V> _cursorToValue;
    private String _sql;
    
    FindListBean(VaultDriverDataImpl<ID,T> driver, 
                   String where,
                   Class<V> api)
    {
      super(driver, where);
      
      Objects.requireNonNull(api);
      
      _cursorToValue = driver.cursorToBean(api);
      
      StringBuilder sb = new StringBuilder();
      sb.append("SELECT ");
      sb.append(driver.selectBean(api));
      sb.append(" FROM ");
      sb.append(driver.entityInfo().tableName());
      
      if (where != null) {
        sb.append(" ");
        sb.append(where);
      }
      
      _sql = sb.toString();
    }

    @Override
    public void invoke(Result<List<V>> result, Object []args)
    {
      driver().findAll(_sql,
                              args,
                              result.of(this::listResult));
    }
    
    private List<V> listResult(Iterable<Cursor> iter)
    {
      ArrayList<V> list = new ArrayList<>();
      
      for (Cursor cursor : iter) {
        list.add(_cursorToValue.apply(cursor));
      }
      
      return list;
    }
  }

  /*
  public static class ListResultField<ID,T,V> extends FindQueryVault<ID,T,V>
  {
    private FieldInfo _field;
    private AssetInfo _entityDesc;

    ListResultField(VaultDriver<ID,T> driver,
                    AssetInfo entityDesc,
                    FieldInfo field,
                    String where)
    {
      super(driver, where);
      
      _entityDesc = entityDesc;
      _field = field;
    }

    @Override
    public void invoke(Result<V> result, Object[] args)
    {
      VaultDriver<ID,T> driver = driver();

      StringBuilder sql = _entityDesc.selectField(_field).append(' ');

      if (getWhere() != null)
        sql.append(getWhere());

      driver.findValueList(sql.toString(), args, (Result<List<Object>>) result);
    }
  }
  */
}
