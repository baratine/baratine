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
    return FindQueryVault.class.getSimpleName() + '[' + _where + ']';
  }

  public static class KeyResult<ID,T,V> extends FindQueryVault<ID,T,V>
  {
    KeyResult(VaultDriver<ID,T> driver, String where)
    {
      super(driver, where);
    }

    @Override
    public void invoke(Result<V> result, Object[] args)
    {
      driver().findOne(getWhere(),
                       args,
                       result.of(x -> (V) x));
    }
  }

  public static class ProxyResult<ID,T,V> extends FindQueryVault<ID,T,V>
  {
    private Class<V> _api;
    
    ProxyResult(VaultDriver<ID,T> driver, 
                String where,
                Class<V> api)
    {
      super(driver, where);
      
      Objects.requireNonNull(api);
      _api = api;
    }

    @Override
    public void invoke(Result<V> result, Object []args)
    {
      driver().findOne(getWhere(),
                     args,
                     result.of(key -> driver().lookup(key).as(_api)));
    }
  }

  public static class DataResult<ID,T,V> extends FindQueryVault<ID,T,V>
  {
    private Class<V> _api;
    private FindDataVault<ID,T,V> _dataBean;
    private String _sql;
    
    DataResult(VaultDriverDataImpl<ID,T> driver, 
                String where,
                Class<V> api)
    {
      super(driver, where);
      
      Objects.requireNonNull(api);
      _api = api;
      
      _dataBean = new FindDataVault<>(driver, api);
      
      String select = _dataBean.select();
      
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
      /*
      driver().findOne(_sql,
                       args,
                       result.of(key -> driver().lookup(key).as(_api)));
                       */
      driver().findOneCursor(_sql,
                             args,
                             result.of(c -> _dataBean.get(c)));
    }
  }

  public static class ListKeyResult<ID,T,V> extends FindQueryVault<ID,T,V>
  {
    ListKeyResult(VaultDriver<ID,T> driver, String where)
    {
      super(driver, where);
    }

    @Override
    public void invoke(Result<V> result, Object[] args)
    {
      driver().findAllIds(getWhere(),
                       args,
                       result.of(x -> (V) x));
    }
  }

  public static class ListDataResult<ID,T,V> extends FindQueryVault<ID,T,List<V>>
  {
    private Class<V> _api;
    private FindDataVault<ID,T,V> _dataBean;
    private String _sql;
    
    ListDataResult(VaultDriverDataImpl<ID,T> driver, 
                   String where,
                   Class<V> api)
    {
      super(driver, where);
      
      Objects.requireNonNull(api);
      _api = api;
      
      _dataBean = new FindDataVault<>(driver, api);
      
      String select = _dataBean.select();
      
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
    public void invoke(Result<List<V>> result, Object []args)
    {
      /*
      driver().findOne(_sql,
                       args,
                       result.of(key -> driver().lookup(key).as(_api)));
                       */
      driver().findCursor(_sql,
                          args,
                          result.of(iter -> listResult(iter)));
    }
    
    public List<V> listResult(Iterable<Cursor> iter)
    {
      ArrayList<V> list = new ArrayList<>();
      
      for (Cursor cursor : iter) {
        list.add(_dataBean.get(cursor));
      }
      
      return list;
    }
  }

  public static class ListProxyResult<ID,T,V> extends FindQueryVault<ID,T,List<V>>
  {
    private Class<V> _api;
    
    ListProxyResult(VaultDriver<ID,T> driver, 
                    String where,
                    Class<V> api)
    {
      super(driver, where);
      
      _api = api;
    }

    @Override
    public void invoke(Result<List<V>> result, Object []args)
    {
      driver().findAllIds(getWhere(),
                          args,
                          result.of(ids -> toProxies(ids)));
    }
    
    private List<V> toProxies(Iterable<ID> ids)
    {
      List<V> list = new ArrayList<>();
      
      for (ID id : ids) {
        list.add(driver().lookup(id).as(_api));
      }
      
      return list;
    }
  }

  public static class ListResultField<ID,T,V> extends FindQueryVault<ID,T,V>
  {
    private FieldInfo _field;
    private EntityInfo _entityDesc;

    ListResultField(VaultDriver<ID,T> driver,
                    EntityInfo entityDesc,
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
}
