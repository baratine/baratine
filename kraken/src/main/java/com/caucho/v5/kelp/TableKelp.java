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

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.db.journal.JournalStore;
import com.caucho.v5.h3.H3;
import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.kelp.PageServiceSync.PutType;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.PredicateKelp;
import com.caucho.v5.kelp.query.QueryParserKelp;
import com.caucho.v5.kelp.segment.InSegment;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.kelp.segment.SegmentKelp.SegmentEntryCallback;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.store.temp.TempStore;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;
import io.baratine.service.ResultFuture;

/**
 * btree-based database
 */
public class TableKelp
{
  private static final L10N L = new L10N(TableKelp.class);
  
  public static final int TABLE_KEY_SIZE = 32;

  private final DatabaseKelp _db;
  private final String _name;
  private final Row _row;
  private final byte[] _tableKey;

  private ServicesAmp _services;

  private JournalStore _jbs;

  private PageServiceSync _tableService;

  private PageServiceImpl _tableServiceImpl;

  private TableGcService _gcService;

  private MemoryGcActor _pageGcService;

  private TableWriterServiceImpl _readWriteImpl;

  private TableWriterService _readWrite;
  
  private OutFactoryH3 _serializer;
  
  private Lifecycle _lifecycle = new Lifecycle();

  TableKelp(DatabaseKelp db,
            String name,
            byte []tableKey,
            Row row)
  {
    Objects.requireNonNull(db);
    Objects.requireNonNull(name);
    Objects.requireNonNull(tableKey);
    Objects.requireNonNull(row);
    
    _db = db;
    _name = name;
    _row = row;
    
    _tableKey = tableKey;

    /*
    if (DatabaseKelp.BLOCK_SIZE <= _inlineBlobMax - _row.getLength()) {
      throw new IllegalStateException(L.l("Inline blob size '{0}' is too large",
                                          _inlineBlobMax));
    }
    */
    
    _services = db.services();
    
    _tableServiceImpl = new PageServiceImpl(this, db.journalStore());
    
    _readWriteImpl = new TableWriterServiceImpl(this,
                                                db.segmentStore(),
                                                db.segmentService());
    
    _readWrite = _services.newService(_readWriteImpl).as(TableWriterService.class);
    
    _tableService = _services.newService(_tableServiceImpl).as(PageServiceSync.class);
    
    TableGcServiceImpl gcActor = new TableGcServiceImpl(this);
    
    _gcService = _services.newService(gcActor).as(TableGcService.class);
    
    MemoryGcActor pageGcActor = new MemoryGcActor(this);
    
    _pageGcService = _services.newService(pageGcActor).as(MemoryGcActor.class);
    
    // _db.addTable(name, this);
    
    // _tableService.start(result);
    
    _serializer = H3.newOutFactory().get();
    
    for (Class<?> schemaType : _row.objectSchema()) {
      _serializer.schema(schemaType);
    }
  }
  
  void start(Result<TableKelp> result)
  {
    _tableService.start(result);
  }
  
  public String getName()
  {
    return _name;
  }

  public byte []tableKey()
  {
    return _tableKey;
  }

  public DatabaseKelp database()
  {
    return _db;
  }

  public int getDeltaLeafMax()
  {
    return _db.getDeltaLeafMax();
  }
  
  public Row row()
  {
    return _row;
  }

  public Column getColumn(String columnName)
  {
    return _row.findColumn(columnName);
  }

  public Column []getColumns()
  {
    return _row.columns();
  }
  
  public int getKeyOffset()
  {
    return row().keyOffset();
  }
  
  public int getKeyLength()
  {
    return row().keyLength();
  }
  
  public long getMemorySize()
  {
    return getPageActor().getMemorySize();
  }

  public long getMemoryMax()
  {
    return _db.getMemoryMax();
  }

  public boolean isValidate()
  {
    return _db.isValidate();
  }

  public int getInlineBlobMax()
  {
    return _db.getBlobInlineMax();
  }

  public int getMaxNodeLength()
  {
    return _db.getMaxNodeLength();
  }

  public int getDeltaMax()
  {
    return _db.getDeltaMax();
  }

  public int getDeltaTreeMax()
  {
    return _db.getDeltaTreeMax();
  }

  public int getGcThreshold()
  {
    return _db.getGcThreshold();
  }

  public TableGcService getGcService()
  {
    return _gcService;
  }

  public MemoryGcActor getPageGcService()
  {
    return _pageGcService;
  }

  public int getBlobPageSizeMax()
  {
    return _db.getBlobPageSizeMax();
  }

  public boolean waitForComplete()
  {
    return _tableService.waitForComplete();
  }

  PageServiceSync getTableService()
  {
    return _tableService;
  }

  PageServiceImpl getTableServiceImpl()
  {
    return _tableServiceImpl;
  }
  
  public PageServiceImpl getPageActor()
  {
    // XXX:
    return _tableServiceImpl;
  }

  /*
  StoreFsyncService  getFsyncService()
  {
    return _readWriteImpl.getFsyncService();
  }
  */

  public TempStore getTempStore()
  {
    return _db.getTempStore();
  }

  public OutFactoryH3 serializer()
  {
    return _serializer;
  }
  
  public RowCursor cursor()
  {
    return new RowCursor(this, _row);
  }
  
  public void checkpoint()
  {
    _tableService.checkpoint();
  }
  
  public void checkpoint(Result<Boolean> cont)
  {
    _tableService.checkpoint(cont);
  }

  Iterable<SegmentKelp> getSegments()
  {
    return _readWrite.getSegments();
  }

  /*
  SegmentWriter allocateSegmentWriter(long sequence)
  {
    // XXX: use service
    return _readWriteImpl.openWriter(sequence);
  }
  */

  void freeSegment(SegmentKelp segment)
  {
    // _readWrite.freeSegment(segment);
    _tableService.freeSegment(segment);
  }
  
  TableWriterService getReadWrite()
  {
    return _readWrite;
  }
  
  TableWriterServiceImpl getReadWriteActor()
  {
    return _readWriteImpl;
  }
  
  //
  // get
  //
  
  public boolean getDirect(RowCursor row)
  {
    return _tableService.getDirect(row);
  }

  public void getDirect(RowCursor row, Result<Boolean> result)
  {
    _tableService.getDirect(row, result);
  }

  public void get(RowCursor row, Result<Boolean> result)
  {
    _tableService.getSafe(row, result);
  }

  public boolean get(RowCursor row)
  {
    return _tableService.getSafe(row);
  }

  public void getStream(RowCursor cursor, 
                        Result<GetStreamResult> result)
  {
    _tableService.getStream(cursor, result);
  }

  public GetStreamResult getStream(RowCursor cursor)
  {
    return _tableService.getStream(cursor);
  }

  public void flush(Result<Boolean> result)
  {
    _tableService.flush((Result) result);
  }
  
  //
  // put
  //
  
  public void put(RowCursor cursor)
  {
    cursor.setVersion(0);
    _tableService.put(cursor, PutType.PUT);
  }
  
  public void put(RowCursor cursor, Result<Boolean> result)
  {
    Objects.requireNonNull(result);
    
    cursor.setVersion(0);
    
    _tableService.put(cursor, PutType.PUT, result);
  }
  
  /**
   * Put using the version in the cursor, instead of clearing it. 
   */
  public void putWithVersion(RowCursor cursor)
  {
    _tableService.put(cursor, PutType.PUT);
  }
  
  /**
   * Put using the version in the cursor, instead of clearing it. 
   */
  public void putWithVersion(RowCursor cursor, Result<Boolean> cont)
  {
    _tableService.put(cursor, PutType.PUT, cont);
  }
  
  
  public void put(RowCursor cursor, 
                  BackupKelp cb, 
                  Result<? super Boolean> result)
  {
    getTableService().putWithBackup(cursor, PutType.PUT, cb, result);
  }
  
  public void put(InputStream is, Result<Boolean> result)
  {
    try {
      RowCursor cursor = cursor();
    
      cursor.readStream(is);

      getTableService().put(cursor, PutType.PUT, result);
    } catch (Throwable e) {
      result.fail(e);
    }
  }
  
  /**
   * Puts a row to the table based on a stream.
   *  
   * @param is the stream to the row
   * @param putType distinguishes local puts from backups and replays
   * @param result called when the put completes.
   */
  public void put(InputStream is, 
                  PutType putType, 
                  Result<Boolean> result)
  {
    try {
      RowCursor cursor = cursor();
    
      cursor.readStream(is);

      getTableService().put(cursor, putType, result);
    } catch (Throwable e) {
      result.fail(e);
    }
  }
  
  //
  // remove
  //
  
  public boolean remove(RowCursor cursor)
  {
    ResultFuture<Boolean> future = new ResultFuture<Boolean>();
    
    cursor.setVersion(0);
    _tableService.remove(cursor, null, future);
    
    return future.get(10, TimeUnit.SECONDS);
  }

  /*
  public void remove(RowCursor cursor, Result<Boolean> result)
  {
    _tableService.remove(cursor, null, result);
  }
  */
  
  public void remove(RowCursor cursor, 
                     BackupKelp backup,
                     Result<Boolean> result)
  {
    cursor.setVersion(0);
    _tableService.remove(cursor, backup, result);
  }
  
  //
  // query
  //
  
  public Iterable<RowCursor> queryRange(RowCursor min,
                                        RowCursor max,
                                        Predicate<RowCursor> predicate)
  {
    return new RangeQuery(this, min, max, predicate);
  }
  
  public Iterable<RowCursor> queryRangeForUpdate(RowCursor min,
                                                 RowCursor max,
                                                 Predicate<RowCursor> predicate)
  {
    return new RangeQueryAll(this, min, max, predicate);
  }
  
  public boolean removeRange(RowCursor min,
                             RowCursor max,
                             Predicate<RowCursor> predicate,
                             BackupKelp backup)
  {
    ResultFuture<Boolean> future = new ResultFuture<Boolean>();
    
    _tableService.removeRange(min, max, predicate, backup, future);
    
    return future.get(10, TimeUnit.SECONDS);
  }
  
  public void removeRange(RowCursor min,
                          RowCursor max,
                          Predicate<RowCursor> predicate,
                          BackupKelp backup,
                          Result<Boolean> cont)
  {
    _tableService.removeRange(min, max, predicate, backup, cont);
  }

  public PredicateKelp parseQuery(String sql)
  {
    QueryParserKelp parser = new QueryParserKelp(this);
    
    return parser.parse(sql);
  }

  public void findOne(RowCursor minCursor, 
                      RowCursor maxCursor,
                      EnvKelp whereKelp, 
                      Result<RowCursor> result)
  {
    _tableService.flush(result.then((x,r)->findOneImpl(minCursor, maxCursor, whereKelp, result)));
  }
  
  public void findOneImpl(RowCursor minCursor, 
                          RowCursor maxCursor,
                      EnvKelp whereKelp, 
                      Result<RowCursor> result)
  {
    for (RowCursor cursor : queryRange(minCursor, maxCursor, whereKelp)) {
      result.ok(cursor);
      return;
    }
    
    result.ok(null);
  }

  public void findAll(RowCursor minCursor,
                      RowCursor maxCursor,
                      EnvKelp whereKelp, 
                      Result<Iterable<RowCursor>> findResult)
  {
    Iterable<RowCursor> iter = queryRange(minCursor, maxCursor, whereKelp);
    
    findResult.ok(iter);
  }

  public void update(RowCursor minCursor, 
                     RowCursor maxCursor,
                     EnvKelp envKelp, 
                     UpdateKelp update,
                     BackupKelp backup,
                     Result<Integer> result)
  {
    getTableService().update(minCursor, maxCursor, envKelp, update, backup, result);
    /*
    for (RowCursor cursor : queryRange(minCursor, maxCursor, envKelp)) {
      if (update.onRow(cursor, envKelp)) {
        getTableService().putWithBackup(cursor, backup, null);
      }
    }

    System.out.println("CMPL:");
    result.completed(null);
    */
  }

  public void replace(RowCursor cursor, 
                      EnvKelp envKelp,
                      UpdateKelp update,
                      BackupKelp backupCallback,
                      Result<Integer> result)
  {
    getTableService().replace(cursor, envKelp, update, backupCallback, result);
  }

  public void map(RowCursor min, 
                  RowCursor max,
                  EnvKelp whereKelp, 
                  MapKelp mapResult)
  {
    StreamKelp stream = new StreamKelp(this, min, max, whereKelp, mapResult);
    
    while (stream.next()) {
    }
  }

  void readSegmentEntries(Iterable<SegmentKelp> segments,
                          SegmentEntryCallback cb)
  {
    _readWriteImpl.readSegmentEntries(segments, cb);
  }

  long getSegmentSize(SegmentKelp segment)
  {
    return getTableServiceImpl().getSequenceSize(segment);
  }

  InSegment openReader(SegmentKelp segment)
  {
    return _readWriteImpl.openRead(segment);
  }

  public void addListener(TableListener tableListener)
  {
    getTableService().addListener(tableListener);
  }

  public void removeListener(TableListener tableListener)
  {
    getTableService().removeListener(tableListener);
  }
  
  //
  // archive/restore
  //
  
  public ArchiveTableKelp archive(Path path)
  {
    return new ArchiveTableKelp(this, path);
  }
  
  public RestoreTableKelp restore(Path path)
  {
    return new RestoreTableKelp(this, path);
  }

  //
  // debug/qa
  //
  
  public DebugKelpTable createDebug()
  {
    return new DebugKelpTable(this);
  }

  //
  // close
  //
  
  public boolean isClosed()
  {
    return _lifecycle.isDestroyed();
  }
  
  public void close(ShutdownModeAmp mode, Result<Void> result)
  {
    if (mode == ShutdownModeAmp.GRACEFUL) {
      _tableService.checkpoint(result.of(x->closeImpl()));
    }
    else {
      result.ok(closeImpl());
    }
  }
  
  private Void closeImpl()
  {
    if (! _lifecycle.toDestroy()) {
      return null;
    }
    
    _gcService.close();
    _tableService.close();
    _readWrite.close();
    
    return null;
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _name 
            + "," + Hex.toShortHex(_tableKey)
            + "]");
  }
}
