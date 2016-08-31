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

package io.baratine.web;

import java.util.List;
import java.util.Map;

/**
 * Class MultipMap maps a key to a list of values. Baratine uses MultiMap
 * for HTTP request headers and query parameters.
 *
 * @param <K> type of key
 * @param <V> type of value
 */
public interface MultiMap<K,V> extends Map<K,List<V>>
{
  /**
   * Returns first value out of a list for the key or null when list contains
   * no values or when list is null
   *
   * @param key Map's key
   * @return first value or null
   */
  default V first(String key)
  {
    List<V> list = get(key);
    
    if (list == null || list.size() == 0) {
      return null;
    }
    else {
      return list.get(0);
    }
  }
}
