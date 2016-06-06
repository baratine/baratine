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

import io.baratine.db.BlobReader;

import java.util.Arrays;
import java.util.logging.Logger;

import com.caucho.v5.kelp.Page.Type;

public class BlobReaderPageImpl implements BlobReader
{
  private static final Logger log
    = Logger.getLogger(BlobReaderPageImpl.class.getName());
  
  private final RowCursor _cursor;
  private final ColumnBlob _column;

  private final PageServiceSync _pageService;
  private final int _blobPid;
  private final int _firstPageLength;
  private final int _firstPageNextId;
  
  private final PageBlob []_pages;

  private long _fullLength;

  /**
   * Creates a blob output stream.
   *
   * @param store the output store
   */
  public BlobReaderPageImpl(RowCursor cursor,
                            ColumnBlob column,
                            PageBlob blobPage)
  {
    _cursor = cursor;
    _column = column;
    
    _pageService = _cursor.table().getTableService(); 

    // XXX: this is only segment length
    _firstPageLength = blobPage.getLength();
    _firstPageNextId = blobPage.getNextId();
    
    _blobPid = blobPage.getId();
    
    long fullLength = 0;
    
    int pageCount = 0;
    
    int pidPtr = _blobPid;
    while (pidPtr > 0) {
      pageCount++;
      
      PageBlob page = _pageService.getBlobPage(pidPtr);
      
      if (! page.isBlob()) {
        throw new IllegalStateException();
      }
      
      fullLength += page.getLength();
      
      pidPtr = page.getNextId();
    }
    
    _fullLength = fullLength;
    _pages = new PageBlob[pageCount];
    
    pidPtr = _blobPid;
    pageCount = 0;
    while (pidPtr > 0) {
      PageBlob page = _pageService.getBlobPage(pidPtr);
      
      _pages[pageCount++] = page;
      
      pidPtr = page.getNextId();
    }
  }
  
  @Override
  public long getLength()
  {
    return _fullLength;
  }
  
  @Override
  public boolean isValid()
  {
    PageBlob page = _pageService.getBlobPage(_blobPid);

    if (! page.isBlob() || ! page.getType().isValid()) {
      return false;
    }
    
    if (page.getLength() != _pages[0].getLength()) {
      return false;
    }
    
    if (page.getNextId() != _pages[0].getNextId()) {
      return false;
    }
    
    return true;
  }

  /**
   * Reads a buffer.
   */
  @Override
  public int read(long pos, byte []buf, int offset, int length)
  {
    if (_fullLength <= pos) {
      return -1;
    }
    
    if (pos < 0) {
      throw new IllegalArgumentException();
    }
    
    PageBlob[] pages = _pages;

    int i = 0;
    for (; i < pages.length && pages[i].getLength() <= pos; i++) {
      pos -= pages[i].getLength();
    }
    
    if (pages.length <= i) {
      return -1;
    }
    
    PageBlob page = _pages[i];
    
    if (page.getType() != Type.BLOB) {
      page = _pageService.getBlobPage(page.getId());
      pages[i] = page;
    }

    /*
    if (! isValid()) {
      return -1;
    }
    */

    // XXX: multipage
    int pageLen = page.getLength();
    int pagePos = (int) pos;
    
    int sublen = Math.min(length, pageLen - pagePos);

    int readLen = page.read(pagePos, buf, offset, sublen);
    
    if (readLen <= 0) {
      log.warning("BLOB-READ UNEXPECTED EOF: " + pos + " " + _fullLength + " " + page); 
    }
    
    return readLen;
    
    /*
    int sublen = _length - _offset;
    
    if (sublen <= 0) {
      int pid = _blobPage.getNextId();
      
      if (pid <= 0) {
        return -1;
      }
      
      _blobPage = _cursor.getTable().getTableService().getBlobPage(pid);
      _length = _blobPage.getLength();
      _offset = 0;
      
      sublen = _length - _offset;
    }
    
    sublen = Math.min(sublen, length);
    
    int result = _blobPage.read(_offset, buf, offset, sublen);
    
    if (result > 0) {
      _offset += result;
    }
    
    return result;
    */
  }
  
  public void close()
  {
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
           + "[" + _column.name() + "," + _cursor
           + "," + _blobPid + "]"); 
  }
}
