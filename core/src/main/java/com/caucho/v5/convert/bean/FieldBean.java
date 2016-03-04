/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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
 * @author Scott Ferguson
 */

package com.caucho.v5.convert.bean;

/**
 * getter/setter for bean fields.
 */
public interface FieldBean<T>
{
  default boolean getBoolean(T bean)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default void setBoolean(T bean, boolean value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default int getInt(T bean)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default void setInt(T bean, int value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default long getLong(T bean)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default void setLong(T bean, long value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default double getDouble(T bean)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default void setDouble(T bean, double value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default String getString(T bean)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default void setString(T bean, String value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default Object getObject(T bean)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default void setObject(T bean, Object value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
