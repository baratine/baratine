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

package com.caucho.v5.store.temp;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import com.caucho.v5.io.StreamSource;
import com.caucho.v5.store.temp.TempStore.Chunk;

/**
 * Temporary file store.
 */
public class TempReader extends StreamSource
{
  private final TempStore _store;
  private final ArrayList<Chunk> _chunks;
  private final long _length;
  
  private final AtomicInteger _useCount = new AtomicInteger(1);
  private boolean _isClosed;
  
  TempReader(TempStore store,
             ArrayList<Chunk> chunks,
             int length)
  {
    _store = store;
    _chunks = new ArrayList<>(chunks);
    _length = length;
  }
  
  @Override
  public void addUseCount()
  {
    int value;
    
    do {
      value = _useCount.get();
      
      if (value <= 0) {
        Thread.dumpStack();
        throw new IllegalStateException();
      }
    } while (! _useCount.compareAndSet(value, value + 1));
  }
  
  @Override
  public long getLength()
  {
    return _length;
  }
  
  @Override
  public InputStream getInputStream()
  {
    return new TempInputStreamFree();
  }
  
  @Override
  public InputStream openInputStream()
  {
    addUseCount();
    
    //return new TempReaderInputStreamNoFree();
    return new TempInputStreamFree();
  }

  public int read(int pos, byte[] buffer, int offset, int length)
  {
    int chunkLength = _store.getChunkSize();
      
    int index = pos / chunkLength;
    int subOffset = pos % chunkLength;
      
    int sublen = (int) Math.min(_length - pos, length);
    sublen = Math.min(chunkLength - subOffset, sublen);
      
    if (_chunks.size() <= index) {
      return -1;
    }
      
    Chunk chunk = _chunks.get(index);
      
    if (chunk != null) {
      chunk.read(subOffset, buffer, offset, sublen);
    }
    else {
      Arrays.fill(buffer, offset, sublen, (byte) 0);
        
      return sublen;
    }
      
    return sublen;
  }
  
  @Override
  public void close()
  {
    if (! _isClosed) {
      _isClosed = true;
      
      // closeSelf();
      freeUseCount();
    }
  }
  
  @Override
  public void freeUseCount()
  {
    if (_useCount.decrementAndGet() <= 0) {
      closeSelf();
    }

    //Thread.dumpStack();
  }
  
  @Override
  protected void closeSelf()
  {
    ArrayList<Chunk> chunks = new ArrayList<>(_chunks);
    _chunks.clear();
    
    for (Chunk chunk : chunks) {
      if (chunk != null) {
        _store.free(chunk);
      }
    }
  }
  
  private class TempReaderInputStreamNoFree extends TempInputStreamFree
  {
    @Override
    public void close()
    {
    }
  }
  
  private class TempInputStreamFree extends InputStream
  {
    private byte _buf[] = new byte[1];
    private long _offset;
    private boolean _isClosed;
    
    @Override
    public int available()
    {
      return (int) Math.min(0x4000_0000, _length - _offset);
    }
    
    @Override
    public int read()
    {
      int sublen = read(_buf, 0, 1);
      
      if (sublen < 1) {
        return sublen;
      }
      else {
        return _buf[0] & 0xff;
      }
    }
    
    @Override
    public long skip(long length)
    {
      _offset += length;
      
      return length;
    }
    
    @Override
    public int read(byte []buffer, int offset, int length)
    {
      int sublen = read(_offset, buffer, offset, length);
      
      if (sublen > 0) {
        _offset += sublen;
      }
      
      return sublen;
    }
    
    public int read(long address, byte []buffer, int offset, int length)
    {
      int chunkLength = _store.getChunkSize();
      
      int index = (int) (address / chunkLength);
      int subOffset = (int) (address % chunkLength);
      
      if (_chunks.size() <= index) {
        return -1;
      }
      
      int sublen = (int) Math.min(_length - address, length);
      sublen = (int) Math.min(chunkLength - subOffset, sublen);
      
      Chunk chunk = _chunks.get(index);
      
      if (chunk != null) {
        chunk.read(subOffset, buffer, offset, sublen);
      }
      else {
        Arrays.fill(buffer, offset, sublen, (byte) 0);
        
        return sublen;
      }
      
      return sublen;
    }

    @Override
    public void close()
    {
      if (! _isClosed) {
        _isClosed = true;

        freeUseCount();
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _offset + "]"; 
    }
  }
}
