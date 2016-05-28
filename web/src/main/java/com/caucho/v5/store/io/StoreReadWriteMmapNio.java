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

package com.caucho.v5.store.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.util.ConcurrentArrayList;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;

/**
 * Filesystem access for a random-access store.
 * 
 * The store is designed around a single writer thread and multiple
 * reader threads. When possible, it uses mmap.
 */
public class StoreReadWriteMmapNio implements StoreReadWrite
{
  private final static L10N L = new L10N(StoreReadWriteMmapNio.class);
  
  // private final static long FILE_SIZE_INCREMENT = 32L * 1024 * 1024; 
  // private final static long FILE_SIZE_INCREMENT = 64 * 1024; 

  private final Path _path;

  private long _fileSize;
  
  private FileChannel _channel;
  
  private long _mmapChunkSize;
  
  private final ConcurrentArrayList<MmapFile> _mmapFiles
    = new ConcurrentArrayList<>(MmapFile.class);
  
  private MmapFile []_mmapFileChunks = new MmapFile[0];
  
  private long _mmapCloseTimeout = 1000L;
  
  private final AtomicBoolean _isClosed = new AtomicBoolean();
  private ServicesAmp _rampManager;

  private StoreFsyncService<MmapFile> _fsyncService;

  /**
   * Creates a new store.
   *
   * @param database the owning database.
   * @param name the store name
   * @param lock the table lock
   * @param path the path to the files
   */
  StoreReadWriteMmapNio(StoreBuilder builder)
  {
    _path = builder.getPath();
    
    _rampManager = builder.ampManager();
    
    Objects.requireNonNull(_path);
    Objects.requireNonNull(_rampManager);
    
    FsyncImpl fsyncImpl = new FsyncImpl();
    StoreFsyncServiceImpl fsyncServiceImpl
      = new StoreFsyncServiceImpl(fsyncImpl);
    
    _fsyncService = getRampManager().newService(fsyncServiceImpl)
                                    .as(StoreFsyncService.class);
  }
  
  ServicesAmp getRampManager()
  {
    return _rampManager;
  }

  /**
   * Returns the file size.
   */
  @Override
  public long fileSize()
  {
    return _fileSize;
  }
  
  private void setFileSize(long size)
  {
    _fileSize = Math.max(_fileSize, size);
  }
  
  @Override
  public long chunkSize()
  {
    return FILE_SIZE_INCREMENT;
  }
  
  public long getMmapCloseTimeout()
  {
    return _mmapCloseTimeout;
  }

  boolean isFileExist()
  {
    return Files.exists(_path);
  }

  /**
   * Creates the store.
   */
  @Override
  public void create()
    throws IOException
  {
    Files.createDirectories(_path.getParent());

    if (Files.exists(_path)) {
      throw new IOException(L.l("CREATE for path '{0}' failed, because the file already exists.  CREATE can not override an existing table.",
                                _path));
    }
    
    init();
    /*
    _channel = _path.openFileChannel(StandardOpenOption.CREATE, 
                                     StandardOpenOption.WRITE,
                                     StandardOpenOption.READ);
    
    setFileSize(_path.length());
    
    initImpl();
    */
  }

  @Override
  public void init()
    throws IOException
  {
    if (_channel == null) {
      SeekableByteChannel channel;
      
      channel = Files.newByteChannel(_path,
                                     StandardOpenOption.CREATE,
                                     StandardOpenOption.WRITE,
                                     StandardOpenOption.READ);
      
      if (channel instanceof FileChannel) {
        _channel = (FileChannel) channel;
      }
      else {
        throw new IllegalStateException(_path + " " + channel);
      }
      
    }

    setFileSize(Files.size(_path));

    /*
    try (StoreWrite os = openWrite(0, FILE_SIZE_INCREMENT)) {
      setFileSize(os.getLength());
    }
    */
    
    initImpl();
  }
  
  private void initImpl()
    throws IOException
  {
    long fileSize = fileSize();
    
    long highBit = Long.highestOneBit(fileSize);
    
    int chunkSize = (int) Math.min(highBit >> 3, 0x1000_0000);
    chunkSize = Math.max(chunkSize, FILE_SIZE_INCREMENT);
    
    _mmapChunkSize = chunkSize;
    
    long offset = 0;
    
    while (offset < fileSize) {
      // long size = Math.min(_mmapChunkSize, fileSize - offset);

      streamOpen(offset, (int) chunkSize);

      offset += chunkSize;
    }
  }

  /**
   * Opens the underlying file to the database.
   */
  @Override
  public InStore openRead(long address, int size)
  {
    if (fileSize() < address + size) {
      throw new IllegalStateException(L.l("{0} read open for length {1}:{2} but file length {3}",
                                this, address, size, fileSize()));
    }

    if (_isClosed.get()) {
      throw new IllegalStateException(L.l("{0} is closed.", this));
    }
    
    if (_fileSize < address + size) {
      throw new IllegalStateException(L.l("Open read of large file {0}:{1}",
                                          Long.toHexString(address), size));
    }
    
    try {
      streamOpen(address, size);
      
      return openReadImpl(address, size);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Opens the underlying file to the database.
   */
  @Override
  public OutStore openWrite(long address, int size)
  {
    if (_isClosed.get()) {
      throw new IllegalStateException(L.l("{0} is closed.", this));
    }
    
    if (size <= 0) {
      throw new IllegalArgumentException(L.l("Invalid size: {0}", size));
    }

    try {
      streamOpen(address, size);
      
      return openWriteImpl(address, size);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private  OutStore openWriteImpl(long address, int size)
  {
    return new OutStoreMmapNio(address, size);
  }

  private InStore openReadImpl(long address, int size)
  {
    return new InStoreImpl(address, size);
  }
  
  private MmapFile streamOpen(long address, int size)
    throws IOException
  {
    if (_isClosed.get()) {
      throw new IllegalStateException();
    }

    int chunkIndex = (int) ((address + size - 1) / _mmapChunkSize);

    if (chunkIndex < _mmapFileChunks.length) {
      MmapFile mmapFile = _mmapFileChunks[chunkIndex];
      
      /*
      if (address < mmapFile.getAddress()
          || mmapFile.getAddress() + mmapFile.getSize() < address + size) {
        throw new IllegalStateException(L.l("Invalid mmap chunk. Requested <0x{0},0x{1}>. Received <0x{2},0x{3}>",
                                            Long.toHexString(address), Long.toHexString(size),
                                            Long.toHexString(mmapFile.getAddress()), 
                                            Long.toHexString(mmapFile.getSize())));
      }
      */
      
      return mmapFile;
    }
    
    synchronized (this) {
      long reqSize = Math.max(address + size, Files.size(_path));
      long fileSize = extendFileSize(_fileSize, reqSize);
      
      _fileSize = fileSize;
      
      if (fileSize % _mmapChunkSize != 0) {
        throw new IllegalStateException(L.l("file size 0x{0} must be an increment of the chunk size 0x{1}",
                                            Long.toHexString(fileSize),
                                            Long.toHexString(_mmapChunkSize)));
      }
      
      long tailAddress = _mmapFileChunks.length * _mmapChunkSize;
      
      MmapFile mmapFile = null;

      while (tailAddress < fileSize) {
        long delta = Math.min(fileSize - tailAddress, Integer.MAX_VALUE / 2);
        
        delta -= delta % _mmapChunkSize;
      
        /*
      if (delta >= Integer.MAX_VALUE) {
        throw new IllegalStateException(L.l("Stream with large file {0} tail={1}", 
                                            fileSize, tailOffset));
      }
      */
      
        mmapFile = new MmapFile(_channel, tailAddress, (int) delta);
      
        appendMmapFile(mmapFile);
        
        tailAddress += delta;
      }
      
      if (mmapFile.getAddress() + mmapFile.getSize() < address + size) {
        throw new IllegalStateException(L.l("Invalid mmap chunk. Requested <0x{0},0x{1}>. Received <0x{2},0x{3}>",
                                            Long.toHexString(address),
                                            Long.toHexString(size),
                                            Long.toHexString(mmapFile.getAddress()), 
                                            Long.toHexString(mmapFile.getSize())));
      }

      return mmapFile;
    }
  }
  
  private void appendMmapFile(MmapFile mmapFile)
  {
    _mmapFiles.add(mmapFile);
    
    long tail = mmapFile.getAddress() + mmapFile.getSize();
    int index = (int) (mmapFile.getAddress() / _mmapChunkSize);
    
    if (index != _mmapFileChunks.length) {
      throw new IllegalStateException();
    }
    
    MmapFile []newMmapChunks;
    newMmapChunks = new MmapFile[(int) (tail / _mmapChunkSize)];
    
    System.arraycopy(_mmapFileChunks, 0, newMmapChunks, 0, index);
    
    for (int i = index; i < newMmapChunks.length; i++) {
      newMmapChunks[i] = mmapFile;
    }
   
    _mmapFileChunks = newMmapChunks;
    
    for (int i = 1; i < newMmapChunks.length; i++) {
      MmapFile ptr = newMmapChunks[i];
      MmapFile prev = newMmapChunks[i - 1];

      if (ptr == prev) {
        continue;
      }
      else if (prev.getAddress() + prev.getSize() != ptr.getAddress()) {
        throw new IllegalStateException();
      }
    }
  }
  
  private long extendFileSize(long oldFileSize, long reqFileSize)
  {
    
    long newFileSize;
    
    if (reqFileSize <= oldFileSize) {
      newFileSize = oldFileSize;
    }
    else {
      newFileSize = 5 * oldFileSize / 4 + FILE_SIZE_INCREMENT;
    }
    
    long index = Long.highestOneBit(newFileSize);
    long mask = ~(index - 1) >> 3;

    newFileSize = Math.max(newFileSize & mask, reqFileSize);
    
    long chunkSize = _mmapChunkSize;
    
    long mod = newFileSize % chunkSize;
    newFileSize -= mod;
    
    if (newFileSize < reqFileSize) {
      newFileSize += chunkSize;
    }
    
    return newFileSize;
  }
  
  @Override
  public void fsync()
  {
    long sequence = _fsyncService.allocateFsyncSequence();
          
    _fsyncService.fsync(sequence, null, Result.ignore());
  }

  @Override
  public void close()
  {
    if (_isClosed.getAndSet(true)) {
      return;
    }
    
    fsync();
    
    FileChannel channel = _channel;
    _channel = null;
    
    if (channel != null) {
      try {
        channel.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }
  
  private class FsyncImpl implements StoreFsync<MmapFile> {
    @Override
    public void addResult(MmapFile mmapFile, Result<Boolean> result)
    {
      if (mmapFile != null) {
        mmapFile.addResult(result);
      }
      else if (_mmapFiles.size() > 0) {
        _mmapFiles.get(_mmapFiles.size() - 1).addResult(result);
      }
      else {
        result.ok(Boolean.TRUE);
      }
    }
    
    
    @Override
    public void fsync()
    {
      for (MmapFile mmapFile : _mmapFiles) {
        mmapFile.fsyncImpl();
      }
    }
  }
  
  private class MmapFile
  {
    private MappedByteBuffer _mmap;
    
    private final long _address;
    private final int _size;
    
    private final ArrayList<Result<Boolean>> _resultList = new ArrayList<>();
    private final AtomicBoolean _isDirty = new AtomicBoolean();

    // private final StoreFsyncService _fsyncService;

    public MmapFile(FileChannel channel, long address, int size)
      throws IOException
    {
      _mmap = channel.map(MapMode.READ_WRITE, address, size);
      
      _address = address;
      _size = size;
    }

    public void addResult(Result<Boolean> result)
    {
      /*
      if (_resultList.contains(result)) {
        Thread.dumpStack();
      }
      */
      _resultList.add(result);
    }

    public long getAddress()
    {
      return _address;
    }
    
    public long getSize()
    {
      return _size;
    }
    
    MappedByteBuffer getByteBuffer()
    {
      return _mmap;
    }

    public void setDirty()
    {
      _isDirty.set(true);
    }

    /**
     * Syncs the buffer to the disk.
     */
    public void fsyncImpl()
    {
      MappedByteBuffer mmap = _mmap;

      try {
        if (mmap != null && _isDirty.compareAndSet(true, false)) {
          ArrayList<Result<Boolean>> resultList = new ArrayList<>(_resultList);
          _resultList.clear();
          
          mmap.force();
          
          for (Result<Boolean> result : resultList) {
            try {
              result.ok(Boolean.TRUE);
            } catch (Throwable e) {
              e.printStackTrace();
            }
          }
        }
        else {
          ArrayList<Result<Boolean>> resultList = new ArrayList<>(_resultList);
          _resultList.clear();
          
          for (Result<Boolean> result : resultList) {
            try {
              result.ok(Boolean.TRUE);
            } catch (Throwable e) {
              e.printStackTrace();
            }
          }
        }
      } catch (Throwable e) {
        e.printStackTrace();
      }
    }

    public void close()
    {
      fsyncImpl();
      
      MappedByteBuffer mmap = _mmap;
      _mmap = null;

      try {
        if (mmap != null) {
          mmap.force();
        }
      } catch (Throwable e) {
        e.printStackTrace();
      }
      
      try {
        _channel.force(true);
      } catch (Throwable e) {
        e.printStackTrace();
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + Long.toHexString(_fileSize) + "]";
    }
  }

  class InStoreImpl implements InStore
  {
    private final long _address;
    private final int _size;
    
    private final MmapFile []_mmapFile;
    
    private final int _indexStart;
    private final int _indexEnd;
    
    private final ByteBuffer []_mmap;

    InStoreImpl(long address, int size)
    {
      _mmapFile = _mmapFileChunks;
      
      _indexStart = (int) (address / _mmapChunkSize);
      _indexEnd = (int) ((address + size - 1) / _mmapChunkSize);
      
      _mmap = new ByteBuffer[_indexEnd - _indexStart + 1];
      
      ByteBuffer lastBuffer = null;
      for (int i = 0; i < _mmap.length; i++) {
        MmapFile mmapFile = _mmapFile[_indexStart + i];
        
        if (lastBuffer != mmapFile.getByteBuffer()) {
          _mmap[i] = mmapFile.getByteBuffer().duplicate();
        }
        else {
          _mmap[i] = _mmap[i - 1];
        }
        
        lastBuffer = _mmap[i];
      }

      _address = address;
      _size = size;
    }

    @Override
    public boolean read(long address, byte[] buffer, int offset, int length)
    {
      if (address < _address) {
        throw new IllegalStateException();
      }
      
      if (_address + _size < offset + length) {
        throw new IllegalStateException();
      }
      
      while (length > 0) {
        int mmapIndex = (int) (address / _mmapChunkSize);
        
        ByteBuffer mmap = _mmap[mmapIndex - _indexStart];
        
        MmapFile mmapFile = _mmapFile[mmapIndex];
        int mmapOffset = (int) (address - mmapFile.getAddress());
        
        int sublen = (int) Math.min(length, mmapFile.getSize() - mmapOffset);

        mmap.limit(mmapOffset + sublen);
        mmap.position(mmapOffset);
      
        mmap.get(buffer, offset, sublen);
        
        offset += sublen;
        length -= sublen;
        address += sublen;
      }
      
      return true;
    }
    
    @Override
    public InStore clone()
    {
      return new InStoreImpl(_address, _size);
    }

    @Override
    public void close()
    {
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _path + "]";
    }
  }


  class OutStoreMmapNio implements OutStore
  {
    private final long _address;
    private final int _size;
    
    private final int _indexStart;
    private final int _indexEnd;
    
    private final MmapFile []_mmapFile;
    private final ByteBuffer []_mmap;
    
    private final ArrayList<MmapFile> _mmapFileList = new ArrayList<>();
    //private final AtomicReferenceArray<Boolean> _mmapDirty;

    OutStoreMmapNio(long address, int size)
    {
      _mmapFile = _mmapFileChunks;
      
      _indexStart = (int) (address / _mmapChunkSize);
      _indexEnd = (int) ((address + size - 1) / _mmapChunkSize);
      
      _mmap = new ByteBuffer[_indexEnd - _indexStart + 1];
      
      ByteBuffer lastBuffer = null;
      
      for (int i = 0; i < _mmap.length; i++) {
        MmapFile mmapFile = _mmapFile[_indexStart + i];
        
        if (lastBuffer != mmapFile.getByteBuffer()) {
          _mmap[i] = mmapFile.getByteBuffer().duplicate();
          _mmapFileList.add(mmapFile);
        }
        else {
          _mmap[i] = _mmap[i - 1];
        }
        
        lastBuffer = _mmap[i];
      }
      
      //_mmapDirty = new AtomicReferenceArray<>(_mmap.length);

      _address = address;
      _size = size;
    }

    @Override
    public long getLength()
    {
      return _fileSize;
    }

    @Override
    public boolean write(long address, byte[] buffer, int offset, int length)
    {
      if (address < _address) {
        throw new IllegalStateException(L.l("Address 0x{0} less than {1} offset 0x{2}",
                                            Long.toHexString(address),
                                            OutStoreMmapNio.class.getSimpleName(),
                                            Long.toHexString(_address)));
      }
      
      if (_address + _size < address + length) {
        throw new IllegalStateException(L.l("Tail 0x{0} greater than mmap tail 0x{1}",
                                            Long.toHexString(address + length),
                                            Long.toHexString(_address + _size)));
      }
      
      while (length > 0) {
        int mmapIndex = (int) (address / _mmapChunkSize);
        
        ByteBuffer mmap = _mmap[mmapIndex - _indexStart];
        
        MmapFile mmapFile = _mmapFile[mmapIndex];
        int mmapOffset = (int) (address - mmapFile.getAddress());
        
        int sublen = (int) Math.min(length, mmapFile.getSize() - mmapOffset);

        mmap.limit(mmapOffset + sublen);
        mmap.position(mmapOffset);

        mmap.put(buffer, offset, sublen);
        
        mmapFile.setDirty();
        
        length -= sublen;
        offset += sublen;
        address += sublen;
      }

      return true;
    }

    @Override
    public void fsync()
    {
      long sequence = _fsyncService.allocateFsyncSequence();
      
      _fsyncService.fsync(sequence, getLastMmap());
    }

    @Override
    public void fsync(Result<Boolean> result)
    {
      long sequence = _fsyncService.allocateFsyncSequence();
      
      _fsyncService.fsync(sequence, getLastMmap(), result);
    }

    @Override
    public void fsyncSchedule(Result<Boolean> result)
    {
      long sequence = _fsyncService.allocateFsyncSequence();
      
      _fsyncService.scheduleFsync(sequence, getLastMmap(), result);
    }
    
    private MmapFile getLastMmap()
    {
      return _mmapFile[_indexStart + _mmap.length - 1];
    }
    
    @Override
    public OutStore clone()
    {
      return new OutStoreMmapNio(_address, _size);
    }

    @Override
    public void close()
    {
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _path + "]";
    }
  }
  
  /*
  private static class ResultCount extends Wrapper<Boolean,Boolean>
  {
    private int _count;
    
    ResultCount(Result<Boolean> result, int count)
    {
      super(result);
      
      _count = count;
    }
    
    @Override
    public void complete(Boolean result)
    {
      if (--_count <= 0) {
        getNext().complete(result);
      }
    }
  }
  */
}
