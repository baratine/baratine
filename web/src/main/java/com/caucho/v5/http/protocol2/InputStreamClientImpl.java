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
import java.util.Map;
import java.util.Objects;

import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.CurrentTime;

/**
 * InputStreamHttp reads a single HTTP frame.
 */
public class InputStreamClientImpl extends InputStreamClient
  implements Http2Constants
{
  private byte []_tempBuffer;
  private int _streamId;
  
  private TempBuffer _head;
  private TempBuffer _tail;
  
  private TempBuffer _readHead;
  private int _offset;
  
  private int _status;
  
  private HashMap<String,String> _headers;
  
  private boolean _isCloseRead;
  
  public InputStreamClientImpl()
  {
  }

  public void setStreamId(int streamId)
  {
    _streamId = streamId;
  }
  
  void header(String key, String value)
  {
    System.out.println("HEADER: " + key + " " + value);
    if (_headers == null) {
      _headers = new HashMap<>();
    }
      
    _headers.put(key, value);
    
    switch (key) {
    case ":status":
      _status = Integer.parseInt(value);
      break;
    }
  }
  
  @Override
  public int getStatus()
  {
    return _status;
  }
  
  @Override
  public String header(String key)
  {
    HashMap<String,String> headers = _headers;
    
    if (headers != null) {
      return headers.get(key);
    }
    else {
      return null;
    }
  }
  
  @Override
  public Map<String,String> getHeaders()
  {
    return _headers;
  }

  @Override
  public int read()
    throws IOException
  {
    byte []buffer = _tempBuffer;
    
    if (buffer != null) {
      buffer = new byte[1];
      _tempBuffer = buffer;
    }
    
    int len = read(buffer, 0, 1);
    
    if (len > 0) {
      return buffer[0] & 0xff;
    }
    else {
      return len;
    }
  }
  
  @Override
  public int available()
  {
    TempBuffer head = _readHead;
    
    if (head == null) {
      if (_isCloseRead && _head == null) {
        return -1;
      }
      else {
        return 0;
      }
    }
    else {
      return head.length() - _offset;
    }
  }

  @Override
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    while (length > 0) {
      TempBuffer head = _readHead;
      
      if (head != null) {
        int dataOffset = _offset;
      
        int sublen = Math.min(head.length() - dataOffset, length); 
          
        if (sublen > 0) {
          System.arraycopy(head.buffer(), dataOffset, buffer, offset, sublen);
          
          _offset = dataOffset + sublen;
        
          return sublen;
        }

        head.free();
      }
      
      _readHead = head = nextBuffer();
      _offset = 0;

      if (head == null && _isCloseRead) {
        return -1;
      }
    }
    
    return 0;
  }
  
  private TempBuffer nextBuffer()
  {
    long now = CurrentTime.getCurrentTimeActual();
    long expires = now + 600 * 1000;
    
    while (true) {
      synchronized (this) {
        TempBuffer head = _head;

        if (head != null) {
          TempBuffer next = head.next();
          head.next(null);
          
          _head = next;
          
          if (next == null) {
            _tail = null;
          }

          return head;
        }
        
        now = CurrentTime.getCurrentTimeActual();

        if (expires < now || _isCloseRead) {
          _isCloseRead = true;
          
          return null;
        }

        try {
          wait(expires - now);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void data(TempBuffer tBuf)
  {
    Objects.requireNonNull(tBuf);
    
    synchronized (this) {
      if (_head == null) {
        _head = tBuf;
        _tail = tBuf;
        
        notifyAll();
      }
      else {
        _tail.next(tBuf);
        _tail = tBuf;
        
        notifyAll();
      }
    }
  }
  
  public void closeRead()
  {
    synchronized (this) {
      _isCloseRead = true;
      notifyAll();
    }
  }
  
  @Override
  public void close()
    throws IOException
  {
    closeRead();
  }
}
