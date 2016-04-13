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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.io.StreamSource;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.TempOutputStream;
import com.caucho.v5.store.temp.TempStore.Chunk;

/**
 * Temporary file store.
 */
public class TempWriter extends OutputStream
{
  private final TempStore _store;
  
  private ArrayList<Chunk> _chunks = new ArrayList<>();
  
  private TempBuffer _tBuf;
  private byte []_buffer;
  private int _offsetBuffer;
  
  private int _offset;
  private int _length;

  private StreamSource _streamSource;
  
  TempWriter(TempStore store)
  {
    _store = store;
    
    _tBuf = TempBuffer.create();
    _buffer = _tBuf.buffer();
  }

  public int getLength()
  {
    return _length + _offsetBuffer;
  }

  @Override
  public void write(int ch)
  {
    if (_buffer.length <= _offsetBuffer) {
      flushBuffer();
    }
    
    _buffer[_offsetBuffer++] = (byte) ch;
  }
  
  @Override
  public void write(byte []buffer, int offset, int length)
  {
    int sublen = Math.min(length, _buffer.length - _offsetBuffer);
    
    System.arraycopy(buffer, offset, _buffer, _offsetBuffer, sublen);
    
    _offsetBuffer += sublen;
    
    if (length <= sublen) {
      return;
    }
    
    flushBuffer();
    
    offset += sublen;
    length -= sublen;
    
    write(_offset, buffer, offset, length);
    _offset += length;
  }

  public void writeStream(int offset, InputStream is, int length)
    throws IOException
  {
    TempBuffer tempBuffer = TempBuffer.create();
    byte []buffer = tempBuffer.buffer();
    
    while (length > 0) {
      int sublen = Math.min(buffer.length, length);
      
      sublen = is.read(buffer, 0, sublen);
      
      if (sublen <= 0) {
        return;
      }
      
      write(offset, buffer, 0, sublen);
      
      length -= sublen;
      offset += sublen;
    }

    tempBuffer.free();
  }
  
  private void flushBuffer()
  {
    write(_offset, _buffer, 0, _offsetBuffer);
    
    _offset += _offsetBuffer;
    _offsetBuffer = 0;
  }
  
  public void write(int address, byte []buffer, int offset, int length)
  {
    _length = Math.max(_length, address + length);
    
    TempStore store = _store;
    int chunkLength = store.getChunkSize();
    ArrayList<Chunk> chunks = _chunks;
    
    int index = (address  + length) / chunkLength;
    
    while (chunks.size() <= index) {
      chunks.add(null);
    }
    
    while (length > 0) {
      index = address / chunkLength;
      int subOffset = address % chunkLength;
      
      int sublen = Math.min(chunkLength - subOffset, length);
      
      Chunk chunk = chunks.get(index);
      
      if (chunk == null) {
        chunk = _store.allocate();
        chunks.set(index, chunk);
      }
      
      chunk.write(subOffset, buffer, offset, sublen);
      
      address += sublen;
      offset += sublen;
      length -= sublen;
    }
  }
  
  public StreamSource getStreamSource()
  {
    if (_streamSource == null) {
      _streamSource = createStreamSource();
    }
    
    return _streamSource;
  }
  
  private StreamSource createStreamSource()
  {
    if (_chunks.size() == 0) {
      Objects.requireNonNull(_tBuf);
      
      TempBuffer tBuf = _tBuf;
      _tBuf = null;
      _buffer = null;
      tBuf.length(_offsetBuffer);
      _offsetBuffer = 0;
      
      return new StreamSource(new TempOutputStream(tBuf)); 
    }
    
    flushBuffer();
    
    TempReader reader = new TempReader(_store, _chunks, _length);
    _chunks.clear();
    
    TempBuffer tBuf = _tBuf;
    _tBuf = null;
    _buffer = null;
    
    if (tBuf != null) {
      tBuf.free();
    }

    return reader;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _store + "]";
  }
  
  @Override
  public void finalize()
    throws Throwable
  {
    super.finalize();
    
    for (int i = 0; i < _chunks.size(); i++) {
      Chunk chunk = _chunks.get(i);
      
      if (chunk != null) {
        _store.free(chunk);
      }
    }
  }
}
