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
import java.util.HashMap;
import java.util.Objects;

import com.caucho.v5.io.ReadStream;
import com.caucho.v5.util.L10N;


/**
 * HeaderOut is the compression for the writer.
 */
public class InHeader extends HeaderCommon implements AutoCloseable
{
  private static final L10N L = new L10N(InHeader.class);
  
  private HashMap<String,TableEntry> _tableKeyMap;
  private HashMap<TableEntry,TableEntry> _tableEntryMap;
    
  private int _tableCapacity = 4096;
  private int _tableSize = 0;
  
  private TableEntry []_entries;
  
  private TableEntry _key = new TableEntry();
  
  private ReadStream _is;

  private StateHeaderIn _state;
  
  private char []_charBuffer = new char[256];
  
  private int _length;
  
  private int _streamId;
  
  private long _sequenceHead;
  private long _sequenceTail;
  
  private long _sequenceReference;

  private int _pad;
  
  public InHeader(ReadStream is)
  {
    Objects.requireNonNull(is);
    
    _tableKeyMap = new HashMap<>();
    _tableKeyMap.putAll(getTableKeyStatic());
    
    _tableEntryMap = new HashMap<>();
    _tableEntryMap.putAll(getTableEntryStatic());
    
    _entries = new TableEntry[256];
    
    _is = is;
  }
  
  boolean readHeaders(InRequest request, int length, int flags)
    throws IOException
  {
    _length = length;
    
    int pad = 0;
    
    if ((flags & Http2Constants.PAD_HIGH) != 0) {
      int padHigh = read();
      int padLow = read();
      
      flags &= ~(Http2Constants.PAD_HIGH|Http2Constants.PAD_LOW);

      pad = (padHigh * 256 + padLow);
    }
    else if ((flags & Http2Constants.PAD_LOW) != 0) {
      int padLow = read();
      
      flags &= ~Http2Constants.PAD_LOW;
      
      pad = padLow;
    }
    
    if ((flags & Http2Constants.PRIORITY) != 0) {
      int depend = readInt();
      int weight = read();
      
      flags &= ~Http2Constants.PRIORITY;
    }
    
    _length -= pad;
    
    switch (flags) {
    case 0:
      _state = StateHeaderIn.HEADER_CONT_STREAM_CONT;
      break;

    case Http2Constants.END_STREAM:
      _state = StateHeaderIn.HEADER_CONT_STREAM_END;
      break;

    case Http2Constants.END_HEADERS:
      _state = StateHeaderIn.HEADER_END_STREAM_CONT;
      break;
      
    case Http2Constants.END_STREAM|Http2Constants.END_HEADERS:
      _state = StateHeaderIn.HEADER_END_STREAM_END;
      break;
      
    default:
      throw new IllegalStateException(L.l("Invalid header flags 0x{0}",
                                          Integer.toHexString(flags)));
    }
    
    long seqReference = ++_sequenceReference;
    
    startHeaders(request);
    
    readHeaders(request, seqReference);
    
    completeHeaders(request, seqReference);
    
    skip(pad);
    
    return true;
  }
  
  private void startHeaders(InRequest request)
  {
  }
  
  private void readHeaders(InRequest request, long seqReference)
    throws IOException
  {
    int op;
    
    while ((op = read()) >= 0) {
      if ((op & 0x80) != 0) {
        readIndex(request, op);
      }
      else if ((op & 0x40) != 0) {
        readHeader(request, 6, op, true, seqReference);
      }
      else if ((op & 0x30) == 0x30) {
        clearReferenceSet();
      }
      else if ((op & 0x30) == 0x20) {
        int capacity = readInt(4, op);
        
        _tableCapacity = capacity;
        
        updateTableSize();
      }
      else if ((op & 0x10) != 0) {
        readHeader(request, 4, op, false, seqReference);
      }
      else {
        readHeader(request, 4, op, false, seqReference);
      }
    }
  }
  
  private void readHeader(InRequest request,
                          int bits,
                          int op, 
                          boolean isUpdateTable,
                          long seqReference)
    throws IOException
  {
    String key;
    
    int mask = (1 << bits) - 1;
    
    if ((op & mask) != 0) {
      int index = readInt(bits, op);
      
      key = readKeyHeader(index);
    }
    else {
      key = readString();
    }
    
    String value = readString();
    
    request.header(key, value);
    
    if (isUpdateTable) {
      TableEntry entry = new TableEntry();
      
      entry.update(0, key, value);
      
      addEntry(entry);
    }
  }

  private void addEntry(TableEntry entry)
  {
    long head = _sequenceHead;
    entry.setSequence(head);
    entry.setReference(_sequenceReference + 1);

    _sequenceHead = head + 1;

    _tableEntryMap.put(entry, entry);

    if (entry.getNext() == null) {
      _tableKeyMap.put(entry.getKey(), entry);
    }

    _entries[(int) (head % _entries.length)] = entry;

    _tableSize += entry.getSize();

    updateTableSize();
  }
  
  private void clearReferenceSet()
  {
    for (long i = _sequenceTail; i < _sequenceHead; i++) {
      TableEntry entry = _entries[(int) (i % _entries.length)];
      
      entry.setReference(0);
    }
  }
  
  private void completeHeaders(InRequest request,
                               long reference)
  {
    for (long ptr = _sequenceTail; ptr < _sequenceHead; ptr++) {
      TableEntry entry = _entries[(int) (ptr % _entries.length)];
      
      if (entry.getReference() == reference) {
        entry.setReference(reference + 1);
        request.header(entry.getKey(), entry.getValue());
      }
    }
  }
  
  private void readIndex(InRequest request, int op)
    throws IOException
  {
    int index = readInt(7, op);
    
    TableEntry entry = getEntry(index);
    
    if (entry.getReference() != _sequenceReference) {
      request.header(entry.getKey(), entry.getValue());
      
      if (entry.isStatic()) {
        TableEntry next = new TableEntry(entry);
        
        addEntry(next);
      }
    }
    else {
      // index entries already in the reference is a deletion.
      entry.setReference(0);
    }
  }
  
  private String readKeyHeader(int index)
    throws IOException
  {
    TableEntry entry = getEntry(index);

    return entry.getKey();
  }
  
  private TableEntry getEntry(int index)
  {
    int length = (int) (_sequenceHead - _sequenceTail);
    
    if (length < index) {
      index -= length;

      TableEntry []entryArray = getEntryArrayStatic();
      
      if (index == 0 || entryArray.length <= index) {
        throw new Http2ProtocolException(L.l("Invalid header index '{0}'", index));
      }
    
      TableEntry entry = entryArray[index];
      
      return entry;
    }
    else {
      TableEntry []entryArray = _entries;
      
      long i = _sequenceHead - index;
      
      TableEntry entry = entryArray[(int) (i % entryArray.length)];
      
      return entry;
    }
  }
  
  private void updateTableSize()
  {
    while (_tableCapacity < _tableSize) {
      removeTableEntry();
    }
  }
  
  private void removeTableEntry()
  {
    long i = _sequenceTail++;
    
    TableEntry entry = _entries[(int) (i % _entries.length)];
    entry.setReference(0);
    
    _tableEntryMap.remove(entry);
    
    TableEntry next = entry.getNext();
    if (next != null) {
      _tableEntryMap.put(next, next);
    }
    
    _tableSize -= entry.getSize();
  }
  
  private int readInt(int bits, int d)
    throws IOException
  {
    int mask = (1 << bits) - 1;
    
    int value = d & mask;
    
    if (value != mask) {
      return value;
    }
    
    int m = 0;
    
    do {
      d = read();
    
      value += (d & 0x7f) << m;
      
      m += 7;
    } while ((d & 0x80) == 0x80);
    
    return value;
  }
 
  private String readString()
    throws IOException
  {
    int len = read();
    
    char []buffer = _charBuffer;
    
    if ((len & 0x80) != 0) {
      // huffman encoded
      len &= 0x7f;
      
      while (buffer.length <= 2 * len) {
        buffer = new char[2 * buffer.length];
        _charBuffer = buffer;
      }
      
      int strlen = huffmanDecode(len, buffer);
      
      return new String(buffer, 0, strlen);
    }
    else {
      while (buffer.length <= len) {
        buffer = new char[2 * buffer.length];
        _charBuffer = buffer;
      }
      
      for (int i = 0; i < len; i++) {
        buffer[i] = (char) read();
      }
      
      return new String(buffer, 0, len);
    }
    
  }
  
  protected int readInt()
    throws IOException
  {
    return (((read() & 0xff) << 24)
           | ((read() & 0xff) << 16)
           | ((read() & 0xff) << 8)
           | ((read() & 0xff)));
  }
  
  protected void skip(int n)
    throws IOException
  {
    _length = n;
    
    for (int i = 0; i < n; i++) {
      read();
    }
  }
  
  @Override
  protected int read()
    throws IOException
  {
    int length = _length;
    
    if (length <= 0) {
      return -1;
    }
    
    length--;
    
    int ch = _is.read();
    
    _length = length;
    
    return ch;
  }
  
  @Override
  public void close()
  {
    //_os.write(_buffer, 0, _index);
  }
  
  static enum StateHeaderIn {
    HEADER_CONT_STREAM_CONT {
      boolean isEndStream() { return false; }
    },
    HEADER_CONT_STREAM_END {
      boolean isEndStream() { return true; }
    },
    HEADER_END_STREAM_END {
      boolean isEndStream() { return true; }
    },
    HEADER_END_STREAM_CONT {
      boolean isEndStream() { return false; }
    };
    
    abstract boolean isEndStream();
  }
}
