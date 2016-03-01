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
import java.io.OutputStream;
import java.util.ArrayList;

import com.caucho.v5.baratine.InService;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.kelp.segment.OutSegment;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.vfs.WriteStream;

/**
 * page containing a single blob.
 * 
 * XXX: temp assume page is in a single segment.
 */
public class PageBlobImpl extends PageBlob
{
  private int _length;
  
  private ArrayList<TempBuffer> _buffers = new ArrayList<>();

  private boolean _isWriting = true;
  
  private PageBlobStub _stub;
  
  PageBlobImpl(int id, 
           int nextId,
           long sequence)
  {
    super(id, nextId, sequence);
  }
  
  @Override
  public
  final Type getType()
  {
    return Type.BLOB;
  }
  
  @Override
  public int getSize()
  {
    return _buffers.size() * BLOCK_SIZE;
  }

  @Override
  int getLength()
  {
    return _length;
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
  void writeChunk(long blobOffset, TempBuffer tempBuffer, int length)
  {
    _buffers.add(tempBuffer);
    
    _length += length;
  }
  
  void writeComplete()
  {
    _isWriting = false;
  }

  @Override
  int read(int pageOffset, byte[] buf, int offset, int sublen)
  {
    sublen = Math.min(_length - pageOffset, sublen);
    
    if (sublen <= 0) {
      return -1;
    }
    
    int index = pageOffset / BLOCK_SIZE;
    
    ArrayList<TempBuffer> buffers = _buffers;
    
    if (buffers == null) {
      // XXX: timing with write and stub
      return _stub.read(pageOffset, buf, offset, sublen);
    }
    
    TempBuffer tempBuffer = buffers.get(index);
    
    byte []pageBuffer = tempBuffer.buffer();
    
    int bufferOffset = pageOffset % pageBuffer.length;
    
    sublen = Math.min(pageBuffer.length - bufferOffset, sublen);
    
    System.arraycopy(pageBuffer, bufferOffset, buf, offset, sublen);

    return sublen;
  }
  
  //
  // checkpoint persistence
  //
  
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
    if (_isWriting) {
      throw new IllegalStateException(toString());
    }
    
    WriteStream os = sOut.out();

    if (sOut.getAvailable() < os.getPosition() + _length) {
      return null;
    }
    
    int offset = (int) os.getPosition();
    
    writeToStream(sOut.out());
    
    int length = (int) (os.getPosition() - offset);
    
    _stub = new PageBlobStub(getId(), getNextId(), sOut.getSegment(),
                             offset, length);

    return _stub;
  }

  @Override
  void writeToStream(OutputStream os)
    throws IOException
  {
    int len = _length;

    for (TempBuffer tBuf : _buffers) {
      int sublen = Math.min(len, tBuf.buffer().length);
      
      os.write(tBuf.buffer(), 0, sublen);
      
      len -= sublen;
    }
  }
  
  /**
   * Callback after the data has been written to the mmap, which allows for
   * reads (but not necessarily fsynced.)
   */
  @Override
  public void afterDataFlush(PageServiceImpl tableService, 
                             int sequenceFlush)
  {
    super.afterDataFlush(tableService, sequenceFlush);
    
    tableService.compareAndSetBlobPage(this, _stub);
    
    ArrayList<TempBuffer> buffers = _buffers;
    _buffers = null;

    if (buffers != null) {
      for (TempBuffer buffer : buffers) {
        buffer.freeSelf();
      }
    }
  }
}
