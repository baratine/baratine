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
import java.util.ArrayList;

import com.caucho.v5.util.BitsUtil;

/**
 * Input stream for a row.
 */
public class RowInputStream extends InputStream
{
  private byte []_block;
  private int _rowOffset;
  
  private byte []_readBuffer = new byte[1];
  private byte []_lengthBuffer = new byte[4];
  private int _lengthOffset = 4;
  
  private RowInSkeleton _inState;
  private int _srcOffset;
  private int _srcLength;
  
  private ArrayList<Page> _srcPageList;
  private Page _srcPage;

  private PageServiceImpl _table;
  
  public RowInputStream(RowInSkeleton initState, 
                        byte []block, 
                        int rowOffset,
                        PageServiceImpl tableService)
  {
    if (block.length < 1024) {
      throw new IllegalStateException();
    }
    
    _inState = initState;
    _block = block;
    _rowOffset = rowOffset;
    
    _table = tableService;
    
    initState();
  }
  
  private void initState()
  {
    _srcOffset = _inState.srcOffset(_block, _rowOffset, _table);
    _srcLength = _inState.srcLength(_block, _rowOffset, _table);
    _srcPage = _inState.srcPage(_block, _rowOffset, _table);
    _srcPageList = fillPageList(_srcPage);

    int length = _inState.blobLength(_block, _rowOffset, _table);

    initLength(length);
  }
  
  private ArrayList<Page> fillPageList(Page page)
  {
    if (page == null) {
      return null;
    }
    
    ArrayList<Page> pageList = new ArrayList<>();
    
    while (page != null) {
      pageList.add(page);
      
      int nextPageId = page.getNextId();
      
      if (nextPageId > 0) {
        page = _table.getBlobPage(nextPageId);
      }
      else {
        page = null;
      }
    }
    
    return pageList;
  }
  
  @Override
  public int available()
  {
    return _inState != null ? 1 : -1;
  }
  
  @Override
  public int read()
      throws IOException
  {
    int len = read(_readBuffer, 0, 1);
    
    if (len <= 0) {
      return len;
    }
    else {
      return _readBuffer[0] & 0xff;
    }
  }
  
  @Override
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    int readLength = 0;
    
    int srcOffset = _srcOffset;
    int srcLength = _srcLength;
    
    //System.out.println("  RD0: " + srcOffset + " " + srcLength);
    
    while (length > 0) {
      if (_lengthOffset < 4) {
        int sublen = Math.min(length, 4 - _lengthOffset);
        
        System.arraycopy(_lengthBuffer, _lengthOffset, buffer, offset, sublen);
        _lengthOffset += sublen;
        offset += sublen;
        length -= sublen;
        readLength += sublen;
        continue;
      }
      
      int sublen = Math.min(length, srcLength);
      
      if (sublen > 0) {
        _inState.read(_srcPage, _block, srcOffset, buffer, offset, sublen); 

        readLength += sublen;
        offset += sublen;
        srcOffset += sublen;
        srcLength -= sublen;
        length -= sublen;
      }
      else {
        if (_inState == null) {
          break;
        }
        
        if (nextPage()) {
          srcOffset = _srcOffset;
          srcLength = _srcLength;
          continue;
        }
        
        _inState = _inState.next(_block, _rowOffset);
        
        if (_inState == null) {
          break;
        }
        
        initState();
        
        srcOffset = _srcOffset;
        srcLength = _srcLength;
      }
    }
    
    _srcOffset = srcOffset;
    _srcLength = srcLength;
    
    return readLength > 0 ? readLength : -1;
  }

  @Override
  public long skip(long length)
  {
    int readLength = 0;
    
    while (length > 0) {
      if (_lengthOffset < 4) {
        int sublen = (int) Math.min(length, 4 - _lengthOffset);
        
        _lengthOffset += sublen;
        length -= sublen;
        readLength += sublen;
        continue;
      }
      
      int sublen = (int) Math.min(length, _srcLength);
      
      if (sublen > 0) {
        readLength += sublen;
        _srcOffset += sublen;
        _srcLength -= sublen;
        length -= sublen;
      }
      else {
        if (_inState == null) {
          break;
        }
        
        if (nextPage()) {
          continue;
        }
        
        _inState = _inState.next(_block, _rowOffset);
        
        if (_inState == null) {
          break;
        }
        
        initState();
      }
    }
    
    return readLength > 0 ? readLength : -1;
  }
  
  private boolean nextPage()
  {
    Page page = _srcPage;
    
    if (page == null) {
      return false;
    }
    
    int index = _srcPageList.indexOf(page);
    
    if (index < 0 || _srcPageList.size() <= index + 1) {
      _srcPage = null;
      return false;
    }
    
    _srcPage = _srcPageList.get(index + 1);

    int length = _srcPage.size();
    
    _srcOffset = 0;
    _srcLength = length;
    
    initLength(length);
    
    return true;
  }
  
  private boolean isLastPage()
  {
    Page page = _srcPage;
    
    if (page == null) {
      return true;
    }
    
    return _srcPageList.indexOf(page) == _srcPageList.size() - 1;  
  }
  
  private void initLength(int length)
  {
    if (length >= 0) {
      if (! isLastPage()) {
        length |= 0x4000_0000;
      }
      
      BitsUtil.writeInt(_lengthBuffer, 0, length);

      _lengthOffset = 0;
    }
    else {
      _lengthOffset = 4;
    }
  }
}
