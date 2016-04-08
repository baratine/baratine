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

package com.caucho.v5.kelp;

import io.baratine.service.Result;

import java.io.IOException;
import java.util.Objects;

import com.caucho.v5.baratine.InService;
import com.caucho.v5.kelp.segment.OutSegment;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.kelp.segment.SegmentServiceImpl;
import com.caucho.v5.kelp.segment.SegmentStream;

/**
 * btree-based node
 */
abstract public class Page
{
  static final int CODE_MASK = 0xc0;
  
  static final int INSERT = 0x40;
  static final int INSERT_DEAD = 0xc0;
  
  // dead/removed delta item
  static final int REMOVE = 0x80;
  // removed item with insert value maintained
  
  private final int _id;
  private final int _nextId;
  
  private long _sequence;
  
  // save queued.
  private int _dataLengthWritten;
  
  // length updated when the write completes
  private int _writeSequence;
  private int _writeSequenceFlushed;

  private long _lruSequence;
  
  Page(int id, 
       int nextId,
       long sequence)
  {
    _id = id;
    _nextId = nextId;
    
    _sequence = sequence;
  }

  public final int getId()
  {
    return _id;
  }

  public final int getNextId()
  {
    return _nextId;
  }
  
  Type getType()
  {
    return Type.NULL;
  }
  
  public Type getLastWriteType()
  {
    return getType();
  }

  final boolean isLeaf()
  {
    return getType().isLeaf();
  }

  final boolean isTree()
  {
    return getType().isTree();
  }

  final boolean isBlob()
  {
    return getType().isBlob();
  }

  /**
   * Returns true for in-memory loaded pages.
   */
  boolean isActive()
  {
    return false;
  }
  
  boolean isStub()
  {
    return false;
  }

  /**
   * Marks the stub as valid
   */
  public void setValid()
  {
  }
  
  public boolean isValid()
  {
    return false;
  }
  
  SegmentKelp getSegment()
  {
    return null;
  }

  public final long getSequence()
  {
    return _sequence;
  }
  
  public final void setSequence(long sequence)
  {
    if (sequence == 0) {
      Thread.dumpStack();
    }
    
    synchronized (this) {
      _sequence = Math.max(_sequence, sequence);
    }
  }

  boolean compareAndSetSequence(long sequence, long newSequence)
  {
    synchronized (this) {
      if (_sequence == sequence && sequence < newSequence) {
        _sequence = newSequence;
        
        return true;
      }
      else {
        return false;
      }
    }
  }

  long getLruSequence()
  {
    return _lruSequence;
  }
  
  /*
  void updateLruSequence(PageLru lru)
  {
    long lruSequence = _lruSequence;
    long newSequence = lru.getLruSequence();
    long delta = lru.getLruSequenceDelta();
    
    if (delta < newSequence - lruSequence || lruSequence <= delta) {
      _lruSequence = newSequence;
      
      lru.updateLruSequence();
    }
  }
  */
  
  void setDirty()
  {
    _dataLengthWritten = -1;
  }
  
  boolean isDirty()
  {
    return _dataLengthWritten < 0;
  }
  
  boolean isSwappable()
  {
    return ! isDirty() && _writeSequence == _writeSequenceFlushed;
  }
  
  void clearDirty()
  {
    _dataLengthWritten = 1;
  }
  
  /**
   * Write length is the offset/length of the previous write.
   */
  final void setDataLengthWritten(int length)
  {
    _dataLengthWritten = length;
  }
  
  final int getDataLengthWritten()
  {
    return _dataLengthWritten;
  }
  
  /**
   * Returns the sequence of the last write start.
   */
  public final int getWriteSequence()
  {
    return _writeSequence;
  }
  
  @InService(PageServiceSync.class)
  final int nextWriteSequence()
  {
    return ++_writeSequence;
  }
  
  /**
   * returns the flush sequence
   */
  final int getWriteSequenceFlushed()
  {
    return _writeSequenceFlushed;
  }

  Page getStub()
  {
    return null;
  }
  
  /**
   * Returns the dirty stub, pending fsync
   */
  Page getNewStub()
  {
    return null;
  }
  
  void setStubFromNew(PageServiceImpl pageActor, Page newStub)
  {
  }

  /*
  void clearStub()
  {
  }
  */

  /*
  @InService(PageActor.class)
  final boolean getAndClearDirty()
  {
    return _isDirty.getAndSet(false);
  }
  */
  
  public int getSize()
  {
    return 0;
  }

  void sweepStub(PageServiceImpl pageActor)
  {
  }
  
  int getSaveTail()
  {
    return 0;
  }
  
  //
  // checkpoint writes
  //
  
  @InService(PageServiceImpl.class)
  void writeSweep(TableKelp table,
                  PageServiceImpl pageActor,
                  TableWriterService readWrite)
  {
    write(table, pageActor, readWrite);
  }

  @InService(PageServiceImpl.class)
  void write(TableKelp table,
             PageServiceImpl tableServiceImpl,
             TableWriterService readWrite)
  {
    if (isDirty()) {
      write(table, tableServiceImpl, getWriteStream(readWrite), getDataLengthWritten());
    }
  }
  
  void write(TableKelp table,
             PageServiceImpl pageActor,
             int saveLength)
  {
    TableWriterService readWrite = table.getReadWrite();
    
    SegmentStream nodeStream = getWriteStream(readWrite);
    
    write(table, pageActor, nodeStream, saveLength);
  }
  
  protected SegmentStream getWriteStream(TableWriterService readWrite)
  {
    return readWrite.getNodeStream();
  }
  
  void write(TableKelp table,
             PageServiceImpl pageActor,
             SegmentStream sOut,
             int saveLength)
  {
    long oldSequence = getSequence();
    int tail = getSaveTail();
  
    clearDirty(); // XXX:
    
    /*
    Page oldPage = pageActor.getPage(getId());
    
    if (! pageActor.compareAndSetPage(oldPage, this)) {
      System.out.println("HMPH2: " + this + " old:" + oldPage);
    }
    */
    
    TableWriterService readWrite = table.getReadWrite();
    writeImpl(table, pageActor, readWrite, sOut, oldSequence, saveLength, tail);
  }
  
  @InService(PageServiceImpl.class)
  void writeImpl(TableKelp db,
                 PageServiceImpl tableServiceImpl,
                 TableWriterService readWrite,
                 SegmentStream sOut,
                 long oldSequence,
                 int saveLength,
                 int tail)
  {
    Objects.requireNonNull(sOut);
    
    int sequenceWrite = nextWriteSequence();
    
    readWrite.writePage(this, sOut, oldSequence, saveLength, tail, sequenceWrite,
                        Result.onOk(x->{ afterDataFlush(tableServiceImpl, sequenceWrite); }));
  }
  
  /**
   * Called by the segment writing service to write the page to the stream.
   * 
   * @param table
   * @param sOut
   * @param oldSequence
   * @param saveLength
   * @param tail
   * @param saveSequence
   * @return
   * @throws IOException
   */
  @InService(SegmentServiceImpl.class)
  public
  Page writeCheckpoint(TableKelp table, 
                       OutSegment sOut,
                       long oldSequence,
                       int saveLength,
                       int tail,
                       int saveSequence)
    throws IOException
  {
    return null;
  }

  /**
   * After the data is written to mmap/file, it can be reloaded on a 
   * stub reload.
  *
   * @param tableService the owning service
   * @param sequenceFlush the write sequence to update
   */
  public void afterDataFlush(PageServiceImpl tableService,
                             int sequenceFlush)
  {
    _writeSequenceFlushed = Math.max(_writeSequenceFlushed, sequenceFlush);
  }
  
  //
  // validation
  //

  /**
   * On a segment free, validate that the page has no references.
   * @param segment
   */
  boolean validateFree(SegmentKelp segment)
  {
    return true;
  }
  
  //
  // utilities
  //
  
  byte []incrementKey(byte []splitKey)
  {
    byte []nextKey = new byte[splitKey.length];
    
    System.arraycopy(splitKey, 0, nextKey, 0, splitKey.length);
    
    Row.incrementKey(nextKey);;
    
    return nextKey;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getId() + ",seq=" + getSequence() + "]";
  }
  
  /*
  class ResultWritePage implements Result<Integer> {
    private PageServiceImpl _tableService;
    private Page _page;
    private long _sequenceWrite;
    
    ResultWritePage(PageServiceImpl tableService,
                    Page page, 
                    long sequenceWrite)
    {
      _tableService = tableService;
      _page = page;
      _sequenceWrite = sequenceWrite;
    }
    
    @Override
    public void handle(Integer value, Throwable exn)
    {
      _tableService.afterDataFlush(_page, (int) _sequenceWrite);
    }
  }
  */

  public static enum Type {
    NULL {
      @Override
      boolean isValid() { return false; }
    },
    
    TREE {
      @Override
      boolean isTree() { return true; }
      
    },
    
    LEAF {
      @Override
      boolean isLeaf() { return true; }
    },
    
    LEAF_DELTA {
    },
    
    BLOB_TEMP {
      @Override
      boolean isBlob() { return true; }
    },
    
    BLOB {
      @Override
      boolean isBlob() { return true; }
    },
    
    BLOB_FREE {
      @Override
      boolean isBlob() { return true; }
      
      @Override
      boolean isValid() { return false; }
    };
    
    boolean isLeaf() { return false; }
    boolean isTree() { return false; }
    boolean isBlob() { return false; }
    
    boolean isValid() { return true; }
    
    static Type valueOf(int index)
    {
      return Type.values()[index];
    }
  }
}
