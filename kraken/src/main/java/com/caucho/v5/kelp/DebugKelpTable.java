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

import com.caucho.v5.kelp.PageServiceSync.PutType;


/**
 * Debugging for qa.
 */
public class DebugKelpTable
{
  private final TableKelp _table;
  
  DebugKelpTable(TableKelp table)
  {
    _table = table;
  }
  
  public DebugLeaf createLeaf(int pid, int next, RowCursor min, RowCursor max)
  {
    Row row = _table.getRow();
    
    int keyLength = row.getKeyLength();
    
    byte []minKey = new byte[keyLength];
    byte []maxKey = new byte[keyLength];
    
    min.getKey(minKey);
    max.getKey(maxKey);
    
    long sequence = 100;
    
    BlockLeaf []blocks = new BlockLeaf[] { new BlockLeaf(pid) };

    PageLeafImpl pageLeaf
      = new PageLeafImpl(pid, next, sequence, _table, minKey, maxKey, blocks);
    
    pageLeaf.setDirty();
    
    return new DebugLeaf(pageLeaf);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _table + "]";
  }
  
  class DebugLeaf {
    private final PageLeafImpl _leaf;
    
    DebugLeaf(PageLeafImpl leaf)
    {
      _leaf = leaf;
    }
    
    public void put(RowCursor cursor)
    {
      RowCursor workCursor = _table.cursor();
      
      _leaf.put(_table, _table.getPageActor(), cursor, workCursor, 
                null, PutType.PUT, Result.ignore());
    }
    
    public void insert()
    {
      _table.getPageActor().addLoadedPage(_leaf);
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _leaf + "]";
    }
  }
}
