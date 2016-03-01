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

package com.caucho.v5.make;

import java.util.ArrayList;
import java.util.logging.Logger;

import com.caucho.v5.io.Dependency;
import com.caucho.v5.vfs.PersistentDependency;

/**
 * Contains a set of dependencies.
 */
public class DependencyList implements PersistentDependency
{
  private ArrayList<PersistentDependency> _dependencyList
    = new ArrayList<PersistentDependency>();

  public DependencyList()
  {
  }
  
  /**
   * Adds a dependency.
   */
  public DependencyList add(PersistentDependency dependency)
  {
    if (dependency == this)
      throw new IllegalArgumentException("Can't add self as a dependency.");
    
    if (! _dependencyList.contains(dependency))
      _dependencyList.add(dependency);

    // server/1d0w
    // XXX: _lastCheckTime = 0;

    return this;
  }
  
  /**
   * Removes a dependency.
   */
  public DependencyList remove(PersistentDependency dependency)
  {
    if (dependency == this)
      throw new IllegalArgumentException("Can't remove self as a dependency.");
    
    _dependencyList.remove(dependency);

    return this;
  }

  /**
   * Returns true if the underlying dependencies have changed.
   */
  public boolean isModified()
  {
    for (int i = _dependencyList.size() - 1; i >= 0; i--) {
      Dependency dependency = _dependencyList.get(i);

      if (dependency.isModified()) {
        return true;
      }
    }

    return false;
  }

  /**
   * Logs the reason for modification.
   */
  public boolean logModified(Logger log)
  {
    for (int i = _dependencyList.size() - 1; i >= 0; i--) {
      Dependency dependency = _dependencyList.get(i);

      if (dependency.logModified(log))
        return true;
    }
      
    return false;
  }

  /**
   * Returns true if the underlying dependencies have changed, forcing a check.
   */
  public boolean isModifiedNow()
  {
    return isModified();
  }

  /**
   * Returns a string to recreate the dependency.
   */
  public String getJavaCreateString()
  {
    StringBuilder sb = new StringBuilder();

    sb.append("new com.caucho.v5.make.DependencyList()");
    
    for (int i = 0; i < _dependencyList.size(); i++) {
      sb.append(".add(");
      sb.append(_dependencyList.get(i).getJavaCreateString());
      sb.append(")");
    }

    return sb.toString();
  }
  
  public String toString()
  {
    return "DependencyList" + _dependencyList;
  }
}
