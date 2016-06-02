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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.TempBuffer;
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
  
  public String debug(Path path)
    throws IOException
  {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    WriteStream out = new WriteStream(bos);
    
    debug(out, path);
    out.close();
    
    return new String(bos.toByteArray());
  }

  public void debug(WriteStream out, Path path)
    throws IOException
  {
    debug(out, path, null);
  }

  /**
   * Debug with a table key
   * 
   * @param out the result of the debug stream
   * @param path the database source
   * @param tableKey the specific table to display
   * @throws IOException
   */
  public void debug(WriteStream out, Path path, byte []tableKey)
    throws IOException
  {
    SegmentKelpBuilder builder = new SegmentKelpBuilder();
    builder.path(path);
    builder.create(false);
    builder.services(ServicesAmp.newManager().get());
    
    SegmentServiceImpl segmentService = builder.build();
    
    for (SegmentExtent extent : segmentService.getSegmentExtents()) {
      debugSegment(out, segmentService, extent, tableKey);
    }
  }
  
  /**
   * Trace through a segment, displaying its sequence, table, and extent.
   */
  private void debugSegment(WriteStream out, 
                            SegmentServiceImpl segmentService,
                            SegmentExtent extent,
                            byte []debugTableKey)
    throws IOException
  {
    int length = extent.length();
    
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
  }
  
  /**
   * Trace through the segment entries.
   */
  private void debugSegmentEntries(WriteStream out, 
                                   ReadStream is,
                                   SegmentExtent extent,
                                   TableEntry table)
    throws IOException
  {
    TempBuffer tBuf = TempBuffer.create();
    byte []buffer = tBuf.buffer();
    
    for (long ptr = extent.length() - BLOCK_SIZE; 
         ptr > 0;
         ptr -= BLOCK_SIZE) {
      is.position(ptr);
      
      is.readAll(buffer, 0, BLOCK_SIZE);
      
      long seq = BitsUtil.readLong(buffer, 0);
      int head = 8;
      
      byte []tableKey = new byte[32];
      System.arraycopy(buffer, head, tableKey, 0, tableKey.length);
      is.readAll(tableKey, 0, tableKey.length);
      head += tableKey.length;
      
      int offset = BLOCK_SIZE - 8;
      int tail = BitsUtil.readInt16(buffer, offset);
      offset += 2;
      boolean isCont = buffer[offset] == 1;

      if (seq <= 0 || tail <= 0) {
        return;
      }
      
      while ((head = debugSegmentIndex(out, is, buffer, extent.address(),
                                       ptr, head,
                                       table)) < tail) {
      }
      
      if (! isCont) {
        break;
      }
    }
  }
  
  /**
   * Debug a single segment index entry.
   */
  private int debugSegmentIndex(WriteStream out,
                                ReadStream is,
                                byte []buffer,
                                long segmentAddress,
                                long ptr,
                                int head,
                                TableEntry table)
    throws IOException
  {
    int sublen = 1 + 4 * 4;
    
    //tail -= sublen;
    
    // is.position(ptr + tail);
    int typeCode = buffer[head] & 0xff;
    head++;
    
    if (typeCode <= 0) {
      return 0;
    }
    
    Type type = Type.valueOf(typeCode);

    int pid = BitsUtil.readInt(buffer, head);
    head += 4;
    int nextPid = BitsUtil.readInt(buffer, head);
    head += 4;
    int offset = BitsUtil.readInt(buffer, head);
    head += 4;
    int length = BitsUtil.readInt(buffer, head);
    head += 4;
    
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
    
    // is.position(pos);
  
    out.println(" pid:" + pid + " next:" + nextPid
                + " offset:" + offset + " length:" + length);
    
    return head;
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
