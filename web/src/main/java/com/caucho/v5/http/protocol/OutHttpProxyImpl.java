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

package com.caucho.v5.http.protocol;

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.network.port.ConnectionTcp;

import io.baratine.io.Buffer;
import io.baratine.service.AfterBatch;

/**
 * Handles a HTTP connection.
 */
class OutHttpProxyImpl implements OutHttpProxy
{
  private ConnectionHttp _connHttp;
  
  private ArrayList<Pending> _pendingList = new ArrayList<>();
  
  private boolean _isClose;
  
  OutHttpProxyImpl(ConnectionHttp conn)
  {
    Objects.requireNonNull(conn);
    _connHttp = conn;
  }
  
  private ConnectionHttp connHttp()
  {
    return _connHttp;
  }
  
  private ConnectionTcp conn()
  {
    return _connHttp.connTcp();
  }
  
  void start()
  {
    _isClose = false;
  }
  
  @Override
  public void write(OutHttpTcp out, 
                    Buffer buffer, 
                    boolean isEnd)
  {
    boolean isClose = false;

    if (out.canWrite(connHttp().sequenceWrite())) {
      if (out.write(conn().writeStream(), buffer, isEnd)) {
        isClose = isEnd;
      }
      
      if (isEnd) {
        connHttp().onWriteEnd();
        writePending();
      }
    }
    else {
      _pendingList.add(new PendingData(out, buffer, isEnd));
    }
    
    _isClose = isClose;
  }

  /*
  @Override
  public void writeNext(OutHttp out, TempBuffer buffer, boolean isEnd)
  {
    if (out.canWrite(_connHttp.sequenceWrite() + 1)) {
      _isClose = out.writeNext(conn().writeStream(), buffer, isEnd);
      
      if (isEnd) {
        writePending();
      }
    }
    else {
      _pendingList.add(new PendingNext(out, buffer, isEnd));
    }
  }
  */
  
  private void writePending()
  {
    boolean isEnd;
    
    do {
      isEnd = false;
      
      for (int i = 0; i < _pendingList.size(); i++) {
        Pending pending = _pendingList.get(i);
        
        if (pending.out().canWrite(_connHttp.sequenceWrite())) {
          _pendingList.remove(i--);
          
          pending.write();
          
          if (pending.isEnd()) {
            isEnd = true;
          }
        }
      }
    } while (isEnd);
  }

  @Override
  public void disconnect(OutHttpTcp out)
  {
    out.disconnect(conn().writeStream());
  }
  
  private boolean isClose()
  {
    if (_isClose) {
      return true;
    }
    else if (connHttp().isCloseRead()) {
      return true;
    }
    else {
      return false;
    }
  }
  
  @AfterBatch
  public void afterBatch()
  {
    try {
      if (isClose()) {
        conn().writeStream().close();
      }
      else {
        conn().writeStream().flush();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      _connHttp.onFlush();
    }
  }
  
  abstract private class Pending
  {
    private OutHttpTcp _out;
    private boolean _isEnd;
    
    Pending(OutHttpTcp out, boolean isEnd)
    {
      _out = out;
      _isEnd = isEnd;
    }
    
    OutHttpTcp out()
    {
      return _out;
    }
    
    boolean isEnd()
    {
      return _isEnd;
    }
    
    abstract void write(); 
  }
  
  private class PendingData extends Pending
  {
    private Buffer _data;
    
    PendingData(OutHttpTcp out,
                Buffer data,
                boolean isEnd)
    {
      super(out, isEnd);

      _data = data;
    }
    
    @Override
    void write()
    {
      OutHttpProxyImpl.this.write(out(), _data, isEnd());
    }
  }
}
