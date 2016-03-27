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

import com.caucho.v5.amp.Direct;

import io.baratine.service.Result;


/**
 * The fsync service batches fsync requests for an underlying store, avoiding
 * the need for multiple fsync calls.
 */
public interface StoreFsyncService<K>
{
  /*
   * Allocate a sequence after the client's data is written.
   */
  @Direct
  long allocateFsyncSequence();

  /**
   * Request an fsync. The result will be called when the fsync completes.
   * 
   * @param sequence the sequence allocated with allocateFsyncSequence
   * @param result the callback when the fsync completes
   */
  void fsync(long sequence, K key, Result<Boolean> result);
  
  /**
   * Blocking version of an fsync, generally used only for QA. 
   */
  boolean fsync(long sequence, K key);
  
  /**
   * Schedules an fsync. The fsync might not be executed until flush()
   * is called.
   * 
   * There is no blocking version for scheduleFsync because of the flush
   * requirement.
   * 
   * @param sequence the fsync sequence allocated from allocateFsyncSequence
   * @param result the callback when the fsync completes.
   */
  void scheduleFsync(long sequence, K key, Result<Boolean> result);
  
  /**
   * Flush any scheduled fsyncs to be executed at the end of the batch.
   */
  void flush();
}
