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

import io.baratine.service.AfterBatch;
import io.baratine.service.Direct;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.baratine.InService;
import com.caucho.v5.db.journal.JournalStore;
import com.caucho.v5.db.journal.JournalStream;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.kelp.PageLeafImpl.Split;
import com.caucho.v5.kelp.PageServiceSync.GcContext;
import com.caucho.v5.kelp.PageServiceSync.PutType;
import com.caucho.v5.kelp.PageTree.SplitTree;
import com.caucho.v5.kelp.RangeIteratorKelp.PredicateTrue;
import com.caucho.v5.kelp.TableListener.TypePut;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.kelp.segment.SegmentStream;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.ConcurrentArrayList;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.LruCache;

/**
 * Actor responsible for managing the page tables.
 */
//@NonBlocking
@Service
public class PageServiceImpl implements PageService
{
  private static final L10N L = new L10N(PageServiceImpl.class);
  private static final Logger log = Logger.getLogger(PageServiceImpl.class.getName());
  
  private static final int ROOT_TREE = 1;
  private static final int ROOT_LEAF = 2;
  
  private final TableKelp _table;
  private final JournalKelpImpl _journal;
  
  private final PageMap<Page> _pages = new PageMap<>();
  // private final PageMap<PageBlob> _blobPages = new PageMap<>();
  
  private AtomicInteger _tailPid = new AtomicInteger();
  // private AtomicInteger _blobCounter = new AtomicInteger();
  
  private ConcurrentArrayList<TableListener> _listeners
    = new ConcurrentArrayList<>(TableListener.class);
  
  private final Deque<BlobFree> _freeBlobs
    = new ArrayDeque<>();
    
  private final AtomicBoolean _isCollecting = new AtomicBoolean();
  
  private int _rootPid = 1;

  private final RowCursor _workCursor;

  private JournalStore _jbs;
  private JournalStream _journalStream;
  
  private long _lastTime;
  private long _lastVersion;
  
  private Lifecycle _lifecycle = new Lifecycle();
  
  private LruCache<Integer,Page> _updateLru = new LruCache<>(1024);
  
  PageServiceImpl(TableKelp table,
                   JournalStore jbs)
  {
    Objects.requireNonNull(table);
    
    _table = table;
    
    _jbs = jbs;
    // XXX: should be table unique name
    _journalStream = _jbs.openJournalStream(table.getName());
    _journal = new JournalKelpImpl(_table, _journalStream);
    
    _workCursor = _table.cursor();
  }
  
  @Override
  public void start(Result<TableKelp> result)
  {
    loadImpl(result.of((t,r)->afterStart(r)));
  }
  
  private void loadImpl(Result<Void> result)
  {
    if (! _lifecycle.toActive()) {
      result.ok(null);
      return;
    }
    
    _table.getReadWriteActor().init(_table, this);
    
    validateAndRepair();
    _journal.replayJournal(this);
    
    collectFreeBlobs();
    
    result.ok(null);
  }
  
  private void afterStart(Result<TableKelp> result)
  {
    DatabaseServiceKelp database = _table.getDatabase().getDatabaseService();
    
    database.addTableImpl(_table.getName(), _table, 
                          result.of((t,r)->afterAddTable(t,r)));
  }
  
  private void afterAddTable(TableKelp table, Result<TableKelp> result)
  {
    if (_table != table) {
      result.ok(table);
      return;
    }

    checkpoint(result.of(v->_table));
  }
  
  public TableKelp getTable()
  {
    return _table;
  }
  
  @Direct
  public long getMemorySize()
  {
    int tail = _tailPid.get();
    
    long size = 0;
    
    for (int i = 0; i <= tail; i++) {
      Page page = _pages.get(i);
      
      if (page != null) {
        size += page.getSize();
      }
    }
    
    return size;
  }
  
  //
  // Service methods
  //
  
  @Direct
  @Override
  public void getDirect(RowCursor cursor, Result<Boolean> result)
  {
    boolean isValid = getImpl(cursor);
    
    result.ok(isValid);
  }
  
  /**
   * Non-peek get.
   */
  @Override
  public void getSafe(RowCursor cursor, Result<Boolean> result)
  {
    result.ok(getImpl(cursor));
  }
  
  private boolean getImpl(RowCursor cursor)
  {
    PageLeafImpl leaf = getLeafByCursor(cursor);
    
    if (leaf == null) {
      return false;
    }
    
    return leaf.get(cursor);
  }
  
  /**
   * For cluster calls, returns a stream to the new value if the value
   * has changed. 
   */
  @Direct
  @Override
  public void getStream(RowCursor cursor, 
                        Result<GetStreamResult> result)
  {
    long version = cursor.getVersion();
    
    PageLeafImpl leaf = getLeafByCursor(cursor);
    
    if (leaf == null) {
      result.ok(new GetStreamResult(false, null));
      return;
    }
    
    if (! leaf.get(cursor)) {
      result.ok(new GetStreamResult(false, null));
      return;
    }

    if (version == cursor.getVersion()) {
      result.ok(new GetStreamResult(true, null));
      return;
    }
    
    StreamSource ss = leaf.getStream(cursor, this);
    
    boolean isFound = ss != null;

    result.ok(new GetStreamResult(isFound, ss));
  }

  @Direct
  public PageLeafImpl getLeafByCursor(RowCursor cursor)
  {
    int pid = getTree(cursor, _rootPid);
    
    while (pid > 0) {
      PageLeafImpl leaf = loadLeaf(pid);
    
      if (cursor.compareKey(leaf.getMaxKey(), 0) <= 0) {
        return leaf;
      }
      
      pid = leaf.getNextId();
    }
    
    return null;
  }

  @Override
  public void put(RowCursor cursor,
                  PutType putType,
                  Result<Boolean> result)
  {
    updateVersion(cursor);
    
    _journal.put(cursor);
      
    putImpl(cursor, null, putType, result);
    
    // result.complete(true);
    
    if (_journal.isCheckpointRequired()) {
      checkpoint(Result.ignore());
    }
    
    collectImpl();
  }

  @Override
  public void putWithBackup(RowCursor cursor, 
                            PutType putType,
                            BackupKelp backupCb,
                            Result<? super Boolean> result)
  {
    updateVersion(cursor);
    
    _journal.put(cursor);
      
    putImpl(cursor, backupCb, putType, (Result) result);

    // result.complete(Boolean.TRUE);
    
    if (_journal.isCheckpointRequired()) {
      checkpoint(Result.ignore());
    }
    
    collectImpl();
    
    /*
    if (backupCb != null) {
      putBackup(cursor, backupCb);
    }
    */
  }
  
  @Override
  public void update(RowCursor min, 
                     RowCursor max, 
                     EnvKelp envKelp,
                     UpdateKelp update, 
                     @Service BackupKelp backup, 
                     Result<Integer> result)
  {
    int count = 0;

    RangeIteratorKelp iter = new RangeIteratorKelp(_table, min, max, envKelp);
    
    while (iter.hasNext()) {
      RowCursor cursor = iter.next();
      
      if (update.onRow(cursor, envKelp)) {
        count++;
        putWithBackup(cursor, PutType.PUT, backup, Result.ignore());
      }
    }

    result.ok(count);
  }
  
  @Override
  public void replace(RowCursor cursor,
                      EnvKelp envKelp,
                      UpdateKelp update, 
                      @Service BackupKelp backup, 
                      Result<Integer> result)
  {
    updateVersion(cursor);
    
    replaceImpl(cursor, envKelp, update, backup, result.of(x->1));
    
    // result.complete(1);
    
    if (_journal.isCheckpointRequired()) {
      checkpoint(Result.ignore());
    }
    
    collectImpl();
  }
  
  private void replaceImpl(RowCursor cursor,
                           EnvKelp envKelp,
                           UpdateKelp update, 
                           @Service BackupKelp backup,
                           Result<Boolean> result)
  {
    PutType putType = PutType.PUT;
    
    RowCursor workCursor = _table.cursor();

    // update version in case of foreign clock skew
    _lastVersion = Math.max(_lastVersion, cursor.getVersion());
    
    // int leafPid;
    
    int leafPid = getTree(cursor, _rootPid);
    
    if (leafPid <= 0) {
      throw new IllegalStateException("Missing leaf for " + String.valueOf(cursor) + " " + _rootPid);
    }

    PageLeafImpl leaf = loadLeafForUpdate(leafPid);

    if (leaf.compareTo(cursor) != 0) {
      // repair parent tree
      // If an updated split leaf is saved but the updated parent tree is not,
      // the loaded database needs to repair the parent tree.
      // System.out.println("REPAIR PARENT TREE: " + cursor);
      
      leaf = getLeafByCursor(cursor);
      
      if (leaf == null) {
        throw new IllegalStateException("Cursor " + cursor + " is not in leaf " + leaf
                                        + " parent " + getTreeParent(cursor.getKey(), 
                                                                     _rootPid,
                                                                     leafPid));
      }
    }
    
    PageLeafImpl newLeaf;
    
    workCursor.copyFrom(cursor);
    
    if (leaf.get(workCursor) && update.onRow(workCursor, envKelp)) {
      _journal.put(workCursor);
      
      RowCursor workCursor2 = _table.cursor();
      
      newLeaf = leaf.put(_table, this, workCursor, workCursor2,
                         backup, putType,
                         result);
    }
    else {
      _journal.put(cursor);
      
      newLeaf = leaf.put(_table, this, cursor, workCursor,
                         backup, putType,
                         result);
    }
    
    if (newLeaf == null) {
      // cursor.removeBlobs();
      return;
    }
    
    if (leaf != newLeaf) {
      if (! compareAndSetLeaf(leaf, newLeaf)) {
        throw new IllegalStateException("Leaf unable to compact " + leaf + " " + newLeaf);
      }
      
      PageLeafStub stub = (PageLeafStub) leaf.getStub();
      
      if (stub != null) {
        stub.copyToCompact(newLeaf);
      }
    }
    
    if (_table.getMaxNodeLength() < newLeaf.getSize()) {
      int parentPid = getTreeParent(cursor.getKey(), _rootPid, newLeaf.getId());
      
      splitPage(parentPid, newLeaf.getId());
    }
    
    //notifyOnPut(cursor, putType);
    
    //cursor.removeBlobs();
  }
  
  @Override
  public int writeBlob(int nextPid, 
                       StreamSource ss,
                       int offset,
                       int length)
  {
    try {
      int pid = allocateBlobPageId();

      PageBlob oldPage = getBlobPage(pid);

      long seq;
      if (oldPage != null) {
        seq = oldPage.getSequence();
      }
      else {
        seq = 0;
      }

      // int offset = len - blobPageSizeMax;

      PageBlobTemp page = new PageBlobTemp(pid, nextPid, seq, 
                                           ss.openChild(), 
                                           offset, length);

      page.setDirty();

      nextPid = pid;

      compareAndSetBlobPage(oldPage, page);

      page.clearDirty();

      int writeSequence = page.nextWriteSequence();
      _table.getReadWrite().writeBlobPage(page,
                                          writeSequence,
                                          Result.onOk(x->page.afterDataFlush(this, writeSequence)));

      return pid;
    } finally {
      // ss is opened by caller
      ss.close();
    }
  }

  @Override
  public void remove(RowCursor cursor, 
                     BackupKelp backup,
                     Result<Boolean> result)
  {
    updateVersion(cursor);
      
    _journal.remove(cursor);
      
    removeImpl(cursor, backup, result);
    
    if (_journal.isCheckpointRequired()) {
      checkpoint(Result.ignore());
    }
  }

  @Override
  public void removeRange(RowCursor min,
                          RowCursor max,
                          Predicate<RowCursor> predicate,
                          BackupKelp backup,
                          Result<Boolean> result)
  {
    if (predicate == null) {
      predicate = PredicateTrue.TRUE;
    }
    
    long version = nextVersion();
    
    try {
      Iterator<RowCursor> iter = new RangeIteratorKelp(_table, min, max, predicate);
      
      while (iter.hasNext()) {
        RowCursor cursor = iter.next();

        cursor.setVersion(version);
        
        _journal.remove(cursor);
      
        removeImpl(cursor, backup, Result.ignore());
      }
    } finally {
      result.ok(true);
    }
    
    if (_journal.isCheckpointRequired()) {
      checkpoint(Result.ignore());
    }
  }
  
  private void updateVersion(RowCursor cursor)
  {
    long version = cursor.getVersion();

    if (version == 0) {
      version = nextVersion();
      cursor.setVersion(version);
    }
  }

  public int getTailPid()
  {
    return _tailPid.get();
  }
  
  public boolean waitForComplete()
  {
    return true;
  }
  
  @Override
  public void flush(Result<Object> result)
  {
    result.ok(true);
  }

  /**
   * @return
   */
  boolean isStrictCollectRequired()
  {
    // return _pageLru.isStrictCollectRequired();
    return false;
  }

  /**
   * Returns the size in bytes of all pages with the given sequence.
   * 
   * Used by the page garbage-collector to determine which segments to
   * collect.
   */
  long getSequenceSize(SegmentKelp segment)
  {
    int tailPid = _tailPid.get();
    
    long size = 0;
    
    for (int i = 0; i <= tailPid; i++) {
      Page page = _pages.get(i);
      
      if (page != null && page.getSegment() == segment) {
        size += page.getSize();
      }
    }
    
    return size;
  }
  
  void checkCollect()
  {
    /*
    if (_pageLru.isCollectRequired()
        && _isCollecting.compareAndSet(false, true)) {
      _table.getPageGcService().collectPageMemory();
    }
    */
  }
  
  public void collect()
  {
    try {
      collectImpl();
    } finally {
      _isCollecting.set(false);
    }
  }
  
  private void collectImpl()
  {
    // _pageLru.collect(_pages, _tailPid.get());
  }

  public void writeBlobChunk(PageBlobImpl blobPage, 
                             long blobOffset,
                             TempBuffer tempBuffer, 
                             int length)
  {
    blobPage.writeChunk(blobOffset, tempBuffer, length);
  }

  @Direct
  public PageBlobImpl allocateBlobPage()
  {
    int blobId = allocateBlobPageId();
    
    return new PageBlobImpl(blobId, -1, getSequence());
  }

  @Direct
  public int allocateBlobPageId()
  {
    PageBlobFree freeBlob = null;
    
    synchronized (_freeBlobs) {
      BlobFree freeItem = _freeBlobs.peek();
      
      if (freeItem != null && freeItem.isAvailable()) {
        freeItem = _freeBlobs.poll();
        
        freeBlob = freeItem.getBlob();
      }

      // freeBlob = null;
      /*
      if (freeBlob != null && freeBlob.getId() == 27) {
        System.out.println("ALLOC: " + freeBlob);
      }
      */
    }
    
    int blobId;
    
    if (freeBlob != null) {
      blobId = freeBlob.getId();
    }
    else {
      blobId = _tailPid.incrementAndGet();
    }
    
    return blobId;
  }
  
  private long getSequence()
  {
    return 1;
  }

  @Direct
  public PageLeafImpl loadLeaf(int pid)
  {
    PageLeaf stub = getLeaf(pid);
      
    return stub.load(_table, this);
  }

  private PageLeafImpl loadLeafForUpdate(int pid)
  {
    PageLeaf stub;
    
    PageLeafImpl leaf;
    
    do {
      stub = getLeaf(pid);
      
      leaf = stub.load(_table, this);
    } while (stub != leaf && ! compareAndSetLeaf(stub, leaf));
    
    _updateLru.put(pid, leaf);

    return leaf;
  }

  @Direct
  public Page getPage(int pid)
  {
    return _pages.get(pid);
  }

  @Direct
  public PageLeaf getLeaf(int pid)
  {
    PageLeaf leaf = (PageLeaf) _pages.get(pid);
    
    return leaf;
  }
  
  @Direct
  public PageTree getTree(int pid)
  {
    return (PageTree) _pages.get(pid);
  }
  
  @Direct
  public PageTree loadTree(int pid)
  {
    return (PageTree) _pages.get(pid);
  }

  @Direct
  public PageBlob getBlobPage(int pid)
  {
    return (PageBlob) _pages.get(pid);
  }

  @Direct
  public void setBlobPage(PageBlob page)
  {
    int pid = page.getId();
    
    updateTailPid(pid);
    
    _pages.set(pid, page);
  }

  @Direct
  public void compareAndSetBlobPage(PageBlob oldPage, PageBlob page)
  {
    int pid = page.getId();
    
    updateTailPid(pid);
    
    _pages.compareAndSet(pid, oldPage, page);
  }
  
  //
  // listeners
  //
  
  /**
   * Adds a table listener.
   */
  public void addListener(@Service TableListener listener)
  {
    _listeners.add(listener);
  }
  
  /**
   * Removes a table listener.
   */
  public void removeListener(@Service TableListener listener)
  {
    _listeners.remove(listener);
  }
  
  
  //
  // checkpoint
  // 

  /**
   * Requests a checkpoint, flushing the in-memory data to disk.
   */
  @Override
  public void checkpoint(Result<Boolean> result)
  {
    if (! _journalStream.saveStart()) {
      result.ok(true);
      return;
    }

    TableWriterService readWrite = _table.getReadWrite();
    
    // long limit = Integer.MAX_VALUE; // 128
    int tailPid = _tailPid.get();
    for (int pid = 1; pid <= tailPid; pid++) {
      Page page = _pages.get(pid);

      if (page != null) {
        page.write(_table, this, readWrite);
      }
    }

    readWrite.fsync(new CheckpointResult(result));
  }
  
  public void writeGcEntries(long sequenceGc,
                             ArrayList<SegmentKelp> segmentList)
  {
    SegmentStream sOut = new SegmentStream();
    sOut.setGc(true);
    sOut.setSequence(sequenceGc);
    
    TableWriterService readWrite = _table.getReadWrite();
    
    for (SegmentKelp segment : segmentList) {
      writeGcSegment(sOut, segment);
      
      readWrite.addFsyncCallback(sOut, ()->readWrite.freeSegment(segment));
      // XXX: try to fsync previous to release entries early
      // _table.getReadWrite().fsyncSegment(cxt.getSegmentStream());
    }

    // XXX: ignore?
    readWrite.closeStream(sOut, Result.ignore());
    // sOut.closeFsync(null);
  }
  
  private void writeGcSegment(SegmentStream sOut,
                              SegmentKelp segment)
  {
    int tailPid = _tailPid.get();
    
    for (int pid = 1; pid <= tailPid; pid++) {
      Page page = _pages.get(pid);
      
      if (page != null
          && ! page.isDirty()
          && page.getSegment() == segment) {
        writeGcPage(page, sOut);
      }
    }
    
    // Callback needs to be in PageWriter to ensure all pages are written
    
  }
  
  public void writeGcPage(Page page, SegmentStream sOut)
  {
    Page srcPage;

    if (page.isDirty()) {
      return;
      //srcPage = page;
    }
    else if (page instanceof PageLeaf) {
      srcPage = ((PageLeaf) page).load(_table, this);
    }
    else if (page instanceof PageBlob) {
      srcPage = page;
    }
    else {
      return;
    }
    
    srcPage.write(_table, this, sOut, 0);
  }

  public boolean closeGcContext(GcContext cxt)
  {
    cxt.close();
    
    return true;
  }
  
  //
  // implementation methods
  //
  
  void addLoadedPage(Page page)
  {
    int pid = (int) page.getId();
    
    updateTailPid(pid);

    _pages.set(pid, page);
    
    /*
    if (page.isLeaf()) {
      _pageLru.addSize(page.getSize());
    }
    */
  }
  
  private void updateTailPid(int pid)
  {
    while (_tailPid.get() < pid) {
      _tailPid.set(pid);
    }
  }

  /*
  boolean setLeaf(PageLeaf oldLeaf, PageLeaf leaf)
  {
    if (oldLeaf == leaf) {
      return true;
    }
    
    leaf.updateLruSequence(_pageLru);
    
    return compareAndSetLeaf(oldLeaf, leaf);
  }
  */
  
  /**
   * After the checkpoint is written to mmap/file, the stub is valid 
   * for reloading.
   */
  @InService(PageServiceSync.class)
  public void afterDataFlush(Page newPage, int sequenceFlush)
  {
    Page page = _pages.get(newPage.getId());
    
    if (page == newPage) {
      page.afterDataFlush(this, sequenceFlush);
    }
    else {
      System.out.println("AfterDataFlush mismatch: " + page + " " + newPage);
    }
  }

  @InService(PageServiceImpl.class)
  boolean compareAndSetPage(Page page, Page newPage)
  {
    if (newPage.isLeaf()) {
      return compareAndSetLeaf(page, newPage);
    }
    else if (newPage.isBlob()) {
      return compareAndSetBlob(page, newPage);
    }
    else {
      return true;
    }
  }
  
  /**
   * Updates the leaf to a new page.
   * Called only from TableService. 
   */
  boolean compareAndSetLeaf(Page oldPage, Page page)
  {
    if (oldPage == page) {
      return true;
    }
    
    int pid = (int) page.getId();
    
    updateTailPid(pid);
    
    if (oldPage instanceof PageLeafImpl && page instanceof PageLeafImpl) {
      PageLeafImpl oldLeaf = (PageLeafImpl) oldPage;
      PageLeafImpl newLeaf = (PageLeafImpl) page;
      
      if (BlockTree.compareKey(oldLeaf.getMaxKey(), newLeaf.getMaxKey(), 0) < 0) {
        System.err.println(" DERP: " + oldPage + " " + page);
        Thread.dumpStack();
        /*
        throw new IllegalStateException("DERP: old=" + oldPage + " " + page
                                        + " old-dirt:" + oldPage.isDirty());
                                        */
      }
    }
    
    boolean result = _pages.compareAndSet(pid, oldPage, page);
    
    return result;
  }
  
  boolean compareAndSetBlob(Page oldPage, Page page)
  {
    if (oldPage == page) {
      return true;
    }
    
    int pid = (int) page.getId();
    
    updateTailPid(pid);
    
    if (_pages.compareAndSet(pid, oldPage, page)) {
      return true;
    }
    else {
      return false;
    }
  }

  int nextPid()
  {
    int pid = _tailPid.incrementAndGet();

    return pid;
  }
  
  int nextTreePid(int treePid)
  {
    for (; treePid <= _tailPid.get(); treePid++) {
      if (_pages.get(treePid) == null) {
        return treePid;
      }
    }
    
    return _tailPid.incrementAndGet();
  }
  
  private int getTree(RowCursor cursor, int pid)
  {
    return getTree(cursor.getBuffer(), _table.getKeyOffset(), pid);
  }
  
  private int getTree(byte []rowBuffer, int keyOffset, int pid)
  {
    while (pid > 0) {
      Page page = _pages.get(pid);

      if (page == null) {
        throw new IllegalStateException(L.l("unknown page {0}", pid));
      }
      
      if (page.isLeaf()) {
        return pid;
      }
      
      if (! page.isTree()) {
        throw new IllegalStateException(String.valueOf(page));
      }
      
      PageTree treePage = (PageTree) page;
      
      while (true) {
        int nextPid = treePage.get(_table.getRow(), rowBuffer, keyOffset);
        /*
        System.out.println("NEXT: next:" + nextPid + " pid:" + pid
                           + " " + Hex.toShortHex(rowBuffer, keyOffset, _db.getRow().getKeyLength())
                           + " " + treePage);
                           */
        if (nextPid > 0) {
          pid = nextPid;
          
          break;
        }
        else {
          //System.out.println("GETT: " + cursor + " " + pid + " " + treePage);
        }
        
        int nextTreePid = treePage.getNextId();

        if (nextTreePid <= 0) {
          //System.out.println("GETT-F: " + cursor + " " + pid);
          return -1;
        }
        
        treePage = (PageTree) _pages.get(nextTreePid);
      }
    }
    
    return -1;
  }
  
  private int getTreeParent(byte []key,
                            int pid,
                            int childPid)
  {
    int parentPid = pid;

    while (pid > 0) {
      if (pid == childPid) {
        return parentPid;
      }
      
      Page page = _pages.get(pid);
      
      if (page.isLeaf()) {
        return parentPid;
      }
      
      if (! page.isTree()) {
        throw new IllegalStateException();
      }
      
      PageTree treePage = (PageTree) page;
      
      parentPid = pid;
      
      while (true) {
        int nextPid = treePage.get(key);

        if (nextPid > 0) {
          pid = nextPid;
          
          break;
        }
        
        int nextTreePid = treePage.getNextId();

        if (nextTreePid <= 0) {
          return -1;
        }
        
        treePage = (PageTree) _pages.get(nextTreePid);
      }
    }
    
    return -1;
  }
  
  /*
  private boolean getLeafRow(RowCursor cursor, int pid)
  {
    while (pid > 0) {
      PageLeafImpl page = loadLeaf(pid);
      
      if (page == null) {
        return false;
      }
      else if (page.get(cursor)) {
        return true;
      }
      else if (page.compareTo(cursor) < 0) {
        return false;
      }
    
      pid = (int) page.getNextId();
    }
    
    return false;
  }
  */

  private void putImpl(RowCursor cursor,
                       BackupKelp backupCallback,
                       PutType putType,
                       Result<Boolean> result)
  {
    RowCursor workCursor = _table.cursor();

    // update version in case of foreign clock skew
    _lastVersion = Math.max(_lastVersion, cursor.getVersion());
    
    // int leafPid;
    
    int leafPid = getTree(cursor, _rootPid);
    
    if (leafPid <= 0) {
      throw new IllegalStateException("Missing leaf for " + String.valueOf(cursor) + " " + _rootPid);
    }

    PageLeafImpl leaf = loadLeafForUpdate(leafPid);

    if (leaf.compareTo(cursor) != 0) {
      // repair parent tree
      // If an updated split leaf is saved but the updated parent tree is not,
      // the loaded database needs to repair the parent tree.
      // System.out.println("REPAIR PARENT TREE: " + cursor);
      
      leaf = getLeafByCursor(cursor);
      
      if (leaf == null) {
        throw new IllegalStateException("Cursor " + cursor + " is not in leaf " + leaf
                                        + " parent " + getTreeParent(cursor.getKey(), 
                                                                     _rootPid,
                                                                     leafPid));
      }
    }

    PageLeafImpl newLeaf = leaf.put(_table, this, cursor, workCursor,
                                    backupCallback, putType,
                                    result);
    
    if (newLeaf == null) {
      //cursor.freeBlobs();
      return;
    }
    
    if (leaf != newLeaf) {
      if (! compareAndSetLeaf(leaf, newLeaf)) {
        throw new IllegalStateException("Leaf unable to compact " + leaf + " " + newLeaf);
      }
      
      PageLeafStub stub = (PageLeafStub) leaf.getStub();
      
      if (stub != null) {
        stub.copyToCompact(newLeaf);
      }
    }
    
    if (_table.getMaxNodeLength() < newLeaf.getSize()) {
      int parentPid = getTreeParent(cursor.getKey(), _rootPid, newLeaf.getId());
      
      splitPage(parentPid, newLeaf.getId());
    }
    
    //notifyOnPut(cursor, putType);
    
    //cursor.freeBlobs();
  }
  
  void notifyOnPut(RowCursor cursor, PutType putType)
  {
    TypePut type;
    
    switch (putType) {
    case LOAD:
      return;
      
    case REPLAY:
      type = TypePut.LOCAL;
      break;

    default:
      type = TypePut.REMOTE;
      break;
    }
    
    for (TableListener listener : _listeners.toArray()) {
      /*
      if (cursor.getKey()[0] == 0) {
        System.err.println("NULLK: " + cursor + " " + this);
        Thread.dumpStack();
      }
      */

      listener.onPut(cursor.getKey(), type);
    }
  }

  void backup(byte[] rowBuffer, int rowOffset, 
              BackupKelp backupCallback,
              Result<Boolean> result)
  {
    Row row = _table.getRow();
    
    int keyLen = row.getKeyLength();
    
    byte []key = new byte[keyLen];
    System.arraycopy(rowBuffer, rowOffset + row.getKeyOffset(),
                     key, 0, keyLen);

    backupCallback.onPut(_table.getTableKey(), key, 
                         toStream(rowBuffer, rowOffset, rowBuffer),
                         result);
  }

  void backupRemove(byte[] buffer, int rowOffset, long version,
                    BackupKelp backupCallback,
                    Result<? super Boolean> result)
  {
    Row row = _table.getRow();
    
    int keyLen = row.getKeyLength();
    
    byte []key = new byte[keyLen];
    System.arraycopy(buffer, rowOffset + ColumnState.LENGTH,
                     key, 0, keyLen);
    //System.out.println("BAK: " + Hex.toHex(buffer, rowOffset, row.getLength()));
    backupCallback.onRemove(_table.getTableKey(), key, version, result);
  }

  /*
  private StreamSource toStreamOld(byte[] buffer, int rowOffset, byte []blobBuffer)
  {
    try {
      TempStore tempStore = _table.getTempStore();
      
      TempWriter tos = tempStore.openWriter();
    
      Row row = _table.getRow();
      
      row.writeStream(tos, buffer, rowOffset, blobBuffer, this);
      
      if (tos.getLength() <= 0) {
        Thread.dumpStack();
      }
      StreamSource source = tos.getStreamSource();
      
      return source;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  */

  StreamSource toStream(byte[] buffer, int rowOffset, byte []blobBuffer)
  {
    Row row = _table.getRow();
    
    return new RowStreamSource(row.getInSkeleton(), buffer, rowOffset,
                               _table.getPageActor());
  }

  void removeImpl(RowCursor cursor,
                  BackupKelp backup,
                  Result<? super Boolean> result)
  {
    RowCursor workCursor = _workCursor;
    
    /*
    try {
      cursor.setRemoveState();
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
    */

    PageLeafImpl oldPage = getLeafByCursor(cursor);
    
    if (oldPage == null) {
      result.ok(false);
      return;
    }
    
    Page stubPage = getPage(oldPage.getId());
    
    // update version in case of foreign clock skew
    _lastVersion = Math.max(_lastVersion, cursor.getVersion());
    
    PageLeafImpl newPage = oldPage.remove(_table, this, 
                                          cursor, // workCursor,
                                          backup,
                                          result);
    
    compareAndSetLeaf(stubPage, newPage);
    // _pages.compareAndSet(newPage.getId(), oldPage, newPage);
    
    TypePut type;
    if (backup != null) {
      type = TypePut.LOCAL;
    }
    else {
      type = TypePut.REMOTE;
    }
    
    for (TableListener listener : _listeners.toArray()) {
      // XXX: remove
      listener.onRemove(cursor.getKey(), type);
    }
  }
  
  private void collectFreeBlobs()
  {
    for (int i = 1; i < _tailPid.get(); i++) {
      Page page = _pages.get(i);
      
      if (page instanceof PageBlobFree) {
        synchronized (_freeBlobs) {
          _freeBlobs.add(new BlobFree((PageBlobFree) page, 0));
        }
      }
    }
  }

  public void freeBlob(int blobId)
  {
    PageBlob oldBlob = getBlobPage(blobId);

    long sequence = Math.max(getSequence(), oldBlob.getSequence());
    
    PageBlobFree freeBlob = new PageBlobFree(blobId, -1, sequence, oldBlob);
    freeBlob.setDirty();
    
    /*
    Page page = _pages.get(blobId);
    
    if (page instanceof PageBlobFree) {
      Thread.dumpStack();
    }
    */
    
    // XXX: possibly have a weak link to the old page for load timing
    setBlobPage(freeBlob);
    
    synchronized (_freeBlobs) {
      /*
      if (freeBlob.getId() == 27) {
        System.out.println("FREE: " + freeBlob);
      }
      */
      _freeBlobs.add(new BlobFree(freeBlob));
    }
  }
  
  void addLruSize(int size)
  {
    // _pageLru.addSize(size);
  }

  private void splitPage(int treePid, int leafPid)
  {
    PageLeafImpl page;
    PageLeafImpl newPage;
    
    Split split;
    
    page = loadLeaf(leafPid);
    
    Page stub = _pages.get(leafPid);
      
    if (page == stub) {
    }
    else if (stub instanceof PageLeafStub) {
      compareAndSetLeaf(stub, page);
    }
    else {
      System.out.println("Unexpected page type: " + page + " " + _pages.get(leafPid));
    }
    
    if (page.getSize() < _table.getMaxNodeLength()) {
      // _pages.set(nextPid, null);
      return;
    }
      
    split = page.split(_table, this);

    newPage = split.getFirstPage();
    PageLeafImpl nextPage = split.getNextPage();
    
    if (nextPage == null) {
      // the split compacted the page
      if (! compareAndSetLeaf(page, newPage)) {
        System.out.println("UNEXPECTED_UPDATE: " + page + " " + newPage);
      }
      return;
    }

    // System.out.println("TREE: " + treePid + " " + _pages.get(treePid));
    // addLruSize(nextPage.getSize());
    PageTree treePage = (PageTree) _pages.get(treePid);
    
    // order matters because lookups are async
    // the new page must be added first to ensure the full range is always
    // available.
    
    if (! compareAndSetLeaf(null, nextPage)) {
      System.out.println("UNEXPECTED_UPDATE2: " + page + " " + nextPage);
    }
    
    treePage.insert(nextPage.getMinKey(), 
                    nextPage.getMaxKey(), 
                    nextPage.getId());
    
    if (! compareAndSetLeaf(page, newPage)) {
      Page currentPage = _pages.get(page.getId());
      
      throw new IllegalStateException(L.l("Unexpected split change page={0} old={1} new={2}",
                                          currentPage,
                                          page,
                                          newPage));
    }
    
    treePage.insert(newPage.getMinKey(), 
                    newPage.getMaxKey(),
                    newPage.getId());
    
    treePage.remove(page.getMinKey(), page.getMaxKey());
    
    // force write of new leaf, saving it before the updated tree to protect 
    // against a saved tree pointing to nowhere.
    nextPage.write(_table, this, 0);
    newPage.write(_table, this, 0);
    
    splitTreePage(treePage);
  }
  
  private void splitTreePage(PageTree treePage)
  {
    if (treePage.getSize() <= _table.getMaxNodeLength()
        && treePage.getDeltaTreeCount(_table.getRow()) < _table.getDeltaTreeMax()) {
      
      // treePage.write(_db, this, 0);
      return;
    }
    
    treePage = treePage.compact(_table);
    //System.out.println("COMPACT: " + treePage);
    if (treePage.getSize() <= _table.getMaxNodeLength()) {
      _pages.set(treePage.getId(), treePage);
      // treePage.write(_db, this, 0);
      return;
    }
    
    if (treePage.getId() == _rootPid) {
      treePage = splitRoot(treePage);
    }
    
    SplitTree split = treePage.split(_table, this);

    if (split != null) {
      PageTree first = split.getFirst();
      PageTree rest = split.getRest();

      first.setDirty();
      rest.setDirty();
      
      _pages.set(rest.getId(), rest);
      _pages.set(first.getId(), first);
      
      int parentPid = getTreeParent(first.getMinKey(), _rootPid, first.getId());
      
      PageTree treeParent = (PageTree) _pages.get(parentPid);
      
      treeParent.remove(first.getMinKey(), rest.getMaxKey());
      treeParent.insert(first.getMinKey(), first.getMaxKey(), first.getId());
      treeParent.insert(rest.getMinKey(), rest.getMaxKey(), rest.getId());
      
      // force write of new tree, saving it before the updated parent to protect 
      // against a saved tree pointing to nowhere.
      // XXX: repair instead?
      // rest.write(_db, this, 0);
      // first.write(_db, this, 0);
      
      splitTreePage(treeParent);
      // XXX: check for split
      // splitTreePage(parentPid);
      // parentPid = getTreeParent(first.getMinKey(), _rootPid, first.getId());
      // treeParent = (PageTree) _pages.get(parentPid);
    }
  }
  
  private PageTree splitRoot(PageTree rootPage)
  {
    int newPid = nextPid();
    
    PageTree newHead = rootPage.copy(_table, newPid);
    newHead.setDirty();
    
    PageTree newRoot = new PageTree(rootPage.getId(), -1,
                                    rootPage.getSequence(),
                                    rootPage.getMinKey(), 
                                    rootPage.getMaxKey());
    
    newRoot.insert(newHead.getMinKey(), newHead.getMaxKey(), newPid);
    newRoot.setDirty();
    
    _pages.set(newPid, newHead);
    _pages.set(rootPage.getId(), newRoot);
    
    return newHead;
  }
  
  //
  // journal replay
  //

  public void replayJournalPut(RowCursor cursor)
  {
    putImpl(cursor, null, PutType.REPLAY, Result.ignore());
  }

  public void replayJournalRemove(RowCursor cursor)
  {
    cursor.setRemoveState();
    
    // XXX: backup?
    removeImpl(cursor, null, Result.ignore());
  }
  
  @AfterBatch
  public void afterBatch()
  {
    _journal.flush();
    
    // collectImpl();
  }
  
  @Override
  public void close(Result<Boolean> result)
  {
    _journal.flush();
    
    _journalStream.complete();
    _journalStream.close();

    // _jbs.close();
    
    result.ok(true);
  }
  
  //
  // validation
  //
  
  private void validateAndRepair()
  {
    Row row = _table.getRow();
    int keyLength = row.getKeyLength();
    
    byte []minKey = new byte[keyLength];
    byte []maxKey = new byte[keyLength];
    
    Row.decrementKey(maxKey);
    
    // byte []workKey = new byte[keyLength];
    
    PageLeaf prev = null;
    
    BlockTree []blocks = new BlockTree[] { new BlockTree(1) };
    PageTree root = new PageTree(1, -1, 1, Row.copyKey(minKey), maxKey, blocks);
    //root.insert(minKey, maxKey, 1);
    root.setDirty();
    
    TreeBuilder treeBuilder = new TreeBuilder();
    
    _pages.set(1, root);
    
    // boolean isTreeValid = true;
    int pid = ROOT_LEAF;
    
    while (pid > 0) {
      Page page = _pages.get(pid);
      
      if (! (page instanceof PageLeaf)) {
        System.out.println("Missing page: " + page + " " + pid);
        break;
      }
      
      PageLeaf leaf = (PageLeaf) page;

      leaf.loadKeys(_table);
      
      int cmp = Row.compareKey(leaf.getMinKey(), minKey);
      
      if (cmp == 0) {
      }
      else if (cmp < 0) {
        //isTreeValid = false;
        System.out.println("Broken pages: " + prev + " " + leaf);
        break;
      }
      else if (prev != null) {
        //isTreeValid = false;
        System.out.println("EXTEND_PREV: " + prev);
      }
      else {
        //isTreeValid = false;
        System.out.println("EXTEND_SELF: " + leaf
                           + " " + Hex.toShortHex(minKey)
                           + " " + Hex.toShortHex(leaf.getMinKey()));
        
        PageLeafImpl oldLeaf = leaf.load(_table, this);
        
        PageLeafImpl newLeaf = new PageLeafImpl(oldLeaf.getId(), 
                                                oldLeaf.getNextId(),
                                                oldLeaf.getSequence(),
                                                _table,
                                                Row.copyKey(minKey),
                                                oldLeaf.getMaxKey(),
                                                oldLeaf.getBlocks());
        
        newLeaf.setDirty();
        
        _pages.set(newLeaf.getId(), newLeaf);
        
        leaf = newLeaf;
      }
      
      treeBuilder.insert(leaf.getMaxKey(), leaf.getId());
      /*
      insertRebuildTree(leaf.getMinKey(), leaf.getMaxKey(), leaf.getId(),
                        workKey, maxKey);
                        */

      /*
      if (isTreeValid) {
        try {
          int leafPid = getTree(leaf.getMinKey(), 0, _rootPid);
        
          if (leafPid != leaf.getId()) {
            System.out.println("INVALID_LEAF_MIN: " + leafPid + " " + leaf);
            isTreeValid = false;
          }
        } catch (Exception e) {
          log.fine(e.toString());
          log.log(Level.FINER, e.toString(), e);
          
          isTreeValid = false;
        }
      }
      
      if (isTreeValid) {
        try {
          int leafPid = getTree(leaf.getMaxKey(), 0, _rootPid);
        
          if (leafPid != leaf.getId()) {
            System.out.println("INVALID_LEAF_MAX: " + leafPid + " " + leaf);
            isTreeValid = false;
          }
        } catch (Exception e) {
          log.fine(e.toString());
          log.log(Level.FINER, e.toString(), e);
        }
      }
      */
      
      prev = leaf;
      
      pid = leaf.getNextId();
      
      System.arraycopy(leaf.getMaxKey(), 0, minKey, 0, minKey.length);
      
      Row.incrementKey(minKey);
    }
    
    //byte []maxKey = new byte[keyLength];
    //Row.decrementKey(maxKey);
    
    if (pid > 0) {
      System.out.println("REPAIR_TAIL:");
      //isTreeValid = false;
    }
    
    if (prev == null) {
      System.
      out.println("MISSING:");
      return;
    }
    
    int cmp = Row.compareKey(prev.getMaxKey(), maxKey);
    
    if (cmp < 0) {
      // extend prev
      System.out.println("REBUILD_TAIL: " + prev + " " + Hex.toShortHex(prev.getMaxKey()) 
                         + " " + Hex.toShortHex(maxKey));
      
      PageLeafImpl oldLeaf = prev.load(_table, this);
      
      PageLeafImpl newLeaf = new PageLeafImpl(oldLeaf.getId(), 
                                              oldLeaf.getNextId(),
                                              oldLeaf.getSequence(),
                                              _table,
                                              oldLeaf.getMinKey(),
                                              Row.copyKey(maxKey),
                                              oldLeaf.getBlocks());
      
      newLeaf.setDirty();
      
      _pages.set(newLeaf.getId(), newLeaf);
      
      treeBuilder.insert(newLeaf.getMaxKey(), newLeaf.getId());
    }
    
    treeBuilder.finish();
  }
  
  public void freeSegment(SegmentKelp segment)
  {
    validateFree(segment);
    
    _table.getReadWrite().freeSegment(segment);
  }

  void validateFree(SegmentKelp segment)
  {
    int pageTail = _tailPid.get();
    
    for (int i = 0; i <= pageTail; i++) {
      Page page = _pages.get(i);
      
      if (page != null) {
        page.validateFree(segment);
      }
    }
  }

  void readItem(InputStream is)
  {
    // _db.replay(_workCursor, is);
  }
  
  private long nextVersion()
  {
    long now = CurrentTime.getCurrentTime();
    
    long version = _lastVersion;

    long versionTime = (now << ColumnState.VERSION_TIME_SHIFT) & ((1L << 63) - 1);
    
    version = Math.max(version + 1, versionTime);
    
    _lastTime = now;
    _lastVersion = version;
    
    return version;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _table.getName() + "]";
  }
  
  private static class PageMap<X extends Page> {
    private final int _chunkSize = 1024;
    
    private AtomicReferenceArray<X> []_chunks;
    
    PageMap()
    {
      _chunks = new AtomicReferenceArray[1];
      _chunks[0] = new AtomicReferenceArray<>(_chunkSize);
    }
    
    X get(int pid)
    {
      int chunkIndex = pid / _chunkSize;
      int chunkOffset = pid % _chunkSize;
      
      AtomicReferenceArray<X> []chunks = _chunks;
      
      if (chunks.length <= chunkIndex) {
        return null;
      }
      
      AtomicReferenceArray<X> chunk = chunks[chunkIndex];
      
      return chunk.get(chunkOffset);
    }
    
    void set(int pid, X page)
    {
      int chunkIndex = pid / _chunkSize;
      int chunkOffset = pid % _chunkSize;
      
      while (_chunks.length <= chunkIndex) {
        extend();
      }
      
      AtomicReferenceArray<X> chunk = _chunks[chunkIndex];
      
      X oldPage = chunk.get(chunkOffset);
      
      if (oldPage != null && page.getSequence() < oldPage.getSequence()) {
        System.err.println("Invalid page set:"
            + " old:" + oldPage + " new:" + page
            + " " + ServiceRef.current());
        
        if (log.isLoggable(Level.FINE)) {
          Thread.dumpStack();
        }
      }
      
      chunk.set(chunkOffset, page);
    }
    
    boolean compareAndSet(int pid, X oldPage, X newPage)
    {
      int chunkIndex = pid / _chunkSize;
      int chunkOffset = pid % _chunkSize;
      
      while (_chunks.length <= chunkIndex) {
        extend();
      }
      
      AtomicReferenceArray<X> chunk = _chunks[chunkIndex];
      
      boolean isUpdate = chunk.compareAndSet(chunkOffset, oldPage, newPage);
      
      if (isUpdate
          && oldPage != null
          && newPage.getSequence() < oldPage.getSequence()) {
        // because of timing of writes, this can be valid when the old page
        // gets an updated write completion (async) during a split
        
        if (newPage.isDirty()) {
          newPage.setSequence(oldPage.getSequence());
        }
        else if (newPage.getSequence() < oldPage.getSequence()) {
          System.err.println("Unexpected sequence update for page compare-and-set: "
              + " old:" + oldPage + " new:" + newPage
              + " (" + oldPage.getSequence() + "," + newPage.getSequence() + ") "
              + ServiceRef.current());
          if (log.isLoggable(Level.FINE)) {
            Thread.dumpStack();
          }
        }
        
        if (log.isLoggable(Level.FINE)) {
          log.fine("Updated sequence for page compare-and-set: "
              + " old:" + oldPage + " new:" + newPage
              + " " + ServiceRef.current());
        }
      }

      return isUpdate;
    }
    
    private void extend()
    {
      AtomicReferenceArray<X> []chunks = _chunks;
      
      AtomicReferenceArray<X> []newChunks
        = new AtomicReferenceArray[chunks.length + 1];
      
      System.arraycopy(chunks, 0, newChunks, 0, chunks.length);
      
      newChunks[chunks.length] = new AtomicReferenceArray<>(_chunkSize);
      
      _chunks = newChunks;
    }
  }
  
  private class CheckpointResult implements Result<Boolean> {
    private final Result<Boolean> _result;
    
    private int _pageStart;
    private int _pageComplete;
    private boolean _isPageSent;
    
    CheckpointResult(Result<Boolean> result)
    {
      Objects.requireNonNull(result);
      
      _result = result;
    }
    
    void startPage()
    {
      _pageStart++;
    }
    
    void writePage()
    {
      _pageComplete++;
    }
    
    @Override
    public void handle(Boolean value, Throwable exn)
    {
      try {
        _journalStream.saveEnd();
      
        // collectImpl();

        if (exn != null) {
          _result.fail(exn);
        }
        else {
          _result.ok(true);
        }
      } catch (Throwable e) {
        _result.fail(e);
      }
    }
  }
  
  private class BlobFree {
    private final PageBlobFree _blob;
    private final long _timeAvailable;
    
    BlobFree(PageBlobFree blob,
             long timeout)
    {
      Objects.requireNonNull(blob);
      
      _blob = blob;
      _timeAvailable = CurrentTime.getCurrentTime() + timeout;
    }
    
    BlobFree(PageBlobFree blob)
    {
      this(blob, 60000L);
    }

    /**
     * @return
     */
    public boolean isAvailable()
    {
      return _timeAvailable < CurrentTime.getCurrentTime();
    }

    /**
     * @return
     */
    public PageBlobFree getBlob()
    {
      return _blob;
    }
  }
  
  private class TreeBuilder {
    private ArrayList<PageTree> _pageList = new ArrayList<>();
    
    private ArrayList<BlockTree> _blockList = new ArrayList<>();
    private int _treePid;
    private BlockTree _block;
    private byte []_minKey;
    private byte []_maxKey;
    private byte []_nextKey;
    private int _maxBlockLength;
    
    TreeBuilder()
    {
      _minKey = new byte[_table.getKeyLength()];
      _nextKey = new byte[_minKey.length];
      _maxKey = new byte[_minKey.length];
      
      _treePid = nextPid();
      _block = new BlockTree(_treePid);
      
      _maxBlockLength = Math.max(1, _table.getMaxNodeLength() / 2);
    }
    
    void insert(byte []nextMaxKey, int pidLeaf)
    {
      if (! _block.insert(_nextKey, nextMaxKey, pidLeaf)) {
        _block.toSorted(_table.getRow());
        _blockList.add(_block);
        
        if (_maxBlockLength <= _blockList.size()) {
          flushPage();
          nextPage();
        }
        
        _block = new BlockTree(_treePid);
        _block.insert(_maxKey, nextMaxKey, pidLeaf);
      }
      
      System.arraycopy(nextMaxKey, 0, _maxKey, 0, _maxKey.length);
      
      System.arraycopy(nextMaxKey, 0, _nextKey, 0, _maxKey.length);
      Row.incrementKey(_nextKey);
    }
    
    void flushPage()
    {
      if (_block != null) {
        _blockList.add(_block);
        _block = null;
      }
      
      PageTree page = new PageTree(_treePid, -1, 1,
                                   dupKey(_minKey), dupKey(_maxKey),
                                   _blockList);
      
      _pageList.add(page);
      _blockList.clear();
      _block = null;
    }
    
    byte []dupKey(byte []key)
    {
      byte []newKey = new byte[key.length];
      System.arraycopy(key, 0, newKey, 0, key.length);
      
      return newKey;
    }
    
    void nextPage()
    {
      System.arraycopy(_nextKey, 0, _minKey, 0, _nextKey.length);
      System.arraycopy(_nextKey, 0, _maxKey, 0, _nextKey.length);

      _treePid = nextPid();
      _block = new BlockTree(_treePid);
    }
    
    void finish()
    {
      if (_blockList.size() > 0 || _block != null) {
        flushPage();
      }
      
      if (_pageList.size() == 0) {
        throw new IllegalStateException();
      }
      
      if (_pageList.size() == 1) {
        finishRoot();
        return;
      }
      
      ArrayList<PageTree> pageList = new ArrayList<>(_pageList);
      _pageList.clear();
      
      int nextPid = -1;
      for (int i = pageList.size() - 1; i >= 0; i--){
        PageTree oldTree = pageList.get(i);
        
        PageTree tree = oldTree.replaceNextId(nextPid);
        _pages.set(tree.getId(), tree);
        
        nextPid = tree.getId();
      }
      
      // recursively build the parent tree
      
      _minKey = new byte[_table.getKeyLength()];
      _maxKey = new byte[_table.getKeyLength()];
      _nextKey = new byte[_table.getKeyLength()];
      
      _treePid = nextTreePid(_treePid + 1);
      _block = new BlockTree(_treePid);
      
      for (int i = 0; i < pageList.size(); i++) {
        PageTree tree = pageList.get(i);
        
        insert(tree.getMaxKey(), tree.getId());
      }
      
      finish();
    }
    
    void finishRoot()
    {
      PageTree oldTree = _pageList.get(0);
      
      PageTree rootTree = oldTree.copy(ROOT_TREE);
      rootTree.toSorted(_table.getRow());
      _pages.set(rootTree.getId(), rootTree);
    }
  }
}
