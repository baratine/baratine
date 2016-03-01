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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.make;

import javax.annotation.*;

import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.vfs.Depend;
import com.caucho.v5.vfs.PathImpl;

/**
 * Checking dependency.
 */
public class DependencyConfig {
  private PathImpl _path;
  
  /**
   * Sets the path
   */
  public void setPath(PathImpl path)
  {
    _path = path;
  }

  /**
   * Initialize
   */
  @PostConstruct
  public void init()
  {
    Depend depend = new Depend(_path);

    EnvLoader.addDependency(depend);
  }

  public String toString()
  {
    return "DependencyConfig[]";
  }
}


