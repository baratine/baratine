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

package com.caucho.v5.jdbc;

public class WrappedValue<T>
{
  private T _value;
  private Exception _exception;

  private long _startTimeMs;
  private long _endTimeMs;

  public WrappedValue()
  {
  }

  public T value()
  {
    return _value;
  }

  public WrappedValue<T> value(T value)
  {
    _value = value;

    return this;
  }

  public Exception exception()
  {
    return _exception;
  }

  public WrappedValue<T> exception(Exception e)
  {
    _exception = e;

    return this;
  }

  public long startTimeMs()
  {
    return _startTimeMs;
  }

  public WrappedValue<T> startTimeMs(long time)
  {
    _startTimeMs = time;

    return this;
  }

  public long endTimeMs()
  {
    return _endTimeMs;
  }

  public WrappedValue<T> endTimeMs(long time)
  {
    _endTimeMs = time;

    return this;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _value + "," + _exception + "," + (_endTimeMs - _startTimeMs) + "ms" + "]";
  }
}
