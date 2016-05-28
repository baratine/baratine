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

import com.caucho.v5.baratine.InService;
import com.caucho.v5.util.Hex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * btree-based node
 */
public class PageTree extends Page
{
  private BlockTree []_blocks;
  private final byte []_minKey;
  private final byte []_maxKey;
  
  PageTree(int id, 
           int nextId,
           long sequence,
           byte []minKey,
           byte []maxKey,
           BlockTree []blocks)
  {
    super(id, nextId, sequence);
    
    _blocks = blocks;
    _minKey = minKey;
    _maxKey = maxKey;
  }
  
  PageTree(int id, 
           int nextId,
           long sequence,
           byte []minKey,
           byte []maxKey,
           ArrayList<BlockTree> blocks)
  {
    this(id, nextId, sequence, minKey, maxKey,
         blocks.toArray(new BlockTree[blocks.size()]));
  }
  
  PageTree(int id, 
           int nextId,
           long sequence,
           byte []minKey,
           byte []maxKey)
  {
    this(id, nextId, sequence, minKey, maxKey,
         new BlockTree[] { new BlockTree(id) });
  }
  
  @Override
  final Type getType()
  {
    return Type.TREE;
  }
  
  @Override
  public int size()
  {
    return _blocks.length * DatabaseKelp.BLOCK_SIZE;
  }
  
  byte []getMinKey()
  {
    return _minKey;
  }
  
  byte []getMaxKey()
  {
    return _maxKey;
  }
  
  int getDeltaTreeCount(Row row)
  {
    int count = 0;
    
    for (BlockTree block : _blocks) {
      count += block.getDeltaTreeCount(row);
    }
    
    return count;
  }

  int get(Row row, byte []rowBuffer, int keyOffset)
  {
    for (BlockTree block : _blocks) {
      int value = block.get(row, rowBuffer, keyOffset);
      
      if (value > 0) {
        return value;
      }
    }
    
    return 0;
  }
  
  int get(byte []key)
  {
    for (BlockTree block : _blocks) {
      int value = block.get(key);
      
      if (value > 0) {
        return value;
      }
    }
    
    return 0;
  }
  
  void insert(byte []minKey, byte []maxKey, int pid)
  {
    setDirty();
    
    /*
    if (getId() == 807) {
      System.out.println("INSERT: " + Hex.toShortHex(minKey) + " " + Hex.toShortHex(maxKey)
                         + " " + pid);
    }
    */
    
    BlockTree topBlock = _blocks[0];
    
    if (! topBlock.insert(minKey, maxKey, pid)) {
      topBlock = extendBlocks();
      
      topBlock.insert(minKey, maxKey, pid);
    }
  }
  
  void remove(byte []minKey, byte []maxKey)
  {
    setDirty();
    
    /*
    if (getId() == 807) {
      System.out.println("REMOVE: " + Hex.toShortHex(minKey) + " " + Hex.toShortHex(maxKey));
    }
    */
    
    BlockTree topBlock = _blocks[0];
    
    if (! topBlock.remove(minKey, maxKey)) {
      topBlock = extendBlocks();
      
      topBlock.remove(minKey, maxKey);
    }
  }

  byte[] getMinKey(byte[] testKey)
  {
    byte []minKey = new byte[testKey.length];
    
    Arrays.fill(minKey, (byte) 0xff);
    
    boolean isKey = false;
    
    for (BlockTree block : _blocks) {
      if (block.getMinKey(testKey, minKey)) {
        isKey = true;
      }
    }
    
    if (isKey) {
      return minKey;
    }
    else {
      return null;
    }
  }

  byte[] getMaxKey(byte[] testKey)
  {
    byte []maxKey = new byte[testKey.length];
    
    Arrays.fill(maxKey, (byte) 0);
    
    boolean isKey = false;
    
    for (BlockTree block : _blocks) {
      if (block.getMaxKey(testKey, maxKey)) {
        isKey = true;
      }
    }
    
    if (isKey) {
      return maxKey;
    }
    else {
      return null;
    }
  }

  BlockTree extendBlocks()
  {
    BlockTree []newBlocks = new BlockTree[_blocks.length + 1];
    System.arraycopy(_blocks, 0, newBlocks, 1, _blocks.length);
    
    newBlocks[0] = new BlockTree(getId());
    
    _blocks = newBlocks;
    
    return newBlocks[0];
  }
  
  @InService(PageServiceSync.class)
  PageTree compact(TableKelp table)
  {
    PageTree tree = new PageTree(getId(), getNextId(), getSequence(),
                                 getMinKey(), getMaxKey());
    
    long lastPid = -1;
    TreeEntry lastEntry = null;
    
    for (TreeEntry entry : fillEntries(table)) {
      if (entry.isInsert()) {
        tree.insert(entry.getMinKey(), entry.getMaxKey(), entry.getPid());
        
        if (entry.getPid() == lastPid) {
          System.out.println("Unexpected duplicate page: "
                             + entry.getPid() + " " + entry + " " + lastEntry);
        }
        
        lastPid = entry.getPid();
        lastEntry = entry;
        
        /*
        System.out.println("  " + Hex.toShortHex(entry.getMinKey())
                           + " " + Hex.toShortHex(entry.getMaxKey())
                           + " " + entry.getPid());
                           */
      }
    }
    
    tree.setDirty();
    
    Row row = table.row();
    
    tree.toSorted(row);
    /*
    for (BlockTree block : _blocks) {
      block.toSorted(row);
    }
    */
    
    return tree;
  }

  @InService(PageServiceImpl.class)
  SplitTree split(TableKelp table,
                  PageServiceImpl pageActor)
  {
    int maxPage = table.getMaxNodeLength();
    
    if (_blocks.length * BLOCK_SIZE < maxPage) {
      return null;
    }

    TreeSet<TreeEntry> set = fillEntries(table);
    
    int size = set.size();

    if (size <= 1) {
      return null;
    }
    
    byte []minKey = getMinKey();
    byte []maxKey = getMaxKey();
    
    int nextPid = pageActor.nextPid();
    
    ArrayList<BlockTree> firstBlocks = new ArrayList<>();
    BlockTree block = new BlockTree(getId());
    firstBlocks.add(block);
    
    Iterator<TreeEntry> iter = set.iterator();
    while (iter.hasNext()) {
      TreeEntry entry = iter.next();
      
      if (! entry.isInsert()) {
        iter.remove();
      }
    }
    
    size = set.size();
    
    iter = set.iterator();
    
    byte []splitKey = maxKey;

    for (int i = 0; i < size / 2 && iter.hasNext(); i++) {
      TreeEntry entry = iter.next();
      
      while (! block.insert(entry.getMinKey(), 
                            entry.getMaxKey(), 
                            entry.getPid())) {
        block = new BlockTree(getId());
        firstBlocks.add(block);
      }
      
      splitKey = entry.getMaxKey();
    }
    ArrayList<BlockTree> restBlocks = new ArrayList<>();
    block = new BlockTree(nextPid);
    restBlocks.add(block);
    
    while (iter.hasNext()) {
      TreeEntry entry = iter.next();
      
      while (! block.insert(entry.getMinKey(), 
                            entry.getMaxKey(), 
                            entry.getPid())) {
        block = new BlockTree(nextPid);
        restBlocks.add(block);
      }
    }
    
    PageTree firstPage = new PageTree(getId(), 
                                      nextPid,
                                      getSequence(),
                                      minKey,
                                      splitKey,
                                      firstBlocks);
    
    firstPage.setDirty();
    firstPage.toSorted(table.row());
    
    byte []nextKey = incrementKey(splitKey);
    
    PageTree restPage = new PageTree(nextPid,
                                     getNextId(),
                                     getSequence(),
                                     nextKey,
                                     maxKey,
                                     restBlocks);
    
    restPage.setDirty();
    restPage.toSorted(table.row());
    
    return new SplitTree(firstPage, restPage);
  }
  
  @Override
  int getSaveTail()
  {
    BlockTree blocks[] = _blocks;
    
    int index = blocks[0].getIndex();
    
    int tail;
    
    if (index < BLOCK_SIZE) {
      tail = blocks.length * BLOCK_SIZE + blocks[0].getIndex();
    }
    else {
      tail = (blocks.length - 1) * BLOCK_SIZE;
    }

    return tail;
  }
  
  /**
   * Tree is not written to the store. It's recreated on load.
   */
  @Override
  public boolean isDirty()
  {
    return false;
  }
  
  /*
  @Override
  Page writeCheckpoint(TableKelp table, 
                       SegmentWriter sOut,
                       long oldSequence,
                       int saveLength,
                       int saveTail,
                       int saveSequence)
    throws IOException
  {
    WriteStream os = sOut.getOut();
    
    os.write(_minKey);
    os.write(_maxKey);
    
    TreeSet<TreeEntry> entrySet = fillEntries(table, saveTail);

    for (TreeEntry entry : entrySet) {
      if (! entry.isInsert()) {
        continue;
      }
      
      os.write(entry.getMinKey());
      os.write(entry.getMaxKey());
      BitsUtil.writeInt(os, entry.getPid());
    }
    
    return this;
  }
  */
  
  TreeSet<TreeEntry> fillEntries(TableKelp table)
  {
    TreeSet<TreeEntry> set = new TreeSet<>();
    
    for (BlockTree block : _blocks) {
      block.fillEntries(table, set, block.getIndex());
    }
    
    return set;
  }
  
  TreeSet<TreeEntry> fillEntries(TableKelp table, int saveTail)
  {
    TreeSet<TreeEntry> set = new TreeSet<>();
    
    int count = saveTail / BLOCK_SIZE;
    int offset = saveTail % BLOCK_SIZE;
    
    if (count == 0) {
      return set;
    }
    
    BlockTree []blocks = _blocks;
    
    int index = blocks.length - count; 
        
    BlockTree block = blocks[index];
    block.fillEntries(table, set, Math.max(offset, block.getIndex()));
    
    for (int i = index + 1; i < blocks.length; i++) {
      block = blocks[i];
      
      block.fillEntries(table, set, block.getIndex());
    }
    
    return set;
  }
  
  void toSorted(Row row)
  {
    for (BlockTree block : _blocks) {
      block.toSorted(row);
    }
  }

  /*
  @InService(TableServiceImpl.class)
  static PageTree read(TableKelp table,
                       TableServiceImpl pageActor,
                       ReadStream is, 
                       int length, 
                       int pid,
                       int nextPid,
                       long sequence)
    throws IOException
  {
    byte []minKey = new byte[table.getKeyLength()];
    byte []maxKey = new byte[table.getKeyLength()];
    
    is.readAll(minKey, 0, minKey.length);
    is.readAll(maxKey, 0, maxKey.length);
    
    length -= minKey.length + maxKey.length;
    
    PageTree page = new PageTree(pid, nextPid, sequence, minKey, maxKey);
    
    int len = 2 * table.getKeyLength() + 4;
    
    byte []min = new byte[table.getKeyLength()];
    byte []max = new byte[table.getKeyLength()];
      
    for (; length > 0; length -= len) {
      is.readAll(min, 0, min.length);
      is.readAll(max, 0, max.length);
      
      int id = BitsUtil.readInt(is);
      
      page.insert(min, max, id);
    }
    
    page.clearDirty();
      
    return page;
  }
  */

  public PageTree copy(TableKelp table, int newPid)
  {
    PageTree newTree = new PageTree(newPid, getNextId(), getSequence(),
                                    getMinKey(), getMaxKey());
    
    for (BlockTree block : _blocks) {
      block.copyTo(table, newTree);
    }
    
    return newTree;
  }

  PageTree replaceNextId(int nextId)
  {
    return new PageTree(getId(), nextId, getSequence(),
                        getMinKey(), getMaxKey(),
                        _blocks);
  }

  PageTree copy(int id)
  {
    BlockTree []blocks = new BlockTree[_blocks.length];

    for (int i = 0; i < blocks.length; i++) {
      blocks[i] = _blocks[i].copy(id);
    }
    return new PageTree(id, getNextId(), getSequence(),
                        getMinKey(), getMaxKey(),
                        blocks);
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + getId()
            + "," + Hex.toHex(_minKey, 0, 2) 
            + ".." + Hex.toHex(_minKey, _minKey.length - 2, 2) 
            + "," + Hex.toHex(_maxKey, 0, 2) 
            + ".." + Hex.toHex(_maxKey, _maxKey.length - 2, 2)
            + "]");
  }
  
  static class TreeEntry implements Comparable<TreeEntry> {
    private final byte [] _minKey;
    private final byte [] _maxKey;
    private final int _code;
    private final int _pid;
    
    TreeEntry(byte []minKey, byte []maxKey, int code, int pid)
    {
      _minKey = minKey;
      _maxKey = maxKey;
      _code = code;
      _pid = pid;
    }
    
    final byte []getMinKey()
    {
      return _minKey;
    }
    
    final byte []getMaxKey()
    {
      return _maxKey;
    }
    
    final int getPid()
    {
      return _pid;
    }
    
    final int getCode()
    {
      return _code;
    }
    
    final boolean isInsert()
    {
      return _code == BlockTree.INSERT;
    }

    @Override
    public int compareTo(TreeEntry entry)
    {
      byte []minA = _minKey;
      byte []minB = entry._minKey;
      
      int len = minA.length;
      
      for (int i = 0; i < len; i++) {
        int cmp = (minA[i] & 0xff) - (minB[i] & 0xff);
        
        if (cmp != 0) {
          return cmp;
        }
      }

      byte []maxA = _maxKey;
      byte []maxB = entry._maxKey;
      
      len = maxA.length;
      
      for (int i = 0; i < len; i++) {
        int cmp = (maxA[i] & 0xff) - (maxB[i] & 0xff);
        
        if (cmp != 0) {
          return cmp;
        }
      }

      return 0;
    }
    
    @Override
    public int hashCode()
    {
      byte []minA = _minKey;
      
      int len = minA.length;
      
      int hash = 0;
      
      for (int i = 0; i < len; i++) {
        hash = 65521 * hash + (minA[i] & 0xff);
      }
      
      return hash;
    }
    
    @Override
    public boolean equals(Object o)
    {
      if (! (o instanceof TreeEntry)) {
        return false;
      }
      
      TreeEntry entry = (TreeEntry) o;
      
      if (! Arrays.equals(_minKey, entry._minKey)) {
        return false;
      }
      
      if (! Arrays.equals(_maxKey, entry._maxKey)) {
        return false;
      }
      
      return true;
    }
    
    public String toString()
    {
      return (getClass().getSimpleName()
              + "["
              + "," + Hex.toShortHex(_minKey)
              + ", " + Hex.toShortHex(_maxKey)
              + "]");
    }
  }
  
  static class SplitTree {
    private PageTree _first;
    private PageTree _rest;
    
    SplitTree(PageTree first, PageTree rest)
    {
      _first = first;
      _rest = rest;
    }
    
    PageTree getFirst()
    {
      return _first;
    }
    
    PageTree getRest()
    {
      return _rest;
    }
  }
}
