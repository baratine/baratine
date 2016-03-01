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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import com.caucho.v5.io.StreamImpl;

/**
 * Always returns <code>FileNotFound</code> for any open attempt.
 * <code>NotFoundPath</code> is a useful utility Path for MergePath when
 * the path doesn't exist in any of the merged paths.
 */
public class NotFoundPath extends PathImpl {
  private String _url;

  /**
   * Creates new NotFoundPath
   */
  public NotFoundPath(SchemeMap schemeMap, String url)
  {
    super(schemeMap);

    _url = url;
  }

  /**
   * Dummy return.
   */
  public PathImpl schemeWalk(String userPath,
                         Map<String,Object> attributes,
                         String path, int offset)
  {
    return this;
  }

  /**
   * The URL is error
   */
  public String getURL()
  {
    return _url;
  }

  public String getScheme()
  {
    return "error";
  }

  /**
   * Returns the URL which can't be found.
   */
  public String getPath()
  {
    return _url;
  }

  /**
   * Throws a FileNotFoundException for any read.
   */
  public StreamImpl openReadImpl()
    throws IOException
  {
    throw new FileNotFoundException(getURL());
  }

  protected PathImpl copyCache()
  {
    return null;
  }

  /**
   * Only identical is equals
   */
  public boolean equals(Object obj)
  {
    return false;
  }
}
