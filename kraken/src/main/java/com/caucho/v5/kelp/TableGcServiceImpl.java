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
import io.baratine.service.Result;
import io.baratine.service.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.caucho.v5.baratine.InService;
import com.caucho.v5.kelp.Page.Type;
import com.caucho.v5.kelp.segment.SegmentFsyncCallback;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.kelp.segment.SegmentServiceImpl;
import com.caucho.v5.kelp.segment.SegmentStream;
import com.caucho.v5.util.L10N;

/**
 * Actor for doing a segment garbage collection
 */
@Service
public class TableGcServiceImpl
{
  private static final L10N L = new L10N(TableGcServiceImpl.class);
  
  private TableKelp _table;
  private PageServiceSync _pageService;

  private boolean _isClosed;
  
  private long _gcSequence;
  
  private int _gcGeneration;
  
  private long _lastSequence;
  
  private long _gcLastSequenceMax;
  private long _gcLastSequenceMin;

  private PageServiceImpl _pageActor;
  
  private ArrayList<Result<Boolean>> _pendingGcResult = new ArrayList<>();

  private boolean _isGc;
  
  TableGcServiceImpl(TableKelp db)
  {
    _table = db;
    _pageService = db.getTableService();
    
    // _lastSequence = _db.pageService.get
  }
  
  public TableGcServiceImpl()
  {
  }
  
  public void gc(long gcSequence)
  {
    if (_isClosed) {
      return;
    }
    
    _gcSequence = gcSequence;
  }
  
  /**
   * Ping on end of gc to enable another gc if pending.
   */
  public void ping()
  {
  }
  
  public void waitForGc(Result<Boolean> result)
  {
    result.ok(Boolean.TRUE);
    /*
    if (! _isGc) {
      result.completed(Boolean.TRUE);
    }
    else {
      _pendingGcResult.add(result);
    }
    */
  }
  
  /**
   * GC executes in @AfterBatch because it can be slower than the new
   * requests under heavy load.
   */
  @AfterBatch
  public void afterBatch()
  {
    if (_isGc) {
      return;
    }
    
    long gcSequence = _gcSequence;
    _gcSequence = 0;
    
    if (gcSequence <= 0) {
      return;
    }
    
    try {
      ArrayList<SegmentHeaderGc> collectList = findCollectList(gcSequence);

      if (collectList.size() < _table.getDatabase().getGcMinCollect()) {
        return;
      }
      
      _gcGeneration++;

      /*
      while (collectList.size() > 0) {
        ArrayList<SegmentHeaderGc> sublist = new ArrayList<>();
        
        // int sublen = Math.min(_db.getGcMinCollect(), collectList.size());
        
        int size = collectList.size();
        
        int sublen = size;
        
        if (size > 16) {
          sublen = Math.min(16, collectList.size() / 2);
        }
        
         // System.out.println("GC: count=" + sublen + " gcSeq=" + gcSequence);
          
          //if (_db.getGcMinCollect() <= sublist.size()) {
          //  sublen = Math.max(2, _db.getGcMinCollect() / 2);
          //}
        
        for (int i = 0; i < sublen; i++) {
          SegmentHeaderGc header = collectList.remove(0);
          header.startGc();
          sublist.add(header);
        }
        
        collect(gcSequence, sublist);
      }
      */
      collect(gcSequence, collectList);
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }
  
  private ArrayList<SegmentHeaderGc> findCollectList(long gcSequence)
  {
    double factor = 0.5;
    
    if (_gcLastSequenceMin <= 0) {
      _gcLastSequenceMin = Long.MAX_VALUE;
      _gcLastSequenceMax = 0;
    }
    
    ArrayList<SegmentHeaderGc> segments = getTableSegments(gcSequence);
    
    int maxCollect = _table.getDatabase().getGcMaxCollect();
    
    ArrayList<SegmentHeaderGc> collectList = new ArrayList<>();
    
    long tailSequence = 0;

    for (SegmentHeaderGc header : segments) {
      /*
      if (gcGeneration > 0 && _gcGeneration <= gcGeneration + 4) {
        continue;
      }
      */
      
      long sequence = header.getSequence();
      /*
      if (maxCollect <= collectList.size() && sequence < tailSequence) {
        return collectList;
      }
      */
      
      _gcLastSequenceMin = Math.min(_gcLastSequenceMin, sequence); 
      _gcLastSequenceMax = Math.max(_gcLastSequenceMax, sequence); 
      
      try {
        long liveLength = _table.getSegmentSize(header.getSegment());
        // _table.readSegmentEntries(header.getSegments(), header);
        // System.out.println("H: " + header.isValid() + " " + header.getLiveLength() + " " + header.getSegmentLength() + " " + header);
        
        if (! header.isValid()) {
        }
        else if (liveLength < factor * header.getSegmentLength()) {
          collectList.add(header);
        }
      } catch (Throwable e) {
        e.printStackTrace();
      }
      
      tailSequence = sequence;
    }
    // System.out.println("CL: " + collectList.size());
    
    _gcLastSequenceMin = -1;
    
    return collectList;
  }
  
  private ArrayList<SegmentHeaderGc> getTableSegments(long gcSequence)
  {
    ArrayList<SegmentKelp> segments = new ArrayList<>();
    
    for (SegmentKelp segment : _table.getSegments()) {
      if (segment == null) {
        continue;
      }
      else if (segment.isClosed()) {
        // segment already collected
        continue;
      }
      else if (segment.isWriting()) {
        // segment hasn't finished writing yet
        continue;
      }
      
      long sequence = segment.getSequence();
      
      if (sequence <= 0) {
        continue;
      }
      
      if (gcSequence <= sequence) {
        continue;
      }
      
      /*
      if (_gcLastSequenceMin <= sequence && sequence <= _gcLastSequenceMax) {
        continue;
      }
      */
      
      segments.add(segment);
    }
    
    Collections.sort(segments, SegmentKelp.SegmentComparatorAscend.CMP);

    return createCollectList(segments);
  }
  
  private ArrayList<SegmentHeaderGc> 
  createCollectList(ArrayList<SegmentKelp> segments)
  {
    ArrayList<SegmentHeaderGc> collectList = new ArrayList<>();
    
    
    for (SegmentKelp segment : segments) {
      long sequence = segment.getSequence();

      // XXX:
      /*
      if (_table.getReadWriteActor().isActiveSequence(sequence)) {
        continue;
      }
      */
      
      SegmentHeaderGc header = new SegmentHeaderGc(_table, sequence, segment);
        
      collectList.add(header);
    }
    
    return collectList;
  }
  
  private void collect(long gcSequence,
                       final ArrayList<SegmentHeaderGc> collectList)
  {
    if (collectList.size() <= 0) {
      return;
    }

    //_isGc = true;

    ArrayList<SegmentKelp> segmentList = new ArrayList<>();
    
    for (SegmentHeaderGc header : collectList) {
      SegmentKelp segment = header.getSegment();
      segment.close();
      segmentList.add(segment);
    }
    
    PageServiceSync tableService = _table.getTableService();
    
    ///SegmentStream sOut = new SegmentStream();
    //sOut.setGc(true);
    //sOut.setSequence(gcSequence);
    
    //GcContextImpl gcContext = new GcContextImpl(gcSequence, collectList);
    //SegmentStream sOut = gcContext.getSegmentStream();
    
    tableService.writeGcEntries(gcSequence, segmentList);
    
    //GcWriter gcWriter = new GcWriter(gcContext, collectList);
    //gcWriter.completed(Boolean.TRUE);
    
    /*
    SegmentHeaderGc header = collectList.remove(0);
    // ArrayList<SegmentGcEntry> entryList = new ArrayList<>();
    
    TableService tableService = _table.getTableService();
    
    tableService.writeGcEntries(gcContext, header.getSequence());

    // ArrayList<MaukaSegment> segmentList = new ArrayList<>();
    System.out.println("  GCL: " + collectList.size());
    for (SegmentHeaderGc header : collectList) {
      gcContext.addSegment(header);
      
      
      Collection<SegmentGcEntry> entries = header.getEntries();
      
      if (entries.size() > 0) {
        entryList.addAll(header.getEntries());
      }
      
      gcContext.addSegment(header);
    }
    
    _table.getTableService().closeGcContext(gcContext);
    */
  }
  
  
  private void finishGc()
  {
    _isGc = false;
    
    for (Result<Boolean> result : _pendingGcResult) {
      result.ok(Boolean.TRUE);
    }
    
    _pendingGcResult.clear();
    
    _table.getGcService().ping();
  }

  public boolean close()
  {
    _isClosed = true;
    
    return true;
  }
  
  class SegmentGcEntry {
    private final long _sequence;
    
    private final Type _type;
    private final int _pid;
    private int _length;

    private Page _oldPage;
    private Page _newPage;
    
    SegmentGcEntry(long sequence, Type type, int pid, int length)
    {
      _sequence = sequence;
      _type = type;
      _pid = pid;
      _length = length;
    }
    
    long getSequence()
    {
      return _sequence;
    }
    
    Type getType()
    {
      return _type;
    }
    
    int getPid()
    {
      return _pid;
    }
    
    void addLength(int length)
    {
      _length += length;
    }
    
    int getLength()
    {
      return _length;
    }

    void setNewPage(PageServiceImpl pageActor, Page oldPage, Page newPage)
    {
      _pageActor = pageActor;
      _oldPage = oldPage;
      _newPage = newPage;
      
      if (oldPage.getId() != newPage.getId()) {
        System.out.println("WTF2: " + oldPage + " " + newPage);
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _pid + "," + _type + "]";
    }
  }
  
  private class SegmentHeaderGc implements SegmentFsyncCallback
  {
    private final TableKelp _table;
    private final long _sequence;
    
    private final SegmentKelp _segment;
    
    private boolean _isValid = true;
    
    SegmentHeaderGc(TableKelp table,
                    long sequence,
                    SegmentKelp segment)
    {
      _table = table;
      _sequence = sequence;
      _segment = segment;
    }
    
    public long getSequence()
    {
      return _sequence;
    }

    SegmentKelp getSegment()
    {
      return _segment;
    }
    
    private long getSegmentLength()
    {
      return _segment.getLength();
    }
    
    boolean isValid()
    {
      return _isValid;
    }

    @Override
    public void onFsync()
    {
      _table.freeSegment(getSegment());
    }
    
    @Override
    public int hashCode()
    {
      return getSegment().hashCode();
    }
    
    public boolean equals(Object o)
    {
      if (! (o instanceof SegmentHeaderGc)) {
        return false;
      }
      
      SegmentHeaderGc header = (SegmentHeaderGc) o;
      
      return getSegment().equals(header.getSegment());
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _segment + "]";
    }
  }
  
  private class GcContextImpl implements PageServiceSync.GcContext, Result<Boolean>
  {
    private SegmentStream _sOut;
    private long _gcSequence;
    private ArrayList<SegmentHeaderGc> _segmentList = new ArrayList<>();
    
    GcContextImpl(long gcSequence, ArrayList<SegmentHeaderGc> segmentList)
    {
      _gcSequence = gcSequence;
      
      _sOut = new SegmentStream();
      _sOut.setGc(true);
      _sOut.setSequence(gcSequence);
      
      _segmentList.addAll(segmentList);
    }
    
    @Override
    public SegmentStream getSegmentStream()
    {
      return _sOut;
    }

    public void addHeaderWritten(SegmentHeaderGc header)
    {
      SegmentStream sOut = getSegmentStream();
      
      if (sOut != null) {
        sOut.addFsyncCallback(header);
        // fsync to try to release the header quicker
        //sOut.fsync(null);
      }
    }
    
    /*
    @InService(TableServiceImpl.class)
    public void writeEntry(TableServiceImpl pageService,
                           SegmentGcEntry entry)
    {
      Page page = pageService.getPage(entry.getPid());

      if (_gcSequence <= page.getSequence() || ! page.isSwappable()) {
        // skip pages that were updated after the gc
        return;
      }
      
      Page oldPage = page;
      
      switch (entry.getType()) {
      case LEAF:
        page = pageService.loadLeaf(entry.getPid());
        break;
        
      case TREE:
        break;
        
      case BLOB:
        page = oldPage;
        break;
        
      default:
        System.out.println("UNKNOWN_TYPE: " + entry.getType());
        break;
      }
      
      page.write(_table, pageService, _sOut, 0);
    }
    */
    
    @InService(PageServiceImpl.class)
    public void close()
    {
      _table.getReadWrite().closeGc(this, this);
    }
    
    @InService(SegmentServiceImpl.class)
    @Override
    public void closeGcFromWriter(Result<Boolean> result)
    {
      SegmentStream sOut = _sOut;
      _sOut = null;

      // System.out.println("CLOSE_GC: " + _segmentList.size());
      
      if (sOut != null) {
        try {
          //sOut.closeFromWriter(result);
          //sOut.closeFsync(result);
          sOut.closeFsync(null);
        } catch (Exception e) {
          result.fail(e);
        }
        
        result.ok(true);
      }
      else {
        result.ok(true);
      }
    }

    @Override
    public void handle(Boolean result, Throwable exn)
    {
      finishGc();
      
      if (exn != null) {
        exn.printStackTrace();
      }
    }
  }

  /*
  private class GcWriter implements Result<Boolean> {
    private GcContextImpl _gcContext;
    private ArrayList<SegmentHeaderGc> _collectList;
    private SegmentHeaderGc _lastHeader;
    
    GcWriter(GcContextImpl gcContext,
             ArrayList<SegmentHeaderGc> collectList)
    {
      _gcContext = gcContext;
      _collectList = collectList;
    }
    
    @Override
    public void completed(Boolean value)
    {
      TableService tableService = _table.getTableService();
      
      if (_lastHeader != null) {
        _gcContext.addHeaderWritten(_lastHeader);
        _lastHeader = null;
      }
      
      if (_collectList.size() > 0) {
        SegmentHeaderGc header = _collectList.remove(0);
        
        // ensure segment is only collected once
        header.getSegment().close();
        
        _lastHeader = header;
        
        tableService.writeGcEntries(_gcContext, header.getSegment(), this);
      }
      else {
        tableService.closeGcContext(_gcContext);
      }
    }
   
    @Override
    public void failed(Throwable exn)
    {
      _isGc = false;

      exn.printStackTrace();
    }
  }
  */
  
  private static class EntryComparator implements Comparator<SegmentGcEntry>
  {
    private static final EntryComparator CMP = new EntryComparator();
    
    @Override
    public int compare(SegmentGcEntry a, SegmentGcEntry b)
    {
      long cmp = a.getSequence() - b.getSequence();
      
      if (cmp != 0) {
        return Long.signum(cmp);
      }
      return a.getPid() - b.getPid();
    }
  }
}
