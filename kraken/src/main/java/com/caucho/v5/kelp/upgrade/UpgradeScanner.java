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

package com.caucho.v5.kelp.upgrade;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.VfsStream;
import com.caucho.v5.store.io.InStore;
import com.caucho.v5.store.io.StoreBuilder;
import com.caucho.v5.store.io.StoreReadWrite;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Crc32Caucho;
import com.caucho.v5.util.Hex;

/**
 * callback for a kelp upgrade reader
 */
public class UpgradeScanner
{
  private static final Logger log
    = Logger.getLogger(UpgradeScanner.class.getName());
  
  private static final int META_SEGMENT_SIZE = 256 * 1024;
  private static final int META_OFFSET = 1024;
  
  private static final int BLOCK_SIZE = 8192;
  
  private final static int CODE_TABLE = 0x1;
  private final static int CODE_SEGMENT = 0x2;
  private final static int CODE_META_SEGMENT = 0x3;
  
  private final static int TABLE_KEY_SIZE = 32;

  private static long KELP_MAGIC;
  
  private Path _root;
  private ServicesAmp _services;
  private StoreReadWrite _store;

  private long _metaOffset;
  private int _nonce;
  
  private int _segmentId;

  private ArrayList<TableEntry10> _tableList
    = new ArrayList<TableEntry10>();

  private ArrayList<SegmentExtent10> _segmentExtents
    = new ArrayList<SegmentExtent10>();

  private ArrayList<Segment10> _segments
    = new ArrayList<Segment10>();

  private SegmentExtent10 _metaSegment;
  
  public UpgradeScanner(Path root)
  {
    Objects.requireNonNull(root);
    
    _root = root;
  }
  
  public void services(ServicesAmp services)
  {
    _services = services;
  }
  
  public boolean isVersionUnderstood(long magic)
  {
    return magic == KELP_MAGIC;
  }

  public void upgrade(KelpUpgrade upgradeKelp)
    throws IOException
  {
    Objects.requireNonNull(upgradeKelp);
    
    if (! Files.exists(_root)) {
      System.out.println("FILE_NOT_EXIST: " + _root);
      return;
    }
    
    StoreBuilder storeBuilder = new StoreBuilder(_root);
    storeBuilder.services(_services);
    
    _store = storeBuilder.build();
    _store.init();
    
    if (! readMetaHeader()) {
      System.out.println("EMPTY OR CORRUPTED:");
      return;
    }
    
    readMetaHeader();
    readMetaData();
    readSegments();
    
    upgradeDatabase(upgradeKelp);
  }

  /**
   * Reads the initial metadata for the store file as a whole.
   */
  private boolean readMetaHeader()
    throws IOException
  {
    try (ReadStream is = openRead(0, META_SEGMENT_SIZE)) {
      int crc = 17;
      
      long magic = BitsUtil.readLong(is);
      
      if (magic != KELP_MAGIC) {
        System.out.println("WRONG_MAGIC: " + magic);
        return false;
      }
      
      crc = Crc32Caucho.generate(crc, magic);
      
      _nonce = BitsUtil.readInt(is);
      crc = Crc32Caucho.generateInt32(crc, _nonce);
      
      int headers = BitsUtil.readInt(is);
      crc = Crc32Caucho.generateInt32(crc, headers);
      
      for (int i = 0; i < headers; i++) {
        int key = BitsUtil.readInt(is);
        crc = Crc32Caucho.generateInt32(crc, key);
        
        int value = BitsUtil.readInt(is);
        crc = Crc32Caucho.generateInt32(crc, value);
      }
      
      int count = BitsUtil.readInt(is);
      crc = Crc32Caucho.generateInt32(crc, count);
      
      ArrayList<Integer> segmentSizes = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        int size = BitsUtil.readInt(is);
        crc = Crc32Caucho.generateInt32(crc, size);
        
        segmentSizes.add(size);
      }
      
      int crcFile = BitsUtil.readInt(is);
      
      if (crc != crcFile) {
        System.out.println("MISMATCHED_CRC: " + crcFile);
        return false;
      }
      
      _metaSegment = new SegmentExtent10(0, 0, META_SEGMENT_SIZE);
      
      _segmentId = 1;
      
      _metaOffset = is.position();
    }
    
    return true;
  }
    
  /**
   * Reads the metadata entries for the tables and the segments. 
   */
  private boolean readMetaData()
      throws IOException
  {
    SegmentExtent10 segment = _metaSegment;
    
    try (ReadStream is = openRead(segment.address(), segment.length())) {
      is.position(META_OFFSET);
      
      while (readMetaEntry(is)) {
      }
    }
    
    return true;
  }
  
  private boolean readMetaEntry(ReadStream is)
      throws IOException
  {
    int crc = _nonce;
      
    int code = is.read();
    crc = Crc32Caucho.generate(crc, code);
    
    System.out.println("RME: " + code);
    
    boolean isValid = false;

    switch (code) {
    case CODE_TABLE:
      isValid = readMetaTable(is, crc);
      break;
        
    case CODE_SEGMENT:
      isValid = readMetaSegment(is, crc);
      break;
        
    case CODE_META_SEGMENT:
      isValid = readMetaContinuation(is, crc);
      break;
        
    default:
      return false;
    }
      
    _metaOffset = is.position();
     
    return isValid;
  }
  
  /**
   * Reads metadata for a table.
   */
  private boolean readMetaTable(ReadStream is, int crc)
    throws IOException
  {
    byte []key = new byte[TABLE_KEY_SIZE];
      
    is.read(key, 0, key.length);
    crc = Crc32Caucho.generate(crc, key);
    
    int rowLength = BitsUtil.readInt16(is);
    crc = Crc32Caucho.generateInt16(crc, rowLength);
      
    int keyOffset = BitsUtil.readInt16(is);
    crc = Crc32Caucho.generateInt16(crc, keyOffset);
      
    int keyLength = BitsUtil.readInt16(is);
    crc = Crc32Caucho.generateInt16(crc, keyLength);
    
    int dataLength = BitsUtil.readInt16(is);
    crc = Crc32Caucho.generateInt16(crc, dataLength);
    
    byte []data = new byte[dataLength];
    is.read(data);
    crc = Crc32Caucho.generate(crc, data);
    
    int crcFile = BitsUtil.readInt(is);
      
    if (crcFile != crc) {
      log.fine("meta-table crc mismatch");
      System.out.println("meta-table crc mismatch");
      return false;
    }
    
    RowUpgrade row = new RowUpgrade10().read(data);
    
    TableEntry10 table = new TableEntry10(key,
                                          rowLength,
                                          keyOffset,
                                          keyLength,
                                          row);

    _tableList.add(table);

    return true;
  }
  
  private boolean readMetaSegment(ReadStream is, int crc)
    throws IOException
  {
    long value = BitsUtil.readLong(is);
    
    crc = Crc32Caucho.generate(crc, value);

    int crcFile = BitsUtil.readInt(is);
    
    if (crcFile != crc) {
      log.fine("meta-segment crc mismatch");
      return false;
    }
    
    long address = value & ~0xffff;
    int length = (int) ((value & 0xffff) << 16);
    
    SegmentExtent10 segment
      = new SegmentExtent10(_segmentId++, address, length);
    
    _segmentExtents.add(segment);
    
    return true;

  }

  /**
   * Reads the segment metadata, the sequence and table key.
   */
  private void readSegments()
    throws IOException
  {
    for (SegmentExtent10 extent : _segmentExtents) {
      try (ReadStream is = openRead(extent.address(), extent.length())) {
        is.skip(extent.length() - BLOCK_SIZE);
        
        long sequence = BitsUtil.readLong(is);
        
        byte []tableKey = new byte[TABLE_KEY_SIZE];
        is.readAll(tableKey, 0, tableKey.length);
        
        // XXX: crc
        
        if (sequence > 0) {
          Segment10 segment = new Segment10(sequence, tableKey, extent);
          
          _segments.add(segment);
        }
      }
    }
  }

  /**
   * Upgrade the store
   */
  private void upgradeDatabase(KelpUpgrade upgradeKelp)
    throws IOException
  {
    Collections.sort(_tableList, 
                     (x,y)->x.row().name().compareTo(y.row().name()));
    
    for (TableEntry10 table : _tableList) {
      TableUpgrade upgradeTable = upgradeKelp.table(table.key(), table.row());
      
      upgradeTable(table, upgradeTable);
    }
  }
  
  private void upgradeTable(TableEntry10 table, TableUpgrade upgradeTable)
    throws IOException
  {
    for (Segment10 segment : tableSegments(table)) {
      try (ReadStream is = openRead(segment.address(), segment.length())) {
        readPages(is, segment);
      }
    }
  }
  
  private void readPages(ReadStream is, Segment10 segment)
    throws IOException
  {
    int address = segment.length() - BLOCK_SIZE;
    
    is.position(address + BLOCK_SIZE - 8);
    
    int tail = BitsUtil.readInt16(is);
    
    if (tail < TABLE_KEY_SIZE + 8 || tail > BLOCK_SIZE - 8) {
      return;
    }
  }
  
  private ArrayList<Segment10> tableSegments(TableEntry10 table)
  {
    ArrayList<Segment10> tableSegments = new ArrayList<>();
    
    for (Segment10 segment : _segments) {
      if (Arrays.equals(segment.key(), table.key())) {
        tableSegments.add(segment);
      }
    }
    
    Collections.sort(tableSegments,
                     (x,y)->Long.signum(y.sequence() - x.sequence()));
    
    return tableSegments;
  }
  
  private ReadStream openRead(long address, int size)
  {
    InStore inStore = _store.openRead(0, META_SEGMENT_SIZE);
    
    InStoreStream is = new InStoreStream(inStore, address, address + size);
    
    return new ReadStream(new VfsStream(is));
  }
  
  private static class Segment10
  {
    private SegmentExtent10 _extent;
    private long _sequence;
    private byte []_tableKey;
    
    Segment10(long sequence,
              byte []tableKey,
              SegmentExtent10 extent)
    {
      _extent = extent;
      _sequence = sequence;
      _tableKey = tableKey;
    }
    
    public long address()
    {
      return _extent.address();
    }
    
    public int length()
    {
      return _extent.length();
    }

    public long sequence()
    {
      return _sequence;
    }

    public byte[] key()
    {
      return _tableKey;
    }

    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
             + "[" + _sequence 
             + "," + Hex.toShortHex(_tableKey)
             + ",0x" + Long.toHexString(_extent.address())
             + ",0x" + Long.toHexString(_extent.length())
             + "]");
    }
  }
  
  private static class SegmentExtent10
  {
    private final int _segmentId;
    private final long _address;
    private final int _length;
    
    SegmentExtent10(int segmentId, long address, int length)
    {
      _segmentId = segmentId;
      _address = address;
      _length = length;
    }
    
    long address()
    {
      return _address;
    }
    
    int length()
    {
      return _length;
    }
    
    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
              + "[" + _segmentId
              + ",0x" + Long.toHexString(_address) 
              + ",0x" + Long.toHexString(_length)
              + "]");
    }
  }
  
  private boolean readMetaContinuation(ReadStream is, int crc)
  {
    System.out.println("RMC: " + is);;
    return false;
  }

  /**
   * InputStream for segment data.
   */
  private static class InStoreStream extends InputStream
  {
    private InStore _store;
    private long _address;
    private long _addressTail;
    private byte []_buf = new byte[1];
    
    InStoreStream(InStore store,
                  long address,
                  long addressTail)
    {
      _store = store;
      _address = address;
      _addressTail = addressTail;
    }
    
    @Override
    public int read()
      throws IOException
    {
      int sublen = read(_buf, 0, 1);
      
      if (sublen < 1) {
        return -1;
      }
      else {
        return _buf[0] & 0xff;
      }
    }
    
    @Override
    public int read(byte []buffer, int offset, int length)
      throws IOException
    {
      int sublen = (int) Math.min(_addressTail - _address, length);
      
      if (sublen <= 0) {
        return -1;
      }
      
      _store.read(_address, buffer, offset, sublen);
      
      _address += sublen;
      
      return sublen;
    }
  }
  
  private static class SegmentMeta {
    
  }
  
  static class TableEntry10 {
    private final byte []_key;
    private final int _rowLength;
    private final int _keyOffset;
    private final int _keyLength;
    private final RowUpgrade _row;
    
    TableEntry10(byte []key,
               int rowLength,
               int keyOffset,
               int keyLength,
               RowUpgrade row)
    {
      Objects.requireNonNull(key);
      Objects.requireNonNull(row);
      
      _key = key;
      _rowLength = rowLength;
      _keyOffset = keyOffset;
      _keyLength = keyLength;
      _row = row;
    }
    
    public byte[] key()
    {
      return _key;
    }
    
    public RowUpgrade row()
    {
      return _row;
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + Hex.toShortHex(_key) + "]";
    }
  }
  
  static {
    byte []magicBytes = "Kelp1102".getBytes();
    
    KELP_MAGIC = BitsUtil.readLong(magicBytes, 0);
  }
}
