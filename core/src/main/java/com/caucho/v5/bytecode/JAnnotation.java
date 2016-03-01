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

import java.util.Map;

/**
 * Represents an introspected annotation.
 */
abstract public class JAnnotation {
  /**
   * Returns the class name.
   */
  abstract public String getType();

  /**
   * Returns the annotation values.
   */
  abstract public Map<String,Object> getValueMap();

  /**
   * Returns the annotation value.
   */
  public Object get(String name)
  {
    return getValueMap().get(name);
  }

  /**
   * Returns the annotation value.
   */
  public String getString(String name)
  {
    return (String) get(name);
  }

  /**
   * Returns the annotation value.
   */
  public JClass getClass(String name)
  {
    return (JClass) get(name);
  }

  /**
   * Returns the annotation value.
   */
  public int getInt(String name)
  {
    Integer value = (Integer) get(name);

    if (value != null)
      return value.intValue();
    else
      return 0;
  }

  /**
   * Returns the annotation value.
   */
  public boolean getBoolean(String name)
  {
    return Boolean.TRUE.equals(get(name));
  }

  /**
   * Returns the annotation value.
   */
  public JAnnotation getAnnotation(String name)
  {
    return (JAnnotation) get(name);
  }
  
  /**
   * Returns true if equals.
   */
  public boolean equals(Object o)
  {
    if (o == this)
      return true;
    else if (o == null || getClass() != o.getClass())
      return false;

    JAnnotation jAnnotation = (JAnnotation) o;

    // note that the equality test doesn't include the class loader
    return getType().equals(jAnnotation.getType());
  }

  public String toString()
  {
    return "JAnnotation[" + getType() + "]";
  }
}
