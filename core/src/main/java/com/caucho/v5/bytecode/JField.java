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

/**
 * Represents an introspected java method.
 */
abstract public class JField extends JAccessibleObject {
  /**
   * Returns the method name.
   */
  abstract public String getName();

  /**
   * Returns the declaring class
   */
  abstract public JClass getDeclaringClass();

  /**
   * Returns the return type.
   */
  abstract public JClass getType();

  /**
   * The return type is the type.
   */
  public JClass getReturnType()
  {
    return getType();
  }

  /**
   * Returns the parameterized type of the field.
   */
  abstract public JType getGenericType();

  /**
   * Returns true for a private field.
   */
  abstract public boolean isPrivate();

  /**
   * Returns true for a transient field
   */
  abstract public boolean isTransient();

  /**
   * Returns true for a static field
   */
  abstract public boolean isStatic();

  /**
   * Returns true if equals.
   */
  public boolean equals(Object o)
  {
    if (o == this)
      return true;
    else if (o == null || getClass() != o.getClass())
      return false;

    JField jField = (JField) o;

    // note that the equality test doesn't include the class loader
    return getName().equals(jField.getName());
  }

  public String toString()
  {
    return "JField[" + getName() + "]";
  }
}
