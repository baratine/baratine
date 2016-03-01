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

package com.caucho.v5.vfs;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Logs usage of the path.
 */
public class SpyPath extends PathWrapper {
  protected final static Logger log
    = Logger.getLogger(SpyPath.class.getName());

  /**
   * Creates a new Path object.
   *
   * @param root the new Path root.
   */
  public SpyPath(PathImpl path)
  {
    super(path);
  }

  /**
   * Returns a new path relative to the current one.
   *
   * <p>Path only handles scheme:xxx.  Subclasses of Path will specialize
   * the xxx.
   *
   * @param userPath relative or absolute path, essentially any url.
   * @param newAttributes attributes for the new path.
   *
   * @return the new path or null if the scheme doesn't exist
   */
   public PathImpl lookup(String userPath, Map<String,Object> newAttributes)
   {
     return new SpyPath(super.lookup(userPath, newAttributes));
   }

  /**
   * Opens a random-access stream.
   */
  public RandomAccessStream openRandomAccess() throws IOException
  {
    return new SpyRandomAccessStream(getWrappedPath().openRandomAccess());
  }
}
