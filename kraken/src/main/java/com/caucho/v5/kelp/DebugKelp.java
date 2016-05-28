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

import static com.caucho.v5.kelp.segment.SegmentServiceImpl.BLOCK_SIZE;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.kelp.Page.Type;
import com.caucho.v5.kelp.segment.InSegment;
import com.caucho.v5.kelp.segment.SegmentExtent;
import com.caucho.v5.kelp.segment.SegmentKelpBuilder;
import com.caucho.v5.kelp.segment.SegmentServiceImpl;
import com.caucho.v5.kelp.segment.SegmentServiceImpl.TableEntry;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Hex;

/**
 * Filesystem access for the BlockStore.
 */
public class DebugKelp
{
  public DebugKelp()
  {
  }

  /*
  public DebugKelp segmentLength(long length)
  {
    _segmentLength = length;
    
    return this;
  }
  
  public DebugKelp segmentTail(long length)
  {
    _segmentTail = length;
    
    return this;
  }
  */

  public void debug(WriteStream out, Path path)
    throws IOException
  {
    debug(out, path, null);
  }

  public void debug(WriteStream out, Path path, byte []tableKey)
    throws IOException
  {
    SegmentKelpBuilder builder = new SegmentKelpBuilder();
    builder.path(path);
    builder.create(false);
    
    SegmentServiceImpl segmentService = builder.build();
    
    for (SegmentExtent extent : segmentService.getSegmentExtents()) {
      debugSegment(out, segmentService, extent, tableKey);
    }
    
    /*
    try (ReadStream is = path.openRead()) {
      long length = path.getLength();
      
      long magic = BitsUtil.readLong(is);
      
      if (magic != SegmentServiceImpl.KELP_MAGIC) {
        out.println("Mismatched database magic number for " + path);
        return;
      }
      
      _segmentLength = BitsUtil.readInt(is);
      
      while (is.read() == 1) {
        byte []tableKey = new byte[SegmentServiceImpl.TABLE_KEY_SIZE];
        is.readAll(tableKey, 0, tableKey.length);
        int rowLength = BitsUtil.readInt(is);
        int keyOffset = BitsUtil.readInt(is);
        int keyLength = BitsUtil.readInt(is);

        TableEntry entry
          = new TableEntry(tableKey, rowLength, keyOffset, keyLength);

        _tableMap.put(new HashKey(tableKey), entry);
      }
      
      if (_segmentLength % SegmentServiceImpl.BLOCK_SIZE != 0
          || _segmentLength <= 0) {
        out.println("Invalid segment length " + _segmentLength + " for " + path);
        return;
      }
      _segmentTail = _segmentLength - DatabaseBuilder.SEGMENT_TAIL;

      for (long ptr = _segmentLength; ptr < length; ptr += _segmentLength) {
        int segment = (int) (ptr / _segmentLength);
        
        is.setPosition(ptr + _segmentLength - BLOCK_SIZE);
        
        long seq = BitsUtil.readLong(is);
        
        if (seq <= 0) {
          continue;
        }
        
        byte []tableKey = new byte[32];
        is.readAll(tableKey, 0, tableKey.length);
      
        out.println();
        out.println("Segment: " + segment + " (seq: " + seq
                    + ", table: " + Hex.toShortHex(tableKey)
                    + ", seg_len: " + Long.toHexString(_segmentLength) + ")");
        
        TableEntry table = _tableMap.get(HashKey.create(tableKey));
        
        if (table != null) {
          debugSegmentEntries(out, is, ptr, table);
        }
      }
    }
    */
  }
  
  private void debugSegment(WriteStream out, 
                            SegmentServiceImpl segmentService,
                            SegmentExtent extent,
                            byte []debugTableKey)
    throws IOException
  {
    int length = extent.getLength();
    
    try (InSegment in = segmentService.openRead(extent)) {
      ReadStream is = new ReadStream(in);

      is.position(length - BLOCK_SIZE);

      long seq = BitsUtil.readLong(is);

      if (seq <= 0) {
        return;
      }

      byte []tableKey = new byte[32];
      is.readAll(tableKey, 0, tableKey.length);
      
      TableEntry table = segmentService.findTable(tableKey);

      if (table == null) {
        return;
      }
      
      if (debugTableKey != null && ! Arrays.equals(debugTableKey, tableKey)) {
        return;
      }

      out.println();
      out.println("Segment: " + extent.getId() + " (seq: " + seq
                  + ", table: " + Hex.toShortHex(tableKey)
                  + ", addr: 0x" + Long.toHexString(extent.address())
                  + ", len: 0x" + Integer.toHexString(length) + ")");
      
      debugSegmentEntries(out, is, extent, table);
    }
    
    /*
    TableEntry table = _tableMap.get(HashKey.create(tableKey));
    
    if (table != null) {
      debugSegmentEntries(out, is, ptr, table);
    }
    out.println("DBG-OUT : " + reader);
    */
  }
  
  private void debugSegmentEntries(WriteStream out, 
                                   ReadStream is,
                                   SegmentExtent extent,
                                   TableEntry table)
    throws IOException
  {
    for (long ptr = extent.getLength() - BLOCK_SIZE; 
         ptr > 0;
         ptr -= BLOCK_SIZE) {
      is.position(ptr);
      
      long seq = BitsUtil.readLong(is);
      byte []tableKey = new byte[32];
      is.readAll(tableKey, 0, tableKey.length);
      int head = BitsUtil.readInt16(is);

      if (seq <= 0 || head == 0) {
        return;
      }
      
      boolean isCont = is.read() == 1;
      
      int tail = BLOCK_SIZE;
      
      while ((tail = debugSegmentEntry(out, is, extent.address(),
                                       ptr, tail,
                                       table)) > head) {
      }
      
      if (! isCont) {
        break;
      }
    }
  }
  
  private int debugSegmentEntry(WriteStream out, 
                                ReadStream is,
                                long segmentAddress,
                                long ptr,
                                int tail,
                                TableEntry table)
    throws IOException
  {
    int sublen = 1 + 4 * 4;
    
    tail -= sublen;
    
    is.position(ptr + tail);
    int typeCode = is.read();
    
    if (typeCode <= 0) {
      return 0;
    }
    
    Type type = Type.valueOf(typeCode);

    int pid = BitsUtil.readInt(is);
    int nextPid = BitsUtil.readInt(is);
    int offset = BitsUtil.readInt(is);
    int length = BitsUtil.readInt(is);
    
    long pos = is.position();
    
    switch (type) {
    case LEAF:
      out.print("  " + type);
      debugLeaf(out, is, segmentAddress, offset, table);
      break;
      
    case LEAF_DELTA:
      out.print("  " + type);
      break;

    case BLOB:
    case BLOB_FREE:
      out.print("  " + type);
      break;
      
    default:
      out.print("  unk(" + type + ")");
      break;
    }
    
    is.position(pos);
  
    out.println(" pid:" + pid + " next:" + nextPid
                + " offset:" + offset + " length:" + length);
    
    return tail;
  }
  
  void debugLeaf(WriteStream out, 
                 ReadStream is, 
                 long segmentAddress, 
                 int offset,
                 TableEntry table)
    throws IOException
  {
    //is.setPosition(segmentAddress + offset);
    is.position(offset);
    
    byte []minKey = new byte[table.keyLength()];
    byte []maxKey = new byte[table.keyLength()];
    
    is.readAll(minKey, 0, minKey.length);
    is.readAll(maxKey, 0, maxKey.length);
    
    out.print(" [");
    printKey(out, minKey);
    out.print(",");
    printKey(out, maxKey);
    out.print("]");
  }
  
  void debugTree(WriteStream out, 
                 ReadStream is, 
                 long segmentAddress, 
                 int offset,
                 TableEntry table)
    throws IOException
  {
    is.position(segmentAddress + offset);
    
    byte []minKey = new byte[table.keyLength()];
    byte []maxKey = new byte[table.keyLength()];
    
    is.readAll(minKey, 0, minKey.length);
    is.readAll(maxKey, 0, maxKey.length);
    
    out.print(" [");
    printKey(out, minKey);
    out.print(",");
    printKey(out, maxKey);
    out.print("]");
  }
  
  private void printKey(WriteStream out, byte []key)
    throws IOException
  {
    if (key.length <= 4) {
      out.print(Hex.toHex(key));
    }
    else {
      out.print(Hex.toHex(key, 0, 2));
      out.print("..");
      out.print(Hex.toHex(key, key.length - 2, 2));
    }
  }
}
