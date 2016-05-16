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

import java.util.TreeSet;

import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.kelp.PageTree.TreeEntry;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Hex;

/**
 * btree-based node
 */
public class BlockTree extends Block
{
  private int _sortHead = BLOCK_SIZE;
  private byte[] _keyMinSort;
  private byte[] _keyMaxSort;
  
  BlockTree(long id)
  {
    super(id);
    
    setIndex(BLOCK_SIZE);
  }
  
  BlockTree(long id, TempBuffer tBuf, int length)
  {
    super(id, tBuf, length);
    
    setIndex(BLOCK_SIZE);
  }
  
  int getDeltaTreeCount(Row row)
  {
    return (_sortHead - getIndex()) / row.getTreeItemLength();
  }
  
  boolean insert(byte []minKey, byte []maxKey, int pid)
  {
    if (pid <= 0) {
      throw new IllegalStateException();
    }
    
    return addCode(INSERT, minKey, maxKey, pid);
  }
  
  boolean remove(byte []minKey, byte []maxKey)
  {
    return addCode(REMOVE, minKey, maxKey, -1);
  }

  /*
  boolean remove(byte []minKey, byte []maxKey)
  {
    int len = minKey.length + maxKey.length + 9;
    
    byte []buffer = getBuffer();
    
    boolean isRemove = false;
    
    int minOffset = 1;
    int maxOffset = minOffset + minKey.length;
    
    for (int ptr = getIndex(); ptr < BLOCK_SIZE; ptr += len) {
      if (compareKey(minKey, buffer, ptr + minOffset) == 0
          && compareKey(maxKey, buffer, ptr + maxOffset) == 0) {
        buffer[ptr] = REMOVE;
        isRemove = true;
      }
    }
    
    return isRemove;
  }
  */
  
  boolean addCode(int code, byte []minKey, byte []maxKey, int pid)
  {
    int len = minKey.length + maxKey.length + 5;
    
    int index = getIndex() - len;
    
    int ptr = index;
    
    if (ptr < 0) {
      return false;
    }
    
    byte []buffer = getBuffer();
    
    buffer[ptr] = (byte) code;
    ptr += 1;
    
    System.arraycopy(minKey, 0, buffer, ptr, minKey.length);
    ptr += minKey.length;
    
    System.arraycopy(maxKey, 0, buffer, ptr, maxKey.length);
    ptr += maxKey.length;

    BitsUtil.writeInt(buffer, ptr, pid);
    
    setIndex(index);

    /*
    System.out.println("ADDC: " + code + " " + pid
                       + " " + Hex.toShortHex(minKey)
                       + " " + Hex.toShortHex(maxKey));
                       */
    
    return true;
  }

  int get(Row row, byte []rowBuffer, int keyOffset)
  {
    byte []buffer = getBuffer();
    
    int keyLength = row.getKeyLength();
    int len = row.getTreeItemLength();
    
    int minOffset = 1;
    int maxOffset = minOffset + keyLength;
    int valueOffset = maxOffset + keyLength;
    int sortHead = _sortHead;
    
    //System.out.println("GET: " + getIndex() + " " + sortHead + " " + len);
    
    for (int ptr = getIndex(); ptr < sortHead; ptr += len) {
      int code = buffer[ptr] & 0xff;
      
      if (Row.compareKey(rowBuffer, keyOffset, buffer, ptr + minOffset, keyLength) >= 0
          && Row.compareKey(rowBuffer, keyOffset, buffer, ptr + maxOffset, keyLength) <= 0) {
        //System.out.println("CURSE: " + cursor);
        if (code == INSERT) {
          return BitsUtil.readInt(buffer, ptr + valueOffset);
        }
        /* remove is a marker for compaction. For sync purposes, so a remove
         * doesn't necessarily imply there's no further match. 
        else {
          return 0;
        }
        */
      }
    }
  
    if (_sortHead < BLOCK_SIZE) {
      //System.out.println("SORTL:");
      return getSorted(row, rowBuffer, keyOffset);
    }
    else {
      return 0;
    }
  }

  int getSorted(Row row, byte []rowBuffer, int keyOffset)
  {
    byte []buffer = getBuffer();
    
    int keyLength = row.getKeyLength();
    int itemLen = row.getTreeItemLength();
    
    int length = (BLOCK_SIZE - _sortHead) / itemLen;
    int offset = _sortHead;
    
    int minOffset = 1;
    int maxOffset = minOffset + keyLength;
    int valueOffset = maxOffset + keyLength;
    
    if (Row.compareKey(rowBuffer, keyOffset, _keyMinSort, 0, keyLength) < 0) {
      return 0;
    }
    
    if (Row.compareKey(rowBuffer, keyOffset, _keyMaxSort, 0, keyLength) > 0) {
      return 0;
    }
    
    while (length > 0) {
      int pivot = length / 2;
      int pivotOffset = offset + pivot * itemLen;
      
      if (Row.compareKey(rowBuffer, keyOffset, 
                         buffer, pivotOffset + minOffset, 
                         keyLength) < 0) {
        offset = pivotOffset + itemLen;
        length = length - pivot - 1;
      }
      else if (Row.compareKey(rowBuffer, keyOffset,
                              buffer, pivotOffset + maxOffset,
                              keyLength) > 0) {
        length = pivot;
      }
      else {
        return BitsUtil.readInt(buffer, pivotOffset + valueOffset);
      }
    }
    
    return 0;
  }

  int get(byte []key)
  {
    byte []buffer = getBuffer();
    
    int keyLength = key.length;
    int len = 2 * keyLength + 5;
    
    for (int ptr = getIndex(); ptr < BLOCK_SIZE; ptr += len) {
      int code = buffer[ptr] & 0xff;
      
      if (code == 0) {
        return 0;
      }
      
      int min = ptr + 1;
      int max = min + keyLength;

      if (compareKey(key, buffer, min) >= 0
          && compareKey(key, buffer, max) <= 0) {
        int valueOffset = max + keyLength;
        
        if (code == INSERT) {
          return BitsUtil.readInt(buffer, valueOffset);
        }
        /*
        else {
          return 0;
        }
        */
      }
    }
    
    return 0;
  }

  boolean getMinKey(byte []testKey, byte []minKey)
  {
    byte []buffer = getBuffer();
    
    int keyLength = testKey.length;
    int len = 2 * keyLength + 5;
    
    boolean isKey = true;
    
    for (int ptr = getIndex(); ptr < BLOCK_SIZE; ptr += len) {
      int code = buffer[ptr] & 0xff;
      
      if (code == 0) {
        return isKey;
      }
      
      int min = ptr + 1;
      int max = min + keyLength;

      if (code == INSERT 
          && compareKey(testKey, buffer, min) >= 0
          && compareKey(testKey, buffer, max) <= 0) {
        isKey = true;
        
        if (compareKey(minKey, buffer, min) > 0) {
          System.arraycopy(buffer, min, minKey, 0, keyLength);
        }
      }
    }
    
    return isKey;
  }

  boolean getMaxKey(byte []testKey, byte []maxKey)
  {
    byte []buffer = getBuffer();
    
    int keyLength = testKey.length;
    int len = 2 * keyLength + 5;
    
    boolean isKey = true;
    
    for (int ptr = getIndex(); ptr < BLOCK_SIZE; ptr += len) {
      int code = buffer[ptr] & 0xff;
      
      if (code == 0) {
        return isKey;
      }
      
      int min = ptr + 1;
      int max = min + keyLength;

      if (code == INSERT
          && compareKey(testKey, buffer, min) >= 0
          && compareKey(testKey, buffer, max) <= 0) {
        isKey = true;
        
        if (compareKey(maxKey, buffer, max) < 0) {
          System.arraycopy(buffer, max, maxKey, 0, keyLength);
        }
      }
    }
    
    return isKey;
  }
  
  /*
  void write(MaukaDatabase db, WriteStream os, int index)
    throws IOException
  {
    byte []buffer = getBuffer();
    
    int keyLength = db.getKeyLength();
    int len = 2 * keyLength + 5;
    
    for (int ptr = index; ptr < BLOCK_SIZE; ptr += len) {
      int code = buffer[ptr] & 0xff;
      
      //if (code != INSERT) {
      //  continue;
      //}

      int pid = BitsUtil.readInt(buffer, ptr + len - 4);
      
      if (pid == 0) {
        throw new IllegalStateException();
      }
      
      os.write(buffer, ptr + 1, len - 1);
    }
  }
  */

  void fillEntries(TableKelp table,
                   TreeSet<TreeEntry> set,
                   int index)
  {
    byte []buffer = getBuffer();
    
    int keyLength = table.getKeyLength();
    int len = table.row().getTreeItemLength();
    
    for (int ptr = index; ptr < BLOCK_SIZE; ptr += len) {
      int code = buffer[ptr] & 0xff;
      
      int min = ptr + 1;
      int max = min + keyLength;
      
      int valueOffset = max + keyLength;

      byte []minKey = new byte[keyLength];
      byte []maxKey = new byte[keyLength];
      
      System.arraycopy(buffer, min, minKey, 0, keyLength);
      System.arraycopy(buffer, max, maxKey, 0, keyLength);
      
      int pid = BitsUtil.readInt(buffer, valueOffset);
      TreeEntry entry = new TreeEntry(minKey, maxKey, code, pid);
      
      set.add(entry);
    }
  }

  public void copyTo(TableKelp table,
                     PageTree tree)
  {
    Row row = table.row();
    
    int keyLength = row.getKeyLength();
    int len = row.getTreeItemLength();
    
    byte []minKey = new byte[keyLength];
    byte []maxKey = new byte[keyLength];
    
    
    byte []buffer = getBuffer();
    
    for (int ptr = getIndex(); ptr < BLOCK_SIZE; ptr += len) {
      int code = buffer[ptr] & 0xff;
      
      if (code != INSERT) {
        continue;
      }
      
      int index = ptr + 1;
      
      System.arraycopy(buffer, index, minKey, 0, keyLength);
      index += keyLength;
      
      System.arraycopy(buffer, index, maxKey, 0, keyLength);
      index += keyLength;
      
      int pid = BitsUtil.readInt(buffer, index);
      if (pid <= 0) {
        throw new IllegalStateException();
      }
      
      tree.insert(minKey, maxKey, pid);
    }
  }

  public BlockTree copy(int id)
  {
    BlockTree newTree = new BlockTree(id);
    
    System.arraycopy(getBuffer(), 0, newTree.getBuffer(), 0,
                     getBuffer().length);
    
    newTree.setIndex(getIndex());
    
    return newTree;
  }

  /*
  BlockTree copy(int newPid)
  {
    BlockTree newBlock = new BlockTree(newPid);
    
    System.arraycopy(getBuffer(), 0, newBlock.getBuffer(), 0, BLOCK_SIZE);
    
    newBlock.setIndex(getIndex());
    
    return newBlock;
  }
  */
  
  BlockTree toSorted(Row row)
  {
    if (getIndex() == BLOCK_SIZE) {
      return this;
    }
    
    int keyLength = row.getKeyLength();
    
    _sortHead = getIndex();
    
    _keyMinSort = new byte[keyLength];
    _keyMaxSort = new byte[keyLength];
    
    int minOffset = 1;
    int maxOffset = minOffset + keyLength;
    int tupleLength = row.getTreeItemLength();
    
    byte []buffer = getBuffer();
    
    System.arraycopy(buffer, _sortHead + maxOffset,
                     _keyMaxSort, 0, 
                     keyLength);
    
    System.arraycopy(buffer, BLOCK_SIZE - tupleLength + minOffset,
                     _keyMinSort, 0, 
                     keyLength);
    
    return this;
  }
  
  static int compareKey(byte []testKey, byte []buffer, int offset)
  {
    int length = testKey.length;
    
    for (int i = 0; i < length; i++) {
      int cmp = (testKey[i] & 0xff) - (buffer[offset + i] & 0xff);
      
      if (cmp != 0) {
        return cmp;
      }
    }
    
    return 0;
  }
}
