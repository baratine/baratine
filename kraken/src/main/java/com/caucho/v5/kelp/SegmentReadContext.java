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

import com.caucho.v5.kelp.segment.SegmentKelp;


/**
 * Read state while reloading the database.
 */
class SegmentReadContext
{
  private final TableKelp _table;
  private PageServiceImpl _pageActor;
  
  // private final HashMap<Long,PageLeafStub> _leafMap = new HashMap<>();

  SegmentReadContext(TableKelp table,
                          PageServiceImpl pageActor)
  {
    _table = table;
    _pageActor = pageActor;
  }
  
  boolean addLeaf(SegmentKelp segment,
               int pid, int nextPid, 
               int offset, int length)
  {
    Page page = _pageActor.getLeaf(pid);
    
    PageLeafStub stub;
    
    if (page instanceof PageLeafStub) {
      stub = (PageLeafStub) page;
      
      if (segment.getSequence() < stub.getSequence()) {
        return false;
      }
    }
    
    stub = new PageLeafStub(pid, nextPid, segment, offset, length);
    stub.setValid();
    
    _pageActor.addLoadedPage(stub);
    
    return true;
  }
  
  boolean addLeafDelta(SegmentKelp segment, 
                    int pid, int nextPid, 
                    int offset, int length)
  {
    Page page = _pageActor.getLeaf(pid);
    
    if (! (page instanceof PageLeafStub)) {
      return false;
    }
    
    PageLeafStub stub = (PageLeafStub) page;
    
    if (stub.getSegment() != segment) {
      return false;
    }
    
    stub.addDelta(_table, offset, length);
    
    return true;
  }
  
  boolean addTree(PageTree tree)
  {
    Page page = _pageActor.getPage(tree.getId());
    
    PageTree stub;
    
    if (page instanceof PageTree) {
      stub = (PageTree) page;
      
      if (tree.getSequence() < stub.getSequence()) {
        return false;
      }
    }
    
    _pageActor.addLoadedPage(tree);
    
    return true;
  }
  
  boolean addBlob(SegmentKelp segment,
                  int pid, int nextPid, 
                  int offset, int length)
  {
    Page page = _pageActor.getPage(pid);
    
    if (page != null) {
      if (segment.getSequence() < page.getSequence()) {
        return false;
      }
    }
    
    PageBlobStub stub = new PageBlobStub(pid, nextPid, segment, offset, length);
    
    _pageActor.addLoadedPage(stub);
    
    return true;
  }
  
  boolean addBlobFree(SegmentKelp segment,
                      int pid, int nextPid, 
                      int offset, int length)
  {
    Page page = _pageActor.getPage(pid);
    
    if (page != null) {
      if (segment.getSequence() < page.getSequence()) {
        return false;
      }
    }
    
    page = new PageBlobFree(pid, nextPid, segment.getSequence(), null);
    
    _pageActor.addLoadedPage(page);
    
    return true;
  }
}
