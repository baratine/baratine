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
public interface JType {
  /**
   * Returns the type name.
   */
  public String getName();

  /**
   * Returns the print name, i.e. Java source name.
   */
  public String getPrintName();

  /**
   * Returns the parameter types.
   */
  public JType []getActualTypeArguments();

  /**
   * Returns the raw type.
   */
  public JClass getRawType();

  // class methods
  
  /**
   * Returns true for a primitive class.
   */
  public boolean isPrimitive();
  
  /**
   * Returns true for a public class.
   */
  public boolean isPublic();
  
  /**
   * Returns true for an class
   */
  public boolean isAbstract();
  
  /**
   * Returns true for a final class
   */
  public boolean isFinal();
  
  /**
   * Returns true for an interface
   */
  public boolean isInterface();

  /**
   * Returns the superclass.
   */
  public JClass getSuperClass();

  /**
   * Returns the interfaces.
   */
  public JClass []getInterfaces();

  /**
   * Returns true for an array class.
   */
  public boolean isArray();

  /**
   * Returns the component for a class.
   */
  public JClass getComponentType();

  /**
   * Returns true if the jClass is assignable to the class.
   */
  public boolean isAssignableTo(Class cl);

  /**
   * Returns true if the jClass is assignable to the class.
   */
  public boolean isAssignableFrom(Class cl);

  /**
   * Returns true if the jClass is assignable to the class.
   */
  public boolean isAssignableFrom(JClass cl);

  /**
   * Returns the declared methods
   */
  public JMethod []getDeclaredMethods();

  /**
   * Returns the public methods
   */
  public JMethod []getMethods();

  /**
   * Returns the matching method.
   */
  public JMethod getMethod(String name, JClass []param);

  /**
   * Returns the declared fields
   */
  public JField []getDeclaredFields();

  /**
   * Returns the fields
   */
  public JField []getFields();
}
