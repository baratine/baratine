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

import java.util.HashMap;

import java.lang.annotation.*;
import java.lang.reflect.*;

/**
 * Wrapper around the java Class for a JClass.
 */
public class JAnnotationWrapper extends JAnnotation
{
  private Annotation _ann;
  private HashMap<String,Object> _values;

  public JAnnotationWrapper(Annotation ann)
  {
    _ann = ann;
  }
  
  /**
   * Returns the class name.
   */
  public String getType()
  {
    return _ann.annotationType().getName();
  }

  /**
   * Returns the annotation values.
   */
  public HashMap<String,Object> getValueMap()
  {
    try {
      if (_values == null) {
        _values = new HashMap<String,Object>();

        Method []methods = _ann.annotationType().getMethods();

        for (int i = 0; i < methods.length; i++) {
          Method method = methods[i];

          if (method.getDeclaringClass().equals(Class.class))
            continue;
          if (method.getDeclaringClass().equals(Object.class))
            continue;
          if (method.getParameterTypes().length != 0)
            continue;

          _values.put(method.getName(), method.invoke(_ann));
        }
      }

      return _values;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
