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

import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.kelp.PageServiceSync.GcContext;
import com.caucho.v5.kelp.segment.OutSegment;
import com.caucho.v5.kelp.segment.SegmentFsyncCallback;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.kelp.segment.SegmentStream;


/**
 * Page writer service for the table. Separate service/thread responsible
 * for writing to the segment.  
 */
public interface TableWriterService
{
  long getSequence();
  
  long getNodeSequence();
  
  SegmentStream getNodeStream();
  SegmentStream getBlobStream();
  
  /**
   * Checkpoint a page to the write segment
   * 
   * @param page the page to write
   * @param sOut
   * @param oldSequence
   * @param saveLength
   * @param tail
   * @param sequenceWrite page write sequence to correlate writes with completions
   */
  void writePage(Page page, 
                 SegmentStream sOut,
                 long oldSequence, 
                 int saveLength,
                 int tail,
                 int sequenceWrite,
                 Result<Integer> result);
  
  void writeBlobPage(PageBlob page,
                     int sequenceWrite,
                     Result<Integer> result);
  
  void writeBlobChunk(PageBlobImpl blobPage, 
                      long blobOffset, 
                      TempBuffer tempBuffer,
                      int length,
                      Result<Integer> result);
  
  boolean waitForComplete();
  void waitForComplete(Result<Boolean> result);
  
  void flush(Result<Boolean> result);
  
  boolean fsync();
  void fsync(Result<Boolean> cont);
  
  boolean close();

  OutSegment openWriterSeq(long gcSequence);
  
  Iterable<SegmentKelp> getSegments();
  
  void freeSegment(SegmentKelp segment);

  //
  // gc
  //
  
  //void writeGcPage(GcContext gcContext, SegmentGcEntry entry,
                   //Page page, int saveTail, int saveSequence);

  void fsyncSegment(SegmentStream sOut);
  
  void closeGc(GcContext gcContext, Result<Boolean> result);

  void closeStream(SegmentStream sOut, Result<Boolean> result);

  void addFsyncCallback(SegmentStream sOut, 
                        SegmentFsyncCallback onFsync);
}
