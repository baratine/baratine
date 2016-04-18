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

package com.caucho.v5.kraken.cluster;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.db.temp_store.StreamSourceChunked;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.kraken.cluster.TablePodNode.NodeTableContext;
import com.caucho.v5.kraken.table.ClusterServiceKraken;

import io.baratine.service.Result;

class NodeTableContextPut extends NodeTableContext
{
  private static final Logger log = Logger.getLogger(NodeTableContextPut.class.getName());
  private byte []_key;
  private byte []_tableKey;
  private StreamSource _data;
  private TablePodImpl _tablePod;
  
  private StreamSource _ss;
  
  private Result<Boolean> _result;
  private int _resultCount = 1;

  NodeTableContextPut(TablePodImpl tablePod,
                   byte []tableKey,
                   byte []key,
                   StreamSource ss,
                   Result<Boolean> result)
  {
    _tablePod = tablePod;
    _tableKey = tableKey;
    _key = key;
    
    _ss = ss;
    _result = result;
    
    /*
    if (ss != null) {
      _ss = new StreamSourceOpen(ss);
    }
    */
    
  }

  @Override
  public boolean isSingleRequest()
  {
    return false;
  }

  @Override
  public void invoke(ClusterServiceKraken service)
  {
    StreamSource source = _ss;

    if (source == null) {
      service.put(_tableKey, null, _result);
      return;
    }

    if (source.getLength() <= _tablePod.getPutChunkMin()) {
      //source.addUseCount();
      StreamSource sourceChild = source.openChild();
      StreamSource sendSource = new StreamSource(sourceChild);

      _resultCount++;
      service.put(_tableKey, sendSource,
                  (x,e)->onSourceClose(sourceChild, e));
      
      return;
    }

    int chunkSize = 256 * 1024;
    int offset = 0;
    long length = source.getLength();
    int index = 0;

    long putId = _tablePod.nextPutSequence();

    while (length > 0) {
      int sublen = (int) Math.min(chunkSize, length);

      try {
        //source.addUseCount();
        StreamSource sourceChild = source.openChild();

        StreamSource chunk = new StreamSourceChunked(sourceChild, offset, sublen);

        StreamSource sendSource = new StreamSource(chunk);

        _resultCount++;
        service.putChunk(_tableKey, putId,
                         source.getLength(), index, chunkSize,
                         sendSource,
                         (x,e)->onSourceClose(sourceChild, e));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      length -= sublen;
      offset += sublen;

      index++;
    }
    return;

    // XXX: chain invoke
  }

  @Override
  public void fallthru()
  {
    _result.ok(true);
    // Result.Adapter.failed(_result, new RuntimeException("failed put"));
  }

  private void onSourceClose(StreamSource ss, Throwable exn)
  {
    //log.log(Level.FINER, exn.toString(), exn);
    if (exn != null) {
      log.log(Level.WARNING, exn.toString(), exn);
      _result.fail(exn);
    }

    if (ss != null) {
      ss.close();
    }
    
    if (--_resultCount <= 0) {
      _result.ok(true);
    }
    System.out.println("OSC-RST: " + _resultCount + " " + _result);
  }
}
