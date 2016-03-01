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

package com.caucho.v5.h3.io;

import java.util.Objects;

import com.caucho.v5.h3.io.ConstH3.ClassTypeH3;

/**
 * Object definition.
 */
public class ClassInfoH3
{
  private final String _name;
  
  private final FieldInfoH3 []_fields;
  
  private final ClassTypeH3 _type;
  
  private final int _sequence;
  
  public ClassInfoH3(String name, FieldInfoH3 []fields)
  {
    this(name, ClassTypeH3.CLASS, fields, 0);
  }
  
  public ClassInfoH3(String name, ClassTypeH3 type, FieldInfoH3 []fields)
  {
    this(name, type, fields, 0);
  }
  
  public ClassInfoH3(String name,
                     ClassTypeH3 type,
                     FieldInfoH3 []fields,
                     int sequence)
  {
    Objects.requireNonNull(name);
    Objects.requireNonNull(type);
    Objects.requireNonNull(fields);
    
    _name = name;
    _type = type;
    _fields = fields;
    _sequence = sequence;
  }

  public int sequence()
  {
    return _sequence;
  }
  
  public ClassInfoH3 sequence(int sequence)
  {
    if (_sequence != 0) {
      throw new IllegalStateException();
    }
    
    return new ClassInfoH3(_name, _type, _fields, sequence);
  }

  public String name()
  {
    return _name;
  }

  public FieldInfoH3 []fields()
  {
    return _fields;
  }

  public ClassTypeH3 type()
  {
    return _type;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _name + "]";
  }
}
