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
abstract public class JMethod extends JAccessibleObject {
  /**
   * Returns the method name.
   */
  abstract public String getName();

  /**
   * Returns true for a static method.
   */
  abstract public boolean isStatic();

  /**
   * Returns true for a private method.
   */
  abstract public boolean isPrivate();

  /**
   * Returns true for a public method.
   */
  abstract public boolean isPublic();

  /**
   * Returns true for a protected method.
   */
  abstract public boolean isProtected();

  /**
   * Returns true for an abstract method.
   */
  abstract public boolean isAbstract();

  /**
   * Returns true for a final method.
   */
  abstract public boolean isFinal();

  /**
   * Returns the declaring class
   */
  abstract public JClass getDeclaringClass();

  /**
   * Returns the return type.
   */
  abstract public JClass getReturnType();

  /**
   * Returns the parameterized return type of the field.
   */
  abstract public JType getGenericReturnType();

  /**
   * Returns the parameter types.
   */
  abstract public JClass []getParameterTypes();

  /**
   * Returns the exception types.
   */
  abstract public JClass []getExceptionTypes();

  /**
   * Returns the declared annotaions.
   */
  abstract public JAnnotation []getDeclaredAnnotations();

  /**
   * Returns a full method name with arguments.
   */
  public String getFullName()
  {
    StringBuilder name = new StringBuilder();

    name.append(getName());
    name.append("(");

    JClass []params = getParameterTypes();
    for (int i = 0; i < params.length; i++) {
      if (i != 0)
        name.append(", ");

      name.append(params[i].getShortName());
    }

    name.append(')');

    return name.toString();
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

    JMethod jMethod = (JMethod) o;

    // note that the equality test doesn't include the class loader
    if (! getName().equals(jMethod.getName()))
      return false;

    JClass []aParam = getParameterTypes();
    JClass []bParam = jMethod.getParameterTypes();

    if (aParam.length != bParam.length)
      return false;

    for (int i = 0; i < aParam.length; i++) {
      if (aParam[i] == bParam[i])
        continue;
      else if (aParam[i] == null || bParam[i] == null)
        return false;
      else if (! aParam[i].equals(bParam[i]))
        return false;
    }

    return true;
  }

  public String toString()
  {
    return "JMethod[" + getName() + "]";
  }
}
