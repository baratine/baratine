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
 * Represents an introspected java class.
 */
public class JClassArray extends JClass {
  private JClass _componentType;

  JClassArray(JClass component)
  {
    if (component == null)
      throw new NullPointerException();
    
    _componentType = component;
  }
  
  /**
   * Returns the class name.
   */
  public String getName()
  {
    return "[" + _componentType.getName();
  }
  
  /**
   * Returns true for a primitive class.
   */
  public boolean isPrimitive()
  {
    return false;
  }
  
  /**
   * Returns true for a public class.
   */
  public boolean isPublic()
  {
    return true;
  }
  
  /**
   * Returns true for an abstract class
   */
  public boolean isAbstract()
  {
    return false;
  }
  
  /**
   * Returns true for a final class
   */
  public boolean isFinal()
  {
    return true;
  }
  
  /**
   * Returns true for an interface
   */
  public boolean isInterface()
  {
    return false;
  }

  /**
   * Returns the superclass.
   */
  public JClass getSuperClass()
  {
    return JClass.OBJECT;
  }

  /**
   * Returns the interfaces.
   */
  public JClass []getInterfaces()
  {
    return new JClass[0];
  }

  /**
   * Returns the constructors
   */
  public JMethod []getConstructors()
  {
    return new JMethod[0];
  }

  /**
   * Returns true for an array class.
   */
  public boolean isArray()
  {
    return true;
  }

  /**
   * Returns the component for a class.
   */
  public JClass getComponentType()
  {
    return _componentType;
  }

  /**
   * Returns true if the jClass is assignable to the class.
   */
  public boolean isAssignableTo(Class cl)
  {
    return getName().equals(cl.getName());
  }

  /**
   * Returns true if the jClass is assignable to the class.
   */
  public boolean isAssignableFrom(Class cl)
  {
    return getName().equals(cl.getName());
  }

  /**
   * Returns true if the jClass is assignable to the class.
   */
  public boolean isAssignableFrom(JClass cl)
  {
    return getName().equals(cl.getName());
  }
  
  /**
   * Returns the declared methods
   */
  public JMethod []getDeclaredMethods()
  {
    return new JMethod[0];
  }

  /**
   * Returns the public methods
   */
  public JMethod []getMethods()
  {
    return new JMethod[0];
  }

  /**
   * Returns the matching method.
   */
  public JMethod getMethod(String name, JClass []param)
  {
    return null;
  }

  /**
   * Returns the declared fields
   */
  public JField []getDeclaredFields()
  {
    return new JField[0];
  }

  /**
   * Returns the fields
   */
  public JField []getFields()
  {
    return new JField[0];
  }

  /**
   * Returns the printable name.
   */
  public String getPrintName()
  {
    if (isArray())
      return getComponentType().getPrintName() + "[]";
    else
      return getName();
  }

  /**
   * Returns a printable version of a class.
   */
  public String getShortName()
  {
    if (isArray())
      return getComponentType().getShortName() + "[]";
    else {
      String name = getName();
      
      int p = name.lastIndexOf('.');

      if (p >= 0)
        return name.substring(p + 1);
      else
        return name;
    }
  }
  
  /**
   * Returns the hash code
   */
  public int hashCode()
  {
    return getName().hashCode();
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

    JClass jClass = (JClass) o;

    // note that the equality test doesn't include the class loader
    return getName().equals(jClass.getName());
  }

  public String toString()
  {
    return "JClass[" + getName() + "]";
  }
}
