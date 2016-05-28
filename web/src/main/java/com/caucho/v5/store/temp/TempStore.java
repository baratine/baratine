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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.caucho.v5.store.io.InStore;
import com.caucho.v5.store.io.OutStore;
import com.caucho.v5.store.io.StoreBuilder;
import com.caucho.v5.store.io.StoreReadWrite;

/**
 * Temporary file store.
 */
public class TempStore
{
  private final Path _path;
  private final StoreReadWrite _store;
  
  private final ConcurrentLinkedQueue<Chunk> _freeList
    = new ConcurrentLinkedQueue<>();
    
  private final int _segmentLength;
  private final int _chunkSize;
    
  private long _address;
  
  private ConcurrentHashMap<Chunk,ChunkDebug> _debugMap
    = new ConcurrentHashMap<>();
  
  TempStore(TempStoreBuilder builder)
  {
    _path = builder.path();
    
    _chunkSize = builder.blockSize();
    
    _segmentLength = 1024 * _chunkSize;
    
    StoreBuilder storeBuilder = new StoreBuilder(_path);
    storeBuilder.services(builder.ampManager());
    _store = storeBuilder.build();
    
    try {
      if (Files.exists(_path)) {
        _store.init();
      }
      else {
        _store.create();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public long getSize()
  {
    return _address;
  }
  
  public long getSizeFree()
  {
    return _freeList.size() * _chunkSize;
  }
  
  public long getSizeUsed()
  {
    debugUsed();
    
    return getSize() - getSizeFree();
  }
  
  private void debugUsed()
  {
    int count = 5;
    
    for (Map.Entry<Chunk, ChunkDebug> entry : _debugMap.entrySet()) {
      if (count-- >= 0) {
        entry.getValue().getLocation().printStackTrace();
      }
    }
  }

  public int getChunkSize()
  {
    return _chunkSize;
  }
  
  public TempWriter openWriter()
  {
    return new TempWriter(this);
  }
  
  Chunk allocate()
  {
    Chunk chunk = _freeList.poll();
    
    if (chunk != null) {
      _debugMap.put(chunk, new ChunkDebug(chunk));
      return chunk;
    }
    
    synchronized (_freeList) {
      while ((chunk = _freeList.poll()) == null) {
        long address = _address;
        _address = address + _segmentLength;
      
        try (OutStore out = _store.openWrite(address, _segmentLength)) {
        }
      
        for (; address < _address; address += _chunkSize) {
          Chunk freeChunk = new Chunk(_store, address, _chunkSize);
        
          _freeList.offer(freeChunk);
        }
      }
    }
    
    _debugMap.put(chunk, new ChunkDebug(chunk));
    
    return chunk;
  }
  
  void free(Chunk chunk)
  {
    _freeList.offer(chunk);
    
    _debugMap.remove(chunk);
  }
  
  public void close()
  {
    _store.close();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }
  
  static class Chunk {
    private final StoreReadWrite _store;
    private final long _address;
    private final int _length;
    
    Chunk(StoreReadWrite store,
          long address,
          int length)
    {
      _store = store;
      _address = address;
      _length = length;
    }

    public void write(int subOffset, byte[] buffer, int offset, int length)
    {
      try (OutStore out = _store.openWrite(_address, _length)) {
        out.write(_address + subOffset, buffer, offset, length);
      }
    }

    public void read(int subOffset, byte[] buffer, int offset, int length)
    {
      try (InStore in = _store.openRead(_address, _length)) {
        in.read(_address + subOffset, buffer, offset, length);
      }
    }
  }
  
  static class ChunkDebug {
    private RuntimeException _allocExn;
    
    ChunkDebug(Chunk chunk)
    {
      _allocExn = new RuntimeException("alloc location: " + chunk);
      _allocExn.fillInStackTrace();
    }
    
    public RuntimeException getLocation()
    {
      return _allocExn;
    }
  }
}
