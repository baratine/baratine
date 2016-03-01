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

package com.caucho.v5.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.caucho.v5.convert.bean.FieldInt;
import com.caucho.v5.convert.bean.FieldLong;
import com.caucho.v5.convert.bean.FieldString;

import io.baratine.db.Cursor;

class FindDataVault<ID,T,V>
{
  private static HashMap<Class<?>,Function<Field,FieldData<?>>> _fieldTypeMap
    = new HashMap<>();
  
  private VaultDriverDataImpl<ID,T> _driver;
  private Class<V> _dataClass;

  private DataFieldItem<V>[] _fields;

  private int _docIndex;

  FindDataVault(VaultDriverDataImpl<ID,T> driver, 
                Class<V> dataClass)
  {
    _driver = driver;
    _dataClass = dataClass;
    
    introspect();
  }
  
  private void introspect()
  {
    if (Modifier.isAbstract(_dataClass.getModifiers())) {
      throw new IllegalArgumentException(_dataClass.getName());
    }
    
    ArrayList<DataFieldItem<V>> fieldList = new ArrayList<>();
    
    int index = 0;
    
    index = introspect(fieldList, _dataClass, index);

    _fields = new DataFieldItem[fieldList.size()];
    fieldList.toArray(_fields);

    boolean isDoc = false;
    for (DataFieldItem item : _fields) {
      if (item.isDocument()) {
        isDoc = true;
      }
    }
    
    if (isDoc) {
      _docIndex = index + 1;
    }
  }
  
  String select()
  {
    StringBuilder sb = new StringBuilder();
    
    boolean isDoc = false;
    
    for (DataFieldItem item : _fields) {
      item.select(sb);
    }
    
    if (sb.length() == 0) {
      sb.append("__doc");
    }
    else if (_docIndex > 0) {
      sb.append(", __doc");
    }

    return sb.toString();
  }
  
  V get(Cursor cursor)
  {
    if (cursor == null) {
      return null;
    }
    
    V bean = newInstance();
    
    Map<String,Object> doc;
    
    if (_docIndex > 0) {
      doc = (Map) cursor.getObject(_docIndex);
    }
    else {
      doc = null;
    }
    
    for (DataFieldItem<V> field : _fields) {
      field.get(bean, cursor, doc);
    }
    
    return bean;
  }
  
  private V newInstance()
  {
    try {
      return _dataClass.newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(_dataClass.getSimpleName() + ": " + e, e);
    }
  }
  
  private int introspect(ArrayList<DataFieldItem<V>> fieldList, 
                         Class<?> type,
                         int index)
  {
    if (type == null) {
      return index;
    }
    
    index = introspect(fieldList, type.getSuperclass(), index);
    
    for (Field field : type.getDeclaredFields()) {
      FieldInfo fieldInfo = _driver.entityInfo().field(field.getName());
      
      if (fieldInfo == null) {
        continue;
      }
      
      FieldData<V> fieldData = fieldData(field);
      
      if (fieldInfo.isColumn()) {
        fieldList.add(new DataFieldColumn<>(fieldInfo, fieldData, ++index));
      }
      else {
        fieldList.add(new DataFieldDoc<>(fieldInfo, fieldData));
      }
    }
    
    return index;
  }
  
  private FieldData<V> fieldData(Field field)
  {
    Class<?> type = field.getType();
    
    Function<Field,FieldData<?>> fun = _fieldTypeMap.get(type);
    
    if (fun != null) {
      return (FieldData<V>) fun.apply(field);
    }
    else {
      throw new UnsupportedOperationException(type.getName() + " is an unsupported value for field " + field);
    }
  }
  
  abstract private static class DataFieldItem<T>
  {
    public void select(StringBuilder sb)
    {
    }
    
    public boolean isDocument()
    {
      return false;
    }

    abstract public void get(T bean, Cursor cursor, Map<String, Object> doc);
  }
  
  private static class DataFieldColumn<T> extends DataFieldItem<T>
  {
    private FieldInfo _fieldInfo;
    private FieldData<T> _fieldData;
    private int _index;
    
    DataFieldColumn(FieldInfo fieldInfo, 
                    FieldData<T> fieldData,
                    int index)
    {
      _fieldInfo = fieldInfo;
      _fieldData = fieldData;
      
      _index = index;
      
      if (index <= 0) {
        throw new IllegalArgumentException();
      }
    }
    
    public void get(T bean, Cursor cursor, Map<String, Object> doc)
    {
      _fieldData.set(bean, cursor, _index);
    }
    
    @Override
    public void select(StringBuilder sb)
    {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      
      sb.append(_fieldInfo.sqlTerm());
    }
    
    @Override
    public boolean isDocument()
    {
      return ! _fieldInfo.isColumn();
    }

    private boolean isColumn()
    {
      return _fieldInfo.isColumn();
    }
    
    private int index()
    {
      return _index;
    }
  }
  
  private static class DataFieldDoc<T> extends DataFieldItem<T>
  {
    private FieldInfo _fieldInfo;
    private FieldData<T> _fieldData;
    
    DataFieldDoc(FieldInfo fieldInfo, FieldData<T> fieldData)
    {
      _fieldInfo = fieldInfo;
      _fieldData = fieldData;
    }
    
    @Override
    public boolean isDocument()
    {
      return true;
    }

    @Override
    public void get(T bean, Cursor cursor, Map<String, Object> doc)
    {
      Object value = doc.get(_fieldInfo.columnName());
      
      _fieldData.set(bean, value);
    }
  }
  
  private interface FieldData<T>
  {
    void set(T bean, Cursor cursor, int index);
    
    void set(T bean, Object value);
  }
  
  private static class FieldDataInt<T> extends FieldInt<T>
    implements FieldData<T>
  {
    FieldDataInt(Field field)
    {
      super(field);
    }
    
    @Override
    public void set(T bean, Cursor cursor, int index)
    {
      setInt(bean, cursor.getInt(index));
    }
    
    @Override
    public void set(T bean, Object value)
    {
      if (value instanceof Number) {
        setInt(bean, ((Number) value).intValue());
      }
    }
  }
  
  private static class FieldDataLong<T> extends FieldLong<T>
    implements FieldData<T>
  {
    FieldDataLong(Field field)
    {
      super(field);
    }
    
    @Override
    public void set(T bean, Cursor cursor, int index)
    {
      setLong(bean, cursor.getLong(index));
    }
    
    @Override
    public void set(T bean, Object value)
    {
      if (value instanceof Number) {
        setLong(bean, ((Number) value).longValue());
      }
    }
  }
  
  private static class FieldDataString<T> extends FieldString<T>
    implements FieldData<T>
  {
    FieldDataString(Field field)
    {
      super(field);
    }
    
    @Override
    public void set(T bean, Cursor cursor, int index)
    {
      setString(bean, cursor.getString(index));
    }
    
    @Override
    public void set(T bean, Object value)
    {
      setString(bean, String.valueOf(value));
    }
  }
  
  static {
    _fieldTypeMap.put(int.class, FieldDataInt::new);
    _fieldTypeMap.put(long.class, FieldDataLong::new);
    _fieldTypeMap.put(String.class, FieldDataString::new);
  }
}
