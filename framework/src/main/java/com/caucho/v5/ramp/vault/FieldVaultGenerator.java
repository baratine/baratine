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
import java.util.function.Function;

import com.caucho.v5.convert.bean.FieldBoolean;
import com.caucho.v5.convert.bean.FieldByte;
import com.caucho.v5.convert.bean.FieldChar;
import com.caucho.v5.convert.bean.FieldDouble;
import com.caucho.v5.convert.bean.FieldFloat;
import com.caucho.v5.convert.bean.FieldInt;
import com.caucho.v5.convert.bean.FieldLong;
import com.caucho.v5.convert.bean.FieldObject;
import com.caucho.v5.convert.bean.FieldShort;
import com.caucho.v5.convert.bean.FieldString;

import io.baratine.db.Cursor;
import io.baratine.service.IdAsset;

class FieldVaultGenerator
{
  private static HashMap<Class<?>,Function<Field,FieldVault<?>>> _fieldTypeMap
    = new HashMap<>();
  
  public static FieldVault getField(Field field)
  {
    Function<Field,FieldVault<?>> fun = _fieldTypeMap.get(field.getType());
    
    if (fun != null) {
      return fun.apply(field);
    }
    else {
      return new FieldDataObject(field);
    }
  }

  /**
   * boolean fields
   */
  private static class FieldDataBoolean<T> extends FieldBoolean<T>
    implements FieldVault<T>
  {
    FieldDataBoolean(Field field)
    {
      super(field);
    }
    
    @Override
    public void set(T bean, Cursor cursor, int index)
    {
      setBoolean(bean, cursor.getInt(index) != 0);
    }
    
    @Override
    public void set(T bean, Object value)
    {
      if (value instanceof Boolean) {
        setBoolean(bean, (Boolean) value);
      }
    }
  }
  
  /**
   * char fields
   */
  private static class FieldDataChar<T> extends FieldChar<T>
    implements FieldVault<T>
  {
    FieldDataChar(Field field)
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
      setObject(bean, value);
    }
  }
  
  /**
   * byte fields
   */
  private static class FieldDataByte<T> extends FieldByte<T>
    implements FieldVault<T>
  {
    FieldDataByte(Field field)
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
  
  /**
   * short fields
   */
  private static class FieldDataShort<T> extends FieldShort<T>
    implements FieldVault<T>
  {
    FieldDataShort(Field field)
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
  
  /**
   * int fields
   */
  private static class FieldDataInt<T> extends FieldInt<T>
    implements FieldVault<T>
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
  
  /**
   * long fields
   */
  private static class FieldDataLong<T> extends FieldLong<T>
    implements FieldVault<T>
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
  
  /**
   * float fields
   */
  private static class FieldDataFloat<T> extends FieldFloat<T>
    implements FieldVault<T>
  {
    FieldDataFloat(Field field)
    {
      super(field);
    }
    
    @Override
    public void set(T bean, Cursor cursor, int index)
    {
      setDouble(bean, cursor.getDouble(index));
    }
    
    @Override
    public void set(T bean, Object value)
    {
      if (value instanceof Number) {
        setDouble(bean, ((Number) value).floatValue());
      }
    }
  }
  
  /**
   * double fields
   */
  private static class FieldDataDouble<T> extends FieldDouble<T>
    implements FieldVault<T>
  {
    FieldDataDouble(Field field)
    {
      super(field);
    }
    
    @Override
    public void set(T bean, Cursor cursor, int index)
    {
      setDouble(bean, cursor.getDouble(index));
    }
    
    @Override
    public void set(T bean, Object value)
    {
      if (value instanceof Number) {
        setDouble(bean, ((Number) value).floatValue());
      }
    }
  }
  
  private static class FieldDataString<T> extends FieldString<T>
    implements FieldVault<T>
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
  
  /**
   * IdAsset fields
   */
  private static class FieldDataAsset<T> extends FieldObject<T,IdAsset>
    implements FieldVault<T>
  {
    FieldDataAsset(Field field)
    {
      super(field);
    }
    
    @Override
    public void set(T bean, Cursor cursor, int index)
    {
      setObject(bean, new IdAsset(cursor.getLong(index)));
    }
    
    @Override
    public void set(T bean, Object value)
    {
      if (value instanceof Number) {
        setObject(bean, new IdAsset(((Number) value).longValue()));
      }
      else if (value instanceof String) {
        setObject(bean, new IdAsset(((String) value)));
      }
      else {
        System.out.println("Unknown value: " + value + " " + this);
      }
    }
  }
  
  private static class FieldDataObject<T> extends FieldObject<T,Object>
    implements FieldVault<T>
  {
    FieldDataObject(Field field)
    {
      super(field);
    }
  
    @Override
    public void set(T bean, Cursor cursor, int index)
    {
      setObject(bean, cursor.getObject(index));
    }
  
    @Override
    public void set(T bean, Object value)
    {
      setObject(bean, value);
    }
  }
  
  static {
    _fieldTypeMap.put(boolean.class, FieldDataBoolean::new);
    _fieldTypeMap.put(char.class, FieldDataChar::new);
    _fieldTypeMap.put(byte.class, FieldDataByte::new);
    _fieldTypeMap.put(short.class, FieldDataShort::new);
    _fieldTypeMap.put(int.class, FieldDataInt::new);
    _fieldTypeMap.put(long.class, FieldDataLong::new);
    _fieldTypeMap.put(float.class, FieldDataFloat::new);
    _fieldTypeMap.put(double.class, FieldDataDouble::new);
    _fieldTypeMap.put(String.class, FieldDataString::new);
    _fieldTypeMap.put(IdAsset.class, FieldDataAsset::new);
  }
}
