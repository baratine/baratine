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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.baratine.InService;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.kelp.segment.OutSegment;
import com.caucho.v5.util.L10N;

/**
 * page containing a single blob.
 * 
 * the blob is backed by a temp file.
 */
public class PageBlobTemp extends PageBlob
{
  private static final L10N L = new L10N(PageBlobTemp.class);
  private static final Logger log
    = Logger.getLogger(PageBlobTemp.class.getName());
  
  private StreamSource _streamSource;
  private int _offset;
  private int _length;
  
  private PageBlobStub _stubWrite;
  private PageBlobStub _stub;
  
  PageBlobTemp(int id, 
               int nextId,
               long sequence,
               StreamSource streamSource,
               int offset,
               int length)
  {
    super(id, nextId, sequence);
    
    _streamSource = streamSource;
    _offset = offset;
    _length = length;
  }
  
  @Override
  final Type getType()
  {
    return Type.BLOB;
  }
  
  @Override
  public int size()
  {
    // return _tempReader.getLength();
    return _length;
  }

  @Override
  int getLength()
  {
    return _length;
  }

  @Override
  int read(int pageOffset, byte[] buf, int offset, int length)
  {
    try (InputStream is = openInputStream()) {
      if (is != null) {
        is.skip(_offset + pageOffset);
        
        int len = is.read(buf, offset, length);
        
        if (len > 0) {
          /*
          if (buf[offset] == 0) {
            System.err.println("BAD_TEMP: " + is + " " + _offset + " " + pageOffset + " " + Hex.toHex(buf, offset, len)
                               + " " + _streamSource);
          }
          */
          return len;
        }
        else if (_stub == null) {
          throw new IllegalStateException(L.l("Failed reading from temp stream {0} with offset {1} len={2} ss-len={3}",
                                              _streamSource, _offset + pageOffset, len, _streamSource.getLength()));
        }
      }
    } catch (IOException e) {
      log.finer(e.toString());
      log.log(Level.FINEST, e.toString(), e);
    }
    
    if (_stub != null) {
      return _stub.read(pageOffset, buf, offset, length);
    }
    
    throw new IllegalStateException();
  }
  
  private InputStream openInputStream()
    throws IOException
  {
    if (_stub != null) {
      return null;
    }
    
    StreamSource streamSource = _streamSource;
    
    if (streamSource != null) {
      return streamSource.openInputStream();
    }
    else {
      return null;
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
    
    if (_stubWrite != null) {
      throw new IllegalStateException(L.l("Attempted to write double checkpoint {0}", table));
    }
    
    WriteStream os = sOut.out();

    if (sOut.getAvailable() < os.position() + _length) {
      return null;
    }
    
    int offset = (int) os.position();
  
    /*
    try (OutputStream zOut = sOut.outCompress()) {
      writeToStream(zOut);
    }
    */
    writeToStream(os);
    
    int length = (int) (os.position() - offset);
    
    if (length != _length) {
      System.out.println("MISMATCHED_LENGTH: " + _length + " " + length
                         + " " + offset + " " + os.position());
    }
    
    _stubWrite = new PageBlobStub(getId(), getNextId(), sOut.getSegment(),
                                      offset, length);
  
    return _stubWrite;
  }
  

  @Override
  void writeToStream(OutputStream os)
    throws IOException
  {
    try (InputStream is = _streamSource.openInputStream()) {
      is.skip(_offset);
      
      IoUtil.copy(is, os, _length);
    }
    
    /*
    int len = _length;
    
    if (len <= 0) {
      return;
    }
    
    try (InputStream is = _streamSource.openInputStream()) {
      TempBuffer tBuf = TempBuffer.allocate();
      byte []buffer = tBuf.getBuffer();
    
      int offset = _offset;
      
      is.skip(offset);
    
      while (len > 0) {
        int sublen = Math.min(len, buffer.length);
      
        int readlen = is.read(buffer, 0, sublen);
      
        if (readlen <= 0) {
          return;
        }
      
        os.write(buffer, 0, readlen);
      
        len -= readlen;
        offset += readlen;
      }
    }
    */
  }
  
  @Override
  @InService(PageServiceSync.class)
  public void afterDataFlush(PageServiceImpl tableService, 
                             int sequenceFlush)
  {
    super.afterDataFlush(tableService, sequenceFlush);

    if (_stubWrite != null) {
      tableService.compareAndSetBlobPage(this, _stubWrite);
      _stub = _stubWrite;
    } else {
      System.out.println("STUBLESS: " + this);
    }
    
    freeStream();
  }
  
  private void freeStream()
  {
    StreamSource streamSource = _streamSource;
    _streamSource = null;
    
    if (streamSource != null) {
      streamSource.close();
    }
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getId() + "," + _stub + "]";
  }
}
