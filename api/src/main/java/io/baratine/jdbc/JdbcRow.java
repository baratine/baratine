/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package io.baratine.jdbc;

/**
 * JdbcRow class represents row filled from a query and provides getter methods to
 * retrieve values for columns.
 */
public class JdbcRow
{
  private Object[] _values;

  /**
   * Constructs a row from column values
   *
   * @param values column values
   */
  public JdbcRow(Object[] values)
  {
    _values = values;
  }

  /**
   * Returns number of columns in a row
   *
   * @return number of columns
   */
  public int getColumnCount()
  {
    return _values.length;
  }

  /**
   * Returns the class of the column.
   *
   * @param index 1-based
   * @return class of column
   */
  public Class<?> getClass(int index)
  {
    Object value = _values[index - 1];

    if (value == null) {
      return null;
    }
    else {
      return value.getClass();
    }
  }

  /**
   * Returns the column as an object without any marshaling.
   *
   * @param index 1-based
   * @return column as original Object
   */
  public Object getObject(int index)
  {
    return _values[index - 1];
  }

  /**
   * Returns the column as a String.
   *
   * @param index 1-based
   * @return column as a String
   */
  public String getString(int index)
  {
    Object value = _values[index - 1];

    if (value != null) {
      return value.toString();
    }
    else {
      return null;
    }
  }

  /**
   * Returns the column as a long.
   *
   * @param index 1-based
   * @return column as a long
   */
  public long getLong(int index)
  {
    Object value = _values[index - 1];

    if (value instanceof Long) {
      return (Long) value;
    }
    else if (value instanceof Integer) {
      return (Integer) value;
    }
    else {
      return Long.valueOf(value.toString());
    }
  }

  /**
   * Returns the column as a double.
   *
   * @param index 1-based
   * @return column as a double
   */
  public double getDouble(int index)
  {
    Object value = _values[index - 1];

    if (value instanceof Double) {
      return (Double) value;
    }
    else if (value instanceof Float) {
      return (Float) value;
    }
    else if (value instanceof Number) {
      return (Double) ((Number) value);
    }
    else {
      return Double.valueOf(value.toString());
    }
  }

  /**
   * Returns the column as a boolean.
   *
   * @param index 1-based
   * @return column as a boolean
   */
  public boolean getBoolean(int index)
  {
    Object value = _values[index - 1];

    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    else {
      return Boolean.valueOf(value.toString());
    }
  }
}
