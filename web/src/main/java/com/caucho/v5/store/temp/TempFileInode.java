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
import java.io.Writer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.caucho.v5.io.OutputStreamWithBuffer;
import com.caucho.v5.util.L10N;

/**
 * Represents an inode to a temporary file.
 */
public class TempFileInode {
  private static final L10N L = new L10N(TempFileInode.class);
  private static final Logger log
    = Logger.getLogger(TempFileInode.class.getName());

  //private final BlockAllocStore _store;
  private AtomicInteger _useCount = new AtomicInteger(1);

  private ArrayList<Long> _blockList = new ArrayList<Long>();
  private long []_blockArray;
  private long _length;

  /*
  TempFileInode(BlockAllocStore store)
  {
    _store = store;
  }
  */

  public long getLength()
  {
    return _length;
  }

  /**
   * Allocates access to the inode.
   */
  public boolean allocate()
  {
    int count;
    
    while ((count = _useCount.get()) > 0) {
      if (_useCount.compareAndSet(count, count + 1))
        return true;
    }
    
    return false;
  }

  /**
   * Opens a stream to write to the temp file
   */
  public OutputStream openOutputStream()
  {
    // return new TempFileOutputStream();
    return null;
  }

  /**
   * Opens a stream to read from the temp file
   */
  public InputStream openInputStream()
  {
    return new TempFileInputStream();
  }

  public Writer openWriter()
  {
    //return new TempFileWriter();
    return null;
  }

  /**
   * Writes the inode value to a stream.
   */
  public void writeToStream(OutputStreamWithBuffer os)
    throws IOException
  {
    writeToStream(os, 0, _length);
  }

  /**
   * Writes the inode value to a stream.
   */
  public void writeToStream(OutputStreamWithBuffer os,
                            long offset, long length)
    throws IOException
  {
    /*
    if (_length < length)
      length = _length;

    byte []buffer = os.getBuffer();
    int writeLength = buffer.length;
    int writeOffset = os.getBufferOffset();
    long []blockArray = _blockArray;

    while (length > 0) {
      int sublen = writeLength - writeOffset;

      if (sublen == 0) {
        buffer = os.nextBuffer(writeOffset);
        writeOffset = os.getBufferOffset();
        sublen = writeLength - writeOffset;
      }

      if (length < sublen)
        sublen = (int) length;

      long blockId = blockArray[(int) (offset / BlockAllocStore.BLOCK_SIZE)];
      int blockOffset = (int) (offset % BlockAllocStore.BLOCK_SIZE);

      if (BlockAllocStore.BLOCK_SIZE - blockOffset < sublen)
        sublen = BlockAllocStore.BLOCK_SIZE - blockOffset;

      int len = _store.readBlock(blockId, blockOffset,
                                 buffer, writeOffset, sublen);

      if (len <= 0) {
        break;
      }

      writeOffset += len;
      offset += len;
      length -= len;
    }

    os.setBufferOffset(writeOffset);

    if (_useCount.get() <= 0)
      throw new IllegalStateException(L.l("Unexpected close of cache inode"));
    */
  }

  /**
   * Writes the inode value to a stream.
   */
  public void writeToWriter(Writer out)
    throws IOException
  {
    /*
    TempCharBuffer charBuffer = TempCharBuffer.allocate();
    char []buffer = charBuffer.getBuffer();

    long offset = 0;

    long length = _length;

    while (length > 0) {
      long blockId = _blockArray[(int) (offset / BlockAllocStore.BLOCK_SIZE)];
      int blockOffset = (int) (offset % BlockAllocStore.BLOCK_SIZE);

      int sublen = (BlockAllocStore.BLOCK_SIZE - blockOffset) / 2;

      if (buffer.length < sublen)
        sublen = buffer.length;

      if (length < 2 * sublen)
        sublen = (int) (length / 2);

      int len = _store.readBlock(blockId, blockOffset,
                                 buffer, 0, sublen);

      if (len <= 0)
        break;

      out.write(buffer, 0, len);

      offset += 2 * len;
      length -= 2 * len;
    }

    TempCharBuffer.free(charBuffer);

    if (_useCount.get() <= 0)
      throw new IllegalStateException(L.l("Unexpected close of cache inode"));
      */
  }

  /**
   * Allocates access to the inode.
   */
  public void free()
  {
    int useCount = _useCount.decrementAndGet();

    if (useCount == 0) {
      remove();
    }
    else if (useCount < 0) {
      //System.out.println("BAD: " + useCount);
      throw new IllegalStateException("illegal use count: " + useCount);
    }
  }

  private void remove()
  {
    /*
    ArrayList<Long> blockList;
    long []blockArray;

    synchronized (this) {
      blockList = _blockList;
      _blockList = null;

      blockArray = _blockArray;
      _blockArray = null;
    }

    if (blockArray != null) {
      if (_useCount.get() > 0)
        Thread.dumpStack();
      
      for (long block : blockArray) {
        try {
          _store.deallocateBlock(block);
        } catch (IOException e) {
          log.log(Level.WARNING, e.toString(), e);
        }
      }
      
      if (_useCount.get() > 0)
        Thread.dumpStack();
    }
    else if (blockList != null) {
      //System.out.println("FRAGMENT-LIST: " + fragmentList);

      for (long block : blockList) {
        try {
          _store.deallocateBlock(block);
        } catch (IOException e) {
          log.log(Level.WARNING, e.toString(), e);
        }
      }
    }
    */
  }

  /*
  class TempFileOutputStream extends OutputStream {
    private final StoreTransaction _xa = RawTransaction.create();
    private final byte []_tempBuffer = new byte[8];

    @Override
    public void write(int ch)
      throws IOException
    {
      _tempBuffer[0] = (byte) ch;

      write(_tempBuffer, 0, 1);
    }

    @Override
    public void write(byte []buffer, int offset, int length)
      throws IOException
    {
      while (length > 0) {
        while (_blockList.size() <= _length / BlockAllocStore.BLOCK_SIZE) {
          Block block = _store.allocateBlock();

          _blockList.add(block.getBlockId());
          
          block.free();
        }

        int blockOffset = (int) (_length % BlockAllocStore.BLOCK_SIZE);
        long blockAddress = _blockList.get(_blockList.size() - 1);

        int sublen = BlockAllocStore.BLOCK_SIZE - blockOffset;
        if (length < sublen)
          sublen = length;

        _length += sublen;
        
        Block block = _store.writeBlock(blockAddress, blockOffset,
                                        buffer, offset, sublen);
        
        _xa.addUpdateBlock(block);

        length -= sublen;
        offset += sublen;
      }
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
      if (_blockList == null)
        return;

      _blockArray = new long[_blockList.size()];

      for (int i = 0; i < _blockList.size(); i++) {
        _blockArray[i] = _blockList.get(i);
      }
    }
  }
  */

  class TempFileInputStream extends InputStream {
    private final byte []_tempBuffer = new byte[1];

    private int _offset;

    public int read()
      throws IOException
    {
      int len = read(_tempBuffer, 0, 1);

      if (len > 0)
        return _tempBuffer[0] & 0xff;
      else
        return -1;
    }

    /**
     * Writes the inode value to a stream.
     */
    public int read(byte []buffer, int offset, int length)
      throws IOException
    {
      return -1;

      /*
      if (_length - _offset < length)
        length = (int) (_length - _offset);

      long []blockArray = _blockArray;
      int readLength = 0;

      while (length > 0) {
        long blockId = blockArray[(int) (_offset / BlockAllocStore.BLOCK_SIZE)];
        int blockOffset = (int) (_offset % BlockAllocStore.BLOCK_SIZE);

        int sublen = length;

        if (BlockAllocStore.BLOCK_SIZE - blockOffset < sublen)
          sublen = BlockAllocStore.BLOCK_SIZE - blockOffset;

        int len = _store.readBlock(blockId, blockOffset,
                                   buffer, offset, sublen);

        if (len <= 0) {
          break;
        }

        offset += len;
        _offset += len;
        length -= len;
        readLength += len;
      }

      if (readLength <= 0)
        return -1;
      else
        return readLength;
        */
    }

    public void close()
    {
    }
  }

  /*
  class TempFileWriter extends Writer {
    private final StoreTransaction _xa = RawTransaction.create();
    private final char []_tempBuffer = new char[8];

    public void write(char ch)
      throws IOException
    {
      _tempBuffer[0] = ch;

      write(_tempBuffer, 0, 1);
    }

    public void write(char []buffer, int offset, int length)
      throws IOException
    {
      while (length > 0) {
        while (_blockList.size() <= _length / BlockAllocStore.BLOCK_SIZE) {
          Block block = _store.allocateBlock();

          _blockList.add(block.getBlockId());
          
          block.free();
        }

        int blockOffset = (int) (_length % BlockAllocStore.BLOCK_SIZE);
        long blockId = _blockList.get(_blockList.size() - 1);

        int sublen = (BlockAllocStore.BLOCK_SIZE - blockOffset) / 2;
        if (length < sublen)
          sublen = length;

        _length += 2 * sublen;
        Block block = _store.writeBlock(blockId, blockOffset,
                                        buffer, offset, sublen);

        _xa.addUpdateBlock(block);
        
        length -= sublen;
        offset += sublen;
      }
    }

    public void flush()
    {
    }

    public void close()
    {
      synchronized (this) {
        if (_blockList == null)
          return;

        _blockArray = new long[_blockList.size()];

        for (int i = 0; i < _blockList.size(); i++) {
          _blockArray[i] = _blockList.get(i);
        }

        _blockList = null;
      }
    }
  }
  */
}
