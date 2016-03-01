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

import com.caucho.v5.util.ModulePrivate;
  
/**
 * Returns true if the class matches, i.e. if enhancement is necessary.
 */
@ModulePrivate
public interface ByteCodeClassMatcher {
  /**
   * Returns true if the class is a match.
   */
  boolean scanClass(String className, int modifiers);
  
  /**
   * Returns true if the annotation class is a match.
   */
  boolean isAnnotationMatch(StringBuilder annotationClassName);

  /**
   * Adds information about the superclass.
   */
  void addSuperClass(char[] buffer, int offset, int length);

  /**
   * Adds information about an interface.
   */
  void addInterface(char[] buffer, int offset, int length);

  /**
   * Adds the class annotation type
   */
  void addClassAnnotation(char[] buffer, int offset, int length);

  /**
   * Adds a class defined in the constant pool
   */
  void addPoolString(char[] charBuffer, int offset, int length);
  
  /**
   * Complete the scan.
   */
  boolean finishScan();
}
