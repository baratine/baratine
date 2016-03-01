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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.caucho.v5.kraken.info.ColumnInfo;
import com.caucho.v5.kraken.info.TableInfo;
import com.caucho.v5.util.L10N;

import io.baratine.db.Cursor;
import io.baratine.service.Id;
import io.baratine.service.ServiceException;

class FieldReflected<T,V> implements FieldInfo<T,V>
{
  private static final L10N L = new L10N(FieldReflected.class);
  
  private final Field _field;
  private final String _columnName; 
  private ColumnInfo _column;

  private boolean _isColumn;
  private boolean _isId;

  private String _sqlType;

  private ValueSetter _setter;

  public FieldReflected(Field field, ColumnVault column)
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
    else if (columnName.equals("id")) {
      _isId = true;
    }
    else if (columnName.equals("_id")) {
      _isId = true;
    }
    else {
      _isId = false;
    }
    
    _sqlType = RepositoryImpl.getColumnType(_field.getType());

    _field.setAccessible(true);
    
    _setter = reflectiveSetters.get(_field.getType());
    
    if (_setter == null) {
      _setter = new ObjectValueSetter();
    }
    Objects.requireNonNull(_setter);
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
    _setter.setValue(bean, _field, cursor, index);
  }

  @Override
  public void setValue(Object bean, Object value)
  {
    try {
      _field.set(bean, value);
    } catch (Exception e) {
      throw new ServiceException(L.l("{0}.{1} {2}", 
                                     _field.getDeclaringClass().getSimpleName(),
                                     _field.getName(),
                                     e.toString()),
                                 e);
    }
  }

  @Override
  public void setValueFromDocument(T bean, Map<String,Object> docMap)
  {
    if (isColumn()) {
      return;
    }
    
    try {
      Object value = docMap.get(columnName());
      
      _field.set(bean, value);
    } catch (Exception e) {
      throw new ServiceException(L.l("{0}.{1} {2}", 
                                     _field.getDeclaringClass().getSimpleName(),
                                     _field.getName(),
                                     e.toString()),
                                 e);
    }
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
    /*
    else {
      column = tableInfo.column("__doc");
    }
    */
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + columnName() + "]";
  }

  /**
   * Sets the field from the cursor.
   */
  private static abstract class ValueSetter
  {
    abstract void setValue(Object target, Field field, Cursor cursor, int index)
      throws IllegalAccessException;
  }
  
  /**
   * String field.
   */
  private static class StringValueSetter extends ValueSetter
  {
    static final StringValueSetter INSTANCE = new StringValueSetter();

    @Override
    void setValue(Object target, Field field, Cursor cursor, int index)
      throws IllegalAccessException
    {
      field.set(target, cursor.getString(index));
    }
  }

  private static class CharValueSetter extends ValueSetter
  {
    static final CharValueSetter INSTANCE = new CharValueSetter();

    @Override
    void setValue(Object target, Field field, Cursor cursor, int index)
      throws IllegalAccessException
    {
      field.setChar(target, cursor.getString(index).charAt(0));
    }
  }

  
  /**
   * Int field.
   */
  private static class IntValueSetter extends ValueSetter
  {
    static final IntValueSetter INSTANCE = new IntValueSetter();

    @Override
    void setValue(Object target, Field field, Cursor cursor, int index)
      throws IllegalAccessException
    {
      field.setInt(target, cursor.getInt(index));
    }
  }

  /**
   * Long field.
   */
  private static class LongValueSetter extends ValueSetter
  {
    static final LongValueSetter INSTANCE = new LongValueSetter();

    @Override
    void setValue(Object target, Field field, Cursor cursor, int index)
      throws IllegalAccessException
    {
      field.setLong(target, cursor.getLong(index));
    }
  }

  /**
   * Float field.
   */
  private static class FloatValueSetter extends ValueSetter
  {
    static final FloatValueSetter INSTANCE = new FloatValueSetter();

    @Override
    void setValue(Object target, Field field, Cursor cursor, int index)
      throws IllegalAccessException
    {
      field.setFloat(target, cursor.getLong(index));
    }
  }

  private static class DoubleValueSetter extends ValueSetter
  {
    static final DoubleValueSetter INSTANCE = new DoubleValueSetter();

    @Override
    void setValue(Object target, Field field, Cursor cursor, int index)
      throws IllegalAccessException
    {
      field.setDouble(target, cursor.getLong(index));
    }
  }

  private static class ObjectValueSetter extends ValueSetter
  {
    @Override
    void setValue(Object target, Field field, Cursor cursor, int index)
      throws IllegalAccessException
    {
      field.set(target, cursor.getObject(index));
    }
  }

  private final static Map<Class,ValueSetter> reflectiveSetters
    = new HashMap<>();

  static {
    reflectiveSetters.put(byte.class, IntValueSetter.INSTANCE);
    reflectiveSetters.put(Byte.class, IntValueSetter.INSTANCE);

    reflectiveSetters.put(short.class, IntValueSetter.INSTANCE);
    reflectiveSetters.put(Short.class, IntValueSetter.INSTANCE);

    reflectiveSetters.put(char.class, CharValueSetter.INSTANCE);
    reflectiveSetters.put(Character.class, CharValueSetter.INSTANCE);

    reflectiveSetters.put(int.class, IntValueSetter.INSTANCE);
    reflectiveSetters.put(Integer.class, IntValueSetter.INSTANCE);

    reflectiveSetters.put(long.class, LongValueSetter.INSTANCE);
    reflectiveSetters.put(Long.class, LongValueSetter.INSTANCE);

    reflectiveSetters.put(float.class, FloatValueSetter.INSTANCE);
    reflectiveSetters.put(Float.class, FloatValueSetter.INSTANCE);

    reflectiveSetters.put(double.class, DoubleValueSetter.INSTANCE);
    reflectiveSetters.put(Double.class, DoubleValueSetter.INSTANCE);

    reflectiveSetters.put(String.class, StringValueSetter.INSTANCE);
  }
}
