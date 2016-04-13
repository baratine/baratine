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

package com.caucho.v5.db.journal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Hex;

/**
 * Filesystem access for the BlockStore.
 */
public class JournalDebug
{
  private long _segmentLength;
  private long _segmentTail;
  private int _rowLength;
  private int _keyOffset;
  private int _keyLength;
  
  public JournalDebug()
  {
  }
  
  public JournalDebug segmentLength(long length)
  {
    _segmentLength = length;
    
    return this;
  }
  
  public JournalDebug segmentTail(long length)
  {
    _segmentTail = length;
    
    return this;
  }

  public void debug(WriteStream out, Path path)
    throws IOException
  {
    try (ReadStream is = new ReadStream(Files.newInputStream(path))) {
      long length = Files.size(path);
      
      long magic = BitsUtil.readLong(is);
      
      if (magic != JournalStore.JOURNAL_MAGIC) {
        out.println("Mismatched journal magic number for " + path);
        return;
      }
      
      _segmentLength = BitsUtil.readLong(is);
      _segmentTail = BitsUtil.readLong(is);
      _keyLength = BitsUtil.readInt(is);
      
      if (_segmentLength % JournalStore.BLOCK_SIZE != 0
          || _segmentLength <= 0) {
        out.println("Invalid segment length " + _segmentLength + " for " + path);
        return;
      }
      
      if (_keyLength != JournalStore.KEY_LENGTH) {
        out.println("Invalid key length " + _keyLength + " for " + path);
        return;
      }
      
      out.println("Journal: " + path);
      out.println("  segment-length : 0x" + Long.toHexString(_segmentLength));
      out.println("  file-length    : 0x" + Long.toHexString(length));
      
      for (long ptr = _segmentLength; ptr < length; ptr += _segmentLength) {
        int segment = (int) (ptr / _segmentLength);
        
        is.setPosition(ptr + _segmentTail);
        
        long initSeq = BitsUtil.readLong(is);
        
        if (initSeq <= 0) {
          /*
          out.println();
          out.println("Segment: " + segment + " free");
          */
          continue;
        }
        
        byte []key = new byte[_keyLength];
        
        is.readAll(key, 0, key.length);
        
        long seq = BitsUtil.readLong(is);
        long checkpointBegin = BitsUtil.readLong(is);
        long checkpointEnd = BitsUtil.readLong(is);
      
        out.println();
        out.println("Segment: " + segment + " key=" + Hex.toHex(key, 0, 4)
                    + " (seg-off: 0x" + Long.toHexString(ptr) + ")");
        out.println("  init-sequence    : " + initSeq);
        out.println("  sequence         : " + seq);
        out.println("  checkpoint-begin : " + Long.toHexString(checkpointBegin));
        out.println("  checkpoint-end   : " + Long.toHexString(checkpointEnd));
      }
    }
  }
}
