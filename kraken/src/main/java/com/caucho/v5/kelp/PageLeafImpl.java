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

import static com.caucho.v5.kelp.BlockLeaf.BLOCK_SIZE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.caucho.v5.baratine.InService;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.kelp.PageServiceSync.PutType;
import com.caucho.v5.kelp.segment.OutSegment;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.kelp.segment.SegmentServiceImpl;
import com.caucho.v5.kelp.segment.SegmentStream;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.Friend;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.LruListener;

import io.baratine.service.Result;

/**
 * btree-based node
 */
public final class PageLeafImpl extends PageLeaf
  implements LruListener
{
  private static final L10N L = new L10N(PageLeafImpl.class);
  
  public static final int INDEX_REMOVED = -1;
  public static final int INDEX_UNMATCH = -2;
  
  private final TableKelp _table;
  
  private final byte []_firstKey;
  private final byte []_lastKey;
  
  private BlockLeaf []_blocks;
  
  private PageLeafStub _stub;
  
  private Type _writeType = Type.LEAF;
  
  PageLeafImpl(int id, 
               int nextId,
               long sequence,
               TableKelp table,
               byte []firstKey,
               byte []lastKey,
               BlockLeaf []nodeBlocks)
  {
    super(id, nextId, sequence);
    
    _table = table;

    _firstKey = firstKey;
    _lastKey = lastKey;
    _blocks = nodeBlocks;
    
    // db/2310
    /*
    if (Arrays.equals(firstKey, lastKey)) {
      throw new IllegalStateException(L.l("Invalid leaf with matching keys {0} {1}",
                                          id, Hex.toShortHex(firstKey)));
    }
    */
    
    if (nodeBlocks.length == 0) {
      throw new IllegalStateException();
    }
  }
  
  PageLeafImpl(int id, 
               int nextId,
               long sequence,
               TableKelp table,
               byte []firstKey,
               byte []lastKey,
               ArrayList<BlockLeaf> blocks)
  {
    this(id, nextId, sequence,
         table, firstKey, lastKey,
         blocks.toArray(new BlockLeaf[blocks.size()]));
  }

  @Override
  public final int getSize()
  {
    return _blocks.length * BLOCK_SIZE;
  }

  /**
   * Returns true for in-memory loaded pages.
   */
  @Override
  boolean isActive()
  {
    return true;
  }

  final byte []getMinKey()
  {
    return _firstKey;
  }
  
  final byte []getMaxKey()
  {
    return _lastKey;
  }
  
  @Override
  boolean isDirty()
  {
    return getDataLengthWritten() != getDataLength();
  }
  
  @Override
  void clearDirty()
  {
    setDataLengthWritten(getDataLength());
  }
  
  @Override
  void setDirty()
  {
    setDataLengthWritten(-1);
  }
  
  final BlockLeaf []getBlocks()
  {
    return _blocks;
  }
  
  int getDeltaLeafCount(Row row)
  {
    int count = 0;
    
    for (BlockLeaf block : _blocks) {
      count += block.getDeltaLeafCount(row);
    }
    
    return count;
  }
  
  @Override
  final PageLeafImpl load(TableKelp table, PageServiceImpl pageActor)
  {
    return this;
  }
  
  @Override
  public Type getLastWriteType()
  {
    return _writeType;
  }
  
  @Override
  public SegmentKelp getSegment()
  {
    if (_stub != null) {
      return _stub.getSegment();
    }
    else {
      return null;
    }
  }

  @Override
  Page getStub()
  {
    if (! isDirty()) {
      return _stub;
    }
    else {
      return null;
    }
  }

  @Override
  Page getNewStub()
  {
    return getStub();
  }

  void setStub(PageLeafStub stub)
  {
    if (stub.getId() != getId()) {
      System.out.println("BROK: " + this + " " + stub);
      throw new IllegalStateException("Stub mismatch " + this + " " + stub);
    }
    
    _stub = stub;
    // System.out.println("SETSTUB: " + _stub);
  }
  
  @Override
  @InService(PageServiceImpl.class)
  void setStubFromNew(PageServiceImpl pageActor, Page newStub)
  {
    if (newStub != null) {
      newStub.setValid();
    }
    
    if (newStub != null && _stub == newStub && ! isDirty()) {
      // System.out.println("STUB-COMPL: " + newStub + " " + this);
      
      if (pageActor.isStrictCollectRequired()) {
        sweepStub(pageActor);
      }
    }
  }
  
  /*
  @Override
  void clearStub()
  {
    _stub = null;
  }
  */

  public int compareTo(RowCursor cursor)
  {
    if (cursor.compareKey(_firstKey, 0) < 0) {
      return 1;
    }
    else if (cursor.compareKey(_lastKey, 0) > 0) {
      return -1;
    }
    else {
      return 0;
    }
  }

  boolean get(RowCursor cursor)
  {
    for (BlockLeaf block : _blocks) {
      int ptr = block.findAndFill(cursor);
      
      if (ptr >= 0) {
        cursor.fillRow(block.getBuffer(), ptr);

        if (cursor.isData()) {
          cursor.setLeafBlock(block, ptr);
        
          return true;
        }
        else {
          return false;
        }
      }
      else if (ptr == INDEX_REMOVED) {
        return false;
      }
    }
    
    return false;
  }

  StreamSource getStream(RowCursor cursor,
                         PageServiceImpl tableActor)
  {
    for (BlockLeaf block : _blocks) {
      int ptr = block.findAndFill(cursor);
      
      if (ptr >= 0) {
        cursor.fillRow(block.getBuffer(), ptr);
        
        if (cursor.isRemoved()) {
          return null;
        }
        
        cursor.setLeafBlock(block, ptr);
        
        return tableActor.toStream(block.getBuffer(), ptr, block.getBuffer());
      }
    }
    
    return null;
  }

  @InService(PageServiceImpl.class)
  byte[] getFirstKey(TableKelp table)
  {
    return _blocks[0].getFirstKey(table);
  }

  @InService(PageServiceImpl.class)
  PageLeafImpl put(TableKelp table,
                   PageServiceImpl tableService,
                   RowCursor cursor, 
                   RowCursor workCursor,
                   BackupKelp backupCallback,
                   PutType putType,
                   Result<Boolean> result)
  {
    if (cursor.compareKey(_firstKey, 0) < 0
        || cursor.compareKey(_lastKey, 0) > 0) {
      throw new IllegalStateException(L.l("Cursor key {0} is not in leaf (id={1}) range {2} to {3}\n  stub={4}",
                                          Hex.toShortHex(cursor.getKey()),
                                          getId(),
                                          Hex.toShortHex(_firstKey),
                                          Hex.toShortHex(_lastKey),
                                          _stub));
    }
    
    BlockLeaf foundBlock = null;
    int foundPtr = 0;
    
    // find old entry to free blob
    // if (! isReplay) {
    for (BlockLeaf block : _blocks) {
      int ptr = block.find(cursor);
      
      if (ptr >= 0) {
        foundBlock = block;
        foundPtr = ptr;
          
        long foundVersion = cursor.getVersion(block.getBuffer(), ptr);
        
        if (cursor.getVersion() < foundVersion) {
          cursor.removeBlobs();

          result.ok(false);
          
          return null;
        }
      }
    }
    // }
    
    // XXX: check found vs current
    
    BlockLeaf top = _blocks[0];
    
    int row;
    
    if ((row = top.insert(cursor.getRow(),
                          cursor.getBuffer(), 0, 
                          cursor.getBlobs())) < 0) {
      BlockLeaf newTop = extendBlocks();
    
      tableService.addLruSize(BLOCK_SIZE);
      if ((row = newTop.insert(cursor.getRow(),
                               cursor.getBuffer(), 0,
                               cursor.getBlobs())) < 0) {
        throw new IllegalStateException();
      }
      
      top = newTop;
    }

    if (foundBlock != null) {
      // free old blob
      byte []buffer = foundBlock.getBuffer();
      
      // remove blobs if the row was alive
      int code = buffer[foundPtr];
      
      if ((code & CODE_MASK) == BlockLeaf.INSERT) {
        buffer[foundPtr] = (byte) ((code & ~ CODE_MASK) | INSERT_DEAD);
        table.row().remove(tableService, buffer, foundPtr);
      }
    }
    
    validate(table);
    
    tableService.notifyOnPut(cursor, putType);
    cursor.freeBlobs();

    if (backupCallback != null) {
      tableService.backup(top.getBuffer(), row, backupCallback, result);
    }
    else {
      result.ok(true);
    }
    
    if (table.getDeltaLeafMax() < getDeltaLeafCount(table.row())) {
      return compact(table);
    }

    return this;
  }

  @InService(PageServiceImpl.class)
  PageLeafImpl remove(TableKelp table,
                      PageServiceImpl pageActor,
                      RowCursor cursor, 
                      // RowCursor workCursor,
                      BackupKelp backupCallback,
                      Result<? super Boolean> result)
  {
    BlockLeaf foundBlock = null;
    int foundPtr = 0;
    
    for (BlockLeaf block : _blocks) {
      int ptr = block.find(cursor);

      if (ptr >= 0) {
        foundBlock = block;
        foundPtr = ptr;
        break;
      }
    }
    
    /*
    if (foundBlock == null) {
      return this;
    }
    */
    
    BlockLeaf top = _blocks[0];
    
    if (! top.remove(cursor)) {
      top = extendBlocks();
      
      if (! top.remove(cursor)) {
        throw new IllegalStateException();
      }
    }

    if (foundBlock != null) {
      byte []buffer = foundBlock.getBuffer();
      
      int code = buffer[foundPtr] & CODE_MASK;
      
      if (code == INSERT) {
        buffer[foundPtr] = (byte) ((buffer[foundPtr] & ~CODE_MASK) | INSERT_DEAD);

        table.row().remove(pageActor, buffer, foundPtr);
      }
    }
    
    validate(table);

    if (backupCallback != null) {
      // pageActor.backupRemove(buffer, foundPtr, backupCallback);
      pageActor.backupRemove(cursor.getBuffer(), 0, cursor.getVersion(),
                             backupCallback,
                             result);
    }
    else {
      result.ok(true);
    }
    
    return this;
  }
  
  @Friend(PageServiceImpl.class)
  Split split(TableKelp table,
              PageServiceImpl pageActor)
  {
    Set<PageLeafEntry> entries = fillEntries(table);
    
    int size = countInsert(entries);
    int count = 0;
    
    Iterator<PageLeafEntry> iter = entries.iterator();
    
    ArrayList<BlockLeaf> blocks = new ArrayList<>();
    BlockLeaf block = new BlockLeaf(getId());
    
    blocks.add(block);
    
    Row row = table.row();
    byte []splitKey = new byte[row.getKeyLength()];
    int splitCount = size / 2;
    
    boolean isSmall = true;
    int smallBlock = BLOCK_SIZE / 2;
    
    while ((count < splitCount || isSmall) && iter.hasNext()) {
      PageLeafEntry entry = iter.next();
      
      if (entry.getCode() != INSERT) {
        continue;
      }

      while (! block.addEntry(row, entry)) {
        block = new BlockLeaf(getId());
        blocks.add(block);
        
        isSmall = false;
      }
      
      if (block.getAvailable() < smallBlock) {
        isSmall = false;
      }
      
      entry.getKey(splitKey);
      
      count++;
    }
    
    if (! iter.hasNext()) {
      PageLeafImpl singlePage = new PageLeafImpl(getId(), getNextId(),
                                                 getSequence(),
                                                 _table,
                                                 getMinKey(),
                                                 getMaxKey(),
                                                 blocks);
      
      singlePage.validate(table);
      
      singlePage.toSorted(table);
      
      if (isDirty()) {
        singlePage.setDirty();
      }
      
      if (_stub != null) {
        _stub.copyToCompact(singlePage);
      }
      
      return new Split(singlePage, null);
    }
    
    int nextId = pageActor.nextPid();
    
    PageLeafImpl firstPage = new PageLeafImpl(getId(), nextId,
                                              getSequence(),
                                              _table,
                                              getMinKey(),
                                              splitKey,
                                              blocks);
    
    byte []nextKey = incrementKey(splitKey);
    
    blocks = new ArrayList<>();
    block = new BlockLeaf(nextId);
    
    blocks.add(block);
    
    while (iter.hasNext()) {
      PageLeafEntry entry = iter.next();
      
      if (entry.getCode() != INSERT) {
        continue;
      }

      while (! block.addEntry(row, entry)) {
        block = new BlockLeaf(nextId);
        blocks.add(block);
      }
    }
    
    PageLeafImpl nextPage = new PageLeafImpl(nextId, getNextId(),
                                             getSequence(),
                                             _table,
                                             nextKey, getMaxKey(),
                                             blocks);
    
    firstPage.setDirty();
    nextPage.setDirty();
    
    firstPage.toSorted(table);
    nextPage.toSorted(table);
    
    firstPage.validate(table);
    nextPage.validate(table);

    return new Split(firstPage, nextPage);
  }
  
  private void toSorted(TableKelp table)
  {
    for (BlockLeaf block : _blocks) {
      block.toSorted(table.row());
    }
  }

  private int countInsert(Set<PageLeafEntry> entries)
  {
    int count = 0;
    
    for (PageLeafEntry entry : entries) {
      if (entry.getCode() == INSERT) {
        count++;
      }
    }
    
    return count;
  }
  
  Set<PageLeafEntry> fillEntries(TableKelp table)
  {
    Set<PageLeafEntry> entries = new TreeSet<>();
    
    for (BlockLeaf block : _blocks) {
      block.fillEntryTree(entries, table.row());
    }
    
    return entries;
  }
  
  Set<PageLeafEntry>
  fillRecentEntries(TableKelp table,
                    int length)
  {
    Set<PageLeafEntry> entries = new TreeSet<>();
    
    int tailBlocks = length / BLOCK_SIZE;
    int head = BLOCK_SIZE - length % BLOCK_SIZE;
    
    int tailIndex = _blocks.length - tailBlocks;
    
    Row row = table.row();
    
    for (int i = 0; i < tailIndex; i++) {
      BlockLeaf block = _blocks[i];
      
      if (i == tailIndex - 1) {
        block.fillDeltaEntries(entries, row, head);
      }
      else {
        block.fillEntryTree(entries, row);
      }
    }

    return entries;
  }

  @Override
  PageLeafImpl first(TableKelp table,
                     RowCursor minCursor,
                     RowCursor resultCursor)
  {
    PageLeafImpl page = this;
    
    while (true) {
      boolean isMatch = false;
    
      for (BlockLeaf block : page._blocks) {
        if (block.first(minCursor, resultCursor, isMatch)) {
          isMatch = true;
        }
      }

      if (isMatch) {
        return page;
      }
      
      int nextPid = page.getNextId();
      
      if (nextPid < 0) {
        return null;
      }
      
      page = (PageLeafImpl) table.getTableService().loadLeaf(nextPid);
    }
  }

  private BlockLeaf extendBlocks()
  {
    BlockLeaf newTop = new BlockLeaf(getId());
    
    BlockLeaf []blocks = new BlockLeaf[_blocks.length + 1];
    System.arraycopy(_blocks, 0, blocks, 1, _blocks.length);
    
    blocks[0] = newTop;
    
    _blocks = blocks;
    
    return newTop;
  }

  //
  // checkpoint
  //

  /**
   * Sends a write-request to the sequence writer for the page.
   * 
   * Called in the TableServerImpl thread.
   */
  @Override
  @InService(PageServiceImpl.class)
  void writeImpl(TableKelp table,
                 PageServiceImpl pageServiceImpl,
                 TableWriterService readWrite,
                 SegmentStream sOut,
                 long oldSequence,
                 int saveLength,
                 int saveTail)
  {
    Objects.requireNonNull(sOut);
    
    // System.out.println("WIMPL:" + this + " "+ Long.toHexString(System.identityHashCode(this)) + " " + _stub);
    if (saveLength <= 0
        || oldSequence != sOut.getSequence()
        || _stub == null
        || ! _stub.allowDelta()) {
      PageLeafImpl newPage;
      
      if (! isDirty() 
          && (_blocks.length == 0 || _blocks[0].isCompact())) {
        newPage = copy(getSequence());
      }
      else {
        newPage = compact(table);
      }

      int sequenceWrite = newPage.nextWriteSequence();

      if (! pageServiceImpl.compareAndSetLeaf(this, newPage)
          && ! pageServiceImpl.compareAndSetLeaf(_stub, newPage)) {
        System.out.println("HMPH: " + pageServiceImpl.getPage(getId()) + " " + this + " " + _stub);
      }
      
      saveLength = newPage.getDataLengthWritten();
      
      // newPage.write(db, pageActor, sOut, saveLength);
      
      // oldSequence = newPage.getSequence();
      
      saveTail = newPage.getSaveTail();
      
      newPage.clearDirty();

      readWrite.writePage(newPage, sOut,
                          oldSequence, saveLength, saveTail,
                          sequenceWrite,
                          Result.on(x->newPage.afterDataFlush(pageServiceImpl, sequenceWrite)));
    }
    else {
      int sequenceWrite = nextWriteSequence();
      
      clearDirty();
      
      readWrite.writePage(this, sOut,
                          oldSequence, saveLength, saveTail,
                          sequenceWrite,
                          Result.on(x->afterDataFlush(pageServiceImpl, sequenceWrite)));

      //write(db, pageActor, sOut, saveLength);
      /*
      readWrite.writePage(this, oldSequence, saveLength, saveTail,
                          nextSaveSequence());
                          */
    }
  }
  
  /**
   * Callback after the data has been written to the mmap, which allows for
   * reads (but not necessarily fsynced.)
   */
  @Override
  @InService(PageServiceSync.class)
  public void afterDataFlush(PageServiceImpl tableService,
                             int sequenceFlush)
  {
    super.afterDataFlush(tableService, sequenceFlush);
    
    sweepStub(tableService);
  }
  
  @Override
  @InService(PageServiceImpl.class)
  void sweepStub(PageServiceImpl tableService)
  {
    PageLeafStub stub = _stub;
    
    if (stub != null && isSwappable()) {
      stub.sweepStub(tableService);

      tableService.compareAndSetLeaf(this, stub);
    }
  }

  @Override
  public int getSaveTail()
  {
    BlockLeaf []blocks = _blocks;
    
    int rowFirst = blocks[0].getRowHead();
    
    int tail;
    
    if (rowFirst < BLOCK_SIZE) {
      tail = BLOCK_SIZE * blocks.length + rowFirst;
    }
    else {
      tail = BLOCK_SIZE * (blocks.length - 1);
    }
    
    return tail;
  }
  
  //
  // write methods from the TableWriter
  //

  /**
   * Callback from the writer and gc to write the page.
   */
  @Override
  @InService(TableWriterService.class)
  public Page writeCheckpoint(TableKelp table,
                              OutSegment sOut,
                              long oldSequence,
                              int saveLength,
                              int saveTail,
                              int saveSequence)
    throws IOException
  {
    BlockLeaf []blocks = _blocks;
    
    int size = BLOCK_SIZE * blocks.length;
    
    WriteStream os = sOut.out();
    
    int available = sOut.getAvailable();
    
    if (available < os.getPosition() + size) {
      return null;
    }
    
    long newSequence = sOut.getSequence();
    
    if (newSequence < oldSequence) {
      return null;
    }
    
    compareAndSetSequence(oldSequence, newSequence);

    PageLeafStub stub = _stub;
    // System.out.println("WRC: " + this + " " + stub);
    Type type;
    
    if (saveLength > 0
        && oldSequence == newSequence
        && stub != null 
        && stub.allowDelta()) {
      int offset = (int) os.getPosition();
      
      type = writeDelta(table, sOut.out(), saveLength);
      
      int length = (int) (os.getPosition() - offset);

      stub.addDelta(table, offset, length);
    }
    else {
      // _lastSequence = newSequence;
      int offset = (int) os.getPosition();
      
      try (OutputStream zOut = sOut.outCompress()) {
        type = writeCheckpointFull(table, zOut, saveTail);
      }
      
      int length = (int) (os.getPosition() - offset);
      
      stub = new PageLeafStub(getId(), getNextId(), 
                              sOut.getSegment(),
                              offset, length);
      
      stub.setLeafRef(this);
      
      _stub = stub;
    }
    
    _writeType = type;
    
    return this;
  }
  
  @InService(SegmentServiceImpl.class)
  private Type writeDelta(TableKelp table, 
                          WriteStream os,
                          int saveLength)
    throws IOException
  {
    for (PageLeafEntry entry : fillRecentEntries(table, saveLength)) {
      entry.writeDelta(os);
    }
    
    return Type.LEAF_DELTA;
  }
  
  /**
   * Compacts the leaf by rebuilding the delta entries and discarding obsolete
   * removed entries.
   */
  private PageLeafImpl compact(TableKelp table)
  {
    long now = CurrentTime.currentTime() / 1000;
    
    Set<PageLeafEntry> entries = fillEntries(table);
    
    ArrayList<BlockLeaf> blocks = new ArrayList<>();
    BlockLeaf block = new BlockLeaf(getId());
    
    blocks.add(block);
    
    Row row = table.row();
    
    for (PageLeafEntry entry : entries) {
      if (entry.getCode() != INSERT && entry.getExpires() <= now) {
        continue;
      }

      while (! block.addEntry(row, entry)) {
        block = new BlockLeaf(getId());
        blocks.add(block);
      }
    }
    
    PageLeafImpl newPage = new PageLeafImpl(getId(), 
                                            getNextId(), 
                                            getSequence(),
                                            _table,
                                            getMinKey(), 
                                            getMaxKey(),
                                            blocks);
    
    newPage.validate(table);
    newPage.toSorted(table);
    
    if (isDirty()) {
      newPage.setDirty();
    }
    
    if (_stub != null) {
      _stub.copyToCompact(newPage);
    }

    return newPage;
  }
  
  /**
   * Writes the page to the output stream as a full checkpoint.
   * 
   * The checkpoint for a leaf page is the full page blocks (row and inline blob),
   * with the free-space gap removed. A checkpoint restore restores the blocks
   * exactly.
   */
  @InService(SegmentServiceImpl.class)
  private Type writeCheckpointFull(TableKelp table, 
                                   OutputStream os, 
                                   int saveTail)
      throws IOException
  {
    os.write(getMinKey());
    os.write(getMaxKey());

    /* db/2310
    if (Arrays.equals(getMinKey(), getMaxKey())) {
      throw new IllegalStateException("bad keys");
    }
    */
    
    BlockLeaf []blocks = _blocks;
    
    int index = blocks.length - (saveTail / BLOCK_SIZE);
    int rowFirst = saveTail % BLOCK_SIZE;
      
    BitsUtil.writeInt16(os, blocks.length - index);
    
    if (blocks.length <= index) {
      return Type.LEAF;
    }

    blocks[index].writeCheckpointFull(os, rowFirst);
      
    for (int i = index + 1; i < blocks.length; i++) {
      blocks[i].writeCheckpointFull(os, 0);
    }
      
    return Type.LEAF;
  }

  /**
   * Reads a full checkpoint entry into the page.
   */
  @InService(PageServiceImpl.class)
  static PageLeafImpl readCheckpointFull(TableKelp table, 
                                         PageServiceImpl pageActor,
                                         InputStream is,
                                         int pid,
                                         int nextPid,
                                         long sequence)
    throws IOException
  {
    byte []minKey = new byte[table.getKeyLength()];
    byte []maxKey = new byte[table.getKeyLength()];
    
    int count = 0;
    BlockLeaf []blocks;
    
    IoUtil.readAll(is, minKey, 0, minKey.length);
    IoUtil.readAll(is, maxKey, 0, maxKey.length);

    count = BitsUtil.readInt16(is);
    
    blocks = new BlockLeaf[count];
    
    for (int i = 0; i < count; i++) {
      blocks[i] = new BlockLeaf(pid);
      
      blocks[i].readCheckpointFull(is);
    }
    
    if (count == 0) {
      blocks = new BlockLeaf[] { new BlockLeaf(pid) };
    }

    PageLeafImpl page = new PageLeafImpl(pid, nextPid, sequence,
                                         table, minKey, maxKey, blocks);
    
    page.clearDirty();
    
    page.validate(table);
    page.toSorted(table);
    
    return page;
  }
  
  /**
   * Reads a delta entry from the checkpoint.
   */
  void readCheckpointDelta(TableKelp table,
                           PageServiceImpl pageActor,
                           ReadStream is,
                           int length)
    throws IOException
  {
    Row row = table.row();
    
    // int keyLength = row.getKeyLength();
    int removeLength = row.getRemoveLength();
    int rowLength = row.getLength();
    
    BlockLeaf block = _blocks[0];

    long endPosition = is.getPosition() + length;
    int rowHead = block.getRowHead();
    int blobTail = block.getBlobTail();
    long pos;

    while ((pos = is.getPosition()) < endPosition) {
      int code = is.read();
      is.unread();
      
      code = code & CODE_MASK;
      
      if (code == REMOVE) {
        rowHead -= removeLength;
        
        if (rowHead < blobTail) {
          block = extendBlocks();

          rowHead = BLOCK_SIZE - removeLength;
          blobTail = 0;
        }
        
        is.readAll(block.getBuffer(), rowHead, removeLength);
      }
      else if (code == INSERT) {
        rowHead -= rowLength;
      
        while ((blobTail = row.readCheckpoint(is, block.getBuffer(), 
                                              rowHead, blobTail)) < 0) {
          //is.setPosition(pos + 1);
          is.setPosition(pos);

          block = extendBlocks();

          rowHead = BLOCK_SIZE - rowLength;
          blobTail = 0;
        }
      
        // byte []buffer = block.getBuffer();
        // buffer[rowHead] = (byte) ((buffer[rowHead] & ~CODE_MASK) | INSERT);
      }
      else {
        throw new IllegalStateException(L.l("{0} Corrupted checkpoint at pos={1} with code {2}",
                                            this, pos, code));
      }
      
      block.setRowHead(rowHead);
      block.setBlobTail(blobTail);
    }

    clearDirty();
    
    validate(table);
  }
  
  private int getDataLength()
  {
    int length = (_blocks.length - 1) * BLOCK_SIZE;
    
    BlockLeaf block = _blocks[0];
    
    length += BLOCK_SIZE - block.getRowHead();
    
    return length;
  }
  
  /**
   * Copy the block and reuse the buffer for compact() when the old block
   * is clean.
   */
  final PageLeafImpl copy(long sequence)
  {
    BlockLeaf []blocks = new BlockLeaf[_blocks.length];
    
    for (int i = 0; i < blocks.length; i++) {
      blocks[i] = _blocks[i].copy();
    }
    
    PageLeafImpl page = new PageLeafImpl(getId(), getNextId(), sequence,
                                         _table, _firstKey, _lastKey,
                                         blocks);
    
    if (_stub != null) {
      _stub.copyToCompact(page);
    }
    
    return page;
  }
  
  //
  // lru
  //

  @Override
  public void lruEvent()
  {
    TableKelp table = _table;
    
    Page page = _table.getPageActor().getPage(getId());
    
    if (this == page) {
      write(table, table.getPageActor(), table.getReadWrite());
    }
  }
  
  //
  // validation
  //
  
  /**
   * Validates the leaf blocks
   */
  void validate(TableKelp table)
  {
    if (! table.isValidate()) {
      return;
    }
    
    Row row = table.row();
    
    for (BlockLeaf block : _blocks) {
      block.validateBlock(row);
    }
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + getId()
            + ",seq=" + getSequence()
            + "," + Hex.toShortHex(_firstKey)
            + "," + Hex.toShortHex(_lastKey)
            + "]");
  }
  
  static class Split {
    private PageLeafImpl _first;
    private PageLeafImpl _rest;
    
    Split(PageLeafImpl first, PageLeafImpl rest)
    {
      _first = first;
      _rest = rest;
    }

    PageLeafImpl getFirstPage()
    {
      return _first;
    }

    PageLeafImpl getNextPage()
    {
      return _rest;
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _first + ", " + _rest + "]";
    }
  }
}
