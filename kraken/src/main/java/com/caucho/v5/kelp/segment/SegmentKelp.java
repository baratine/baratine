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

package com.caucho.v5.kelp.segment;

import static com.caucho.v5.kelp.segment.SegmentServiceImpl.BLOCK_SIZE;

import com.caucho.v5.baratine.InService;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.kelp.PageServiceImpl;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.TableWriterServiceImpl.LoadCallback;
import com.caucho.v5.store.io.InStore;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Filesystem access for the BlockStore.
 */
public class SegmentKelp
{
  private static final Logger log 
    = Logger.getLogger(SegmentKelp.class.getName());
  
  private static final L10N L = new L10N(SegmentKelp.class);
  
  private final SegmentExtent _extent;
  private final long _sequence;
  
  private final byte []_tableKey;
  
  private final SegmentServiceImpl _segmentActor;
  // private final TableWriterServiceImpl _writerActor;
  
  private int _writeTail;
  
  private State _state = State.INIT;
  
  private long _gcGeneration;
  
  SegmentKelp(SegmentExtent extent,
              long sequence,
              byte []tableKey,
              SegmentServiceImpl segmentActor)
  {
    Objects.requireNonNull(extent);
    Objects.requireNonNull(tableKey);
    
    _extent = extent;
    _sequence = sequence;
    
    _tableKey = tableKey;
    
    _segmentActor = segmentActor;
    // _writerActor = tableWriterActor;
  }

  public SegmentExtent getExtent()
  {
    return _extent;
  }

  public long getAddress()
  {
    return _extent.getAddress();
  }

  public int getLength()
  {
    return _extent.getLength();
  }

  int getSegmentTail()
  {
    return getLength() - BLOCK_SIZE;
  }

  public long getSequence()
  {
    return _sequence;
  }

  public boolean isTable(byte[] tableKey)
  {
    return Arrays.equals(_tableKey, tableKey);
  }
  
  int getWriteTail()
  {
    return _writeTail;
  }
  
  public void setLoaded()
  {
    if (_state != State.INIT) {
      throw new IllegalStateException(_state + " " + this);
    }
    
    _state = State.LOAD;
  }
  
  public boolean isWriting()
  {
    return _state == State.WRITING;
  }
  
  public void writing()
  {
    if (_state != State.INIT) {
      throw new IllegalStateException(_state + " " + this);
    }
    
    _state = State.WRITING;
  }
  
  public void finishWriting()
  {
    if (_state != State.WRITING) {
      throw new IllegalStateException(_state + " " + this);
    }
    
    _state = State.LOAD;
  }
  
  public boolean isClosed()
  {
    return _state == State.CLOSED;
  }
  
  public long getGcGeneration()
  {
    return _gcGeneration;
  }
  
  public void setGcGeneration(long gcSequence)
  {
    _gcGeneration = gcSequence;
  }
  
  public InSegment openRead()
  {
    return _segmentActor.openRead(this);
  }

  int writeEntry(byte []buffer, int tail, int head,
                 int type,
                 int pid,
                 int nextPid,
                 int entryOffset, 
                 int entryLength)
  {
    int sublen = 1 + 4 * 4;
    
    if (tail - head < sublen) {
      return -1;
    }
    
    tail -= sublen;
    
    int index = tail;
    buffer[index] = (byte) type;
    index++;
    
    BitsUtil.writeInt(buffer, index, pid);
    index += 4;
    
    BitsUtil.writeInt(buffer, index, nextPid);
    index += 4;
    
    BitsUtil.writeInt(buffer, index, entryOffset);
    index += 4;
    
    BitsUtil.writeInt(buffer, index, entryLength);
    index += 4;
    
    return tail;
  }
  
  @InService(PageServiceImpl.class)
  public
  void readEntries(TableKelp table,
                   PageServiceImpl pageActor,
                   long sequence,
                   LoadCallback loadCallback,
                   InSegment reader)
//                   StoreRead sIn)
      throws IOException
  {
    // LoadCallback loadCallback = new LoadCallback(this, reader.getIn(), readContext);
    
    readEntries(table, reader, loadCallback);
  }

  public void readEntries(TableKelp table, 
                          InSegment reader,
                          SegmentEntryCallback cb)
  {
    TempBuffer tBuf = TempBuffer.allocateLarge();
    byte []buffer = tBuf.buffer();
    
    InStore sIn = reader.getStoreRead();
    byte []tableKey = new byte[TableKelp.TABLE_KEY_SIZE];
    
    for (int ptr = getLength() - BLOCK_SIZE; ptr > 0; ptr -= BLOCK_SIZE) {
      sIn.read(getAddress() + ptr, buffer, 0, buffer.length);
      
      int index = 0;
      
      long seq = BitsUtil.readLong(buffer, index);
      index += 8;
      
      if (seq != getSequence()) {
        log.warning(L.l("Invalid sequence {0} expected {1} at 0x{2}",
                        seq, 
                        getSequence(),
                        Long.toHexString(getAddress() + ptr)));
        
        break;
      }
      
      System.arraycopy(buffer, index, tableKey, 0, tableKey.length);
      index += tableKey.length;
      
      if (! Arrays.equals(tableKey, _tableKey)) {
        log.warning(L.l("Invalid table {0} table {1} at 0x{2}",
                        Hex.toShortHex(tableKey),
                        Hex.toShortHex(_tableKey),
                        Long.toHexString(getAddress() + ptr)));
        break;
      }
      
      int head = BitsUtil.readInt16(buffer, index);
      index += 2;
      
      if (head <= 0) {
        throw new IllegalStateException();
      }
      
      boolean isCont = buffer[index] != 0;
      
      int tail = BLOCK_SIZE;
      while (head < tail) {
        tail = readEntry(table, buffer, tail, cb, getAddress());
      }
      
      if (! isCont) {
        break;
      }
    }
    
    tBuf.freeSelf();
  }

  private int readEntry(TableKelp table,
                        byte []buffer,
                        int tail,
                        SegmentEntryCallback cb,
                        long address)
  {
    int sublen = 1 + 4 * 4;
    
    tail -= sublen;
    
    int index = tail;
    int typeCode = buffer[index++];

    int pid = BitsUtil.readInt(buffer, index);
    index += 4;
  
    if (pid <= 0) {
      throw new IllegalStateException(L.l("Invalid pid={0} while reading entry at 0x{1}:{2}", 
                                          pid,
                                          Long.toHexString(address),
                                          tail));
    }
  
    int nextPid = BitsUtil.readInt(buffer, index);
    index += 4;
  
    int offset = BitsUtil.readInt(buffer, index);
    index += 4;
    
    int length = BitsUtil.readInt(buffer, index);
    index += 4;

    cb.onEntry(typeCode, pid, nextPid, offset, length);
    
    return tail;
  }

  int findFirstEntryBlock(InStore sIn)
      throws IOException
  {
    TempBuffer tBuf = TempBuffer.allocateLarge();
    byte []buffer = tBuf.buffer();
    
    for (int ptr = getLength() - BLOCK_SIZE; ptr > 0; ptr -= BLOCK_SIZE) {
      sIn.read(getAddress() + ptr, buffer, 0, 64);
      
      int index = 0;
      long seq = BitsUtil.readLong(buffer, index);
      index += 8;
      
      if (seq != getSequence()) {
        return ptr + BLOCK_SIZE;
      }
      
      index += 2; // skip tail
      
      boolean isCont = buffer[index] != 0;
      
      if (! isCont) {
        return ptr;
      }
    }
    
    throw new IllegalStateException();
  }
  
  public void close()
  {
    if (_state == State.WRITING) {
      throw new IllegalStateException(_state + " " + this);
    }
    
    _state = State.CLOSED;
  }
  
  @Override
  public int hashCode()
  {
    long address = getAddress();
    
    return (int) ((address >> 16) + address);
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (! (o instanceof SegmentKelp)) {
      return false;
    }
    
    SegmentKelp seg = (SegmentKelp) o;
    
    return _extent == seg._extent;
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[0x" + Long.toHexString(getAddress())
            + ":0x" + Integer.toHexString(getLength())
            + ",seq=" + _sequence
            + ",table=" + Hex.toShortHex(_tableKey)
            + "," + _state
            + "]");
  }
  
  enum State {
    INIT,
    LOAD,
    WRITING,
    CLOSED;
  }
  
  public interface SegmentEntryCallback
  {
    void onEntry(int type, int pid, int nextPid, int offset, int length);
  }

  static class Entry {
    private final int _type;
    private final int _pid;
    private final int _nextPid;
    private final int _offset;
    private final int _length;
    
    Entry(int type,
          int pid, 
          int nextPid,
          int offset, 
          int length)
    {
      Objects.requireNonNull(type);
      
      if (pid <= 0) {
        throw new IllegalStateException();
      }
      
      _type = type;
      _pid = pid;
      _nextPid = nextPid;
      _offset = offset;
      _length = length;
    }
    
    int getNextPid()
    {
      return _nextPid;
    }

    int getType()
    {
      return _type;
    }

    int getPid()
    {
      return _pid;
    }
    
    int getOffset()
    {
      return _offset;
    }
    
    int getLength()
    {
      return _length;
    }
    
    @Override
    public int hashCode()
    {
      return _pid;
    }
    
    @Override
    public boolean equals(Object o)
    {
      Entry entry = (Entry) o ;
      
      return _type == entry._type && _pid == entry._pid;
    }
  }
  
  public static class SegmentComparatorDescend implements Comparator<SegmentKelp>
  {
    public static final SegmentComparatorDescend CMP = new SegmentComparatorDescend();

    @Override
    public int compare(SegmentKelp a, SegmentKelp b)
    {
      int cmp = Long.signum(b.getSequence() - a.getSequence());
      
      if (cmp != 0) {
        return cmp;
      }
      
      cmp = Long.signum(b.getAddress() - a.getAddress());
      
      return cmp;
    }
  }
  
  public static class SegmentComparatorAscend implements Comparator<SegmentKelp>
  {
    public static final SegmentComparatorAscend CMP = new SegmentComparatorAscend();

    @Override
    public int compare(SegmentKelp a, SegmentKelp b)
    {
      int cmp = Long.signum(a.getSequence() - b.getSequence());
      
      if (cmp != 0) {
        return cmp;
      }
      
      cmp = Long.signum(a.getAddress() - b.getAddress());
      
      return cmp;
    }
  }
}
