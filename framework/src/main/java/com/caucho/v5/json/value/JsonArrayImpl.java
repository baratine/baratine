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

package com.caucho.v5.json.value;

import java.util.AbstractList;
import java.util.List;


public class JsonArrayImpl extends JsonValueBase implements JsonArray
{
  public final List<JsonValue> _value;

  public JsonArrayImpl(List<JsonValue> value)
  {
    _value = value;
  }

  @Override
  public JsonValue get(int index)
  {
    return _value.get(index);
  }

  @Override
  public int size()
  {
    return _value.size();
  }

  @Override
  public ValueType getValueType()
  {
    return ValueType.ARRAY;
  }
  
  @Override
  public Iterable<JsonValue> values()
  {
    return _value;
  }

  /* (non-Javadoc)
   * @see javax.json.JsonArray#getBoolean(java.lang.String)
   */
  @Override
  public boolean getBoolean(String name)
  {
    // TODO Auto-generated method stub
    return false;
  }

  /* (non-Javadoc)
   * @see javax.json.JsonArray#getBoolean(java.lang.String, boolean)
   */
  @Override
  public boolean getBoolean(String name, boolean defaultValue)
  {
    // TODO Auto-generated method stub
    return false;
  }

  /* (non-Javadoc)
   * @see javax.json.JsonArray#getInt(java.lang.String)
   */
  @Override
  public int getInt(String name)
  {
    // TODO Auto-generated method stub
    return 0;
  }

  /* (non-Javadoc)
   * @see javax.json.JsonArray#getInt(java.lang.String, int)
   */
  @Override
  public int getInt(String name, int defaultValue)
  {
    // TODO Auto-generated method stub
    return 0;
  }

  /* (non-Javadoc)
   * @see javax.json.JsonArray#getJsonArray(java.lang.String)
   */
  @Override
  public JsonArray getJsonArray(String name)
  {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see javax.json.JsonArray#getJsonNumber(java.lang.String)
   */
  @Override
  public JsonNumber getJsonNumber(String name)
  {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see javax.json.JsonArray#getJsonObject(java.lang.String)
   */
  @Override
  public JsonObject getJsonObject(String name)
  {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see javax.json.JsonArray#getJsonString(java.lang.String)
   */
  @Override
  public JsonString getJsonString(String name)
  {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see javax.json.JsonArray#getString(java.lang.String)
   */
  @Override
  public String getString(String name)
  {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see javax.json.JsonArray#getString(java.lang.String, java.lang.String)
   */
  @Override
  public String getString(String name, String defaultValue)
  {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see javax.json.JsonArray#isNull(int)
   */
  @Override
  public boolean isNull(int index)
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public String toString()
  {
    return String.valueOf(_value);
  }
}
