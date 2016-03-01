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

import java.util.HashMap;

/**
 * Path for a H3 query.
 */
public class PathH3Node implements PathH3Amp
{
  private HashMap<String,PathH3Amp> _fields = new HashMap<>();
  
  PathH3Amp leaf(String fieldName, int index)
  {
    PathH3Leaf leaf = new PathH3Leaf(index);
    
    _fields.put(fieldName, leaf);
    
    return leaf;
  }
  
  @Override
  public int index()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public PathH3Amp field(String name)
  {
    return _fields.get(name);
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _fields + "]";
  }
}
