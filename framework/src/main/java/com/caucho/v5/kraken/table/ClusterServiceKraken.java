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

package com.caucho.v5.kraken.table;

import io.baratine.service.Result;

import com.caucho.v5.io.StreamSource;
import com.caucho.v5.kelp.GetStreamResult;

/**
 * Service for handling the distributed row store.
 */
public interface ClusterServiceKraken
{
  public static final String UID = "/kraken";
  
  void get(byte []tableKey, byte []key, long version,
            Result<GetStreamResult> result);

  // XXX: needs result to properly free the source
  void put(byte []tableKey, StreamSource rowSource, Result<Boolean> result);

  // XXX: needs result to properly free the source
  void putChunk(byte[] tableKey,
                long putId,
                long length, int index, int chunkSize,
                StreamSource sendSource,
                Result<Boolean> result);
  
  void update(Result<Integer> result, 
              int node,
              String sql, 
              Object[] args);
  
  void remove(byte []tableKey, byte []rowKey, 
              long version,
              Result<Boolean> result);

  //
  // query
  //
  
  void find(byte[] tableKey, 
            Object arg, 
            Result<byte[]> result);
  
  void findOne(Result<byte[]> result,
               byte []tableKey,
               String sql, 
               Object[] args);

  void findAll(Result<Iterable<byte[]>> result, 
               byte[] tableKey, 
               String sql,
               Object[] args);
  
  //
  // watches
  //

  void addWatch(byte []tableKey, byte[] key, String serverId);

  /**
   * Notify local and remote watches for the target table and key.
   * 
   * @param tableKey the table key of updated row
   * @param key the key of the updated row
   */
  void notifyWatch(byte[]tableKey, byte[] key);

  /**
   * Notify local watches for the target table and key.
   * 
   * @param tableKey the table key of updated row
   * @param key the key of the updated row
   */
  void notifyLocalWatch(byte[]tableKey, byte[] key);
  
  //
  // startup
  // 
  
  void requestStartupUpdates(String from, 
                             byte []tableHash,
                             int shardIndex,
                             long delta, 
                             Result<Boolean> cb);

  void start();
}
