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
 * @author Scott Ferguson
 */

package com.caucho.v5.cli.shell;

import java.util.HashMap;
import java.util.Objects;

/**
 * Table object value type for the command-line
 */
public class TableCli extends ValueCli
{
  private final HashMap<ValueCli,ValueCli> _table = new HashMap<>();
  
  public TableCli()
  {
  }
  
  @Override
  public TypeCli type()
  {
    return TypeCli.TABLE;
  }
  
  @Override
  public Object javaValue()
  {
    return null;
  }
  
  @Override
  public String stringValue()
  {
    return String.valueOf(_table);
  }
  
  public ValueCli get(ValueCli key)
  {
    Objects.requireNonNull(key);
    
    ValueCli value = _table.get(key);
    
    if (value != null) {
      return value;
    }
    else { 
      return NullCli.NULL;
    }
    
  }
  
  public Object get(String key)
  {
    Objects.requireNonNull(key);
    
    ValueCli value = _table.get(new StringCli(key));
    
    if (value != null) {
      return value.javaValue();
    }
    else { 
      return null;
    }
  }

  public void put(ValueCli key, ValueCli value)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    
    _table.put(key, value);
  }
  
  public void put(String keyJava, Object value)
  {
    Objects.requireNonNull(keyJava);
    
    StringCli key = new StringCli(keyJava);
    
    if (value == null) {
      _table.put(key, NullCli.NULL);
    }
    else if (value instanceof ValueCli) {
      _table.put(key, (ValueCli) value);
    }
    else if (value instanceof Boolean) {
      _table.put(key, BooleanCli.create((Boolean) value));
    }
    else if (value instanceof String) {
      _table.put(key, new StringCli((String) value));
    }
    else if (value instanceof Number) {
      _table.put(key, new NumberCli(((Number) value).doubleValue()));
    }
    else {
      _table.put(key, new JavaValueCli(value));
    }
  }

  public void put(String key, ValueCli value)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    
    _table.put(new StringCli(key), value);
  }

  public void remove(Class<?> key)
  {
    remove(key.getSimpleName());
  }
  
  public void remove(String key)
  {
    Objects.requireNonNull(key);
    
    _table.remove(new StringCli(key));
  }

  @Override
  public int compareTo(Object valueObj)
  {
    if (! (valueObj instanceof ValueCli)) {
      return -1;
    }
    
    ValueCli value = (ValueCli) valueObj;
    
    int cmp = type().ordinal() - value.type().ordinal();
    
    if (cmp != 0) {
      return Integer.signum(cmp);
    }
    
    return 0;
  }
}
