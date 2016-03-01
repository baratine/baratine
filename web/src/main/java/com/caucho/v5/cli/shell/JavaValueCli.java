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

/**
 * Java object value type for the command-line
 */
public class JavaValueCli extends ValueCli
{
  private final Object _value;
  
  public JavaValueCli(Object value)
  {
    _value = value;
  }
  
  @Override
  public TypeCli type()
  {
    return TypeCli.JAVA;
  }
  
  @Override
  public Object javaValue()
  {
    return _value;
  }
  
  @Override
  public String stringValue()
  {
    return String.valueOf(_value);
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
    
    JavaValueCli javaValue = (JavaValueCli) value;
    
    if (_value instanceof Comparable) {
      return ((Comparable) _value).compareTo(javaValue._value);
    }
    else {
      return 0;
    }
  }
}
