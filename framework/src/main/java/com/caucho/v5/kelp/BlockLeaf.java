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
import java.util.Set;

import com.caucho.v5.io.IoUtil;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.L10N;

/**
 * btree-based node.
 * 
 * Rows grow up from the block tail.
 * Inline blobs grow down from the block head.
 * 
 * The block is full when rowHead meets blobTail.
 * 
 * <pre><code>
 * blobs (blobTail) ... (rowHead) rows
 * </code></pre>
 */
class BlockLeaf
{
  private static final L10N L = new L10N(BlockLeaf.class);
  
  static final int BLOCK_SIZE = 8192;
  
  static final int CODE_MASK = Page.CODE_MASK;
  static final int INSERT = Page.INSERT;
  static final int INSERT_DEAD = Page.INSERT_DEAD;
  static final int REMOVE = Page.REMOVE;
  
  private final long _id;
  private final byte []_buffer;

  private volatile int _blobTail = 0;
  private volatile int _rowHead = BLOCK_SIZE;
  
  private int _rowSortHead = BLOCK_SIZE;
  private byte []_keyMinSort;
  private byte []_keyMaxSort;
  
  BlockLeaf(long id)
  {
    this(id, new byte[BLOCK_SIZE]);
  }
  
  private BlockLeaf(long id, byte []buffer)
  {
    _id = id;
    
    _buffer = buffer;
    
    assert(BLOCK_SIZE == _buffer.length);
  }
  
  final long getId()
  {
    return _id;
  }

  /**
   * Returns the block's buffer.
   */
  final byte[] getBuffer()
  {
    return _buffer;
  }
  
  /**
   * Returns remaining spec in the block
   */
  int getAvailable()
  {
    return _rowHead - _blobTail;
  }
  
  /**
   * Returns true for a compacted/sorted page.
   */
  boolean isCompact()
  {
    return _rowSortHead == _rowHead;
  }
  
  int getDeltaLeafCount(Row row)
  {
    return (_rowSortHead - _rowHead) / row.getLength();
  }
  
  /**
   * Inserts a new row into the block.
   * 
   * @param row the row schema
   * @param source the source bytes for the new row
   * @param sourceOffset the row offset into the source buffer
   * @param blobs the blobs from the source
   * 
   * @return false if the insert failed due to lack of space
   */
  int insert(Row row,
             byte []sourceBuffer, 
             int sourceOffset,
             BlobOutputStream []blobs)
  {
    int rowHead = _rowHead;
    int blobTail = _blobTail;
    
    int rowLength = row.getLength();
    
    rowHead -= rowLength;
    
    // return false if the block is full
    if (rowHead < blobTail) {
      return -1;
    }
    
    byte []buffer = _buffer;
    
    System.arraycopy(sourceBuffer, sourceOffset, 
                     buffer, rowHead,
                     rowLength);
    
    // XXX: timestamp
    buffer[rowHead] = (byte) ((buffer[rowHead] & ~CODE_MASK) | INSERT);

    blobTail = row.insertBlobs(buffer, rowHead, blobTail, blobs);

    // System.out.println("HEXL: " + Hex.toHex(buffer, rowFirst, rowLength));
    // if inline blobs can't fit, return false
    if (blobTail < 0) {
      return -1;
    }
    
    setBlobTail(blobTail);
    setRowHead(rowHead);
    
    validateBlock(row);
    
    return rowHead;
  }
  
  /**
   * Removes a key by adding a remove entry to the row.
   * 
   * The remove is represented as a delta entry so checkpoints can be
   * written asynchronously.
   * 
   * @return false if the new record cannot fit into the block.
   */
  boolean remove(RowCursor cursor)
  {
    int rowHead = _rowHead;
    int blobTail = _blobTail;
    
    rowHead -= cursor.getRemoveLength();
    
    if (rowHead < blobTail) {
      return false;
    }
    
    byte []buffer = _buffer;
    
    // buffer[rowHead] = REMOVE;
    
    cursor.getRemove(buffer, rowHead);
    // cursor.getKey(buffer, rowHead + ColumnState.LENGTH);
    
    setRowHead(rowHead);
    
    validateBlock(cursor.getRow());    
    
    return true;
  }

  /**
   * Searches for a row matching the cursor's key in the block.
   * If the key is found, fill the cursor with the row. 
   */
  int findAndFill(RowCursor cursor)
  {
    int ptr = find(cursor);

    if (ptr >= 0) {
      cursor.setRow(_buffer, ptr);
      cursor.setLeafBlock(this, ptr);
    }
    
    return ptr;
  }

  /**
   * Searches for a row matching the cursor's key.
   */
  int find(RowCursor cursor)
  {
    int rowOffset = _rowHead;
    int sortOffset = _rowSortHead;
    int rowLength = cursor.getLength();
    
    int removeLength = cursor.getRemoveLength();
    
    byte []buffer = _buffer;

    while (rowOffset < sortOffset) {
      int code = buffer[rowOffset] & CODE_MASK;

      switch (code) {
      case INSERT:
        if (cursor.compareKeyRow(buffer, rowOffset) == 0) {
          return rowOffset;
        }
        else {
          rowOffset += rowLength;
          break;
        }
        
      case INSERT_DEAD:
        rowOffset += rowLength;
        break;
        
      case REMOVE:
        if (cursor.compareKeyRemove(buffer, rowOffset) == 0) {
          // return PageLeafImpl.INDEX_REMOVED;
          return rowOffset;
        }
        else {
          rowOffset += removeLength;
          break;
        }
        
      default:
        throw new IllegalStateException(L.l("Corrupted block {0} offset {1} code {2}\n",
                                            this, rowOffset, code));
      }
    }
    
    if (sortOffset < BLOCK_SIZE) {
      return findSorted(cursor);
    }
    
    return PageLeafImpl.INDEX_UNMATCH;
  }
  
  private int findSorted(RowCursor cursor)
  {
    int rowOffset = _rowSortHead;
    int rowLength = cursor.getLength();
    
    int cmp = cursor.compareKey(_keyMinSort, 0);

    if (cmp < 0) {
      return PageLeafImpl.INDEX_UNMATCH;
    }
    else if (cmp == 0) {
      return BLOCK_SIZE - rowLength;
    }
    
    cmp = cursor.compareKey(_keyMaxSort, 0);
    
    if (cmp > 0) {
      return PageLeafImpl.INDEX_UNMATCH;
    }
    else if (cmp == 0) {
      return rowOffset;
    }
    
    int length = (BLOCK_SIZE - rowOffset) / rowLength - 2;
    
    rowOffset += rowLength;
    
    while (length > 0) {
      int pivot = length / 2;
      
      int pivotOffset = rowOffset + pivot * rowLength;
      
      cmp = cursor.compareKeyRow(_buffer, pivotOffset);
      
      if (cmp == 0) {
        return pivotOffset;
      }
      else if (cmp < 0) {
        rowOffset = pivotOffset + rowLength;
        length = length - pivot - 1;
      }
      else {
        length = pivot;
      }
    }
    
    return PageLeafImpl.INDEX_UNMATCH;
  }

  /**
   * Finds the row with the smallest key larger than minCursor
   * and fills the cursor.
   */
  boolean first(RowCursor minCursor, 
                RowCursor resultCursor, 
                boolean isMatch)
  {
    int ptr = _rowHead;
    int rowLength = resultCursor.getLength();
    int removeLength = resultCursor.getRemoveLength();
    int sortOffset = _rowSortHead;
    
    byte []buffer = _buffer;

    while (ptr < sortOffset) {
      int code = buffer[ptr] & CODE_MASK;
      
      int minCmp;
      int cmp;

      switch (code) {
      case INSERT:
        if ((minCmp = minCursor.compareKeyRow(buffer, ptr)) <= 0
            && ((cmp = resultCursor.compareKeyRow(buffer, ptr)) > 0
                || cmp == 0 && ! isMatch)) {
          fillMatch(ptr, resultCursor);
          
          if (minCmp == 0) {
            return true;
          }
          
          isMatch = true;
        }

        ptr += rowLength;
        break;
        
      case INSERT_DEAD:
        ptr += rowLength;
        break;
        
      case REMOVE:
        if ((minCmp = minCursor.compareKeyRemove(buffer, ptr)) <= 0
            && ((cmp = resultCursor.compareKeyRemove(buffer, ptr)) > 0
                || cmp == 0 && ! isMatch)) {
          resultCursor.setRemove(buffer, ptr);
          //resultCursor.setKey(buffer, ptr + 1);
          
          if (minCmp == 0) {
            return true;
          }
          
          isMatch = true;
        }

        ptr += removeLength;
        break;
        
      default:
        System.out.println("BROKEN_ENTRY:");
        return false;
      }
    }

    if (sortOffset < BLOCK_SIZE) {
      return findFirstSorted(minCursor, resultCursor, isMatch);
    }
    else {
      return isMatch;
    }
  }
  
  /**
   * In a sorted block, find the minimum key less than the currentResult
   * and greater than the min.
   * 
   * The sort is reverse order. The smallest value is at the end.
   * 
   * @param minCursor the minimum allowed key value for the search
   * @param resultCursor the current best match for the search
   * @param isMatch true if the current resultCursor is a match
   */
  private boolean findFirstSorted(RowCursor minCursor,
                                  RowCursor resultCursor,
                                  boolean isMatch)
  {
    int rowOffset = _rowSortHead;
    int rowLength = resultCursor.getLength();

    int cmp = resultCursor.compareKey(_keyMinSort, 0);

    if (cmp < 0 || cmp == 0 && isMatch) {
      return isMatch;
    }
    
    int minCmp = minCursor.compareKey(_keyMaxSort, 0);
    
    if (minCmp > 0) {
      return isMatch;
    }
    
    minCmp = minCursor.compareKey(_keyMinSort, 0);

    if (minCmp <= 0) {
      fillMatch(BLOCK_SIZE - rowLength, resultCursor);
      
      return true;
    }
    
    int length = (BLOCK_SIZE - rowOffset) / rowLength - 1;
    // rowOffset += rowLength;
    
    while (length > 0) {
      int pivot = length / 2;
      
      int pivotOffset = rowOffset + pivot * rowLength;

      // if minCursor is in this block, test if the row is greater
      minCmp = minCursor.compareKeyRow(_buffer, pivotOffset);
      
      if (minCmp == 0) {
        return fillMatch(pivotOffset, resultCursor);
      }
      else if (minCmp > 0) {
        length = pivot;

        continue;
      }
      
      // test row against current min
      cmp = resultCursor.compareKeyRow(_buffer, pivotOffset);
      
      if (cmp > 0) {
        // it's a better result, copy and search for smaller
        isMatch = true;
        
        fillMatch(pivotOffset, resultCursor);
      }
      
      // search for smaller
      rowOffset = pivotOffset + rowLength;
      length = length - pivot - 1;
    }
    
    return isMatch;
  }
  
  private boolean fillMatch(int ptr, RowCursor resultCursor)
  {
    resultCursor.setRow(_buffer, ptr);
    resultCursor.setLeafBlock(this, ptr);
    
    return true;
  }

  /**
   * Returns the first key in the block.
   * 
   * XXX: ??? since the keys are unsorted, why is this valuable
   */
  byte[] getFirstKey(TableKelp table)
  {
    int keyOffset = table.getKeyOffset();
    int keyLength = table.getKeyLength();
    
    int offset = _rowHead + keyOffset;
    
    byte []key = new byte[keyLength];
    byte []buffer = getBuffer();
    
    System.arraycopy(buffer, offset, key, 0, keyLength);
    
    return key;
  }

  /**
   * Fills the entry tree map with entries from the block.
   */
  void fillEntryTree(Set<PageLeafEntry> entries,
                     Row row)
  {
    int ptr = _rowHead;
    
    byte []buffer = _buffer;

    while (ptr < BLOCK_SIZE) {
      int code = buffer[ptr] & CODE_MASK;
      int len = getLength(code, row);

      if (code == INSERT || code == REMOVE) {
        PageLeafEntry entry = new PageLeafEntry(this, row, ptr, len, code);
        
        entries.add(entry);
      }
      
      ptr += len;
    }
  }
  
  /**
   * Validate the block, checking that row lengths and values are sensible.
   */
  void validateBlock(Row row)
  {
    if (! row.getDatabase().isValidate()) {
      return;
    }
    
    int rowHead = _rowHead;
    int blobTail = _blobTail;
        
    if (rowHead < blobTail) {
      throw new IllegalStateException(this 
                                      + " rowHead:" + rowHead
                                      + " blobTail:" + blobTail); 
    }
    
    int rowOffset = _rowHead;
    
    byte []buffer = _buffer;

    while (rowOffset < BLOCK_SIZE) {
      int code = buffer[rowOffset] & CODE_MASK;
      
      switch (code) {
      case INSERT:
        row.validate(buffer, rowOffset, rowHead, blobTail);
        break;
        
      case INSERT_DEAD:
      case REMOVE:
        break;
        
      default:
        throw new IllegalStateException(this + " " + rowOffset + " " + code + " unknown code");
      }
      
      int len = getLength(code, row);
      
      if (len < 0 || len + rowOffset > BLOCK_SIZE) {
        throw new IllegalStateException(this + " " + rowOffset + " code:" + code + " len:" + len + " invalid len");
      }
      
      rowOffset += len;
    }  
  }

  /**
   * Fill the entry set from the tree map.
   */
  void fillDeltaEntries(Set<PageLeafEntry> entries,
                        Row row,
                        int tail)
  {
    int rowOffset = _rowHead;
    
    byte []buffer = _buffer;

    while (rowOffset < tail) {
      int code = buffer[rowOffset] & CODE_MASK;

      int len = getLength(code, row);
      if (code == INSERT || code == REMOVE) {
        PageLeafEntry entry = new PageLeafEntry(this, row, rowOffset, len, code);
      
        entries.add(entry);
      }
      
      rowOffset += len;
    }
  }
  
  private int getLength(int code, Row row)
  {
    switch (code) {
    case INSERT:
    case INSERT_DEAD:
      return row.getLength();
      
    case REMOVE:
      return row.getRemoveLength();
      
    default:
      throw new IllegalStateException(String.valueOf(code));
    }
  }

  boolean addEntry(Row row, PageLeafEntry entry)
  {
    int rowHead = _rowHead;
    
    byte []buffer = _buffer;
    
    rowHead -= row.getLength();
    
    int blobTail = _blobTail;
    
    if (rowHead < blobTail) {
      return false;
    }
    
    blobTail = entry.copyTo(buffer, rowHead, blobTail);
    
    if (blobTail < 0) {
      return false;
    }
    
    setRowHead(rowHead);
    setBlobTail(blobTail);
    
    validateBlock(row);
    
    return true;
  }
  
  void setRowHead(int rowHead)
  {
    _rowHead = rowHead;
  }

  int getRowHead()
  {
    return _rowHead;
  }
  
  void setBlobTail(int blobTail)
  {
    _blobTail = blobTail;
  }

  int getBlobTail()
  {
    return _blobTail;
  }
  
  /**
   * Copy the block and reuse the buffer for compact() when the old block
   * is clean.
   */
  final BlockLeaf copy()
  {
    BlockLeaf block = new BlockLeaf(getId(), _buffer);
    
    block._blobTail = _blobTail;
    block._rowHead = _rowHead;
    block._rowSortHead = _rowSortHead;
    block._keyMinSort = _keyMinSort;
    block._keyMaxSort = _keyMaxSort;
    
    return block;
  }

  //
  // checkpoint
  //

  /**
   * Writes the block to the checkpoint stream.
   * 
   * Because of timing, the requested rowHead might be for an older
   * checkpoint of the block, if new rows have arrived since the request.
   * 
   * <pre><code>
   * b16 - inline blob length (blobTail)
   * &lt;n> - inline blob data
   * b16 - row data length (block_size - row_head)
   * &lt;m> - row data
   * </code></pre>
   * 
   * @param rowHead the requested row head
   */
  void writeCheckpointFull(OutputStream os, int rowHead)
    throws IOException
  {
    BitsUtil.writeInt16(os, _blobTail);
    os.write(_buffer, 0, _blobTail);
    
    rowHead = Math.max(rowHead, _rowHead);
    
    BitsUtil.writeInt16(os, rowHead);
    os.write(_buffer, rowHead, _buffer.length - rowHead);
  }

  void readCheckpointFull(InputStream is)
    throws IOException
  {
    _blobTail = BitsUtil.readInt16(is);
    
    if (_blobTail < 0 || _buffer.length < _blobTail) {
      throw new IllegalStateException("Invalid blob tail: " + _blobTail
                                      //+ " pos=" + (is.getPosition() - 2)
                                      + " " + this);
    }
    
    byte []buffer = _buffer;
    
    IoUtil.readAll(is, buffer, 0, _blobTail);
    
    setRowHead(BitsUtil.readInt16(is));
    
    int rowHead = getRowHead();
    
    if (rowHead < getBlobTail() || buffer.length < rowHead) {
      throw new IllegalStateException(L.l("Invalid row-head={0} blob-tail={1}",
                                          getRowHead(), getBlobTail()));
    }
    IoUtil.readAll(is, buffer, rowHead, buffer.length - rowHead);
    
    // validateBlock(row);    
  }

  /*
  BlockLeaf toSorted(Row row)
  {
    TempBuffer tBuf = _tBuf;
    _tBuf = null;
    
    byte []buffer = _buffer;
    _buffer = null;
    
    return new BlockLeafSorted(_id, tBuf, buffer,
                               getRowHead(), getBlobTail(),
                               row);
  }
  */
  BlockLeaf toSorted(Row row)
  {
    if (_rowHead == BLOCK_SIZE) {
      return this;
    }
    
    int keyLength = row.getKeyLength();
    int keyOffset = row.getKeyOffset();
    int rowLength = row.getLength();
    
    _rowSortHead = _rowHead;
    
    _keyMinSort = new byte[row.getKeyLength()];
    _keyMaxSort = new byte[row.getKeyLength()];
    
    System.arraycopy(_buffer, _rowSortHead + keyOffset,
                     _keyMaxSort, 0, 
                     keyLength);
    
    System.arraycopy(_buffer, BLOCK_SIZE - rowLength + keyOffset,
                     _keyMinSort, 0, 
                     keyLength);
    
                       
    return this;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + Long.toHexString(_id) + "]"
        + "@" + System.identityHashCode(this);
  }
}
