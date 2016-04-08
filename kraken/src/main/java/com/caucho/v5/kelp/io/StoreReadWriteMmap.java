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

package com.caucho.v5.kelp.io;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import com.caucho.v5.store.io.InStore;
import com.caucho.v5.store.io.OutStore;
import com.caucho.v5.store.io.StoreBuilder;
import com.caucho.v5.store.io.StoreReadWrite;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.RandomAccessStream;
import com.caucho.v5.vfs.WriteStream;

import io.baratine.service.Result;

/**
 * Filesystem access for a random-access store.
 * 
 * The store is designed around a single writer thread and multiple
 * reader threads. When possible, it uses mmap.
 */
public class StoreReadWriteMmap implements StoreReadWrite
{
  private final static Logger log
    = Logger.getLogger(StoreReadWriteMmap.class.getName());
  private final static L10N L = new L10N(StoreReadWriteMmap.class);
  
  // private final static int FILE_SIZE_INCREMENT = 8L * 1024 * 1024; 
  // private final static long FILE_SIZE_INCREMENT = 32L * 1024 * 1024; 
  // private final static long FILE_SIZE_INCREMENT = 64 * 1024; 

  private final PathImpl _path;

  private long _fileSize;

  private final AtomicReference<RandomAccessStream> _mmapFile
    = new AtomicReference<>();
  
  private long _mmapCloseTimeout = 1000L;
  
  private final AtomicBoolean _isClosed = new AtomicBoolean();

  /**
   * Creates a new store.
   *
   * @param database the owning database.
   * @param name the store name
   * @param lock the table lock
   * @param path the path to the files
   */
  StoreReadWriteMmap(StoreBuilder builder)
  {
    _path = null;//builder.getPath();
    if (true) throw new UnsupportedOperationException();
  }

  /**
   * Returns the file size.
   */
  public long fileSize()
  {
    return _fileSize;
  }
  
  private void setFileSize(long size)
  {
    _fileSize = Math.max(_fileSize, size);
  }
  
  @Override
  public long getChunkSize()
  {
    return 0x1_0000;
  }
  
  @Override
  public long getMmapCloseTimeout()
  {
    return _mmapCloseTimeout;
  }

  /**
   * Creates the store.
   */
  public void create()
    throws IOException
  {
    _path.getParent().mkdirs();

    if (_path.exists()) {
      throw new IOException(L.l("CREATE for path '{0}' failed, because the file already exists.  CREATE can not override an existing table.",
                                _path.getNativePath()));
    }
    
    try (WriteStream os = _path.openWrite()) {
    }
  }

  boolean isFileExist()
  {
    return _path.exists();
  }

  public void init()
    throws IOException
  {
    try (OutStore os = openWrite(0, FILE_SIZE_INCREMENT)) {
      setFileSize(os.getLength());
    }
  }

  /**
   * Opens the underlying file to the database.
   */
  public InStore openRead(long offset, int size)
  {
    long addressMax = offset + size;
    
    if (fileSize() < addressMax) {
      throw new IllegalStateException(L.l("{0} read open for length {1} but file length {2}",
                                this, addressMax, fileSize()));
    }

    InStore is = null;
    try {
      is = openReadImpl(addressMax);

      return is;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Opens the underlying file to the database.
   */
  private InStore openReadImpl(long fileSize)
    throws IOException
  {
    if (_isClosed.get()) {
      throw new IllegalStateException(L.l("{0} is closed.", this));
    }
    
    RandomAccessStream is = _mmapFile.get();

    if (is != null) {
      if (is.getLength() < fileSize) {
        is = null;
      }
    }

    while (is == null || ! is.allocate()) {
      PathImpl path = _path;
      
      is = null;

      if (path != null) {
        is = streamOpen(fileSize);
      }
      
      if (is != null) {
      }
      else {
        throw new IllegalStateException("Cannot open file");
      }
    }
    
    return new StreamRead(is);
  }

  /**
   * Opens the underlying file to the database.
   */
  public OutStore openWrite(long offset, int size)
  {
    long fileSize = offset + size;
    
    try {
      if (_isClosed.get()) {
        throw new IllegalStateException(L.l("{0} is closed.", this));
      }

      RandomAccessStream os = _mmapFile.get();

      if (os != null) {
        if (os.getLength() < fileSize) {
          os = null;
        }
      }

      while (os == null || ! os.allocate()) {
        PathImpl path = _path;

        os = null;

        if (path != null) {
          os = streamOpen(fileSize);
        }

        if (os != null) {
        }
        else {
          throw new IllegalStateException("Cannot open file");
        }
      }

      // extendFile(os, fileSize);
    
      return new StreamWrite(os);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  private RandomAccessStream streamOpen(long fileSize)
    throws IOException
  {
    if (_isClosed.get()) {
      throw new IllegalStateException();
    }
    
    int retry = 8;
    
    while (retry-- >= 0) {
      RandomAccessStream mmapFile = _mmapFile.get();
      
      if (mmapFile != null
          && mmapFile.isOpen()
          && fileSize <= mmapFile.getLength()) {
        return mmapFile;
      }
      
      synchronized (_mmapFile) {
        mmapFile = _mmapFile.get();

        if (mmapFile != null && fileSize <= mmapFile.getLength()) {
          return mmapFile;
        }
        
        if (! _mmapFile.compareAndSet(mmapFile, null)) {
          System.out.println("INVALID-MMAP-FILE");
        }
       
        try {
          if (mmapFile != null) {
            mmapFile.close();
            /*
            long timeout = getMmapCloseTimeout();
            long expires = CurrentTime.getCurrentTimeActual() + timeout;
            
            while (mmapFile.isOpen()
                   && CurrentTime.getCurrentTimeActual() < expires) {
              // wait for close
            }
            */
          }
        } catch (Exception e) {
          e.printStackTrace();
        }

        RandomAccessStream file = null;
        
        fileSize = extendFileSize(_fileSize, fileSize);

        setFileSize(fileSize);
          
        file = _path.openMemoryMappedFile(_fileSize);

        if (file != null) {
          // System.out.println("REZIE: " + Long.toHexString(fileSize));
          // mmap has extra allocation because it's not automatically closed
          // XXX: file.allocate();
            
          if (_mmapFile.compareAndSet(null, file)) {
            return file;
          }
          else {
            System.out.println("CANNOT SET");
            file.close();
            file = null;
          }
        }
      }
    }
    
    return null;
  }
  
  private long extendFileSize(long oldFileSize, long reqFileSize)
  {
    long newFileSize = 5 * oldFileSize / 4 + FILE_SIZE_INCREMENT;
    
    long index = Long.highestOneBit(newFileSize);
    long mask = ~(index - 1) >> 3;

    return Math.max(newFileSize & mask, reqFileSize);
  }
  
  public void fsync()
  {
    RandomAccessStream mmap = _mmapFile.get();
    
    if (mmap != null) {
      try {
        mmap.fsync();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  public void fsync(Result<Boolean> cont)
  {
    try {
      fsync();
      
      cont.ok(true);
    } catch (Throwable e) {
      cont.fail(e);
      
      throw e;
    }
  }
  
  public void close()
  {
    if (_isClosed.getAndSet(true)) {
      return;
    }
    
    RandomAccessStream mmap = _mmapFile.getAndSet(null);
    if (mmap != null) {
      mmap.close();
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }

  class StreamRead implements InStore
  {
    private RandomAccessStream _is;

    StreamRead(RandomAccessStream is)
    {
      _is = is;
    }

    RandomAccessStream getFile()
    {
      return _is;
    }

    @Override
    public boolean read(long address, byte[] buffer, int offset, int length)
    {
      try {
        while (length > 0) {
          int sublen = _is.read(address, buffer, offset, length);
          
          offset += sublen;
          address += sublen;
          length -= sublen;
        }
        
        return true;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    
    @Override
    public StreamRead clone()
    {
      return this;
    }

    @Override
    public void close()
    {
      RandomAccessStream is = _is;
      _is = null;
      
      if (is == null) {
        return;
      }

      is.free();
      
      if (is == _mmapFile.get()) {
        return;
      }
      
      if (_isClosed.get()) {
        is.close();
      }
      // XXX: cache
      // XXX: _cachedReadFile = this; // XXX:
      else {
        is.close();
      }
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _is + "]";
    }
  }


  class StreamWrite implements OutStore
  {
    private RandomAccessStream _os;

    StreamWrite(RandomAccessStream os)
    {
      _os = os;
    }

    RandomAccessStream getFile()
    {
      return _os;
    }
    
    @Override
    public long getLength()
    {
      try {
        return _os.getLength();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean write(long address, byte[] buffer, int offset, int length)
    {
      try {
        _os.write(address, buffer, offset, length);

        return true;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void fsync()
    {
      try {
        _os.fsync();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    
    public void fsync(Result<Boolean> result)
    {
      try {
        fsync();
        
        result.ok(true);
      } catch (Exception e) {
        result.fail(e);
        
        throw e;
      }
    }
    
    @Override
    public OutStore clone()
    {
      return this;
    }

    @Override
    public void close()
    {
      RandomAccessStream os = _os;
      _os = null;

      if (os == null) {
        return;
      }
      
      os.free();
        
      if (os == _mmapFile.get()) {
        return;
      }
        
      os.close();
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _os + "]";
    }
  }
}
