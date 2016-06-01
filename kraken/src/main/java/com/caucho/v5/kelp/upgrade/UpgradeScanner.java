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
import java.util.TreeMap;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.TempBuffer;
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
  
  private static final int FOOTER_OFFSET = BLOCK_SIZE - 8;
  private static final int INDEX_OFFSET = TABLE_KEY_SIZE + 8;
  
  private static final int CODE_MASK = 0xc0;
  
  private static final int INSERT = 0x40;
  private static final int INSERT_DEAD = 0xc0;
  private static final int REMOVE = 0x80;
  
  private static final int STATE_LENGTH = 12;
  
  private static final int LARGE_BLOB_MASK = 0x8000;

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
  
  private TreeMap<Integer,Page10> _pageMap = new TreeMap<>();

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
    
    RowUpgrade row = new RowUpgrade10(keyOffset, keyLength).read(data);
    
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

  /**
   * Upgrade rows from a table.
   */
  private void upgradeTable(TableEntry10 table, 
                            TableUpgrade upgradeTable)
    throws IOException
  {
    _pageMap = new TreeMap<>();
    
    readTableIndex(table);
    
    for (Page10 page : _pageMap.values()) {
      upgradeLeaf(table, upgradeTable, page);
    }
  }
  
  /**
   * Read all page metadata for a table.
   */
  private void readTableIndex(TableEntry10 table)
    throws IOException
  {
    for (Segment10 segment : tableSegments(table)) {
      try (ReadStream is = openRead(segment.address(), segment.length())) {
        readSegmentIndex(is, segment);
      }
    }
  }
  
  /**
   * Read page index from a segment.
   */
  private void readSegmentIndex(ReadStream is, Segment10 segment)
    throws IOException
  {
    int address = segment.length() - BLOCK_SIZE;
    
    TempBuffer tBuf = TempBuffer.create();
    byte []buffer = tBuf.buffer();
    
    is.position(address);
    is.read(buffer, 0, BLOCK_SIZE);
    
    int tail = BitsUtil.readInt16(buffer, FOOTER_OFFSET);
    
    if (tail < TABLE_KEY_SIZE + 8 || tail > BLOCK_SIZE - 8) {
      return;
    }
    
    int offset = INDEX_OFFSET;
    
    while (offset < tail) {
      int type = buffer[offset++] & 0xff;
      
      int pid = BitsUtil.readInt(buffer, offset);
      offset += 4;
      
      int nextPid = BitsUtil.readInt(buffer, offset);
      offset += 4;
      
      int entryAddress = BitsUtil.readInt(buffer, offset);
      offset += 4;
      
      int entryLength = BitsUtil.readInt(buffer, offset);
      offset += 4;
      
      if (pid <= 1) {
        System.out.println("INVALID_PID: " + pid);
        return;
      }
      
      switch (PageType10.values()[type]) {
      case LEAF:
        addLeaf(segment, pid, nextPid, entryAddress, entryLength);
        break;
        
      default:
        System.out.println("UNKONWN: " + PageType10.values()[type]);
      }
    }
  }
  
  /**
   * Adds a new leaf entry to the page list.
   * 
   * Because pages are added in order, each new page overrides the older one.
   */
  private void addLeaf(Segment10 segment,
                       int pid, 
                       int nextPid,
                       int address,
                       int length)
  {
    Page10 page = _pageMap.get(pid);
    
    if (page != null && page.sequence() < segment.sequence()) {
      return;
    }
    
    page = new Page10(PageType10.LEAF, segment, pid, nextPid, address, length);
    
    _pageMap.put(pid, page);
  }

  /**
   * Returns segments for a table in reverse sequence order.
   * 
   * The reverse order minimizes extra pages reads, because older pages
   * don't need to be read.
   */
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

  /**
   * Upgrade a table page.
   */
  private void upgradeLeaf(TableEntry10 table, 
                           TableUpgrade upgradeTable,
                           Page10 page)
    throws IOException
  {
    try (ReadStream is = openRead(page.segment().address(),
                                  page.segment().length())) {
      is.position(page.address());
      
      byte []minKey = new byte[table.keyLength()];
      byte []maxKey = new byte[table.keyLength()];
      
      is.read(minKey, 0, minKey.length);
      is.read(maxKey, 0, maxKey.length);
      
      int blocks = BitsUtil.readInt16(is);
      
      for (int i = 0; i < blocks; i++) {
        upgradeLeafBlock(is, table, upgradeTable, page);
      }
    }
  }
  
  /**
   * Reads data for a leaf.
   * 
   * <code><pre>
   * blobLen int16
   * blobData {blobLen}
   * rowLen int16
   * rowData {rowLen}
   * </pre></code>
   */
  private void upgradeLeafBlock(ReadStream is,
                                TableEntry10 table,
                                TableUpgrade upgradeTable,
                                Page10 page)
    throws IOException
  {
    TempBuffer tBuf = TempBuffer.create();
    
    byte []buffer = tBuf.buffer();
    
    int blobLen = BitsUtil.readInt16(is);
    
    is.readAll(buffer, 0, blobLen);
    
    int rowDataLen = BitsUtil.readInt16(is);
    int rowOffset = buffer.length - rowDataLen;
    
    is.readAll(buffer, rowOffset, rowDataLen);
    
    int rowLen = table.rowLength();
    int keyLen = table.keyLength();
    
    while (rowOffset < buffer.length) {
      int code = buffer[rowOffset] & CODE_MASK;
      
      switch (code) {
      case INSERT:
        rowInsert(table.row(), upgradeTable, buffer, rowOffset);
        
        rowOffset += rowLen;
        break;
        
      case REMOVE:
        rowOffset += keyLen + STATE_LENGTH;
        break;
        
      default:
        System.out.println("UNKNOWN: " + Integer.toHexString(code));
        return;
      }
    }
    
    tBuf.free();
  }

  /**
   * Insert a row.
   */
  private void rowInsert(RowUpgrade row,
                         TableUpgrade upgradeTable,
                         byte []buffer,
                         int offset)
  {
    Cursor10 cursor = new Cursor10(row, buffer, offset);
    
    upgradeTable.row(cursor);
  }
  
  /**
   * Open a read stream to a segment.
   * 
   * @param address file address for the segment
   * @param size length of the segment
   * 
   * @return opened ReadStream
   */
  private ReadStream openRead(long address, int size)
  {
    InStore inStore = _store.openRead(address, size);
    
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
    
    public int keyLength()
    {
      return _keyLength;
    }
    
    public int rowLength()
    {
      return _rowLength;
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
      return (getClass().getSimpleName()
              + "[" + Hex.toShortHex(_key)
              + "," + _row.name()
              + "]");
    }
  }
  
  private class Page10 implements Comparable<Page10>
  {
    private PageType10 _type;
    private Segment10 _segment;
    private int _pid;
    private int _pidNext;
    private int _address;
    private int _length;
    
    Page10(PageType10 type,
           Segment10 segment,
           int pid,
           int pidNext,
           int address,
           int length)
    {
      _type = type;
      _segment = segment;
      _pid = pid;
      _pidNext = pidNext;
      _address = address;
      _length = length;
    }
    
    private Segment10 segment()
    {
      return _segment;
    }
    
    public long sequence()
    {
      return _segment.sequence();
    }
    
    public int address()
    {
      return _address;
    }
    
    public int length()
    {
      return _length;
    }
    
    @Override
    public int hashCode()
    {
      return _pid;
    }
    
    @Override
    public boolean equals(Object value)
    {
      if (! (value instanceof Page10)) {
        return false;
      }
      
      Page10 page = (Page10) value;
      
      return _pid == page._pid;
    }

    @Override
    public int compareTo(Page10 page)
    {
      return _pid - page._pid;
    }
    
    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
              + "[" + _type
              + "," + _pid
              + ",seq=" + sequence()
              + "]");
    }
  }
  
  private class Cursor10 implements CursorUpgrade
  {
    private RowUpgrade _row;
    private byte []_buffer;
    private int _offset;
    
    Cursor10(RowUpgrade row,
             byte []buffer,
             int offset)
    {
      _row = row;
      _buffer = buffer;
      _offset = offset;
    }
    
    @Override
    public RowUpgrade row()
    {
      return _row;
    }

    @Override
    public int size()
    {
      return _row.columns().length;
    }

    @Override
    public int getInt(int index)
    {
      ColumnUpgrade column = column(index);
      
      int offset = column.offset();
      
      switch (column.type()) {
      case INT8:
        return _buffer[_offset + offset] & 0xff;
        
      case INT16:
        return BitsUtil.readInt16(_buffer, _offset + offset);
        
        
      case INT32:
        return BitsUtil.readInt(_buffer, _offset + offset);
        
      default:
        throw new UnsupportedOperationException(column.toString());
      }
    }

    @Override
    public long getLong(int index)
    {
      ColumnUpgrade column = column(index);
      
      int offset = column.offset();
      
      switch (column.type()) {
      case INT8:
        return _buffer[_offset + offset] & 0xff;
        
      case INT16:
        return BitsUtil.readInt16(_buffer, _offset + offset);
        
      case INT32:
        return BitsUtil.readInt(_buffer, _offset + offset);
        
      case INT64:
        return BitsUtil.readLong(_buffer, _offset + offset);
        
      default:
        throw new UnsupportedOperationException(column.toString());
      }
    }

    @Override
    public double getDouble(int column)
    {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public boolean getBoolean(int column)
    {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public byte[] getBytes(int column)
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getString(int column)
    {
      int offset = column(column).offset();
      
      int blobOffset = BitsUtil.readInt16(_buffer, _offset + offset);
      int blobLength = BitsUtil.readInt16(_buffer, _offset + offset + 2);
      
      if ((blobLength & LARGE_BLOB_MASK) != 0) {
        System.out.println("LARGE: " + blobOffset + " " + blobLength);
        
        return null;
      }
      
      try {
        return new String(_buffer, blobOffset, blobLength, "utf-8");
      } catch (Exception e) {
        throw new RuntimeException(e.toString());
      }
    }
    
    private ColumnUpgrade column(int index)
    {
      return _row.columns()[index];
    }

    @Override
    public int getBlobLength(int column)
    {
      int offset = column(column).offset();
      
      int blobOffset = BitsUtil.readInt16(_buffer, _offset + offset);
      int blobLength = BitsUtil.readInt16(_buffer, _offset + offset + 2);
      
      if ((blobLength & LARGE_BLOB_MASK) != 0) {
        System.out.println("LARGE: " + blobOffset + " " + blobLength);
        return -1;
      }
      
      return blobLength;
    }

    @Override
    public byte[] getBlobBytes(int column)
    {
      int offset = column(column).offset();
      
      int blobOffset = BitsUtil.readInt16(_buffer, _offset + offset);
      int blobLength = BitsUtil.readInt16(_buffer, _offset + offset + 2);
      
      if ((blobLength & LARGE_BLOB_MASK) != 0) {
        System.out.println("LARGE: " + blobOffset + " " + blobLength);
        
        return null;
      }
      
      byte []data = new byte[blobLength];
      
      System.arraycopy(_buffer, blobOffset, data, 0, blobLength);
      
      return data;
    }

    @Override
    public int getBlobPage(int column)
    {
      // TODO Auto-generated method stub
      return 0;
    }
    
    @Override
    public String toString()
    {
      StringBuilder sb = new StringBuilder();
      
      sb.append(getClass().getSimpleName());
      sb.append("[");
      
      boolean isFirst = true;
      for (ColumnUpgrade column : _row.columns()) {
        if (column.isKey()) {
          if (! isFirst) {
            sb.append(",");
          }
          isFirst = false;
          
          sb.append(Hex.toShortHex(_buffer, 
                                   _offset + column.offset(),
                                   column.length()));
        }
      }
      
      sb.append("]");
      
      return sb.toString();
    }
  }
  
  private enum PageType10
  {
    NONE,
    TREE,
    LEAF,
    LEAF_DELTA,
    BLOB_TEMP,
    BLOB,
    BLOB_FREE;
    
  }
  
  static {
    byte []magicBytes = "Kelp1102".getBytes();
    
    KELP_MAGIC = BitsUtil.readLong(magicBytes, 0);
  }
}
