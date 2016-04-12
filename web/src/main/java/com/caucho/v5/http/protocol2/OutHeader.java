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

package com.caucho.v5.http.protocol2;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.util.BitsUtil;


/**
 * HeaderOut is the compression for the writer.
 */
public class OutHeader extends HeaderCommon implements AutoCloseable
{
  private HashMap<String,TableEntry> _tableKeyMap;
  private HashMap<TableEntry,TableEntry> _tableEntryMap;
    
  private int _tableCapacity = 4096;
  
  private TableEntry []_entries;
  
  private TableEntry _key = new TableEntry();
  
  private final WriteBuffer _os;
  
  private byte []_buffer;
  private int _headerOffset;
  private int _offset;
  
  private int _streamId;
  private StateHeader _state;
  
  private long _sequence;
  private long _tailSequence;
  
  private long _seqReference = 1;
  
  private int _tableSize = 0;
  
  private int _priorityDependency;
  private int _priorityWeight;
  private boolean _isPriorityExclusive;
  
  private int _pad;
  
  public OutHeader(WriteBuffer os)
  {
    Objects.requireNonNull(os);
    
    _tableKeyMap = new HashMap<>();
    _tableKeyMap.putAll(getTableKeyStatic());
    
    _tableEntryMap = new HashMap<>();
    _tableEntryMap.putAll(getTableEntryStatic());
    
    _entries = new TableEntry[256];
    
    _os = os;
  }
  
  public void openHeaders(int streamId, FlagsHttp flags)
    throws IOException
  {
    openHeaders(streamId, 0, -1, -1, false, flags);
  }
    
  public void openHeaders(int streamId,
                          int pad,
                          int priorityDependency,
                          int priorityWeight,
                          boolean isPriorityExclusive,
                          FlagsHttp flags)
    throws IOException
  {
    _streamId = streamId;
    
    _pad = pad;
    _priorityDependency = priorityDependency;
    _priorityWeight = priorityWeight;
    _isPriorityExclusive = isPriorityExclusive;
    
    ++_seqReference;
    
    startChunk();
    
    switch (flags) {
    case END_STREAM:
      _state = StateHeader.HEADER_INIT_STREAM_END;
      break;
      
    case CONT_STREAM:
      _state = StateHeader.HEADER_INIT_STREAM_CONT;
      break;
      
    default:
      throw new IllegalStateException(String.valueOf(flags));
    }
  }
  
  private void startChunk()
    throws IOException
  {
    WriteBuffer os = _os;
    
    int offset = os.offset();
    byte []buffer = os.buffer();
    int size = buffer.length;
    
    if (size - offset < 16) {
      os.flush();
      
      offset = os.offset();
      buffer = os.buffer();
    }
    
    _headerOffset = offset;
    _buffer = buffer;
    
    offset += 8;
    
    if (_pad >= 256) {
      offset += 2;
    }
    else if (_pad > 0) {
      offset += 1;
    }
    
    if (_priorityWeight >= 0) {
      offset += 5;
    }
    
    _offset = offset;
  }
  
  private int flushChunk(int offset)
    throws IOException
  {
    StateHeader state = _state;
    
    writeHeaders(offset, state.flagsFlush());
    
    _os.flush();
    
    _state = state.onFlush();
    
    return 8;
  }
  
  public void closeHeaders()
    throws IOException
  {
    completeReferenceSet();
    
    writeHeaders(_offset, _state.flagsClose());
  }
  
  private void writeHeaders(int offset, int flags)
    throws IOException
  {
    int headerOffset = _headerOffset;
    byte []buffer = _buffer;
    
    StateHeader state = _state;
    
    int startOffset = headerOffset + 8;
    
    int len = offset - startOffset;
    int fillLen = 0;
    
    if (_pad > 0) {
      fillLen = _pad - len % _pad;
      
      if (fillLen == _pad) {
        fillLen = 0;
      }
      
      len += fillLen;
      
      if (_pad >= 256) {
        flags |= Http2Constants.PAD_HIGH|Http2Constants.PAD_LOW;
        
        buffer[startOffset + 0] = (byte) (fillLen / 256);
        buffer[startOffset + 1] = (byte) fillLen;
        
        startOffset += 2;
      }
      else {
        flags |= Http2Constants.PAD_LOW;
        
        buffer[startOffset + 0] = (byte) fillLen;
        
        startOffset += 1;
      }
    }
    
    if (_priorityWeight >= 0) {
      flags |= Http2Constants.PRIORITY;
      
      BitsUtil.writeInt(buffer, startOffset, _priorityDependency);
      buffer[startOffset + 4] = (byte) _priorityWeight;
      
      startOffset += 5;
      
      _priorityDependency = -1;
      _priorityWeight = -1;
    }
    
    buffer[headerOffset + 0] = (byte) (len >> 8);
    buffer[headerOffset + 1] = (byte) (len);
    buffer[headerOffset + 2] = (byte) state.opcode();
    buffer[headerOffset + 3] = (byte) flags;
    
    BitsUtil.writeInt(buffer, headerOffset + 4, _streamId);
    
    WriteBuffer os = _os;
    
    if (fillLen > 0) {
      Arrays.fill(buffer, offset, offset + fillLen, (byte) 0);
    }
    os.offset(offset + fillLen);
    
    _headerOffset = 0;
  }

  public void header(String key, String value)
    throws IOException
  {
    // literal header field with incremental indexing
    // i.e. add to index table
    
    TableEntry entryKey = _key;
    
    entryKey.update(0, key, value);
    
    TableEntry entry = _tableEntryMap.get(entryKey);
    
    if (entry != null) {
      int index = getIndex(entry);
      
      if (entry.getReference() < _seqReference) {
        writeKeyValue(0x80, index);

        if (entry.isStatic()) {
          // static has a copy added
          entry = new TableEntry(entry);
          _tableEntryMap.put(entry, entry);
          addEntry(entry);
          
          return;
        }
      }
      
      if (entry.getReference() <= _seqReference) {
        if (! entry.isStatic()) {
          // XXX: Need to add
          entry.setReference(_seqReference + 1);
        }
        
      }
      
      return;
    }
    
    writeKey(0x40, 6, key);

    writeString(value);
    
    _key = new TableEntry();
    
    addEntry(entryKey);
  }
  
  private void addEntry(TableEntry entry)
  {
    long seq = _sequence++;
    entry.setSequence(seq);
    entry.setReference(_seqReference + 1);
    
    _tableEntryMap.put(entry, entry);
    
    if (entry.getNext() == null) {
      _tableKeyMap.put(entry.getKey(), entry);
    }
    
    _entries[(int) (seq % _entries.length)] = entry;
    
    _tableSize += entry.getSize();
    
    updateTableSize();
  }
  
  public void headerUnique(String key, String value)
    throws IOException
  {
    // literal header field without incremental indexing
    // i.e. don't add to index table
    
    TableEntry entryKey = _key;
    
    entryKey.update(0, key, value);
    
    TableEntry entry = _tableEntryMap.get(entryKey);
    
    if (entry != null) {
      int index = getIndex(entry);
      
      writeKeyValue(0x80, index);
      
      return;
    }
    
    writeKey(0x00, 4, key);
    
    writeString(value);
  }
  
  public void headerNever(String key, String value)
    throws IOException
  {
    // literal header field without incremental indexing
    // i.e. don't add to index table
    writeKey(0x10, 4, key);
    
    writeString(value);
  }
  
  public void clearReferenceSet()
    throws IOException
  {
    write(0x30);
    
    for (long i = _tailSequence; i < _sequence; i++) {
      TableEntry entry = _entries[(int) (i % _entries.length)];
      entry.setReference(0);
    }
  }
  
  public void setTableSize(int size)
    throws IOException
  {
    if (size < 0) {
      throw new IllegalArgumentException();
    }
    _tableCapacity = size;
    
    writeInt(0x20, 4, size);
    
    updateTableSize();
  }
  
  private void updateTableSize()
  {
    while (_tableCapacity < _tableSize) {
      removeTableEntry();
    }
  }
  
  private void removeTableEntry()
  {
    long i = _tailSequence++;
    
    TableEntry entry = _entries[(int) (i % _entries.length)];
    entry.setReference(0);
    
    _tableEntryMap.remove(entry);
    
    TableEntry next = entry.getNext();
    
    if (next != null) {
      // restore static
      _tableEntryMap.put(next, next);
    }
    
    _tableSize -= entry.getSize();
  }
  
  private void writeKeyValue(int opcode, long seq)
    throws IOException
  {
    writeInt(opcode, 7, seq);
  }
  
  private void writeKey(int opcode, int bits, String key)
    throws IOException
  {
    TableEntry entry = _tableKeyMap.get(key);
    
    if (entry != null
        && (entry.isStatic() || _tailSequence <= entry.getSequence())) {
      int index = getIndex(entry);
      
      writeInt(opcode, bits, index);
    }
    else {
      write((byte) opcode);
      
      writeString(key);
    }
  }
  
  private int getIndex(TableEntry entry)
  {
    if (entry.isStatic()) {
      return (int) ((_sequence - _tailSequence) + entry.getSequence());
    }
    else {
      return (int) (_sequence - entry.getSequence());
    }
  }
  
  private void writeString(String value)
    throws IOException
  {
    int strlen = value.length();
    
    int offset = _offset;
    byte []buffer = _buffer;
    int bufferLength = buffer.length;
    
    if (bufferLength < offset + 4 + strlen) {
      offset = flushChunk(offset);
      buffer = _buffer;
    }
    
    _offset = writeStringImpl(buffer, offset, value, strlen);
  }
  
  protected int writeStringImpl(byte []buffer, int offset, 
                                String value, int strlen)
    throws IOException
  {
    int bufferLength = buffer.length;
    
    buffer[offset++] = (byte) strlen;
    
    int i = 0;
    
    while (true) {
      int end = Math.min(offset + strlen - i, bufferLength);
      
      while (offset < end) {
        buffer[offset++] = (byte) value.charAt(i++);
      }
      
      if (offset < bufferLength) {
        return offset;
      }
      
      offset = flushChunk(offset);
      buffer = _buffer;
    }
  }
  
  private void writeInt(int opcode, int bits, long value)
    throws IOException
  {
    int mask = (1 << bits) - 1;
    
    if ((mask & value) == value) {
      int d = (int) (opcode + value);
        
      write(d);
    }
    else {
      write((int) (opcode + mask));
      
      value -= mask;
      while (value >= 0x80) {
        write((int) (0x80 | (value & 0x7f)));
        
        value >>= 7;
      }
      
      write((int) value);
    }
  }
  
  private void write(int v)
    throws IOException
  {
    int offset = ensureCapacity(1);
    byte []buffer = _buffer;
    
    buffer[offset++] = (byte) v;
    
    _offset = offset;
  }
  
  private int ensureCapacity(int len)
    throws IOException
  {
    int offset = _offset;
    byte []buffer = _buffer;
    
    if (offset + len <= buffer.length) {
      return offset;
    }
    else {
      offset = flushChunk(offset);
      
      _offset = offset;
      
      return offset;
    }
  }
  
  public void flush()
  {
  }

  /**
   * References that were not used need to be deleted.
   */
  private void completeReferenceSet()
    throws IOException
  {
    long reference = _seqReference;
    
    for (long i = _tailSequence; i < _sequence; i++) {
      TableEntry entry = _entries[(int) (i % _entries.length)];
      
      if (entry.getReference() == reference) {
        int index = getIndex(entry);
        write(0x80 + index);
      }
    }
  }
  
  @Override
  public void close()
    throws IOException
  {
    //_os.write(_buffer, 0, _index);
  }
  
  static enum StateHeader {
    HEADER_INIT_STREAM_END {
      @Override
      int opcode()
      { 
        return Http2Constants.FRAME_HEADERS; 
      }
      
      @Override
      int flagsFlush()
      {
        return Http2Constants.END_STREAM;
      }
      
      @Override
      int flagsClose()
      { 
        return Http2Constants.END_STREAM|Http2Constants.END_HEADERS;
      }
    },
    
    HEADER_INIT_STREAM_CONT {
      @Override
      int opcode()
      { 
        return Http2Constants.FRAME_HEADERS; 
      }
      
      @Override
      int flagsFlush()
      {
        return 0;
      }
      
      @Override
      int flagsClose()
      { 
        return Http2Constants.END_HEADERS;
      }
    },
    
    HEADER_CONT {
      @Override
      int opcode()
      { 
        return Http2Constants.FRAME_CONT; 
      }
      
      @Override
      int flagsFlush()
      {
        return 0;
      }
      
      @Override
      int flagsClose()
      { 
        return Http2Constants.END_HEADERS;
      }
    };
    
    int opcode()
    {
      throw new IllegalStateException(toString());
    }
    
    int flagsFlush()
    {
      throw new IllegalStateException(toString());
    }
    
    int flagsClose()
    {
      throw new IllegalStateException(toString());
    }
    
    StateHeader onFlush()
    {
      return HEADER_CONT;
    }
  }
}
