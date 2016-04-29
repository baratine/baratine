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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.caucho.v5.kraken.info.ColumnInfo;
import com.caucho.v5.kraken.info.TableInfo;
import com.caucho.v5.util.L10N;

import io.baratine.db.Cursor;
import io.baratine.service.ServiceException;
import io.baratine.vault.Id;

class FieldAsset<T,V> implements FieldInfo<T,V>
{
  private static final L10N L = new L10N(FieldAsset.class);
  
  private final Field _field;
  private final String _columnName; 
  private ColumnInfo _column;

  private boolean _isColumn;
  private boolean _isId;

  private String _sqlType;

  //private ValueSetter _setter;
  
  private FieldVault<T> _fieldData; 

  public FieldAsset(Field field, ColumnVault column)
  {
    _field = field;
    
    String columnName = "";
    
    if (column != null) {
      columnName = column.name();
    }
    
    if (columnName.isEmpty()) {
      columnName = field.getName();
    }
    
    _columnName = columnName;
    
    if (field.isAnnotationPresent(Id.class)) {
      _isId = true;
    }
    /*
    else if (columnName.equals("id")) {
      _isId = true;
    }
    else if (columnName.equals("_id")) {
      _isId = true;
    }
    */
    else {
      _isId = false;
    }
    
    _sqlType = RepositoryImpl.getColumnType(_field.getType());

    _fieldData = FieldVaultGenerator.getField(field);
  }

  @Override
  public boolean isId()
  {
    return _isId;
  }

  @Override
  public String columnName()
  {
    return _columnName;
  }
  
  public boolean isColumn()
  {
    return _isColumn;
  }

  @Override
  public String sqlTerm()
  {
    if (_isColumn) {
      return _columnName;
    }
    else {
      return "__doc." + _columnName;
    }
  }

  @Override
  public Class<?> getJavaType()
  {
    return _field.getType();
  }

  @Override
  public String sqlType()
  {
    return _sqlType;
  }

  @Override
  public Object getValue(Object bean)
  {
    try {
      return _field.get(bean);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException();
    }
  }

  @Override
  public void setValue(Object bean, Cursor cursor, int index)
    throws IllegalAccessException
  {
    _fieldData.set((T) bean, cursor, index);
  }

  @Override
  public void setValue(Object bean, Object value)
  {
    _fieldData.set((T) bean, value);
  }

  @Override
  public void setValueFromDocument(T bean, Map<String,Object> docMap)
  {
    if (isColumn()) {
      return;
    }
    
    Object value = docMap.get(columnName());
    
    setValue(bean, value);
  }

  @Override
  public void fillColumn(TableInfo tableInfo)
  {
    ColumnInfo column = tableInfo.column(_field.getName());
    
    if (column == null && _field.getName().startsWith("_")) {
      column = tableInfo.column(_field.getName().substring(1));
    }
    
    if (column != null) {
      _isColumn = true;
      _column = column;
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + columnName() + "]";
  }
}
