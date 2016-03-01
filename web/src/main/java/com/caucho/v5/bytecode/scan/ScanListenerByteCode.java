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

import java.nio.file.Path;

import com.caucho.v5.util.ModulePrivate;

/**
 * Interface for a scan manager
 */
@ModulePrivate
public interface ScanListenerByteCode {
  /**
   * Returns the listener's priority.
   *
   *  0 is an enhancer like Amber
   *  1 is an extender like CanDI
   *  2 is an extender like WebApp 3.0
   */
  default int getScanPriority()
  {
    return 1;
  }
  
  /**
   * Called to check if the archive should be scanned.
   */
  default boolean isRootScannable(Path root, String packageRoot)
  {
    return true;
  }

  /**
   * Returns the state when scanning the class
   *
   * @param root the module/jar's root path
   * @param packageRoot the virtual package root (usually for Testing) 
   * @param name the class name
   * @param modifiers the class modifiers
   *
   * @return the ScanClass object
   */
  default ScanClass scanClass(Path root, String packageRoot, 
                              String name, int modifiers)
  {
    return null;
  }
  
  /**
   * Returns true if the string matches an annotation class.
   */
  default boolean isScanMatchAnnotation(StringBuilder string)
  {
    return false;
  }
  
  /**
   * Callback to note the class matches
   */
  default void classMatchEvent(ClassLoader loader,
                               Path root,
                               String className)
  {
  }
  
  default void completePath(Path root)
  {
  }

  default boolean isScanClassName(String className)
  {
    return true;
  }
}
