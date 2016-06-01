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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.caucho.v5.amp.Direct;
import com.caucho.v5.baratine.InService;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.kelp.Page.Type;
import com.caucho.v5.kelp.PageServiceSync.GcContext;
import com.caucho.v5.kelp.io.CompressorKelp;
import com.caucho.v5.kelp.segment.InSegment;
import com.caucho.v5.kelp.segment.OutSegment;
import com.caucho.v5.kelp.segment.SegmentExtent;
import com.caucho.v5.kelp.segment.SegmentFsyncCallback;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.kelp.segment.SegmentKelp.SegmentComparatorDescend;
import com.caucho.v5.kelp.segment.SegmentKelp.SegmentEntryCallback;
import com.caucho.v5.kelp.segment.SegmentService;
import com.caucho.v5.kelp.segment.SegmentStream;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.store.io.InStore;
import com.caucho.v5.store.io.OutStore;
import com.caucho.v5.store.io.StoreReadWrite;
import com.caucho.v5.util.L10N;

import io.baratine.service.AfterBatch;
import io.baratine.service.Result;

/**
 * Filesystem access for the BlockStore.
 */
public class TableWriterServiceImpl
{
  private final static Logger log
    = Logger.getLogger(TableWriterServiceImpl.class.getName());
  private final static L10N L = new L10N(TableWriterServiceImpl.class);
  
  final static int BLOCK_SIZE = 8 * 1024;
  
  // private final static long FILE_SIZE_INCREMENT = 32L * 1024 * 1024; 
  // private final static long FILE_SIZE_INCREMENT = 64 * 1024;
  
  private final TableKelp _table;
  private final StoreReadWrite _store;
  private final SegmentService _segmentService;
  
  private int _segmentSizeNew;
  private int _segmentSizeGc;
  
  // largest blob in the system
  private int _blobSizeMax;
  
  private long _sequence = 0;
  
  private ConcurrentHashMap<Long,Boolean> _activeSequenceSet = new ConcurrentHashMap<>();
  
  private Lifecycle _lifecycle = new Lifecycle();
  
  // private SegmentWriter _nodeWriter;
  // private SegmentWriter _blobWriter;
  
  private SegmentStream _nodeStream;
  // private SegmentStream _blobStream;
  
  private boolean _isBlobDirty;
  
  private long _gcSequence;
  // private StoreFsyncService _fsyncService;
  private long _tableLength;
  private CompressorKelp _compressor;

  /**
   * Creates a new store.
   *
   * @param database the owning database.
   * @param name the store name
   * @param lock the table lock
   * @param path the path to the files
   */
  TableWriterServiceImpl(TableKelp table,
                         StoreReadWrite store,
                         SegmentService segmentService)
  {
    Objects.requireNonNull(table);
    Objects.requireNonNull(store);
    
    _table = table;
    _segmentService = segmentService;
    _store = store;
    
    _nodeStream = new SegmentStream();
    //_blobStream = new SegmentStream();
    
    _segmentSizeNew = table.database().getSegmentSizeMin();
    _segmentSizeGc = table.database().getSegmentSizeMin();
    
    _compressor = _segmentService.compressor();
    Objects.requireNonNull(_compressor);
    
    /*
    StoreFsyncServiceImpl fsyncActor = new StoreFsyncServiceImpl(_store);
    
    _fsyncService = db.getRampManager().service(fsyncActor)
                                       .as(StoreFsyncService.class);
                                       */
  }
  
  private byte []getTableKey()
  {
    return _table.tableKey();
  }

  /**
   * Returns the file size.
   */
  public long getFileSize()
  {
    return _store.fileSize();
  }
  
  /*
  StoreFsyncService getFsyncService()
  {
    return _fsyncService;
  }
  */
  
  @Direct
  public long getSequence()
  {
    return _sequence;
  }
  
  @Direct
  public long getNodeSequence()
  {
    SegmentStream nodeStream = _nodeStream;
    
    if (nodeStream != null) {
      return nodeStream.getSequence();
    }
    else {
      return _sequence;
    }
  }
  
  @Direct
  public SegmentStream getNodeStream()
  {
    return _nodeStream;
  }
  
  @Direct
  public SegmentStream getBlobStream()
  {
    // return _blobStream;
    
    return _nodeStream;
  }
  
  public boolean isActiveSequence(long sequence)
  {
    return _activeSequenceSet.get(sequence) != null;
  }
  
  public CompressorKelp compressor()
  {
    return _compressor;
  }

  void init(TableKelp table, PageServiceImpl pageActor)
  {
    try {
      Iterable<SegmentKelp> segments
        = _segmentService.initSegments(table.tableKey());
      
      initImpl(table, pageActor, segments);
    } catch (Throwable e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    
    _lifecycle.toActive();
  }
    
  /**
   * Creates the store.
   */
  @InService(PageServiceImpl.class)
  void create(TableKelp table,
              PageServiceImpl pageActor)
    throws IOException
  {
    int rootPid = 1;
    
    BlockTree treeBlock = new BlockTree(rootPid);
    
    int leafPid = 2;
    int nextPid = -1;
    
    BlockLeaf []blocks = new BlockLeaf[] { new BlockLeaf(leafPid) };
    
    byte []minKey = new byte[table.getKeyLength()];
    byte []maxKey = new byte[table.getKeyLength()];
    
    Arrays.fill(minKey, (byte) 0);
    Arrays.fill(maxKey, (byte) 0xff);
    
    long sequence = 1;
    
    PageLeafImpl leafPage = new PageLeafImpl(leafPid, nextPid,
                                             sequence,
                                             _table,
                                             minKey, maxKey, 
                                             blocks);
    
    leafPage.setDirty();
    
    treeBlock.insert(minKey, maxKey, leafPid);
    
    BlockTree []treeBlocks = new BlockTree[] { treeBlock };
    
    PageTree treePage = new PageTree(rootPid, nextPid, sequence,
                                     minKey, maxKey, treeBlocks);
    
    treePage.setDirty();
    
    pageActor.addLoadedPage(leafPage);
    pageActor.addLoadedPage(treePage);
  }

  @InService(PageServiceImpl.class)
  void initImpl(TableKelp table,
                PageServiceImpl pageActor,
                Iterable<SegmentKelp> segmentIter)
    throws IOException
  {
    boolean isValidSegment = false;
    boolean isSegment = false;
    
    try {
      SegmentReadContext readCxt
        = new SegmentReadContext(table, pageActor);
      
      // segments sorted in descending order to minimize page overwrites
      ArrayList<SegmentKelp> segments = sortSegments(segmentIter);
      
      for (SegmentKelp segment : segments) {
        isSegment = true;
        
        if (loadSegment(table, pageActor, readCxt, segment)) {
          isValidSegment = true;
          
          addTableSegmentLength(segment.length());
        }
        else {
          // if segment has no used pages, free it
          _segmentService.freeSegment(segment);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    if (! isSegment) {
      log.config(L.l("Creating new table {0}.", this));
      
      create(table, pageActor);
    }
    else if (! isValidSegment) {
      log.warning(L.l("{0} corrupted table. Recreating.", this));
      
      create(table, pageActor);
    }
  }
  
  private ArrayList<SegmentKelp> sortSegments(Iterable<SegmentKelp> segmentIter)
  {
    ArrayList<SegmentKelp> segments = new ArrayList<>();
    
    for (SegmentKelp segment : segmentIter) {
      segments.add(segment);
    }
    
    Collections.sort(segments, SegmentComparatorDescend.CMP);
    
    return segments;
  }
  
  @InService(PageServiceImpl.class)
  private boolean loadSegment(TableKelp table,
                              PageServiceImpl pageActor,
                              SegmentReadContext readCxt,
                              SegmentKelp segment)
    throws IOException
  {
    try (InSegment reader = openRead(segment)) {
      ReadStream is = reader.in();
      
      int length = segment.length();
    
      is.position(length - BLOCK_SIZE);
    
      long seq = segment.getSequence();
      
      _sequence = Math.max(_sequence, seq);
      
      if (seq <= 0) {
        System.out.println("BAD_SEQ:");

        return false;
      }
      
      LoadCallback loadCallback = new LoadCallback(segment, reader.in(), readCxt);

      segment.readEntries(table, pageActor, seq, loadCallback, reader);

      return loadCallback.isLoadedPage();
      /*
      synchronized (_segments) {
        _segments.add(segment);
      }
      */
    }
    
    // return true;
  }

  void readSegmentEntries(Iterable<SegmentKelp> segments, 
                          SegmentEntryCallback cb)
  {
    for (SegmentKelp segment : segments) {
      try (InSegment reader = openRead(segment)) {
        segment.readEntries(_table, reader, cb); 
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Direct
  public Iterable<SegmentKelp> getSegments()
  {
    return _segmentService.getSegments(getTableKey());
  }
  
  /**
   * Frees a segment to be reused. Called by the segment-gc service.
   */
  public void freeSegment(SegmentKelp segment)
    throws IOException
  {
    freeTableSegmentLength(segment.length());
    
    // System.out.println("FREE: " + _tableLength + " " + segment.getLength() + " " + segment);
    
    _segmentService.freeSegment(segment);
  }
  
  InSegment openRead(SegmentKelp segment)
  {
    InStore sIn = _store.openRead(segment.getAddress(), 
                                  segment.length());
    
    return new InSegment(sIn, segment.extent(), compressor());
  }
  
  /**
   * Writes a page to the current segment
   * 
   * @param page
   * @param sOut
   * @param oldSequence
   * @param saveLength
   * @param saveTail
   * @param sequenceWrite page sequence to correlate write requests with
   *   flush completions
   */
  @InService(TableWriterService.class)
  public void writePage(Page page,
                        SegmentStream sOut,
                        long oldSequence, 
                        int saveLength,
                        int saveTail,
                        int sequenceWrite,
                        Result<Integer> result)
  {
    try {
      sOut.writePage(this, page, saveLength, saveTail, sequenceWrite,
                     result);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Writes a blob page to the current output segment.
   * 
   * @param page the blob to be written
   * @param saveSequence
   * @return true on completion
   */
  @InService(TableWriterService.class)
  public void writeBlobPage(PageBlob page,
                               int saveSequence,
                               Result<Integer> result)
  {
    SegmentStream sOut = getBlobStream();
    
    int saveLength = 0;
    int saveTail = 0;
    
    if (_blobSizeMax < page.getLength()) {
      _blobSizeMax = page.getLength();
      calculateSegmentSize();
    }
    
    sOut.writePage(this, page, saveLength, saveTail, saveSequence, result);
    
    _isBlobDirty = true;
  }
  
  public void writeBlobChunk(PageBlobImpl blobPage, 
                             long blobOffset, 
                             TempBuffer tempBuffer,
                             int blobLength,
                             Result<Integer> result)
  {
    _isBlobDirty = true;
    
    if (_blobSizeMax < blobLength) {
      _blobSizeMax = blobLength;
      calculateSegmentSize();
    }
    
    getBlobStream().writeBlobChunk(this, blobPage, blobOffset, 
                                   tempBuffer, blobLength, result);
  }
  
  public void closeGc(GcContext gcContext, Result<Boolean> result)
  {
    gcContext.closeGcFromWriter(result);
  }
  
  public void addFsyncCallback(SegmentStream sOut, 
                               SegmentFsyncCallback onFsync)
  {
    sOut.addFsyncCallback(onFsync);
  }
  
  public void closeStream(SegmentStream sOut, Result<Boolean> result)
  {
    sOut.closeFsync(result);
    // gcContext.closeGcFromWriter(result);
  }
  
  /**
   * Opens a new segment writer. The segment's sequence id will be the next
   * sequence number.
   * 
   * @return the new segment writer
   */
  public OutSegment openWriter()
  {
    if (isGcRequired()) {
      _gcSequence = ++_sequence;
      _table.getGcService().gc(_gcSequence);
    }
    
    long sequence = ++_sequence;
    
    return openWriterSeq(sequence);
  }
  
  /**
   * Opens a new segment writer with a specified sequence. Called by the GC
   * which has multiple segments with the same sequence number.
   * 
   * @param sequence the sequence id for the new segment.
   */
  public OutSegment openWriterSeq(long sequence)
  {
    int segmentSize = _segmentSizeNew;

    SegmentKelp segment = _segmentService.createSegment(segmentSize, getTableKey(), sequence);

    addTableSegmentLength(segmentSize);
    
    _activeSequenceSet.put(sequence, Boolean.TRUE);
    
    return new OutSegment(_table, _table.getTableService(), this, segment);
  }

  public void afterSequenceClose(long sequence)
  {
    _activeSequenceSet.remove(sequence);
  }
  
  /**
   * Opens a new segment writer with a specified sequence. Called by the GC
   * which has multiple segments with the same sequence number.
   * 
   * @param sequence the sequence id for the new segment.
   */
  public OutSegment openWriterGc(long sequence)
  {
    int segmentSize = _segmentSizeGc;
    
    SegmentKelp segment = _segmentService.createSegment(segmentSize, getTableKey(), sequence);
    
    addTableSegmentLength(segmentSize);
    
    return new OutSegment(_table, _table.getTableService(), this, segment);
  }
  
  private void freeTableSegmentLength(int length)
  {
    _tableLength -= length;
  }
  
  private void addTableSegmentLength(int length)
  {
    _tableLength += length;
    
    calculateSegmentSize();
  }
  
  /**
   * Calculates the dynamic segment size based on the current table size.
   * 
   * Small tables use small segments and large tables use large segments
   * to improve efficiency. A small table will have about 5 active segments
   * because of GC, which means a table with 64k live entries will still use
   * 5 * minSegmentSize. 
   * 
   * new segments: 1/64 of total table size
   * gc segments: 1/8 of total table size
   */
  private void calculateSegmentSize()
  {
    DatabaseKelp db = _table.database();
    
    _segmentSizeNew = calculateSegmentSize(db.getSegmentSizeFactorNew(),
                                           _segmentSizeNew);
    
    _segmentSizeGc = calculateSegmentSize(db.getSegmentSizeFactorGc(),
                                          _segmentSizeGc);
  }

  /**
   * Calculate the dynamic segment size.
   * 
   * The new segment size is a fraction of the current table size.
   * 
   * @param factor the target ratio compared to the table size
   * @param segmentSizeOld the previous segment size
   */
  private int calculateSegmentSize(int factor, int segmentSizeOld)
  {
    DatabaseKelp db = _table.database();
    
    long segmentFactor = _tableLength / db.getSegmentSizeMin();
  
    long segmentFactorNew = segmentFactor / factor;
    
    // segment size is (tableSize / factor), restricted to a power of 4
    // over the min segment size.
  
    if (segmentFactorNew > 0) {
      int bit = 63 - Long.numberOfLeadingZeros(segmentFactorNew);
    
      bit &= ~0x1;
    
      long segmentFactorPower = (1 << bit);
      
      if (segmentFactorPower < segmentFactorNew) {
        segmentFactorNew = 4 * segmentFactorPower;
      }
    }
    
    long segmentSizeNew = segmentFactorNew * db.getSegmentSizeMin();
    segmentSizeNew = Math.max(db.getSegmentSizeMin(), segmentSizeNew);
    
    long segmentSizeBlob = _blobSizeMax * 4;
    
    while (segmentSizeNew < segmentSizeBlob) {
      segmentSizeNew *= 4;
    }
    
    segmentSizeNew = Math.min(db.getSegmentSizeMax(), segmentSizeNew);
  
    return (int) Math.max(segmentSizeNew, segmentSizeOld);
  }

  private boolean isGcRequired()
  {
    if (! _lifecycle.isActive()) {
      return false;
    }
    else {
      int delta = Math.max(2, _table.getGcThreshold());
      
      return delta <= _sequence - _gcSequence;
    }
  }

  public OutStore openWrite(SegmentExtent extent)
  {
    return _store.openWrite(extent.address(), extent.length());
  }

  public boolean waitForComplete()
  {
    return true;
  }

  public boolean flush()
  {
    return true;
  }

  /**
   * sync the output stream with the filesystem when possible.
   */
  public void fsync(Result<Boolean> result)
      throws IOException
  {
    SegmentStream nodeStream = _nodeStream;
      
    // FlushCompletion cont = new FlushCompletion(result, nodeStream, blobStream);
    
    if (nodeStream != null) {
      nodeStream.fsync(result);
    }
    else {
      result.ok(true);
    }
  }
  
  public void fsyncSegment(SegmentStream sOut)
  {
    if (sOut != null) {
      sOut.fsync(Result.ignore());
    }
  }
  
  /**
   * Flushes the stream after a batch of writes.
   * 
   * If the writes included a blob write, the segment must be fsynced because
   * the blob is not saved in the journal.
   */
  @AfterBatch
  public void afterDeliver()
  {
    SegmentStream nodeStream = _nodeStream;
    
    if (nodeStream != null) {
      if (_isBlobDirty) {
        _isBlobDirty = false;

        // nodeStream.flush(null);
        nodeStream.fsync(Result.ignore());
      }
      else {
        nodeStream.flush(Result.ignore());
      }
    }
    
    /*
    if (blobWriter != null) {
      try {
        blobWriter.flushSegment();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    */
  }

  /**
   * Closes the store.
   */
  public void close(Result<Boolean> result)
  {
    _lifecycle.toDestroy();
    
    SegmentStream nodeStream = _nodeStream;
    _nodeStream = null;
      
    if (nodeStream != null) {
      nodeStream.closeFsync(result.of(v->closeImpl()));
    }
    else {
      result.ok(true);
    }
  }

  private boolean closeImpl()
  {
    // _store.close();
    
    return true;
  }
  
  public class LoadCallback implements SegmentEntryCallback
  {
    private final SegmentKelp _segment;
    private final ReadStream _is;
    private final SegmentReadContext _reader;
    
    private boolean _isLoadedPage;
    
    LoadCallback(SegmentKelp segment,
                 ReadStream is,
                 SegmentReadContext reader)
    {
      _segment = segment;
      _is = is;
      _reader = reader;
    }
    
    public boolean isLoadedPage()
    {
      return _isLoadedPage;
    }

    @Override
    public void onEntry(int typeCode, int pid, int nextPid, int offset, int length)
    {
      try {
        _is.position(offset);
        
        Type type = Type.valueOf(typeCode);

        switch (type) {
        case LEAF:
          if (_reader.addLeaf(_segment, pid, nextPid, offset, length)) {
            _isLoadedPage = true;
          }
          break;

        case LEAF_DELTA:
          if (_reader.addLeafDelta(_segment, pid, nextPid, offset, length)) {
            _isLoadedPage = true;
          }
          break;

          /*
        case TREE: {
          PageTree tree = PageTree.read(_table, _pageActor, _is, 
                                        length, pid, nextPid, _sequence);
          
          _reader.addTree(tree);
          
          break;
        }
        */

        case BLOB:
          if (_reader.addBlob(_segment, pid, nextPid, offset, length)) {
            _isLoadedPage = true;
            _blobSizeMax = Math.max(_blobSizeMax, length);
          }
          break;

        case BLOB_FREE:
          if (_reader.addBlobFree(_segment, pid, nextPid, offset, length)) {
            _isLoadedPage = true;
          }
          break;

        default:
          throw new IllegalStateException(L.l("Unknown segment data {0}", type));
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  /*
  private class CloseResult extends Result.Wrapper<Boolean,Boolean> {
    private int _count = 2;
    
    CloseResult(Result<Boolean> cont)
    {
      super(cont);
    }
    
    @Override
    public void complete(Boolean value)
    {
      try {
        closeImpl();
      } finally {
        getNext().complete(true);
      }
    }
  }
  */

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _table.getName() + "]";
  }
}
