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

package com.caucho.v5.vfs;

import com.caucho.v5.loader.EnvironmentLocal;

import javax.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;

/**
 * Configuration for CaseInsensitive environments.
 */
public class CaseInsensitive {
  private final static EnvironmentLocal<Boolean> _caseInsensitive
    = new EnvironmentLocal<Boolean>("caucho.vfs.case-insensitive");
  
  private static boolean _isCaseInsensitive = true;

  public CaseInsensitive()
  {
  }

  /**
   * Returns true if the local environment is case sensitive.
   */
  public static boolean isCaseInsensitive()
  {
    Boolean value = _caseInsensitive.get();

    if (value == null) {
      return _isCaseInsensitive;
    }
    else
      return value.booleanValue();
  }

  /**
   * Sets true if case sensitive.
   */
  public void setValue(boolean isInsensitive)
  {
    _isCaseInsensitive = isInsensitive;
  }

  /**
   * Init.
   */
  @PostConstruct
  public void init()
  {
    _caseInsensitive.set(new Boolean(_isCaseInsensitive));
  }
  
  static {
    _isCaseInsensitive = false;
  }
}
