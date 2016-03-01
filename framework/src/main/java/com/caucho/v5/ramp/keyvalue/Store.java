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
 */

package com.caucho.v5.ramp.keyvalue;

import io.baratine.service.Result;



/**
 * Simple Key/Value store integrated with Baratine's pod system.
 * 
 * For distributed pods, the hashing of the store key is consistent with
 * the hashing of URLs. Baratine will hash both "pod:///my-service/14" and
 * the Store key "/my-service/14" to the same pod node.
 * 
 * Because the key is stored on the owning service node, gets and puts will
 * be local operations, and therefore faster.
 * 
 * <pre><code>
 * &#64;Inject &#64;Lookup("store:///")
 * Store _db;
 * </code></pre>
 * 
 * Or programmatically,
 *
 * <pre><code>
 * _db = serviceManager.lookup("store:///").as(Store.class);
 * </code></pre>
 * 
 * The special "local" pod is local to the current machine, allowing for
 * local caching.
 * <pre><code>
 * &#64;Inject &#64;Lookup("store://local/my-service")
 * Store _db;
 * </code></pre>
 * 
 * The lookup for a Store creates a prefix for get/put.
 */
public interface Store<V>
{
  void lookup(String path, Result<Store<V>> result);
  
  void get(String key, Result<V> value);

  void put(String key, V value);
  void put(String key, V value, Result<Void> result);

  void remove(String key);
  void remove(String key, Result<Void> result);
}
