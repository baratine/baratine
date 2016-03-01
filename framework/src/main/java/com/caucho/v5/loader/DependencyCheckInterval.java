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

package com.caucho.v5.loader;

import com.caucho.v5.config.types.Period;

import javax.annotation.PostConstruct;

/**
 * Class for update interval checking.
 */
public class DependencyCheckInterval {
  private long _interval = 2000L;
  
  /**
   * Sets the interval.
   */
  public void setValue(Period period)
  {
    _interval = period.getPeriod();
  }

  /**
   * Initialize
   */
  @PostConstruct
  public void init()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof DynamicClassLoader) {
        DynamicClassLoader dynLoader;
        dynLoader = (DynamicClassLoader) loader;

        dynLoader.setDependencyCheckInterval(_interval);
        return;
      }
    }

    if (loader == null) {
      DynamicClassLoader.setGlobalDependencyCheckInterval(_interval);
    }
  }

  public String toString()
  {
    return "DependencyCheckInterval[]";
  }
}


