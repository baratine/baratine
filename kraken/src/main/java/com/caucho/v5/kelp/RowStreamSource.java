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

import java.io.InputStream;

import com.caucho.v5.io.StreamSource;

/**
 * Input stream for a row.
 */
public class RowStreamSource extends StreamSource
{
  private RowInSkeleton _initState;
  private byte []_block;
  private int _rowOffset;
  private PageServiceImpl _pageService;
  
  public RowStreamSource(RowInSkeleton initState, 
                         byte []block, int rowOffset,
                         PageServiceImpl table)
  {
    if (block.length < 1024) {
      throw new IllegalStateException();
    }
    
    _initState = initState;
    _block = block;
    _rowOffset = rowOffset;
    
    _pageService = table;
  }
  
  @Override
  public long getLength()
  {
    Row row = _pageService.getTable().row();
    
    return row.getLength(_block, _rowOffset, _pageService);
  }
  
  @Override
  public InputStream openInputStream()
  {
    return new RowInputStream(_initState, _block, _rowOffset, _pageService);
  }
  
  @Override
  public InputStream getInputStream()
  {
    return new RowInputStream(_initState, _block, _rowOffset, _pageService);
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _pageService + "," + _rowOffset + "]";
  }
}
