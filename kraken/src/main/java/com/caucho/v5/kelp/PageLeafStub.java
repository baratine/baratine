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
import java.lang.ref.WeakReference;

import com.caucho.v5.baratine.InService;
import com.caucho.v5.kelp.segment.InSegment;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.kelp.segment.SegmentStream;
import com.caucho.v5.vfs.ReadStream;

/**
 * Stub of a btree-based node
 */
class PageLeafStub extends PageLeaf
{
  private final SegmentKelp _segment;
  private final int _offset;
  private final int _length;
  
  private int []_delta;
  private int _deltaTail;
  
  private byte []_minKey;
  private byte []_maxKey;
  
  //private SoftReference<PageLeafImpl> _leafRef;
  private WeakReference<PageLeafImpl> _leafRef;
  
  // strong reference for writes
  //private PageLeafImpl _leaf;
  private boolean _isValid;
  
  PageLeafStub(int id, 
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
  
  void setLeafRef(PageLeafImpl leaf)
  {
    if (leaf != null) {
      //_leafRef = new SoftReference<>(leaf);
      _leafRef = new WeakReference<>(leaf);
      
      leaf.setStub(this);
    }
  }
  
  void setLeafDirty(PageLeafImpl leaf)
  {
    // _leaf = leaf;
  }
  
  @Override
  byte []getMinKey()
  {
    return _minKey;
  }
  
  @Override
  byte []getMaxKey()
  {
    return _maxKey;
  }
  
  @Override
  final boolean isStub()
  {
    return true;
  }
  
  @Override
  public final boolean isValid()
  {
    return _isValid;
  }
  
  @Override
  public final void setValid()
  {
    _isValid = true;
  }
  
  @Override
  public final int getSize()
  {
    PageLeafImpl leaf = null;//_leaf;
    
    if (leaf != null) {
      return leaf.getSize();
    }
    else {
      return 0;
    }
  }

  @Override
  final SegmentKelp getSegment()
  {
    return _segment;
  }
  
  final long getOffset()
  {
    return _offset;
  }
  
  final long getLength()
  {
    return _length;
  }

  boolean allowDelta()
  {
    return _delta == null || _deltaTail < _delta.length;
  }
  
  /**
   * Adds a delta record to the leaf stub. 
   */
  void addDelta(TableKelp db, int offset, int length)
  {
    if (_delta == null) {
      _delta = new int[2 * db.getDeltaMax()];
    }
    
    _delta[_deltaTail++] = offset;
    _delta[_deltaTail++] = length;
  }

  @Override
  PageLeafImpl load(TableKelp table,
                    PageServiceImpl pageActor)
  {
    // load is multithreaded because of the @Direct call to get() 
    synchronized (this) {
      PageLeafImpl leaf = null; // _leaf;
      
      //SoftReference<PageLeafImpl> leafRef = _leafRef;
      WeakReference<PageLeafImpl> leafRef = _leafRef;
      
      /*
      if (leaf != null) {
        return leaf;
      }
      */
    
      if (leafRef != null) {
        leaf = leafRef.get();
      
        if (leaf != null) {
          return leaf;
        }
      }

      try (InSegment sIn = table.openReader(_segment)) {
        ReadStream is = sIn.in();
      
        //is.setPosition(_offset);

        try (InputStream zIs = sIn.inCompress(_offset, _length)) {
          leaf = PageLeafImpl.readCheckpointFull(table,
                                                 pageActor,
                                                 zIs,
                                                 getId(), getNextId(),
                                                 getSequence());
        } catch (IllegalStateException e) {
          throw new IllegalStateException(_segment + ": " + e 
                                          + "\n  seq=" + getSequence()
                                          + " offset=" + Long.toHexString(_offset)
                                          + " len=" + _length
                                          + " " + this,
                                          e);
        }

        for (int i = 0; i < _deltaTail; i += 2) {
          int offset = _delta[i + 0];
          int length = _delta[i + 1];
          
          is.setPosition(offset);
          
          leaf.readCheckpointDelta(table, pageActor, is, length);
        }
        
        leaf.setStub(this);
        leaf.clearDirty();
        
        setLeafRef(leaf);
        
        //_leafRef = new SoftReference<PageLeafImpl>(leaf);
        // _leafRef = new WeakReference<PageLeafImpl>(leaf);
        // _leaf = leaf;
        
        // pageActor.addLruSize(leaf.getSize());
        
        return leaf;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  void loadKeys(TableKelp table)
  {
    if (_minKey != null) {
      return;
    }
    
    try (InSegment sIn = table.openReader(_segment)) {
      ReadStream is = sIn.in();
  
      is.setPosition(_offset);

      byte []minKey = new byte[table.getKeyLength()];
      byte []maxKey = new byte[table.getKeyLength()];
      
      is.readAll(minKey, 0, minKey.length);
      is.readAll(maxKey, 0, maxKey.length);
      
      _minKey = minKey;
      _maxKey = maxKey;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  boolean isSwappable()
  {
    return true;
  }
  
  @InService(PageServiceImpl.class)
  void writeImpl(TableKelp db,
                 PageServiceImpl pageActor,
                 TableWriterService readWrite,
                 SegmentStream sOut,
                 long oldSequence,
                 int saveLength,
                 int tail)
  {
    Thread.dumpStack();
  }

  @Override
  void sweepStub(PageServiceImpl tableServiceImpl)
  {
    //_leaf = null;
  }

  public PageLeafStub copyToCompact(PageLeafImpl newLeaf)
  {
    PageLeafStub stubCopy
      = new PageLeafStub(getId(), getNextId(), getSegment(), _offset, _length);

    stubCopy._minKey = _minKey; 
    stubCopy._maxKey = _maxKey;
    
    int []deltaOld = _delta;
    if (deltaOld != null) {
      int []delta = new int[deltaOld.length];
      System.arraycopy(_delta, 0, delta, 0, delta.length);

      stubCopy._delta = delta;
      stubCopy._deltaTail = _deltaTail;
    }
    
    stubCopy.setLeafRef(newLeaf);
    newLeaf.setStub(stubCopy);
    
    //if (isDirty()) {
    //  stubCopy.setLeafDirty();
    //}
    
    return stubCopy;
  }

  /**
   * On a segment free, validate that the page has no references.
   */
  @Override
  boolean validateFree(SegmentKelp segment)
  {
    if (segment == _segment && isSwappable()) {
      PageLeafImpl leaf = null;
      
      if (_leafRef != null) {
        leaf = _leafRef.get();
      }
      
      System.out.println("\nSegment-free failed validation: " + this + " " + segment + " "+ leaf + "\n");
      
      return false;
    }
    
    return true;
  }
  
  public String toString()
  {
    return (getClass().getSimpleName()
           + "[" + getId() 
           + ",seq=" + getSequence()
           + ",off=" + getOffset()
           + ",len=" + getLength() + "]");
  }
}
