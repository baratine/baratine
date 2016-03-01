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

import java.util.Objects;

/**
 * The dispatch server is responsible for building Invocations,
 * specifically for creating the FilterChain for the invocation.
 */
public class InvocationManagerBuilder<I extends Invocation>
{
  private InvocationRouter<I> _router;

  private int _cacheSize = 64 * 1024;
  private int _maxURLLength = 256;
  //sets a limit on URIs Resin serves
  private int _maxURILength = 1024;

  private InvocationDecoder<I> _decoder;
  
  /**
   * Sets router
   */
  public InvocationManagerBuilder<I> router(InvocationRouter<I> router)
  {
    Objects.requireNonNull(router);
    
    _router = router;
    
    return this;
  }

  /**
   * Gets the router
   */
  InvocationRouter<I> getRouter()
  {
    return _router;
  }

  /**
   * Sets the invocation cache size.
   */
  public InvocationManagerBuilder<I> cacheSize(int size)
  {
    _cacheSize = Math.max(size, 16);
    
    return this;
  }
  
  public int getCacheSize()
  {
    return _cacheSize;
  }

  public int getMaxURLLength()
  {
    return _maxURLLength;
  }

  /**
   * Sets the max url length.
   */
  public InvocationManagerBuilder<I> maxURLLength(int length)
  {
    _maxURLLength = length;
    
    return this;
  }

  public int getMaxURILength()
  {
    return _maxURILength;
  }

  /**
   * Sets max uri length
   */
  public InvocationManagerBuilder<I> maxURILength(int maxURILength)
  {
    _maxURILength = maxURILength;
    
    return this;
  }
  
  public InvocationManagerBuilder<I> decoder(InvocationDecoder<I> decoder)
  {
    Objects.requireNonNull(decoder);
    
    _decoder = decoder;
    
    return this;
  }

  public InvocationDecoder<I> getDecoder()
  {
    if (_decoder != null) {
      return _decoder;
    }
    else {
      return new InvocationDecoder<>();
    }
  }
  
  public InvocationManager<I> build()
  {
    return new InvocationManager<>(this);
  }
}
