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

import io.baratine.db.Cursor;

import java.util.Objects;

import com.caucho.v5.kraken.info.TableInfo;

class FieldObject implements FieldInfo
{
  private ColumnVault _column;
  private Class<?> _type;

  public FieldObject(Class<?> type, ColumnVault column)
  {
    Objects.requireNonNull(type);
    Objects.requireNonNull(column);

    _type = type;
    _column = column;
  }

  @Override
  public boolean isId()
  {
    return false;
  }

  @Override
  public String columnName()
  {
    String name = _column.name();

    if (name == null) {
      name = _type.getSimpleName();
    }

    return name;
  }

  @Override
  public Class<?> getJavaType()
  {
    return Object.class;
  }

  @Override
  public String sqlType()
  {
    return "object";
  }

  @Override
  public Object getValue(Object t)
  {
    return t;
  }

  @Override
  public void setValue(Object target, Cursor cursor, int index)
  {
    throw new IllegalStateException();
  }

  @Override
  public void setValue(Object target, Object object)
  {
    throw new IllegalStateException();
  }

  @Override
  public void fillColumn(TableInfo tableInfo)
  {
    System.out.println("FC: " + this);
  }
}
