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

package com.caucho.v5.http.dispatch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.util.LruCache;

/**
 * The dispatch server is responsible for building Invocations,
 * specifically for creating the FilterChain for the invocation.
 */
public class InvocationManager<I extends Invocation>
{
  private final InvocationRouter<I> _router;

  private final int _invocationCacheSize;
  private final int _maxURLLength;
  private final int _maxURILength;

  // Cache of uri -> invocation maps
  private final LruCache<Object,I> _invocationCache;

  private final InvocationDecoder<I> _invocationDecoder;
  
  public InvocationManager(InvocationManagerBuilder<I> builder)
  {
    Objects.requireNonNull(builder);
    
    _router = builder.getRouter();
    Objects.requireNonNull(_router);
    
    _invocationCacheSize = builder.getCacheSize();
    
    _invocationCache = new LruCache<Object,I>(_invocationCacheSize);
    _invocationCache.setEnableStatistics(true);
    
    _maxURLLength = builder.getMaxURLLength();
    _maxURILength = builder.getMaxURILength();
    
    _invocationDecoder = builder.getDecoder();
    _invocationDecoder.setMaxURILength(_maxURILength);
  }

  /**
   * Gets the dispatch builder.
   */
  public InvocationRouter<I> getInvocationBuilder()
  {
    return _router;
  }
  
  public int getInvocationCacheSize()
  {
    return _invocationCacheSize;
  }

  public int getMaxURILength()
  {
    return _maxURILength;
  }

  /**
   * Returns the InvocationDecoder.
   */
  public InvocationDecoder<I> getInvocationDecoder()
  {
    return _invocationDecoder;
  }

  /**
   * Sets URL encoding.
   */
  public String getURLCharacterEncoding()
  {
    return getInvocationDecoder().getEncoding();
  }

  /**
   * Returns the invocation decoder for configuration.
   */
  public InvocationDecoder createInvocationDecoder()
  {
    return getInvocationDecoder();
  }

  /**
   * Returns the cached invocation.
   */
  public final I getInvocation(Object protocolKey)
  {
    I invocation = null;

    // XXX: see if can remove this
    LruCache<Object,I> invocationCache = _invocationCache;

    if (invocationCache != null) {
      invocation = invocationCache.get(protocolKey);
    }

    if (invocation == null) {
      return null;
    }
    else if (invocation.isModified()) {
      return null;
    }
    else {
      return invocation;
    }
  }

  /**
   * Creates an invocation.
   */
  public I createInvocation()
  {
    return getInvocationBuilder().createInvocation();
  }

  /**
   * Builds the invocation, saving its value keyed by the protocol key.
   *
   * @param protocolKey protocol-specific key to save the invocation in
   * @param invocation the invocation to build.
   */
  public I buildInvocation(Object protocolKey, I invocation)
    throws ConfigException
  {
    Objects.requireNonNull(invocation);
    
    invocation = buildInvocation(invocation);

    // XXX: see if can remove this, and rely on the invocation cache existing
    LruCache<Object,I> invocationCache = _invocationCache;

    if (invocationCache != null) {
      I oldInvocation;
      oldInvocation = invocationCache.get(protocolKey);

      // server/10r2
      if (oldInvocation != null && ! oldInvocation.isModified()) {
        return oldInvocation;
      }

      if (invocation.getURLLength() < _maxURLLength) {
        invocationCache.put(protocolKey, invocation);
      }
    }

    return invocation;
  }

  /**
   * Builds the invocation.
   */
  public I buildInvocation(I invocation)
    throws ConfigException
  {
    return getInvocationBuilder().routeInvocation(invocation);
  }

  /**
   * Clears the invocation cache.
   */
  public void clearCache()
  {
    // XXX: see if can remove this, and rely on the invocation cache existing
    LruCache<Object,I> invocationCache = _invocationCache;

    if (invocationCache != null) {
      invocationCache.clear();
    }
  }

  /**
   * Returns the invocations.
   */
  public ArrayList<I> getInvocations()
  {
    LruCache<Object,I> invocationCache = _invocationCache;

    ArrayList<I> invocationList = new ArrayList<>();
      
    synchronized (invocationCache) {
      Iterator<I> iter;
      iter = invocationCache.values();

      while (iter.hasNext()) {
        invocationList.add(iter.next());
      }
    }

    return invocationList;
  }

  /**
   * Returns the invocation cache hit count.
   */
  public long getInvocationCacheHitCount()
  {
    LruCache<Object,I> invocationCache = _invocationCache;

    if (invocationCache != null) {
      return invocationCache.getHitCount();
    }
    else {
      return 0;
    }
  }

  /**
   * Returns the invocation cache hit count.
   */
  public long getInvocationCacheMissCount()
  {
    LruCache<Object,I> invocationCache = _invocationCache;

    if (invocationCache != null) {
      return invocationCache.getMissCount();
    }
    else {
      return 0;
    }
  }

  /**
   * Returns true if the server has been modified and needs restarting.
   */
  public boolean isModified()
  {
    return false;
  }

  /**
   * Log the reason for modification.
   */
  public boolean logModified(Logger log)
  {
    return false;
  }
}
