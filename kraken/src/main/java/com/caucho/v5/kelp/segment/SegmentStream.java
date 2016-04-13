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

import java.io.IOException;

import com.caucho.v5.baratine.InService;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.kelp.Page;
import com.caucho.v5.kelp.Page.Type;
import com.caucho.v5.kelp.PageBlobImpl;
import com.caucho.v5.kelp.TableWriterService;
import com.caucho.v5.kelp.TableWriterServiceImpl;
import com.caucho.v5.util.BitsUtil;

import io.baratine.service.Result;

/**
 * Actor for doing a segment garbage collection
 */
public class SegmentStream
{
  private OutSegment _sOut;
  private long _sequence;
  private boolean _isGc;
  
  public SegmentStream()
  {
  }

  public void setGc(boolean isGc)
  {
    _isGc = isGc;
  }
  
  boolean isGc()
  {
    return _isGc;
  }
  
  public void setSequence(long sequence)
  {
    _sequence = sequence;
  }

  public long getSequence()
  {
    if (_sequence > 0) {
      return _sequence;
    }
    else if (_sOut != null) {
      return _sOut.getSequence();
    }
    else {
      return -1;
    }
  }
  
  private OutSegment openWriter(TableWriterServiceImpl rwServiceImpl)
  {
    OutSegment sOut = _sOut;
    
    if (sOut == null) {
      if (_sequence > 0) {
        if (isGc()) {
          sOut = rwServiceImpl.openWriterGc(_sequence);
        }
        else {
          sOut = rwServiceImpl.openWriterSeq(_sequence);
        }
      }
      else {
        sOut = rwServiceImpl.openWriter();
      }
    }
        
    _sOut = sOut;
    
    return sOut;
  }
    
  
  @InService(TableWriterServiceImpl.class)
  public
  void writeBlobChunk(TableWriterServiceImpl rwActor,
                      PageBlobImpl blobPage, 
                      long blobOffset, 
                      TempBuffer tempBuffer,
                      int blobLength,
                      Result<Integer> result)
  {
    Type type = blobPage.getType();
    int pid = blobPage.getId();
    
    int saveSequence = blobPage.getWriteSequence();
    
    OutSegment sOut = openWriter(rwActor);
    WriteStream out = sOut.out();
    
    try {
      int head = (int) out.getPosition();
      out.write(type.ordinal());
      BitsUtil.writeInt(out, blobPage.getId());
      BitsUtil.writeInt16(out, blobLength);
      
      out.write(tempBuffer.buffer(), 0, blobLength);
      
      int tail = (int) out.getPosition();
      
      sOut.addEntry(out,
                      blobPage,
                      blobPage,
                      saveSequence, 
                      type, pid, -1, head, tail - head,
                      result);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  @InService(SegmentServiceImpl.class)
  public
  void writePage(TableWriterServiceImpl rwActor,
                 Page page, 
                 int saveLength, 
                 int saveTail, 
                 int sequenceWrite,
                 Result<Integer> result)
  {
    long pageSequence = page.getSequence();
      
    OutSegment sOut = openWriter(rwActor);
        
    // sOut.getSegment().setGcGeneration(_gcGeneration);
    // System.out.println("GC-SEG: " + sOut.getSegment() + " " + _gcSequence);

    // _pageWriteSet.add(page.getId());
      
    Page newPage;
    
    if ((newPage = sOut.writePage(page, pageSequence, saveLength, saveTail, sequenceWrite, result)) == null) {
      _sOut = null;
      
      if (_isGc) {
        // GC requests fsync immediately to free segments quickly
        //System.out.println("WRITE: " + sOut);
        sOut.fsync(Result.ignore());
      }
      else {
        //sOut.flushData();
        // XXX: callbacks for GC
        //System.out.println("WRITE2: " + sOut);
        sOut.closeSchedule(Result.ignore());
      }
        
      sOut = openWriter(rwActor);
        
      if ((newPage = sOut.writePage(page, pageSequence, saveLength, saveTail, sequenceWrite, result)) == null) {
        String msg = ("BAD_DWRITE: " + page + " len=" + page.getSize() 
            + " save-len=" + saveLength + " avail:" + sOut.getAvailable());
        System.err.println("SegmentStream.writePage: " + msg);
        RuntimeException exn = new IllegalStateException(msg);
        exn.fillInStackTrace();
        
        result.fail(exn);
        
        throw exn;
      }
    }
  }
  
  @InService(TableWriterService.class)
  public void flush(Result<Boolean> result)
  {
    OutSegment sOut = _sOut;
    
    if (sOut != null) {
      sOut.flushData();
    }

    result.ok(true);
  }

  @InService(TableWriterService.class)
  public void fsync(Result<Boolean> result)
  {
    OutSegment sOut = _sOut;
    
    if (sOut != null) {
      sOut.flushData();
      sOut.fsync(result);
    }
    else {
      result.ok(true);
    }
  }

  public void addFsyncCallback(SegmentFsyncCallback onFsync)
  {
    OutSegment sOut = _sOut;
    
    if (sOut != null) {
      sOut.addFsyncCallback(onFsync);
    }
    else {
      onFsync.onFsync();
    }
  }
    
  @InService(SegmentServiceImpl.class)
  public
  void closeFromWriter(Result<Boolean> result)
  {
    try {
      OutSegment sOut = _sOut;
      _sOut = null;
      
      if (sOut != null) {
        sOut.closeSchedule(result);
      }
      else {
        result.ok(true);
      }
    } catch (Exception e) {
      e.printStackTrace();
      result.fail(e);
    }
  }
  
  @InService(SegmentServiceImpl.class)
  public
  void closeFsync(Result<Boolean> result)
  {
    try {
      OutSegment sOut = _sOut;
      _sOut = null;
    
      if (sOut != null) {
        sOut.closeFsync(result);
      }
      else {
        result.ok(true);
      }
    } catch (Throwable e) {
      result.fail(e);
    }
  }
}
