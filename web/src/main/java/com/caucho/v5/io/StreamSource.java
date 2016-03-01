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

package com.caucho.v5.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.caucho.v5.util.L10N;

/**
 * Stream source for Hessian serialization of large data
 */
public class StreamSource
{
  private static final L10N L = new L10N(StreamSource.class);
  
  private StreamSource _indirectSource;
  
  private TempOutputStream _out;
  private transient AtomicInteger _useCount;

  /**
   * Constructor for subclasses.
   */
  protected StreamSource()
  {
  }

  /**
   * Constructor for Hessian deserialization.
   */
  public StreamSource(TempOutputStream os)
  {
    Objects.requireNonNull(os);
    
    _out = os;
    _useCount = new AtomicInteger(1);
  }

  /**
   * Constructor allowing for dynamic opening.
   */
  public StreamSource(StreamSource indirectSource)
  {
    _indirectSource = indirectSource;
  }
  
  public StreamSource openChild()
  {
    StreamSource next = _indirectSource;
    
    if (next != null) {
      return next.openChild();
    }
    else {
      addUseCount();
    
      return new StreamSource(this);
    }
  }
  
  public int getUseCount()
  {
    if (_indirectSource != null) {
      return _indirectSource.getUseCount();
    }
    else if (_useCount != null) {
      return _useCount.get();
    }
    else {
      return 0;
    }
  }
  
  /**
   * Returns the length of the stream, if known.
   */
  public long getLength()
  {
    StreamSource indirectSource = _indirectSource;
    
    if (indirectSource != null) {
      return indirectSource.getLength();
    }
    
    TempOutputStream out = _out;
    
    if (out != null) {
      return out.getLength();
    }
    else {
      return -1;
    }
  }
  
  /**
   * Adds a use-counter, so getInputStream can be called multiple times.
   */
  public void addUseCount()
  {
    if (_indirectSource != null) {
      _indirectSource.addUseCount();
    }
    else if (_useCount != null) {
      _useCount.incrementAndGet();
    }
  }
  
  /**
   * Frees a use-counter, so getInputStream can be called multiple times.
   */
  public void freeUseCount()
  {
    if (_indirectSource != null) {
      _indirectSource.freeUseCount();
    }
    else if (_useCount != null) {
      if (_useCount.decrementAndGet() < 0) {
        closeSelf();
      }
    }
  }

  /**
   * Returns an input stream, freeing the results
   */
  public InputStream getInputStream()
    throws IOException
  {
    if (_indirectSource != null)
      return _indirectSource.getInputStream();
    else if (_out != null)
      return _out.openInputStream();
    else
      throw new IllegalStateException(L.l("{0}: no input stream is available",
                                          this));
  }

  /**
   * Returns an input stream, without freeing the results
   */
  public InputStream openInputStream()
    throws IOException
  {
    StreamSource indirectSource = _indirectSource;
    
    if (indirectSource != null) {
      return indirectSource.openInputStream();
    }
    
    TempOutputStream out = _out;
    if (out != null) {
      return out.openInputStreamNoFree();
    }
    
    // System.err.println("OpenInputStream: fail to open input stream for " + this);
    throw new IOException(L.l("{0}: no input stream is available",
                              this));
  }

  /**
   * Close the stream.
   */
  public void close()
  {
    StreamSource ss = _indirectSource;
    _indirectSource = null;
    
    if (ss != null) {
      ss.freeUseCount();
    }
    else {
      freeUseCount();
    }
  }
  
  protected void closeSelf()
  {
    TempOutputStream out = _out;
    _out = null;
    
    if (out != null) {
      out.destroy();
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _out + "," + _indirectSource + "]";
  }
}
