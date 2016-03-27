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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.TempOutputStream;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.io.CompressorKelp;
import com.caucho.v5.store.io.InStore;
import com.caucho.v5.store.io.OutStore;
import com.caucho.v5.store.io.StoreBuilder;
import com.caucho.v5.store.io.StoreReadWrite;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.ConcurrentArrayList;
import com.caucho.v5.util.Crc32Caucho;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.ReadStream;

import io.baratine.service.Result;

/**
 * Segment management for kelp.
 * 
 * meta-syntax:
 *   i64 - kelp magic
 *   i32 - nonce
 *   i32 - segment-size+
 *   i32 - 0
 *   i32 - crc32
 *   entry*
 *   
 * table-entry:
 *   i8 - 0x01
 *   byte[key_size] - key
 *   i16 - row length
 *   i16 - key offset
 *   i16 - key length
 *   i32 - crc32
 *   
 * segment-entry:
 *   i8 - 0x02
 *   i48 - address >> 16
 *   i16 - length >> 16
 *   i32 - crc32
 */
public class SegmentServiceImpl
{
  private final static Logger log
    = Logger.getLogger(SegmentServiceImpl.class.getName());
  private final static L10N L = new L10N(SegmentServiceImpl.class);
  
  public final static long KELP_MAGIC;
  
  public final static int BLOCK_SIZE = 8 * 1024;
  public final static int TABLE_KEY_SIZE = TableKelp.TABLE_KEY_SIZE;
  
  private final static int CRC_INIT = 17;
  
  private final static int CODE_TABLE = 0x1;
  private final static int CODE_SEGMENT = 0x2;
  private final static int CODE_META_SEGMENT = 0x3;
  
  final static int SEGMENT_MIN = 64 * 1024;
  private final static int META_SEGMENT_SIZE = 256 * 1024;
  
  private final static int META_OFFSET = 1024;
  
  // private final static long FILE_SIZE_INCREMENT = 32L * 1024 * 1024; 
  // private final static long FILE_SIZE_INCREMENT = 64 * 1024;
  
  // private final DatabaseKelp _db;
  private final Path _path;
  private StoreReadWrite _store;
  
  private SegmentMeta []_segmentMeta;
  
  private int _nonce;
  
  private ArrayList<SegmentExtent> _metaExtents = new ArrayList<>();
  private ArrayList<TableEntry> _tableList = new ArrayList<>();
  
  private int _segmentId;
  private long _addressTail;
  
  private final AtomicLong _segmentAlloc = new AtomicLong();
  private final AtomicLong _freeAlloc = new AtomicLong();
  private final AtomicLong _freeFree = new AtomicLong();
  
  private ConcurrentArrayList<SegmentGap> _gapList
    = new ConcurrentArrayList<>(SegmentGap.class);
  
  private long _metaAddress;
  private long _metaOffset;
  private long _metaTail;
  private long _storeChunkSize;
  private ServicesAmp _ampManager;
  private CompressorKelp _compressor;

  /**
   * Creates a new store.
   *
   * @param database the owning database.
   * @param name the store name
   * @param lock the table lock
   * @param path the path to the files
   */
  SegmentServiceImpl(SegmentKelpBuilder builder)
  {
    _path = builder.path();
    _ampManager = builder.ampManager();
    
    Objects.requireNonNull(_path);
    
    ArrayList<Integer> segmentSizes = builder.getSegmentSizes();
    
    if (segmentSizes.size() == 0) {
      segmentSizes = new ArrayList<>();
      segmentSizes.add(1024 * 1024);
    }
    
    Collections.sort(segmentSizes);

    SegmentMeta[] segmentMeta = new SegmentMeta[segmentSizes.size()];
    for (int i = 0; i < segmentMeta.length; i++) {
      segmentMeta[i] = new SegmentMeta(segmentSizes.get(i));
    }
    
    _compressor = builder.compressor();
    Objects.requireNonNull(_compressor);
    
    //_blobStream = new SegmentStream();
    
    try {
      if (Files.exists(_path) && initImpl()) {
      }
      else if (builder.isCreate()) {
        Files.deleteIfExists(_path);
        
        create(segmentMeta, builder.ampManager());
      }
      else {
        throw new IllegalStateException(L.l("load failed but create forbidden"));
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public StoreReadWrite store()
  {
    return _store;
  }

  /**
   * Returns the file size.
   */
  public long fileSize()
  {
    return _store.fileSize();
  }
  
  public CompressorKelp compressor()
  {
    return _compressor;
  }
    
  /**
   * Creates the store.
   */
  private void create(SegmentMeta []segmentMetaList,
                      ServicesAmp manager)
    throws IOException
  {
    Objects.requireNonNull(segmentMetaList);
    
    _store = buildStore(manager);
    
    _store.create();
    
    createMetaSegment(segmentMetaList);
    
    _segmentId = 1;
  }

  boolean isFileExist()
  {
    return Files.exists(_path);
  }

  private boolean initImpl()
    throws IOException
  {
    _store = buildStore(_ampManager);
    
    _store.init();
    
    try {
      if (load()) {
        return true;
      }
      
      log.warning(L.l("{0} corrupted database file. Recreating.", this));
    } catch (Exception e) {
      log.warning(L.l("{0} corrupted database file. Recreating.\n  {0}", this, e));
    }
    
    _store.close();
    _store = null;
    
    return false;
    
    /*
    _path.remove();
      
    StoreBuilder storeBuilder = new StoreBuilder(_path);
    storeBuilder.mmap(true);
      
    _store = storeBuilder.build();
      
    create();
    */
  }
  
  private StoreReadWrite buildStore(ServicesAmp manager)
  {
    StoreBuilder storeBuilder = new StoreBuilder(_path);
    storeBuilder.mmap(true);
    storeBuilder.ampManager(manager);
    
    StoreReadWrite store = storeBuilder.build();
    
    _storeChunkSize = store.getChunkSize();
    
    return store;
  }

  private boolean load()
    throws IOException
  {
    _store.init();
    
    try {
      if (! readMetaData()) {
        return false;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    
    ArrayList<SegmentExtent> extents = new ArrayList<>();
    
    try {
      extents.addAll(_metaExtents);
      
      for (SegmentMeta segmentMeta : _segmentMeta) {
        for (SegmentExtent extent : segmentMeta.getSegments()) {
          loadSegment(extent);
          
          extents.add(extent);
        }
      }
      
      initGaps(extents);
      
      return true;
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    return false;
  }
  
  private void initGaps(ArrayList<SegmentExtent> extents)
  {
    _gapList.clear();
    
    Collections.sort(extents, SegmentExtentComparator.CMP);
    
    long addressTail = 0;
    
    for (SegmentExtent extent : extents) {
      long address = extent.getAddress();
      
      if (addressTail < address) {
        int length = (int) (address - addressTail);
        
        _gapList.add(new SegmentGap(addressTail, length));
      }
      
      
      addressTail = address + extent.getLength();
    }
    
    _addressTail = addressTail;
  }
  
  private void createMetaSegment(SegmentMeta []segmentMetaList)
    throws IOException
  {
    int metaSegmentSize = segmentMetaList[0].getSize(); 
        
    try (OutStore sOut = _store.openWrite(0, metaSegmentSize)) {
      TempOutputStream tos = new TempOutputStream();
      
      int crc = CRC_INIT;

      BitsUtil.writeLong(tos, KELP_MAGIC);
      crc = Crc32Caucho.generate(crc, KELP_MAGIC);
      
      _nonce = new Random().nextInt();
      if (_nonce == 0) {
        _nonce = 1;
      }
      BitsUtil.writeInt(tos, _nonce);
      crc = Crc32Caucho.generateInt32(crc, _nonce);
      
      crc = writeMetaHeader(tos, crc);
      
      int count = segmentMetaList.length;
      BitsUtil.writeInt(tos, count);
      crc = Crc32Caucho.generateInt32(crc, count);
      
      for (int i = 0; i < count; i++ ){
        SegmentMeta meta = segmentMetaList[i];

        BitsUtil.writeInt(tos, meta.getSize());
        crc = Crc32Caucho.generateInt32(crc, meta.getSize());
      }
      
      _segmentMeta = segmentMetaList;
      
      BitsUtil.writeInt(tos, crc);
      tos.close();
    
      long offset = 0;
      
      offset = writeTemp(sOut, offset, tos.getHead());
      
      if (META_OFFSET < offset) {
        throw new IllegalStateException();
      }
      
      _metaAddress = 0;
      _metaOffset = META_OFFSET;
      _metaTail = metaSegmentSize;
      
      _metaExtents.add(new SegmentExtent(0, 0, metaSegmentSize));
    }
    
    _addressTail = _metaTail;
  }
  
  private int writeMetaHeader(OutputStream os, int crc)
    throws IOException
  {
    int headers = 1;

    crc = writeInt(os, headers, crc);
    
    crc = writeInt(os, KelpHeader.COMPRESS.ordinal(), crc);
    crc = writeInt(os, KelpCompress.DEFLATE.ordinal(), crc);
    
    return crc;
  }
  
  private int writeInt(OutputStream os, int value, int crc)
    throws IOException
  {
    BitsUtil.writeInt(os, value);
    crc = Crc32Caucho.generateInt32(crc, value);
    
    return crc;
  }
  
  private long writeTemp(OutStore sOut, long offset, TempBuffer tBuf)
  {
    for (; tBuf != null; tBuf = tBuf.getNext()) {
      byte []buffer = tBuf.buffer();
      int sublen = Math.min(buffer.length, tBuf.length());
      
      sOut.write(offset, buffer, 0, sublen);
      offset += sublen;
    }
    
    return offset;
  }

  public TableEntry findTable(byte[] tableKey)
  {
    for (TableEntry entry : _tableList) {
      if (Arrays.equals(tableKey, entry.getTableKey())) {
        return entry;
      }
    }
    
    return null;
  }
  
  public boolean addTable(byte []key,
                          int rowLength,
                          int keyOffset,
                          int keyLength)
  {
    for (TableEntry entry : _tableList) {
      if (Arrays.equals(key, entry.getTableKey())) {
        return false;
      }
    }
    
    TableEntry entry = new TableEntry(key,
                                      rowLength,
                                      keyOffset,
                                      keyLength);
    
    _tableList.add(entry);
    
    writeMetaTable(entry);
    
    return true;
  }
  
  private void writeMetaTable(TableEntry entry)
  {
    TempBuffer tBuf = TempBuffer.allocate();
    byte []buffer = tBuf.buffer();
    
    int offset = 0;
    buffer[offset++] = CODE_TABLE;
    offset += BitsUtil.write(buffer, offset, entry.getTableKey());
    offset += BitsUtil.writeInt16(buffer, offset, entry.getRowLength());
    offset += BitsUtil.writeInt16(buffer, offset, entry.getKeyOffset());
    offset += BitsUtil.writeInt16(buffer, offset, entry.getKeyLength());
    
    int crc = _nonce;
    crc = Crc32Caucho.generate(crc, buffer, 0, offset);

    offset += BitsUtil.writeInt(buffer, offset, crc);
    
    // XXX: overflow
    try (OutStore sOut = openWrite(_metaOffset, offset)) {
      sOut.write(_metaOffset, buffer, 0, offset);
      _metaOffset += offset;
    }
    
    tBuf.freeSelf();
    
    if (_metaTail - _metaOffset < 16) {
      writeMetaContinuation();
    }
  }
  
  private void writeMetaSegment(SegmentExtent extent)
  {
    TempBuffer tBuf = TempBuffer.allocate();
    byte []buffer = tBuf.buffer();
    
    int offset = 0;
    buffer[offset++] = CODE_SEGMENT;
    
    long address = extent.getAddress();
    int length = extent.getLength();
    
    long value = (address & ~0xffff) | (length >> 16);
    
    offset += BitsUtil.writeLong(buffer, offset, value);
    
    int crc = _nonce;
    crc = Crc32Caucho.generate(crc, buffer, 0, offset);

    offset += BitsUtil.writeInt(buffer, offset, crc);

    try (OutStore sOut = openWrite(_metaOffset, offset)) {
      sOut.write(_metaOffset, buffer, 0, offset);
      _metaOffset += offset;
    }
    
    tBuf.freeSelf();
    
    
    if (_metaTail - _metaOffset < 16) {
      writeMetaContinuation();
    }
  }

  private void writeMetaContinuation()
  {
    TempBuffer tBuf = TempBuffer.allocate();
    byte []buffer = tBuf.buffer();
    
    int metaLength = _segmentMeta[0].getSize();
    
    SegmentExtent extent = new SegmentExtent(0, _addressTail, metaLength);
    
    _metaExtents.add(extent);
    
    _addressTail += metaLength;
    
    int offset = 0;
    buffer[offset++] = CODE_META_SEGMENT;
    
    long address = extent.getAddress();
    int length = extent.getLength();
    
    long value = (address & ~0xffff) | (length >> 16);
    
    offset += BitsUtil.writeLong(buffer, offset, value);
    
    int crc = _nonce;
    crc = Crc32Caucho.generate(crc, buffer, 0, offset);

    offset += BitsUtil.writeInt(buffer, offset, crc);

    try (OutStore sOut = openWrite(_metaOffset, offset)) {
      sOut.write(_metaOffset, buffer, 0, offset);
    }
    
    tBuf.freeSelf();
    
    _metaAddress = address;
    _metaOffset = address;
    _metaTail = address + length;
  }

  private boolean readMetaData()
    throws IOException
  {
    SegmentExtent metaExtentInit = new SegmentExtent(0, 0, META_SEGMENT_SIZE);
    
    try (InSegment reader = openRead(metaExtentInit)) {
      ReadStream is = reader.in();
      
      if (! readMetaDataHeader(is)) {
        return false;
      }
      
      _segmentId = 1;
    }
    
    int metaLength = _segmentMeta[0].getSize();
    
    SegmentExtent metaExtent = new SegmentExtent(0, 0, metaLength);
    
    _metaExtents.clear();
    _metaExtents.add(metaExtent);
    
    _metaAddress = 0;
    _metaOffset = META_OFFSET;
    _metaTail = _metaOffset + metaLength;
    
    while (true) {
      try (InSegment reader = openRead(metaExtent)) {
        ReadStream is = reader.in();
        
        if (metaExtent.getAddress() == 0) {
          is.setPosition(META_OFFSET);
        }
        
        long metaAddress = _metaAddress;

        while (readMetaDataEntry(is)) {
        }
        
        if (_metaAddress == metaAddress) {
          return true;
        }
        
        metaExtent = new SegmentExtent(0, _metaAddress, metaLength);
      }
    }
  }
  
  private boolean readMetaDataHeader(ReadStream is)
    throws IOException
  {
    long magic = BitsUtil.readLong(is);
  
    if (magic != KELP_MAGIC) {
      log.info(L.l("Mismatched kelp version {0} {1}\n",
                   Long.toHexString(magic),
                   _path));
    
      return false;
    }
    
    int crc = CRC_INIT;
    
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
      log.info(L.l("Mismatched crc files in kelp meta header"));
      
      return false;
    }
    
    SegmentMeta []segmentMetaList = new SegmentMeta[segmentSizes.size()];
    
    for (int i = 0; i < segmentMetaList.length; i++) {
      segmentMetaList[i] = new SegmentMeta(segmentSizes.get(i));
    }
    
    _segmentMeta = segmentMetaList;
    
    _metaOffset = is.getPosition();
    _addressTail = META_SEGMENT_SIZE;
  
    return true;
  }
  
  private boolean readMetaDataEntry(ReadStream is)
    throws IOException
  {
    int crc = _nonce;
    
    int code = is.read();
    crc = Crc32Caucho.generate(crc, code);
    
    switch (code) {
    case CODE_TABLE:
      readMetaTable(is, crc);
      break;
      
    case CODE_SEGMENT:
      readMetaSegment(is, crc);
      break;
      
    case CODE_META_SEGMENT:
      readMetaContinuation(is, crc);
      break;
      
    default:
      return false;
    }
    
    _metaOffset = is.getPosition();
    return true;
  }
  
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

    int crcFile = BitsUtil.readInt(is);
    
    if (crcFile != crc) {
      log.fine("meta-table crc mismatch");
      return false;
    }
    
    TableEntry entry = new TableEntry(key,
                                      rowLength,
                                      keyOffset,
                                      keyLength);
    
    _tableList.add(entry);

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
    
    SegmentExtent segment = new SegmentExtent(_segmentId++, address, length);

    SegmentMeta segmentMeta = findSegmentMeta(length);
    
    segmentMeta.addSegment(segment);
    
    return true;
  }
  
  private boolean readMetaContinuation(ReadStream is, int crc)
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
    
    if (length != _segmentMeta[0].getSize()) {
      throw new IllegalStateException();
    }
    
    SegmentExtent extent = new SegmentExtent(0, address, length);
    
    _metaExtents.add(extent);
    
    _metaAddress = address;
    _metaOffset = address;
    _metaTail = address + length;

    // false continues to the next segment
    return false;
  }
  
  private SegmentMeta findSegmentMeta(int size)
  {
    for (SegmentMeta segmentMeta : this._segmentMeta) {
      if (segmentMeta.getSize() == size) {
        return segmentMeta;
      }
    }
    
    throw new IllegalStateException(L.l("{0} is an invalid segment size", size));
  }
  
  private boolean loadSegment(SegmentExtent extent)
    throws IOException
  {
    long address = extent.getAddress();
    int length = extent.getLength();

    SegmentMeta segmentMeta = findSegmentMeta(length);
    
    try (InStore in = _store.openRead(address, length)) {
      _addressTail = Math.max(_addressTail, address + length);
      
      long addressBlock = address + length - BLOCK_SIZE;
      
      TempBuffer tBuf = TempBuffer.allocate();
      byte []buffer = tBuf.buffer();
      
      int offset = 0;
      
      in.read(addressBlock, buffer, 0, BLOCK_SIZE);

      long seq = BitsUtil.readLong(buffer, offset);
      offset += 8;
      
      byte []tableKey = new byte[TABLE_KEY_SIZE];
      System.arraycopy(buffer, offset, tableKey, 0, TABLE_KEY_SIZE);
      offset += TABLE_KEY_SIZE;
      
      SegmentKelp segment = new SegmentKelp(extent, seq, tableKey, this);
      segment.setLoaded();

      if (seq > 0) {
        segmentMeta.addLoaded(segment);
      }
      else {
        segment.close();
        segmentMeta.addFree(extent);
        
        return false;
      }
    }
    
    return true;
  }
  
  /**
   * Create a new writing sequence with the given sequence id. 
   */
  public SegmentKelp createSegment(int length,
                                   byte []tableKey,
                                   long sequence)
  {
    SegmentMeta segmentMeta = findSegmentMeta(length);
    
    SegmentKelp segment;
  
    SegmentExtent extent = segmentMeta.allocate();
    
    if (extent == null) {
      extent = allocateSegment(segmentMeta);
    }
    
    segment = new SegmentKelp(extent, sequence, tableKey, this);
    segment.writing();
    
    segmentMeta.addLoaded(segment);
  
    return segment;
  }
  
  private SegmentExtent allocateSegment(SegmentMeta segmentMeta)
  {
    int length = segmentMeta.getSize();
    
    SegmentExtent extent = null; // allocateSegmentGap(segmentMeta);
    
    long address;
    
    if (extent != null) {
      address = extent.getAddress();
    }
    else {
      address = _addressTail;
    
      // align
      // address += length - 1;
      // address -= address % length;
      
      if (_addressTail < address) {
        // if the alignment creates a gap, add the gap to the gap list
        _gapList.add(new SegmentGap(_addressTail, 
                                    (int) (address - _addressTail)));
      }
    
      _addressTail = address + length;
    
      extent = new SegmentExtent(_segmentId++, address, length);
    }
  
    segmentMeta.addSegment(extent);
  
    writeMetaSegment(extent);

    // ensure space in the backing store
    try (OutStore sOut = _store.openWrite(address, length)) {
    }
  
    return extent;
  }
  
  private SegmentExtent allocateSegmentGap(SegmentMeta segmentMeta)
  {
    int length = segmentMeta.getSize();
    
    for (SegmentGap gap : _gapList) {
      long address = gap.allocate(length);
      
      if (address > 0) {
        return new SegmentExtent(_segmentId++, address, length);
      }
    }
    
    return null;
  }

  // @Direct
  public Iterable<SegmentKelp> initSegments(byte []tableKey)
  {
    ArrayList<SegmentKelp> segments = new ArrayList<>();

    for (SegmentMeta segmentMeta : _segmentMeta) {
      segmentMeta.initSegments(segments, tableKey);
    }
    
    return segments;
  }

  public Iterable<SegmentExtent>  getSegmentExtents()
  {
    ArrayList<SegmentExtent> extents = new ArrayList<>();
    
    for (SegmentMeta segmentMeta : _segmentMeta) {
      for (SegmentExtent extent : segmentMeta.getSegments()) {
        extents.add(extent);
      }
    }
    
    // XXX: sort
    
    return extents;
  }

  // @Direct
  public Iterable<SegmentKelp> getSegments(byte []tableKey)
  {
    return initSegments(tableKey);
  }
  
  /**
   * Frees a segment to be reused. Called by the segment-gc service.
   */
  public void freeSegment(SegmentKelp segment)
    throws IOException
  {
    // System.out.println("FREE: " + segment);
    SegmentMeta segmentMeta = findSegmentMeta(segment.getLength());
    
    segmentMeta.remove(segment);
    
    segment.close();
    
    segmentMeta.addFree(segment.getExtent());
    
    // System.out.println("  FREE: " + segmentMeta.getFreeCount() + " " + segment.getLength());
  }
  
  InSegment openRead(SegmentKelp segment)
  {
    return openRead(segment.getExtent());
  }

  public InSegment openRead(SegmentExtent extent)
  {
    long address = extent.getAddress();
    int length = extent.getLength();
    
    InStore sIn = _store.openRead(address, length);
    
    return new InSegment(sIn, extent, compressor());
  }

  public boolean remove()
  {
    try {
      Path path = _path;

      close(null);

      if (path != null) {
        Files.delete(path);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    return true;
  }

  OutStore openWrite(long offset, int size)
  {
    return _store.openWrite(offset, size);
  }

  public boolean waitForComplete()
  {
    return true;
  }

  public void fsync(Result<Boolean> result)
      throws IOException
  {
    /*
      TableSegmentStream nodeStream = _nodeStream;
      TableSegmentStream blobStream = null; // _blobStream;
      
      FlushCompletion cont = new FlushCompletion(result, nodeStream, blobStream);
      */
      
      result.ok(true);
  }

  /**
   * Closes the store.
   */
  public void close(Result<Boolean> result)
  {
    closeImpl();
    
    result.ok(true);
    
    /*
    try {
      SegmentWriter nodeWriter = _nodeWriter;
      _nodeWriter = null;
      
      SegmentWriter blobWriter = _blobWriter;
      _blobWriter = null;
      
      CloseResult cont = new CloseResult(result);
      
      if (nodeWriter != null) {
        nodeWriter.closeSegment(cont);
      }
      else {
        cont.completed(true);
      }
      
      if (blobWriter != null) {
        blobWriter.closeSegment(cont);
      }
      else {
        cont.completed(true);
      }
    } catch (Exception e) {
      Result.fail(result, e);
    }
      */
  }

  /*
  public boolean close()
  {
    closeImpl();
    
    return true;
  }
  */

  private void closeImpl()
  {
    _store.close();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }
  
  // public for debug
  public static class TableEntry {
    private final byte []_tableKey;
    private final int _rowLength;
    private final int _keyOffset;
    private final int _keyLength;
    
    public TableEntry(byte []tableKey,
             int rowLength,
             int keyOffset,
             int keyLength)
    {
      _tableKey = tableKey;
      _rowLength = rowLength;
      _keyOffset = keyOffset;
      _keyLength = keyLength;
    }
    
    public int getKeyLength()
    {
      return _keyLength;
    }

    public int getKeyOffset()
    {
      return _keyOffset;
    }

    public int getRowLength()
    {
      return _rowLength;
    }

    byte []getTableKey()
    {
      return _tableKey;
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + Hex.toShortHex(_tableKey) + "]";
    }
  }
  
  private static class SegmentMeta {
    private final int _size;
    
    private ArrayList<SegmentExtent> _segments = new ArrayList<>();
    
    private ArrayList<SegmentKelp> _loadedSegments = new ArrayList<>();
    private ArrayList<SegmentExtent> _freeSegments = new ArrayList<>();

    SegmentMeta(int size)
    {
      if (size < SEGMENT_MIN) {
        throw new IllegalStateException(L.l("{0} is a too small segment", size));
      }
      
      if (Integer.bitCount(size) != 1) {
        throw new IllegalStateException(L.l("0x{0} is an invalid segment size", 
                                            Integer.toHexString(size)));
      }
      
      _size = size;
    }

    public void initSegments(ArrayList<SegmentKelp> segments, byte[] tableKey)
    {
      int tbl = 0;
      
      for (SegmentKelp segment : _loadedSegments) {
        if (segment.isTable(tableKey)) {
          tbl++;
          segments.add(segment);
        }
      }
    }

    int getSize()
    {
      return _size;
    }
    
    int getFreeCount()
    {
      return _freeSegments.size();
    }
    
    void addSegment(SegmentExtent segment)
    {
      _segments.add(segment);
    }

    public Iterable<SegmentExtent> getSegments()
    {
      return _segments;
    }

    public SegmentExtent allocate()
    {
      if (_freeSegments.size() > 0) {
        return _freeSegments.remove(_freeSegments.size() - 1);
      }
      else {
        return null;
      }
    }

    public void addFree(SegmentExtent extent)
    {
      _freeSegments.add(extent);
      Collections.sort(_freeSegments);
    }
    
    public void addLoaded(SegmentKelp segment)
    {
      _loadedSegments.add(segment);
    }
    
    public void remove(SegmentKelp segment)
    {
      synchronized (_loadedSegments) {
        _loadedSegments.remove(segment);
      }
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _size + "]";
    }
  }
  
  private class SegmentGap {
    private long _addressHead;
    private long _addressTail;
    
    SegmentGap(long address, int length)
    {
      _addressHead = address;
      _addressTail = address + length;
    }
    
    public long getAddress()
    {
      return _addressHead;
    }
    
    public int getLength()
    {
      return (int) (_addressTail - _addressHead);
    }
    
    public long allocate(int length)
    {
      int available = getLength();
      
      if (available < length) {
        return -1;
      }
      
      long address = (_addressHead + length - 1);
      address -= address % length;
      
      if (_addressHead < address) {
        SegmentGap gap = new SegmentGap(_addressHead, 
                                        (int) (address - _addressHead));
        
        _gapList.add(gap);
      }
      
      _addressHead = address + length;
      
      if (_addressHead == _addressTail) {
        _gapList.remove(this);
      }
      
      return address;
    }
  }
  
  private static class SegmentExtentComparator
    implements Comparator<SegmentExtent>
  {
    private static SegmentExtentComparator CMP = new SegmentExtentComparator();
    
    @Override
    public int compare(SegmentExtent a, SegmentExtent b)
    {
      return Long.signum(a.getAddress() - b.getAddress());
    }
  }
  
  static {
    byte []magicBytes = "Kelp1102".getBytes();
    
    KELP_MAGIC = BitsUtil.readLong(magicBytes, 0);
  }
  
  static enum KelpHeader {
    NONE,
    COMPRESS;
  }
  
  static enum KelpCompress {
    NONE,
    DEFLATE;
  }
}
