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

package com.caucho.v5.kelp.segment;

import java.io.IOException;
import java.io.InputStream;

import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.kelp.io.CompressorKelp;
import com.caucho.v5.store.io.InStore;
import com.caucho.v5.util.L10N;

/**
 * Filesystem access for the BlockStore.
 */
public class InSegment extends StreamImpl implements AutoCloseable
{
  private static final L10N L = new L10N(InSegment.class);
  
  private InStore _sIn;
  private ReadStream _is;
  private long _address;
  
  private int _length;
  
  private int _position;
  
  private CompressorKelp _compressor;
  
  public InSegment(InStore sIn,
                   SegmentExtent extent,
                   CompressorKelp compressor)
  {
    _sIn = sIn;
    
    _address = extent.address();
    _length = extent.getLength();
    _position = 0;
    
    _compressor = compressor;
  }

  InStore getStoreRead()
  {
    return _sIn;
  }
  
  public ReadStream in()
  {
    if (_is == null) {
      _is = new ReadStream(this);
    }
    
    return _is;
  }
  
  public InputStream inCompress(long offset, int length) throws IOException
  {
    return _compressor.in(in(), offset, length);
  }
  
  @Override
  public void seekStart(long offset)
  {
    if (offset < 0 || _length < offset) {
      throw new IllegalArgumentException(L.l("Seek '{0}' is invalid",
                                             offset));
    }
    
    _position = (int) offset;
  }
  
  @Override
  public boolean hasSkip()
  {
    return true;
  }
  
  @Override
  public long skip(long n)
  {
    _position += n;
    
    return n;
  }
  
  public void setPosition(int position)
  {
    if (position < 0 || _length < position) {
      throw new IllegalStateException();
    }
    
    _position = position;
  }

  @Override
  public boolean canRead()
  {
    return true;
  }

  @Override
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    int sublen = _length - _position;
    
    if (sublen <= 0) {
      return -1;
    }
    
    sublen = Math.min(sublen, length);
    
    if (! _sIn.read(_address + _position, buffer, offset, sublen)) {
      return -1;
    }
      
    _position += sublen;
      
    return sublen;
  }
  
  @Override
  public void close()
    throws IOException
  {
    ReadStream is = _is;
    _is = null;
    
    if (is != null) {
      is.close();
    }
    
    InStore sIn = _sIn;
    _sIn = null;
    
    if (sIn != null) {
      sIn.close();
    }
    
    super.close();
  }
}
