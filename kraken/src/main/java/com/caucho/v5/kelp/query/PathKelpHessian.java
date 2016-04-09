/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.kelp.query;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Building path values for kelp.
 */
public class PathKelpHessian extends PathKelp
{
  private static final Logger log
  = Logger.getLogger(PathObjectKelp.class.getName());

  private final PathMapHessian _pathMap;

  PathKelpHessian()
  {
    super(null);
    
    _pathMap = null;//new PathMapHessian();
    throw new UnsupportedOperationException();
  }

  @Override
  public PathMapHessian pathMap()
  {
    return _pathMap;
  }

  @Override
  void scan(EnvKelp query, Object []values, InputStream is)
    throws IOException
  {
    query.scan(_pathMap, is);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
