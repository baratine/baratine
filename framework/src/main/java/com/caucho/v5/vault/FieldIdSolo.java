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

package com.caucho.v5.vault;

import com.caucho.v5.kraken.info.TableInfo;

import io.baratine.db.Cursor;

class FieldIdSolo<T> implements FieldInfo<T,Integer>
{
  @Override
  public boolean isId()
  {
    return true;
  }

  @Override
  public String columnName()
  {
    return "id";
  }

  @Override
  public Class<?> getJavaType()
  {
    return Void.class;
  }
  
  @Override
  public boolean isColumn()
  {
    return true;
  }
  
  @Override
  public String sqlTerm()
  {
    return "id";
  }

  @Override
  public String sqlType()
  {
    return "int32";
  }

  @Override
  public Integer getValue(T t)
  {
    return 1;
  }

  @Override
  public void setValue(T target, Cursor cursor, int index)
  {
    //throw new IllegalStateException();
  }

  @Override
  public void setValue(T target, Integer value)
  {
    //throw new IllegalStateException();
  }

  @Override
  public void fillColumn(TableInfo tableInfo)
  {
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + columnName() + "]";
  }
}
