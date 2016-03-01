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

package com.caucho.v5.store.io;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import io.baratine.service.AfterBatch;
import io.baratine.service.Api;
import io.baratine.service.Direct;
import io.baratine.service.Result;


/**
 * Fsync service batches fsync requests to the underlying store.
 * 
 * The fsync only occurs at batch time, and when the fsyncs are flushed.
 * 
 * Fsync clients allocate a sequence after their data is written before
 * requesting the fsync itself. The sequence allows for fsyncs that occur
 * after data is written but before the service message is received, avoiding
 * duplicates.
 * 
 * <code><pre>
 * [write data]
 * sequence = fsync.allocateFsyncSequence();
 * fsync.fsync(sequence, result);
 * </pre></code>
 * 
 * Or if the client knows it's batching several fsyncable calls, it can
 * schedule the fsync, but not flush it until all are completed
 * 
 * <code><pre>
 * while (...) {
 *   [write data]
 *   sequence = fsync.allocateFsyncSequence();
 *   fsync.scheduleFsync(sequence, result);
 * }
 * fsync.flush();
 * </pre></code>
 */
@Api(StoreFsyncService.class)
public class StoreFsyncServiceImpl<K>
{
  private final StoreFsync<K> _storeFsync;
  
  private final AtomicLong _headSequence = new AtomicLong();
  
  private long _requestSequence;
  private long _tailSequence;

  private boolean _isFsync;

  /**
   * Creates a fsync service which batches fsync calls to the store.
   */
  public StoreFsyncServiceImpl(StoreFsync<K> store)
  {
    Objects.requireNonNull(store);
    
    _storeFsync = store;
  }
  
  /**
   * Request a sequence for a fsync request. The client must have written
   * the data already.
   * 
   * Since the allocate is a @Direct call, the client will not block even
   * if called synchronously.
   * 
   * @return a sequence number to be used in a fsync call. 
   */
  @Direct
  public long allocateFsyncSequence()
  {
    return _headSequence.incrementAndGet();
  }
  
  /**
   * Request an fsync for the allocated sequence, and request a flush
   * to occur at the next service batch.
   * 
   * @param sequence the sequence id allocated from allocateFsyncSequence()
   * @param result the completion to be called after the fsync completes
   */
  public void fsync(long sequence, K key, Result<Boolean> result)
  {
    scheduleFsync(sequence, key, result);
    flush();
  }
  
  /**
   * Schedule an fsync for the given sequence, but do not request a flush.
   * The fsync can occur before the flush, depending on other clients.
   * 
   * @param sequence the sequence allocated from allocateFsyncSequence
   * @param result notifies the caller when the fsync completes
   */
  public void scheduleFsync(long sequence, K key, Result<Boolean> result)
  {
    _requestSequence = Math.max(_requestSequence, sequence);

    if (sequence <= _tailSequence) {
      result.ok(Boolean.TRUE);
    }
    else {
      _storeFsync.addResult(key, result);
    }
  }
  
  /**
   * Request an fsync flush at the next batch. The fsync will only occur
   * if there are also pending fsyncs.
   */
  public void flush()
  {
    _isFsync = true;
  }
  
  /**
   * Completes any fsyncs in a batch. 
   * 
   * Because the fsync is slow, this method will typically block while 
   * new requests fill the queue, making the next batch efficient. 
   */
  @AfterBatch
  public void afterBatch()
  {
    long requestSequence = _requestSequence;
    
    if (! _isFsync) {
      return;
    }
    
    _isFsync = false;
    
    try {
      _storeFsync.fsync();
      /*
      if (_tailSequence < requestSequence) {
        _storeFsync.fsync();
      }
      */
    } catch (Throwable e) {
      e.printStackTrace();
    } finally {
      _tailSequence = requestSequence;
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _storeFsync + "]";
  }
}
