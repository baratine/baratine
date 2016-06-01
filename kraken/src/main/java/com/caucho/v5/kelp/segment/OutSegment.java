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

import static com.caucho.v5.kelp.segment.SegmentServiceImpl.BLOCK_SIZE;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.baratine.InService;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.kelp.Page;
import com.caucho.v5.kelp.Page.Type;
import com.caucho.v5.kelp.PageServiceSync;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.TableWriterServiceImpl;
import com.caucho.v5.kelp.io.CompressorKelp;
import com.caucho.v5.store.io.OutStore;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;

/**
 * Writer to a database segment. The writer is owned by a single 
 * thread/service; it is not thread safe.
 * 
 * Segment structure looks like:
 * <code><pre>
 * [data] -> ... &lt;- [index] 
 * </pre></code>
 * 
 * Data grows from low to high, and the index grows from high to low.
 * 
 * Data must be fsynced before the index entry is written so index entries
 * are always valid even if written in a crash.
 */
public class OutSegment extends StreamImpl implements AutoCloseable
{
  private static final L10N L = new L10N(OutSegment.class);
  private static final Logger log
    = Logger.getLogger(OutSegment.class.getName());

  private static final int FOOTER_SIZE = 8;
  private static final int FOOTER_OFFSET = BLOCK_SIZE - FOOTER_SIZE;
  
  private TableWriterServiceImpl _readWrite;
  private final SegmentKelp _segment;
  
  private OutStore _sOut;
  private WriteStream _out;
  
  private int _position;
  
  private TempBuffer _indexTempBuf;
  private byte []_indexBuffer;
  private int _indexTail;
  
  private int _indexAddress;
  
  private int _lastFlushIndexAddress = -1;
  private int _lastFlushIndexTail;
  
  private byte []_footerBuffer = new byte[FOOTER_SIZE];
  
  private boolean _isDirty;
  
  private boolean _isClosed;
  
  private ArrayList<PendingEntry> _pendingFlushEntries = new ArrayList<>();
  private ArrayList<PendingEntry> _pendingFsyncEntries = new ArrayList<>();
  
  private ArrayList<SegmentFsyncCallback> _fsyncListeners
    = new ArrayList<>();
  
  private CompressorKelp _compressor;

  private TableKelp _table;
  //private PageServiceSync _tableService;

  /**
   * Creates a new store.
   *
   * @param database the owning database.
   * @param name the store name
   * @param lock the table lock
   * @param path the path to the files
   */
  public OutSegment(TableKelp table,
                    PageServiceSync tableService,
                    TableWriterServiceImpl readWrite,
                    SegmentKelp segment)
  {
    Objects.requireNonNull(table);
    Objects.requireNonNull(tableService);
    Objects.requireNonNull(readWrite);
    Objects.requireNonNull(segment);
    
    _table = table;
    //_tableService = tableService;
    _readWrite = readWrite;
    _segment = segment;
    
    if (! _segment.isWriting()) {
      throw new IllegalStateException(String.valueOf(_segment));
    }
    
    _indexTempBuf = TempBuffer.createLarge();
    _indexBuffer = _indexTempBuf.buffer();

    _indexAddress = _segment.length() - BLOCK_SIZE;
    fillHeader();
    
    _compressor = readWrite.compressor();
    
    _sOut = _readWrite.openWrite(segment.extent());
  }

  public SegmentKelp getSegment()
  {
    return _segment;
  }

  /**
   * Returns the segment's sequence number.
   */
  public long getSequence()
  {
    return _segment.getSequence();
  }

  /**
   * Returns the WriteStream for data in the current segment. Data writes
   * from low to high.
   */
  public WriteStream out()
  {
    if (_out == null) {
      _out = new WriteStream(this);
    }
    
    return _out;
  }
  
  public OutputStream outCompress()
    throws IOException
  {
    return _compressor.out(out());
  }

  public boolean isCompress()
  {
    return _compressor.isCompress();
  }
  
  @Override
  public void seekStart(long pos)
  {
    if (pos < 0 || _indexAddress < pos) {
      throw new IllegalStateException();
    }
    
    _position = (int) pos;
  }

  @Override
  public boolean canWrite()
  {
    return true;
  }
  
  @Override
  public int getAvailable()
  {
    return _indexAddress;
  }

  public void addFsyncCallback(SegmentFsyncCallback onFlush)
  {
    _fsyncListeners.add(onFlush);
  }
  
  /**
   * Writes the page to the segment.
   * 
   * @param page
   * @param oldSequence
   * @param saveLength
   * @param saveTail
   * @param saveSequence
   * @return
   */
  @InService(SegmentServiceImpl.class)
  public
  Page writePage(Page page,
                 long oldSequence,
                 int saveLength,
                 int saveTail,
                 int saveSequence,
                 Result<Integer> result)
  {
    if (isClosed()) {
      return null;
    }
    
    // Type type = page.getType();
    int pid = page.getId();
    int nextPid = page.getNextId();
    
    WriteStream out = out();
    
    int head = (int) out.position();
    
    try {
      int available = getAvailable();
      
      if (available < head + page.size()) {
        return null;
      }
      
      Page newPage = page.writeCheckpoint(_table, this, oldSequence,
                                          saveLength, saveTail, saveSequence);

      if (newPage == null) {
        return null;
      }
      
      newPage.setSequence(getSequence());
      
      out = out();
      
      int tail = (int) out.position();

      if (addIndex(out, page, newPage,
                   saveSequence, newPage.getLastWriteType(), 
                   pid, nextPid, head, tail - head,
                   result)) {
        return newPage;
      }
      else {
        return null;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * <pre>
   * data(0)
   * ...
   * data(n)
   * ...
   * entry(m)
   * entry(m-1)
   * ...
   * entry(0)
   * </pre>
   */
  boolean addIndex(WriteStream out,
                    Page page,
                    Page newPage,
                    int saveSequence,
                    Type type,
                    int pid,
                    int nextPid,
                    int pageOffset, 
                    int pageLength,
                    Result<Integer> result)
     throws IOException
  {
    int head;
    
    while ((head = _segment.writePageIndex(_indexBuffer, 
                                       _indexTail,
                                       type.ordinal(), pid, nextPid, 
                                       pageOffset, pageLength)) < 0) {
      writeIndexBlock();
      
      // isCont is true if the new page/entry fits in the segment
      boolean isCont = _position + 2 * BLOCK_SIZE <= _indexAddress;
      
      if (! isCont) {
        // close();

        return false;
      }
      
      _indexAddress -= BLOCK_SIZE;
      fillHeader();
    }
    
    _isDirty = true;
    _indexTail = head;
    
    int position = pageOffset; 
    
    //System.out.print(" [" + page.getId() + ",seq=" + page.getSequence() + "]");
    
    if (isClosed()) {
      System.out.println("FLUSH_AFTER_CLOSE");
    }
    
    _pendingFlushEntries.add(new PendingEntry(page, newPage, saveSequence,
                                              position,
                                              _indexAddress, head,
                                              result));
    
    return true;
  }
  
  //
  // flush - data is written to the mmap/os buffers (not necessarily to disk) 
  //
  
  /**
   * Flushes the buffered data to the segment.
   * 
   * After the flush, pending entries are notified, allowing page stubs to
   * replace buffered pages. Since the written data is now in the mmap, the
   * buffered page can be gc'ed and replaced with a stub read.
   */
  public void flushData()
  {
    if (isClosed()) {
      if (_pendingFlushEntries.size() > 0) {
        System.out.println("PENDING_FLUSH");
      }
      return;
    }

    WriteStream out = _out;
    
    if (out != null) {
      try {
        out.flush();
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }
    
    // flush entries to allow for stubs to replace buffers
    completePendingFlush();
  }

  /**
   * Writes to the file from the WriteStream flush.
   * 
   * After the write, a read from the mmap will succeed, but the headers
   * cannot be written until after the fsync.
   */
  @Override
  public void write(byte []buffer, int offset, int length,
                    boolean isEnd)
    throws IOException
  {
    int position = _position;
    
    if (length < 0) {
      throw new IllegalArgumentException();
    }
    
    if (_indexAddress < position + length) {
      throw new IllegalArgumentException(L.l("Segment write overflow pos=0x{0} len=0x{1} entry-head=0x{2} seg-len=0x{3}. {4}",
                                          Long.toHexString(_position),
                                          Long.toHexString(length),
                                          Long.toHexString(_indexAddress),
                                          Long.toHexString(_segment.length()),
                                          _segment));
    }
    

    //try (StoreWrite sOut = _readWrite.openWrite(_segment.getAddress(),
    //                                                _segment.getLength())) {
    
    _sOut.write(_segment.getAddress() + position, buffer, offset, length);

    _position = position + length;
      
    _isDirty = true;
  }

  /**
   * Notifies the calling service after the entry is written to the mmap.
   * 
   * The entries will be added to the pending sync list.
   */
  private void completePendingFlush()
  {
    int size = _pendingFlushEntries.size();
    
    if (size == 0) {
      return;
    }

    // ArrayList<TableService.PageFlush> pageList = new ArrayList<>();
    
    for (int i = 0; i < size; i++) {
      PendingEntry entry = _pendingFlushEntries.get(i);
      
      entry.afterFlush();
      
      _pendingFsyncEntries.add(entry);
    }
    
    // _tableService.afterDataFlush(pageList);
    
    _pendingFlushEntries.clear();
  }
  
  //
  // fsync - data is forced to disk
  //
  // sync is normally triggered by a blob write (because blobs aren't 
  // journalled) or by the segment closing.
  
  /**
   * Schedules an fsync and a notification when the fsync completes.
   * 
   * The fsync might be delayed for batching purposes
   */
  public void fsyncSchedule(Result<Boolean> result)
  {
    fsyncImpl(result, FsyncType.SCHEDULE);
  }
  
  /**
   * Requests an immediate fsync and a notification when the fsync completes.
   */
  public void fsync(Result<Boolean> result)
  {
    fsyncImpl(result, FsyncType.HEADER);
  }
  
  /**
   * Syncs the segment to the disk. After the segment's data is synced, the
   * headers can be written. A second sync is needed to complete the header
   * writes.
   */
  private void fsyncImpl(Result<Boolean> result, FsyncType fsyncType)
  {
    try {
      flushData();
      
      ArrayList<SegmentFsyncCallback> fsyncListeners
        = new ArrayList<>(_fsyncListeners);
      _fsyncListeners.clear();
      
      Result<Boolean> resultNext = result.then((v,r)->
        afterDataFsync(r, _position, fsyncType, fsyncListeners));
        
      if (_isDirty || ! fsyncType.isSchedule()) {
        _isDirty = false;
      
        try (OutStore sOut = _readWrite.openWrite(_segment.extent())) {
          if (fsyncType.isSchedule()) {
            sOut.fsyncSchedule(resultNext);
          }
          else {
            sOut.fsync(resultNext);
          }
        }
      }
      else {
        resultNext.ok(true);
      }
    } catch (Throwable e) {
      e.printStackTrace();
      result.fail(e);
    }
  }
  
  /**
   * Callback after the page data has been fynced, so the index can be
   * written.
   * 
   * The page write is split in two, so the index is always written after
   * the data is guaranteed to be flushed to disk.
   */
  private void afterDataFsync(Result<Boolean> result,
                              int position,
                              FsyncType fsyncType,
                              ArrayList<SegmentFsyncCallback> listeners)
  {
    try {
      completeIndex(position);
      
      Result<Boolean> cont = result.then((v,r)->
        afterIndexFsync(r, fsyncType, listeners));

      if (fsyncType.isSchedule()) {
        _sOut.fsyncSchedule(cont);
      }
      else {
        _sOut.fsync(cont);
        
      }
    } catch (Throwable e) {
      result.fail(e);
    }
  }
  
  /**
   * Callback after the index has been flushed. 
   */
  private void afterIndexFsync(Result<Boolean> result,
                               FsyncType fsyncType,
                               ArrayList<SegmentFsyncCallback> fsyncListeners)
  {
    try {
      // completePendingEntries(_position);
      if (fsyncType.isClose()) {
        _isClosed = true;
        _segment.finishWriting();

        if (_pendingFlushEntries.size() > 0 || _pendingFsyncEntries.size() > 0) {
          System.out.println("BROKEN_PEND: flush="
                             + _pendingFlushEntries.size()
                             + " fsync=" + _pendingFsyncEntries.size()
                             + " " + _pendingFlushEntries);
        }

        _readWrite.afterSequenceClose(_segment.getSequence());
      }

      for (SegmentFsyncCallback listener : _fsyncListeners) {
        listener.onFsync();
      }

      result.ok(true);
    } catch (Throwable exn) {
      result.fail(exn);
    }
  }
  
  final void completeIndex(int position)
  {
    int indexAddress = _lastFlushIndexAddress;
    int indexTail = _lastFlushIndexTail;
    int entryHead = indexTail;

    // PageService pageService = _db.getPageService();
    
    for (PendingEntry entry : _pendingFsyncEntries) {
      if (indexAddress != entry.address() && indexAddress > 0) {
        // since index block is full, fill the footer and write it
        fillFooter(_footerBuffer, 0, indexTail, true);
        
        writeIndex(indexAddress + FOOTER_OFFSET, 
                   _footerBuffer, 0, FOOTER_SIZE);
        entryHead = 0;
      }
      
      indexAddress = entry.address();
      indexTail = entry.tail();
      _isDirty = true;
      
      // entry.afterFlush();
    }
    
    // XXX: freeSegments
    
    _pendingFsyncEntries.clear();
    
    if (indexAddress < 0) {
    }
    else if (indexAddress == _indexAddress) {
      fillFooter(_indexBuffer, FOOTER_OFFSET, indexTail, false);

      writeIndex(indexAddress + entryHead,
                 _indexBuffer, 
                 entryHead, 
                 BLOCK_SIZE - entryHead);
    }
    else {
      fillFooter(_footerBuffer, 0, indexTail, false);
      
      writeIndex(indexAddress + FOOTER_OFFSET, 
                 _footerBuffer, 0, FOOTER_SIZE);
    }
    
    _lastFlushIndexAddress = indexAddress;
    _lastFlushIndexTail = indexTail;
  }
  
  /**
   * <pre>
   * u64 sequence
   * b32 key
   * </pre>
   */
  private void fillHeader()
  {
    byte[] buffer = _indexBuffer;
    
    Arrays.fill(buffer, (byte) 0x0);
    
    int index = 0;
    
    BitsUtil.writeLong(buffer, index, _segment.getSequence());
    index += 8;
    
    byte []tableKey = _table.tableKey();
    System.arraycopy(tableKey, 0, buffer, index, tableKey.length);
    index += tableKey.length;

    /*
    BitsUtil.writeInt16(buffer, index, tail);
    index += 2;
  
    buffer[index] = (byte) (isCont ? 1 : 0);
    index++;
    */
    
    _indexTail = index;
    
    fillFooter(buffer, FOOTER_OFFSET, index, false);
  }
  
  /**
   * <pre>
   * u8 type = 0
   * u8 isCont
   * </pre>
   */
  private int fillFooter(byte []buffer, 
                         int offset, 
                         int entryTail, 
                         boolean isCont)
  {
    BitsUtil.writeInt16(buffer, offset, entryTail);
    offset += 2;

    buffer[offset] = (byte) (isCont ? 1 : 0);
    offset++;
    
    return offset;
  }
  
  /**
   * Write the current header to the output.
   */
  private void writeIndexBlock()
  {
    writeIndex(_indexAddress, _indexBuffer, 0, _indexBuffer.length);
  }
  
  /**
   * Write the current header to the output.
   */
  private void writeIndex(int entryAddress,
                          byte []entryBuffer, int offset, int length)
  {
    long address = _segment.getAddress() + entryAddress;

    if (_segment.length() < entryAddress + length) {
      throw new IllegalStateException(L.l("offset=0x{0} length={1}", entryAddress, length));
    }
    //try (StoreWrite os = _readWrite.openWrite(address, BLOCK_SIZE)) {
    _sOut.write(address, entryBuffer, offset, length);
    
    _isDirty = true;
    // }
  }

  public void closeSchedule(Result<Boolean> result)
  {
    closeImpl(result, FsyncType.CLOSE_SCHEDULE);
  }

  public void closeFsync(Result<Boolean> result)
    throws IOException
  {
    closeImpl(result, FsyncType.CLOSE);
  }

  private void closeImpl(Result<Boolean> result, FsyncType fsyncType)
  {
    try {
      // XXX: sOut.close();
      
      if (! isClosed()) {
        flushData();
        
        fsyncImpl(result, fsyncType);
      }
      else if (result != null) {
        result.ok(true);
      }
    } catch (Throwable e) {
      result.fail(e);
    } finally {
      // _readWrite = null;
      _out = null;
      // _isClosed = true;
      
      // _segment.clearWriting();
    }
  }
  
  @Override
  public boolean isClosed()
  {
    return _isClosed;
  }
  
  @Override
  public void close()
    throws IOException
  {
    if (isClosed()) {
      return;
    }
    
    closeSchedule(Result.ignore());
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _segment + "]";
  }
  
  /**
   * The pending entry allows batching of segment writes, notifying the
   * calling page when the data has been written to disk.
   */
  private class PendingEntry {
    private final Page _page;
    private final Page _newPage;
    private final int _writeSequence;
    private final int _position;
    private final int _indexAddress;
    private final int _indexTail;
    private final Result<Integer> _result;
    
    PendingEntry(Page page, 
                 Page newPage,
                 int saveSequence,
                 int position,
                 int indexAddress, 
                 int indexTail,
                 Result<Integer> result)
    {
      _page = page;
      _newPage = newPage;
      _writeSequence = saveSequence;
      
      _position = position;
      
      _indexAddress = indexAddress;
      _indexTail = indexTail;
      
      _result = result;
    }
    
    public Page getPage()
    {
      return _newPage;
    }
    
    public int getSequenceFlush()
    {
      return _writeSequence;
    }
    
    public int tail()
    {
      return _indexTail;
    }

    public int address()
    {
      return _indexAddress;
    }
    
    void afterFlush()
    {
      _result.ok(_writeSequence);
      //_tableService.afterDataFlush(_page, _writeSequence);
      
      //_page.afterDataFlush(_table);
      //_page.setWrittenSequence(_saveSequence);
      
      // db/2576 - 
      //if (_page != null && _page != _newPage && _newPage != null) {
      //  _tableService.updatePage(_page, _newPage);
      //}
    }
    
    @Override
    public String toString()
    {
      return (getClass().getSimpleName() + "[" + _page
              + ",addr=" + Long.toHexString(address()) + "]");
    }
  }
  
  enum FsyncType {
    SCHEDULE {
      boolean isSchedule() { return true; }
    },
    HEADER,
    CLOSE_SCHEDULE {
      boolean isSchedule() { return true; }
      boolean isClose() { return true; }
    },
    CLOSE {
      boolean isClose() { return true; }
    };
    
    boolean isSchedule() { return false; }
    boolean isClose() { return false; }
  }
}
