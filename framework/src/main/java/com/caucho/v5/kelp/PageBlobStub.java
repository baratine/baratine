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

import com.caucho.v5.baratine.InService;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.kelp.segment.InSegment;
import com.caucho.v5.kelp.segment.OutSegment;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.kelp.segment.SegmentServiceImpl;
import com.caucho.v5.vfs.WriteStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Stub of a btree-based node
 */
class PageBlobStub extends PageBlob
{
  private final SegmentKelp _segment;
  private final int _offset;
  private final int _length;
  
  private PageBlobStub _stubNext;
  
  private boolean _isFsync;
  
  PageBlobStub(int id, 
               int nextId,
               SegmentKelp segment,
               int offset,
               int length)
  {
    super(id, nextId, segment.getSequence());

    _segment = segment;
    _offset = offset;
    _length = length;
  }
  
  @Override
  final Type getType()
  {
    return Type.BLOB;
  }

  @Override
  final boolean isStub()
  {
    return true;
  }
  
  public int getOffset()
  {
    return _offset;
  }
  
  @Override
  public int getLength()
  {
    return _length;
  }
  
  @Override
  public int getSize()
  {
    return getLength();
  }

  @Override
  final SegmentKelp getSegment()
  {
    return _segment;
  }
  
  @Override
  public Page getNewStub()
  {
    return this;
  }
  
  @Override
  int read(int pageOffset, byte[] buffer, int offset, int length)
  {
    try (InSegment sIs = _segment.openRead()) {
      try (InputStream zIs = sIs.inCompress(getOffset(), getLength())) {
        zIs.skip(pageOffset);

        int sublen = zIs.read(buffer, offset, length);
      
        return sublen;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  @InService(SegmentServiceImpl.class)
  public Page writeCheckpoint(TableKelp table,
                       OutSegment sOut,
                       long oldSequence,
                       int saveLength,
                       int saveTail,
                       int saveSequence)
    throws IOException
  {
    WriteStream os = sOut.out();
    
    if (sOut.getAvailable() <= os.getPosition() + _length) {
      return null;
    }
    
    
    int offset = (int) os.getPosition();
    
    writeToStream(os);
    
    int length = (int) (os.getPosition() - offset);
    
    _stubNext = new PageBlobStub(getId(), getNextId(), sOut.getSegment(),
                            offset, length);
    
    return _stubNext;
  }

  @Override
  void writeToStream(OutputStream os)
    throws IOException
  {
    try (InSegment is = _segment.openRead()) {
      is.setPosition(getOffset());

      TempBuffer tBuf = TempBuffer.allocate();
      byte []buffer = tBuf.buffer();
      
      int length = _length;
      
      for (int i = 0; i < length; i += buffer.length) {
        int sublen = Math.min(length - i, buffer.length);
        
        is.read(buffer, 0, sublen);
        
        os.write(buffer, 0, sublen);
      }
      
      tBuf.freeSelf();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  @InService(PageServiceSync.class)
  public void afterDataFlush(PageServiceImpl tableService, 
                             int sequenceFlush)
  {
    _isFsync = true;
    
    super.afterDataFlush(tableService, sequenceFlush);

    if (_stubNext != null) {
      tableService.compareAndSetBlobPage(this, _stubNext);
    } else {
      System.out.println("STUBLESS: " + this);
    }
  }

  public String toString()
  {
    return (getClass().getSimpleName()
           + "[" + getId() 
           + ",seq=" + getSequence()
           + ",off=" + getOffset()
           + ",len=" + getLength() + "]"
           + ",fsync=" + _isFsync + "]");
  }
}
