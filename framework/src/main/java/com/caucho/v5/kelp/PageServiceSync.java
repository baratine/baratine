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
import io.baratine.service.Service;

import java.util.ArrayList;
import java.util.function.Predicate;

import com.caucho.v5.amp.Direct;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.kelp.segment.SegmentStream;

/**
 * Page/btree service for the table.
 */
public interface PageServiceSync extends PageService
{
  long getMemorySize();
  
  @Direct
  boolean getDirect(RowCursor row);
  
  boolean getSafe(RowCursor row);
  
  GetStreamResult getStream(RowCursor cursor);

  boolean put(RowCursor cursor, PutType type);

  boolean putWithBackup(RowCursor cursor, 
                        PutType type,
                        @Service BackupKelp backupCb);

  
  //
  // internal leaf/tree
  //
  
  Page getPage(int pid);
  Page getLeaf(int pid);

  PageLeaf getLeafByCursor(RowCursor cursor);
  
  PageLeafImpl loadLeaf(int pid);
  
  // void setLeaf(PageLeafStub stub);
  
  Page getTree(int pid);
  
  Page loadTree(int pid);
  
  //
  // internal write
  //
  
  //
  // blobs
  //
  
  PageBlob getBlobPage(int blobId);
  
  PageBlobImpl allocateBlobPage();
  
  int allocateBlobPageId();
  
  void setBlobPage(PageBlob blobPage);

  void compareAndSetBlobPage(PageBlob oldPage, PageBlob newPage);

  void writeBlobChunk(PageBlobImpl blobPage, long blobOffset,
                      TempBuffer tempBuffer, int length);
  
  void freeBlob(int blobId);

  /**
   * After the page checkpoint is written to the mmap/file, the stub can
   * reload the page without needing the buffer.
   * 
   * @param page the checkpointed page
   * @param flushSequence the page write-count, which correlates save requests
   *   with completions.
   */
  void afterDataFlush(Page page, int seq); //ArrayList<PageFlush> pageList);

  void freeSegment(SegmentKelp segment);

  //
  // query
  //
  /*
  void findOne(RowCursor minCursor, 
               RowCursor maxCursor, 
               ExprKelp whereKelp,
               Result<RowCursor> findResult);
  */
  //
  // listeners
  //
  
  void addListener(@Service TableListener listener);
  void removeListener(@Service TableListener listener);
  
  //
  // flush
  // 

  /**
   * Called on write completion.
   */
  //void completeEntry(PendingEntry entry);
  
  boolean waitForComplete();
  
  //
  // checkpoint
  //
  
  boolean checkpoint();
  
  //
  // memory gc
  //
  
  void collect();
  
  //
  // segment gc
  //
  
  /*
  void writeGcEntries(GcContext cxt, 
                      ArrayList<SegmentGcEntry> entryList);
                      */
  
  void writeGcEntries(long sequenceGc,
                      ArrayList<SegmentKelp> segments);
  
  void closeGcContext(GcContext gcContext);
  
  void start(Result<TableKelp> result);

  void flush(Result<Object> result);
  
  //
  // close
  //

  boolean close();
  void close(Result<Boolean> result);
  
  static interface GcContext
  {
    // void writeEntry(TableServiceImpl pageActor, SegmentGcEntry entry);
    
    SegmentStream getSegmentStream();

    void close();

    void closeGcFromWriter(Result<Boolean> result);
  }
  
  interface PageFlush {
    Page getPage();
    int getSequenceFlush();
  }
  
  public enum PutType {
    PUT,
    LOAD,
    REPLAY;
  }
}
