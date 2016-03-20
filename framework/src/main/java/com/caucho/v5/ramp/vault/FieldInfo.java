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

import java.util.Map;

import com.caucho.v5.kraken.info.TableInfo;

import io.baratine.db.Cursor;

interface FieldInfo<T,V>
{
  boolean isId();
  
  default boolean isColumn()
  {
    return false;
  }

  String columnName();

  Class<?> getJavaType();

  String sqlType();
  
  default String sqlTerm()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  V getValue(T bean);

  void setValue(T bean, Cursor cursor, int index)
    throws IllegalAccessException;

  default void setValueFromDocument(T bean, Map<String,Object> doc)
    throws IllegalAccessException
  {
  }

  void setValue(T bean, V value);

  void fillColumn(TableInfo tableInfo);

  default Object toParam(Object value)
  {
    return value;
  }
}
