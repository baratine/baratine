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
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;

import com.caucho.v5.db.journal.JournalStream.ReplayCallback;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.store.io.InStore;
import com.caucho.v5.store.io.OutStore;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;

/**
 * The store manages the block-based journal store file.
 * 
 * Journals are shared with the same file. Each journal has a unique
 * key.
 */
public class JournalSegment
{
  private static final L10N L = new L10N(JournalSegment.class); 
  private static final Logger log
    = Logger.getLogger(JournalSegment.class.getName());
  
  private final static int BLOCK_SIZE = 8 * 1024;
  private final static int OFFSET_MASK = BLOCK_SIZE - 1;
  private final static int ADDRESS_MASK = ~OFFSET_MASK;
  private final static int PAD = 8;
  
  private final static int CHECKPOINT_START = 0x8000;
  private final static int CHECKPOINT_END   = 0x8001;
  
  private final JournalStore _blockStore;
  
  private final long _startAddress;
  
  private final long _headAddress;
  private final long _tailAddress;
  
  private final byte []_key;
  
  private long _initialSequence = 1;
  private long _sequence = _initialSequence;
  private int _sequenceHash;
  
  private byte []_sequenceHashBytes = new byte[4];
  
  private long _lastCheckpointStart;
  private long _lastCheckpointEnd;
  
  private boolean _isWhileCheckpoint;
  
  private long _index;
  private long _flushIndex;
  
  private TempBuffer _tBuf;
  private byte []_buffer;
  private byte []_headerBuffer = new byte[8];

  private final CRC32 _crc = new CRC32();
  
  private OutStore _os;

  private JournalGroup _group;
  
  JournalSegment(JournalStore blockStore,
                 byte []key,
                 long startAddress,
                 long initSequence,
                 long sequence)
  {
    _blockStore = blockStore;
    
    _tBuf = TempBuffer.createLarge();
    _buffer = _tBuf.buffer();
    
    _startAddress = startAddress;
    _key = new byte [key.length];
    System.arraycopy(key, 0, _key, 0, _key.length);
    
    _headAddress = _startAddress + 256;
    _tailAddress = _startAddress + blockStore.getTailAddress();

    _initialSequence = initSequence;
    
    setSequence(sequence);
  }

  public long getAddress()
  {
    return _startAddress;
  }
  
  private void setSequence(long sequence)
  {
    _sequence = sequence;

    _sequenceHash = calculateSequenceHash(_sequence);
    
    _sequenceHashBytes[0] = (byte) (_sequenceHash >> 24);
    _sequenceHashBytes[1] = (byte) (_sequenceHash >> 16);
    _sequenceHashBytes[2] = (byte) (_sequenceHash >> 8);
    _sequenceHashBytes[3] = (byte) (_sequenceHash);
  }
  
  private int calculateSequenceHash(long sequence)
  {
    _crc.reset();
    _crc.update(_key);
    _crc.update((int) (sequence >> 56));
    _crc.update((int) (sequence >> 48));
    _crc.update((int) (sequence >> 40));
    _crc.update((int) (sequence >> 32));
    _crc.update((int) (sequence >> 24));
    _crc.update((int) (sequence >> 16));
    _crc.update((int) (sequence >> 8));
    _crc.update((int) (sequence));

    return (int) _crc.getValue();
  }

  long getSequence()
  {
    return _sequence;
  }

  public byte[] getKey()
  {
    return _key;
  }

  public long getTailAddress()
  {
    return _tailAddress;
  }
  
  private int getOffset(long address)
  {
    return (int) (address & OFFSET_MASK);
  }
  
  private long getBlockAddress(long address)
  {
    return (address & ADDRESS_MASK);
  }
  
  private int getSegmentSize()
  {
    return _blockStore.getSegmentSize();
  }
  
  void startGroup(JournalGroup group)
  {
    _group = group;
  }
  
  public void openWrite()
  {
    if (_os != null) {
      throw new IllegalStateException();
    }
    
    _os = _blockStore.openWrite(_startAddress, getSegmentSize());
    _index = _headAddress;
    _flushIndex = _startAddress;

    Arrays.fill(_buffer, (byte) 0);
    
    BitsUtil.writeLong(_buffer, 0, _sequence);
    
    System.arraycopy(_key, 0, _buffer, 8, _key.length); 
  }
  
  public void startWrite()
  {
    initCrc();
  }
  
  public void startRead()
  {
    initCrc();
  }
  
  private void initCrc()
  {
    _crc.reset();
    _crc.update(_sequenceHashBytes, 0, 4);
  }
  
  /**
   * Writes a partial journal entry. The entry will only be completed
   * after completeWrite() has been called.
   * 
   * The write will return false if the journal segment is full. The caller
   * needs to retry the write with a new segment.
   */
  
  public boolean write(byte []buffer, int offset, int length)
  {
    if (length == 0) {
      return true; // throw new IllegalStateException();
    }
    
    int count = length / BLOCK_SIZE + 1;
    
    if (_tailAddress <= _index + length + PAD + 2 * count) {
      return false;
    }

    _crc.update(buffer, offset, length);
    
    byte []headerBuffer = _headerBuffer;
    
    while (length > 0) {
      int sublen = Math.min(length, BLOCK_SIZE);
      
      BitsUtil.writeInt16(headerBuffer, 0, sublen);
      
      writeImpl(headerBuffer, 0, 2);
      writeImpl(buffer, offset, sublen);
      
      length -= sublen;
      offset += sublen;
    }
    
    return true;
  }

  /**
   * Completes a journal entry started by a write() call.
   */
  public boolean completeWrite()
  {
    int digest = (int) _crc.getValue();

    byte []headerBuffer = _headerBuffer;
    BitsUtil.writeInt16(headerBuffer, 0, 0);
    BitsUtil.writeInt(headerBuffer, 2, digest);
    
    writeImpl(headerBuffer, 0, 6);
    
    return true;
  }
  
  private void writeImpl(byte []buffer, int offset, int length)
  {
    int blockOffset = getOffset(_index);

    while (length > 0) {
      int sublen = Math.min(length, BLOCK_SIZE - blockOffset);
      
      byte []blockBuffer = _buffer;
      
      System.arraycopy(buffer, offset, blockBuffer, blockOffset, sublen);
      
      length -= sublen;
      offset += sublen;
      blockOffset += sublen;
      _index += sublen;
      
      if (blockOffset == BLOCK_SIZE) {
        if (! nextBlock()) {
          throw new IllegalStateException();
        }
        
        blockOffset = 0;
        continue;
      }
    }
  }
  
  /**
   * Starts checkpoint processing. A checkpoint start changes the epoch
   * ID for new journal entries, but does not yet close the previous epoch.
   * 
   * Journal entries written between checkpointStart() and checkpointEnd()
   * belong to the successor epoch.
   */
  public void checkpointStart()
  {
    if (_isWhileCheckpoint) {
      throw new IllegalStateException();
    }
    
    _isWhileCheckpoint = true;
    
    long sequence = ++_sequence;
    
    setSequence(sequence);
    
    byte []headerBuffer = _headerBuffer;
    BitsUtil.writeInt16(headerBuffer, 0, CHECKPOINT_START);
    writeImpl(headerBuffer, 0, 2);
    
    _lastCheckpointStart = _index - _startAddress;
  }
  
  /**
   * Closes the previous epoch. Journal entries in a closed epoch will not
   * be replayed on a journal restore.
   */
  public void checkpointEnd()
  {
    if (! _isWhileCheckpoint) {
      throw new IllegalStateException();
    }
    
    _isWhileCheckpoint = false;
    
    byte []headerBuffer = _headerBuffer;
    BitsUtil.writeInt16(headerBuffer, 0, CHECKPOINT_END);
    writeImpl(headerBuffer, 0, 2);
    
    flush();
    
    _lastCheckpointEnd = _index - _startAddress;
    
    writeTail(_os);
  }
  
  void checkpointClose()
  {
    try (OutStore out = _blockStore.openWrite(_startAddress, getSegmentSize())) {
      setSequence(0);
      _initialSequence = 0;
      _lastCheckpointStart = 0;
      _lastCheckpointEnd = 0;
      
      writeTail(out);
    }
    
    // _store.free(this);
  }
  

  /**
   * Replays all open journal entries. The journal entry will call into
   * the callback listener with an open InputStream to read the entry.
   */
  public void replay(ReplayCallback replayCallback)
  {
    TempBuffer tReadBuffer = TempBuffer.createLarge();
    byte []readBuffer = tReadBuffer.buffer();
    int bufferLength = readBuffer.length;

    try (InStore jIn = _blockStore.openRead(_startAddress, getSegmentSize())) {
      Replay replay = readReplay(jIn);

      if (replay == null) {
        return;
      }
      
      long address = replay.getCheckpointStart();
      long next;
      
      setSequence(replay.getSequence());

      TempBuffer tBuffer = TempBuffer.create();
      byte []tempBuffer = tBuffer.buffer();
      
      jIn.read(getBlockAddress(address), readBuffer, 0, bufferLength);
      ReadStream is = new ReadStream();
      
      while (address < _tailAddress
             && (next = scanItem(jIn, address, readBuffer, tempBuffer)) > 0) {
        boolean isOverflow = getBlockAddress(address) != getBlockAddress(next);
        
        // if scanning has passed the buffer boundary, need to re-read
        // the initial buffer
        if (isOverflow) {
          jIn.read(getBlockAddress(address), readBuffer, 0, bufferLength);
        }
      
        ReplayInputStream rIn = new ReplayInputStream(jIn, readBuffer, address);
        
        is.init(rIn);

        try {
          replayCallback.onItem(is);
        } catch (Exception e) {
          e.printStackTrace();
          log.log(Level.FINER, e.toString(), e);
        }
      
        address = next;
        
        if (isOverflow) {
          jIn.read(getBlockAddress(address), readBuffer, 0, bufferLength);
        }
        
        _index = address;
        _flushIndex = address;
      }
    }
  }
  
  /**
   * Validates that the item is complete and correct.
   */
  private long scanItem(InStore is, long address, byte []readBuffer,
                        byte []tempBuffer)
  {
    startRead();
    
    byte []headerBuffer = _headerBuffer;
    
    while (address + 2 < _tailAddress) {
      readImpl(is, address, readBuffer, headerBuffer, 0, 2);
      
      address += 2;
      
      int len = BitsUtil.readInt16(headerBuffer, 0);

      if ((len & 0x8000) != 0) {
        if (len == CHECKPOINT_START) {
          setSequence(_sequence + 1);
        }
        
        startRead();
        
        continue;
      }
        
      if (len == 0) {
        readImpl(is, address, readBuffer, headerBuffer, 0, 4);
        address += 4;
        
        int crc = BitsUtil.readInt(headerBuffer, 0);
        
        int digest = (int) _crc.getValue();

        if (crc == digest) {
          return address;
        }
        else {
          return -1;
        }
      }
      
      if (_tailAddress < address + len) {
        return -1;
      }
      
      int readLen = len;
      
      while (readLen > 0) {
        int sublen = Math.min(readLen, tempBuffer.length);
      
        readImpl(is, address, readBuffer, tempBuffer, 0, sublen);
        _crc.update(tempBuffer, 0, sublen);
        
        address += sublen;
        readLen -= sublen;
      }
    }

    return -1;
  }
  
  private int readImpl(InStore is, 
                       long address, byte []readBuffer, 
                       byte []buffer, int offset, int length)
  {
    if (_tailAddress < address + length) {
      throw new IllegalStateException(L.l("tail {0} address {1} length {2}",
                                          _tailAddress, address, length));
    }
    
    int remaining = length;
    
    while (remaining > 0) {
      int readOffset = getOffset(address);
      
      if (readOffset == 0) {
        is.read(getBlockAddress(address), readBuffer, 0, readBuffer.length);
      }
      
      int sublen = Math.min(readBuffer.length - readOffset, remaining);
      
      System.arraycopy(readBuffer, readOffset, buffer, offset, sublen);

      address += sublen;
      offset += sublen;
      remaining -= sublen;
    }

    return length;
  }
  
  private boolean nextBlock()
  {
    int offset = getOffset(_index);
    
    if (offset != 0) {
      throw new IllegalStateException();
    }
    
    flush();
    
    return _index < _tailAddress;
  }
  

  public void flush()
  {
    long flushIndex = _flushIndex;
    long index = _index;
    
    if (flushIndex < index && _os != null) {
      int flushOffset = getOffset(flushIndex);
      long flushAddress = getBlockAddress(flushIndex);
      
      int offset = getOffset(index);
      long address = getBlockAddress(index);
      
      int sublen;
      
      if (flushAddress == address) {
        sublen = offset - flushOffset;
      }
      else {
        sublen = BLOCK_SIZE - flushOffset;
      }
      
      _os.write(flushIndex, _buffer, flushOffset, sublen);

      _flushIndex = index;
    }
  }
  
  private Replay readReplay(InStore jIn)
  {
    byte []buffer = _buffer;
    
    jIn.read(_tailAddress, buffer, 0, buffer.length);
    
    int offset = 0;
    
    long initSequence = BitsUtil.readLong(buffer, offset);
    offset += 8;
    
    offset += _key.length;
    
    long sequence = BitsUtil.readLong(buffer, offset);
    offset += 8;
    
    long checkpointStart = BitsUtil.readLong(buffer, offset);
    offset += 8;
    
    long checkpointEnd = BitsUtil.readLong(buffer, offset);
    offset += 8;
    
    if (sequence <= 0 || checkpointStart <= 0) {
      sequence = initSequence;
      checkpointStart = _headAddress;
      checkpointEnd = _headAddress;
    }
    else {
      checkpointStart += _startAddress;
      checkpointEnd += _startAddress;
    }
    
    if (initSequence <= 0) {
      return null;
    }
    
    return new Replay(sequence, checkpointStart, checkpointEnd);
  }
  
  public void writeTail(OutStore os)
  {
    if (os == null) {
      return; // XXX: shutdown timing?
    }
    TempBuffer tBuf = TempBuffer.createLarge();
    byte []buffer = tBuf.buffer();
    
    int offset = 0;
    
    BitsUtil.writeLong(buffer, offset, _initialSequence);
    offset += 8;
    
    System.arraycopy(_key, 0, buffer, offset, _key.length);
    offset += _key.length;
    
    BitsUtil.writeLong(buffer, offset, _sequence);
    offset += 8;
    
    BitsUtil.writeLong(buffer, offset, _lastCheckpointStart);
    offset += 8;
    
    BitsUtil.writeLong(buffer, offset, _lastCheckpointEnd);
    offset += 8;
    
    os.write(_tailAddress, buffer, 0, offset);
    
    tBuf.free();
  }
  
  public void close()
  {
    OutStore os = _os;

    if (os != null) {
      if (_isWhileCheckpoint) {
        System.err.println("ERROR: Closing while checkpoint " + this);
      }
      
      flush();
    
      _os = null;

      os.close();
    }

    JournalGroup group = _group;
    _group = null;
    
    if (group != null) {
      group.closeSegment(this);
    }
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + Long.toHexString(_startAddress)
            + ",key=" + Hex.toHex(_key, 0, 4)
            + "]");
           
  }
  
  class ReplayInputStream extends StreamImpl {
    private InStore _jIn;
    private byte []_buffer;
    
    private long _address;
    private int _sublen;
    private boolean _isDone;
    private byte []_data = new byte[1];
    
    ReplayInputStream(InStore jIn, byte []readBuffer, long address)
    {
      _jIn = jIn;
      _buffer = readBuffer;
      _address = address;

      if (getOffset(address) == 0) {
        _jIn.read(address, readBuffer, 0, readBuffer.length);
      }
    }
    
    @Override
    public boolean canRead()
    {
      return true;
    }
    
    @Override
    public int read(byte []buffer, int offset, int length)
      throws IOException
    {
      if (_sublen <= 0) {
        if (_isDone) {
          return -1;
        }

        // skip checkpoint
        do {
          readImpl(_jIn, _address, _buffer, _headerBuffer, 0, 2);
          
          _address += 2;
          
          _sublen = BitsUtil.readInt16(_headerBuffer, 0);
        } while ((_sublen & 0x8000) != 0);
          
        if (_sublen == 0) {
          _address += 4;
          _isDone = true;
          return -1;
        }
      }
      
      int sublen = Math.min(_sublen, length);
        
      readImpl(_jIn, _address, _buffer, buffer, offset, sublen);
          
      _address += sublen;
      _sublen -= sublen;
          
      return sublen;
    }
  }
  
  static class Replay {
    private final long _sequence;
    private final long _checkpointStart;
    private final long _checkpointEnd;
    
    Replay(long sequence,
           long checkpointStart,
           long checkpointEnd)
    {
      _sequence = sequence;
      _checkpointStart = checkpointStart;
      _checkpointEnd = checkpointEnd;
    }
    
    long getSequence()
    {
      return _sequence;
    }
    
    long getCheckpointStart()
    {
      return _checkpointStart;
    }
    
    long getCheckpointEnd()
    {
      return _checkpointEnd;
    }
  }
}
