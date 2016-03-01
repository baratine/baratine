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
abstract public class JClass extends JAnnotationObject implements JType {
  public static final JClass VOID = new JClassWrapper(void.class);
  public static final JClass BOOLEAN = new JClassWrapper(boolean.class);
  public static final JClass BYTE = new JClassWrapper(byte.class);
  public static final JClass SHORT = new JClassWrapper(short.class);
  public static final JClass INT = new JClassWrapper(int.class);
  public static final JClass LONG = new JClassWrapper(long.class);
  public static final JClass FLOAT = new JClassWrapper(float.class);
  public static final JClass DOUBLE = new JClassWrapper(double.class);
  public static final JClass CHAR = new JClassWrapper(char.class);
  public static final JClass STRING = new JClassWrapper(String.class);
  public static final JClass OBJECT = new JClassWrapper(Object.class);
  
  /**
   * Returns the class name.
   */
  abstract public String getName();
  
  /**
   * Returns the class name.
   */
  public String getSimpleName()
  {
    String name = getName();

    int p = name.lastIndexOf('.');

    return name.substring(p + 1);
  }

  /**
   * Returns the Java class.
   */
  public Class getJavaClass()
  {
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      
      return Class.forName(getName(), false, loader);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the parameter types.
   */
  public JType []getActualTypeArguments()
  {
    return new JType[0];
  }

  /**
   * Returns the raw type.
   */
  public JClass getRawType()
  {
    return this;
  }
  
  /**
   * Returns true for a primitive class.
   */
  abstract public boolean isPrimitive();
  
  /**
   * Returns true for a public class.
   */
  abstract public boolean isPublic();
  
  /**
   * Returns true for an abstract class
   */
  abstract public boolean isAbstract();
  
  /**
   * Returns true for a final class
   */
  abstract public boolean isFinal();
  
  /**
   * Returns true for an interface
   */
  abstract public boolean isInterface();

  /**
   * Returns the superclass.
   */
  abstract public JClass getSuperClass();

  /**
   * Returns the interfaces.
   */
  abstract public JClass []getInterfaces();

  /**
   * Returns true for an array class.
   */
  abstract public boolean isArray();

  /**
   * Returns the component for a class.
   */
  public JClass getComponentType()
  {
    return null;
  }

  /**
   * Returns true if the jClass is assignable to the class.
   */
  abstract public boolean isAssignableTo(Class cl);

  /**
   * Returns true if the jClass is assignable to the class.
   */
  abstract public boolean isAssignableFrom(Class cl);

  /**
   * Returns true if the jClass is assignable to the class.
   */
  abstract public boolean isAssignableFrom(JClass cl);

  /**
   * Returns the declared methods
   */
  abstract public JMethod []getDeclaredMethods();

  /**
   * Returns the public methods
   */
  abstract public JMethod []getMethods();

  /**
   * Returns the constructors.
   */
  abstract public JMethod []getConstructors();

  /**
   * Returns a matching constructor.
   */
  public JMethod getConstructor(JClass []param)
  {
    JMethod []ctors = getConstructors();

    loop:
    for (int i = 0; i < ctors.length; i++) {
      JClass []args = ctors[i].getParameterTypes();

      if (args.length != param.length)
        continue loop;

      for (int j = 0; j < args.length; j++)
        if (! args[i].equals(param[j]))
          continue loop;

      return ctors[i];
    }

    return null;
  }

  /**
   * Returns the matching method.
   */
  abstract public JMethod getMethod(String name, JClass []param);

  /**
   * Returns the declared fields
   */
  abstract public JField []getDeclaredFields();

  /**
   * Returns the fields
   */
  abstract public JField []getFields();

  /**
   * Returns the printable name.
   */
  public String getPrintName()
  {
    if (isArray())
      return getComponentType().getPrintName() + "[]";
    else
      return getName().replace('$', '.');
  }

  /**
   * Returns a printable version of a class.
   */
  public String getShortName()
  {
    if (isArray())
      return getComponentType().getShortName() + "[]";
    else {
      String name = getName().replace('$', '.');
      
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
