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

import com.caucho.v5.baratine.InService;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.kelp.segment.OutSegment;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.util.CurrentTime;

/**
 * A blob that is available.
 */
public class PageBlobFree extends PageBlob
{
  private PageBlob _oldBlob;
  private long _oldBlobExpire;
  
  PageBlobFree(int id, 
               int nextId,
               long sequence,
               PageBlob oldBlob)
  {
    super(id, nextId, sequence);
    
    _oldBlob = oldBlob;
    
    if (oldBlob != null) {
      _oldBlobExpire = CurrentTime.currentTime() + 60000L;
    }
  }
  
  @Override
  final Type getType()
  {
    return Type.BLOB_FREE;
  }
  
  @Override
  public final int getSize()
  {
    return 0;
  }
  
  final PageBlob getOldBlob()
  {
    return _oldBlob;
  }

  @Override
  int getLength()
  {
    PageBlob oldBlob = _oldBlob;
    
    if (oldBlob == null) {
      return 0;
    } 
    else if (CurrentTime.currentTime() <= _oldBlobExpire) {
      return oldBlob.getLength();
    }
    else {
      _oldBlob = null;
      return 0;
    }
  }

  @Override
  int read(int pageOffset, byte[] buf, int offset, int sublen)
  {
    PageBlob oldBlob = _oldBlob;
    
    if (oldBlob != null) {
      return oldBlob.read(pageOffset, buf, offset, sublen);
    }
    else {
      return -1;
    }
  }

  //
  // checkpoint persistence
  //
  
  @Override
  public Page writeCheckpoint(TableKelp table,
                       OutSegment sOut,
                       long oldSequence,
                       int saveLength,
                       int saveTail,
                       int saveSequence)
    throws IOException
  {
    return this;
  }

  @InService(PageServiceImpl.class)
  static void readCheckpoint(TableKelp table,
                             PageServiceImpl pageActor,
                             SegmentKelp segment,
                             ReadStream is, 
                             int length, 
                             int pid,
                             int nextPid)
    throws IOException
  {
    PageBlobFree page = new PageBlobFree(pid, nextPid, segment.getSequence(), null);
    
    pageActor.setBlobPage(page);
  }
}
