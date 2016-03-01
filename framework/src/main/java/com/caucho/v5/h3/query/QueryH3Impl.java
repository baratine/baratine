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

package com.caucho.v5.h3.query;

import java.util.Objects;

import com.caucho.v5.h3.context.ContextH3;

/**
 * H3 output interface
 */
public class QueryH3Impl implements QueryH3Amp
{
  private final PathH3Amp _root;
  private final int _count;
  private ContextH3 _context;
  
  public QueryH3Impl(ContextH3 context, PathH3Amp root, int count)
  {
    Objects.requireNonNull(context);
    _context = context;
    
    Objects.requireNonNull(root);
    _root = root;
    
    if (count <= 0) {
      throw new IllegalArgumentException("count=" + count);
    }
    
    _count = count;
  }

  @Override
  public int count()
  {
    return _count;
  }
  
  @Override
  public PathH3Amp root()
  {
    return _root;
  }
}
