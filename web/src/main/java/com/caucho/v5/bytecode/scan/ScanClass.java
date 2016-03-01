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

package com.caucho.v5.bytecode.scan;

import com.caucho.v5.util.ModulePrivate;

/**
 * Interface for a scanned class.
 */
@ModulePrivate
public interface ScanClass {
  /**
   * Adds the superclass information to the scan class.
   */
  default void addSuperClass(char[] buffer, int offset, int length)
  {
  }

  /**
   * Adds interface information to the scan class.
   */
  default void addInterface(char[] buffer, int offset, int length)
  {
  }
  
  /**
   * Adds a class annotation
   */
  default void addClassAnnotation(char [] buffer, int offset, int length)
  {
  }
  
  /**
   * Adds a pool string of the form "L...;" to test for annotations.
   */
  default void addPoolString(char []buffer, int offset, int length)
  {
  }

  /**
   * Complete scan processing.
   */
  default boolean finishScan()
  {
    return false;
  }
}
