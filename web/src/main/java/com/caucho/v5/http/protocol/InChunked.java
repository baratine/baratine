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

import java.io.IOException;

import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.StreamImpl;

/**
 * StreamImpl so servlets can read POST data as a normal stream.
 */
class InChunked extends StreamImpl
{
  private ReadStream _next;
  private int _available;

  void init(ReadStream next)
  {
    _next = next;
    _available = 0;
  }

  @Override
  public boolean canRead()
  {
    return true;
  }
  
  @Override
  public boolean isReady()
  {
    if (_available < 0) {
      return false;
    }
    else if (_next != null) {
      //return _next.isReady();
      try{
        if (_available > 0) {
          return _next.isReady();
        }
        else {
          return false;
        }
      } catch (Exception e) {
        return false;
      }
    }
    else {
      return false;
    }
  }

  @Override
  public int getAvailable()
  {
    return _available;
  }

  /**
   * Reads more data from the input stream.
   */
  @Override
  public int read(byte []buf, int offset, int len) throws IOException
  {
    int available = _available;
    
    // The chunk still has more data left
    if (available > 0) {
      len = Math.min(len, available);

      len = _next.read(buf, offset, len);

      if (len > 0) {
        _available -= len;
      }
    }
    // The chunk is done, so read the next chunk
    else if (available == 0) {
      _available = readChunkLength();

      // the new chunk has data
      if (_available > 0) {
        len = Math.min(len, _available);

        len = _next.read(buf, offset, len);

        if (len > 0)
          _available -= len;
      }
      // the new chunk is the last
      else {
        _available = -1;
        len = -1;
      }
    }
    else {
      len = -1;
    }
    
    return len;
  }

  /**
   * Reads the next chunk length from the input stream.
   */
  private int readChunkLength()
    throws IOException
  {
    int length = 0;
    int ch;

    ReadStream is = _next;

    // skip whitespace
    for (ch = is.read();
         ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n';
         ch = is.read()) {
    }

    // XXX: This doesn't properly handle the case when when the browser
    // sends headers at the end of the data.  See the HTTP/1.1 spec.
    for (; ch > 0 && ch != '\r' && ch != '\n'; ch = is.read()) {
      if ('0' <= ch && ch <= '9')
        length = 16 * length + ch - '0';
      else if ('a' <= ch && ch <= 'f')
        length = 16 * length + ch - 'a' + 10;
      else if ('A' <= ch && ch <= 'F')
        length = 16 * length + ch - 'A' + 10;
      else if (ch == ' ' || ch == '\t') {
        //if (dbg.canWrite())
        //  dbg.println("unexpected chunk whitespace.");
      }
      else {
        StringBuilder sb = new StringBuilder();

        sb.append((char) ch);
        for (int ch1 = is.read();
             ch1 >= 0 && ch1 != '\r' && ch1 != '\n';
             ch1 = is.read()) {
          sb.append((char) ch1);
        }

        throw new IOException("HTTP/1.1 protocol error: bad chunk at"
                              + " '" + sb + "'"
                              + " 0x" + Integer.toHexString(ch)
                              + " length=" + length);
      }
    }

    if (ch == '\r')
      ch = is.read();

    return length;
  }
}
