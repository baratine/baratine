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

package com.caucho.v5.bytecode;

import java.util.ArrayList;

/**
 * Manages an introspected java classes.
 */
public class TypeSignatureParser {
  private final JClassLoader _loader;
  private final String _sig;
  private final int _length;
  private int _index;
  
  public TypeSignatureParser(JClassLoader loader, String sig)
  {
    this(loader, sig, 0);
  }
  
  public TypeSignatureParser(JClassLoader loader, String sig, int index)
  {
    _loader = loader;
    _sig = sig;
    _length = sig.length();
    _index = index;
  }

  JType nextType()
  {
    int ch = read();

    switch (ch) {
    case -1:
      return null;
      
    case '>':
    case ')':
      _index--;
      return null;

    case 'V':
      return JClass.VOID;
    case 'Z':
      return JClass.BOOLEAN;
    case 'B':
      return JClass.BYTE;
    case 'S':
      return JClass.SHORT;
    case 'I':
      return JClass.INT;
    case 'J':
      return JClass.LONG;
    case 'F':
      return JClass.FLOAT;
    case 'D':
      return JClass.DOUBLE;
    case 'C':
      return JClass.CHAR;

    case 'L':
      return parseClass();

    default:
      throw new IllegalStateException("Can't parse: " + _sig);
    }
  }

  private JType parseClass()
  {
    int begin = _index;
    int end = begin;

    int ch;

    for (ch = read();
         ch >= 0 &&
           ch != ';' && ch != ')' &&
           ch != ',' && ch != '<' && ch != '>';
         ch = read()) {
      end = _index;
    }

    String className = _sig.substring(begin, end).replace('/', '.');
    JClass rawClass = _loader.forName(className);

    if (ch == '<')
      return parseParameterizedType(rawClass);
    else
      return rawClass;
  }

  private JType parseParameterizedType(JClass rawClass)
  {
    ArrayList<JType> argList = new ArrayList<JType>();

    JType type;
    while ((type = nextType()) != null) {
      argList.add(type);
    }

    int ch = read();
    if (ch != '>')
      throw new IllegalStateException("expected '>' at " + (char) ch);

    JType []args = new JType[argList.size()];
    argList.toArray(args);

    return new JavaParameterizedType(_loader, rawClass, args);
  }

  /**
   * Reads the next character.
   */
  private int read()
  {
    if (_index < _length)
      return _sig.charAt(_index++);
    else
      return -1;
  }
}
