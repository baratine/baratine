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
import java.nio.file.Path;
import java.util.logging.Logger;

import com.caucho.v5.http.container.HttpContainer;
import com.caucho.v5.io.SendfileOutputStream;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.L10N;

public class OutResponseHttp
//extends OutResponseCache
  extends OutResponseBase
  implements SendfileOutputStream
{
  private static final L10N L = new L10N(OutResponseHttp.class);
  private static final Logger log
    = Logger.getLogger(OutResponseHttp.class.getName());

  private static final int CHUNK_HEADER = 8;
  private static final int CHUNK_TAIL = 7;
  private static final int _tailChunkedLength = 7;
  private static final byte []_tailChunked
    = new byte[] {'\r', '\n', '0', '\r', '\n', '\r', '\n'};

  private RequestHttp _request;

  private boolean _isChunked;
  private boolean _isHeaders;
  // private int _bufferStartOffset;

  OutResponseHttp(RequestHttp request)
  {
    //super(request);

    _request = request;
  }

  @Override
  public boolean isClosed()
  {
    return super.isClosed() || _request.isClosed();
  }

  /**
   * initializes the Response stream at the beginning of a request.
   */
  @Override
  public void start()
  {
    _isChunked = false;
    _isHeaders = false;
    
    super.start();
  }
  
  /*
  RequestFacade request()
  {
    return _request.request();
  }
  */

  //
  // implementations
  //
  
  @Override
  protected int bufferStart()
  {
    if (_isChunked) {
      return CHUNK_HEADER;
    }
    else {
      return 0;
    }
  }
  
  @Override
  public boolean isChunkedEncoding()
  {
    return _isChunked;
  }
  
  @Override
  public void upgrade()
  {
    if (_isChunked) {
      _isChunked = false;
      System.out.println("UBZDSF");
    }
  }

  @Override
  protected final TempBuffer flushData(TempBuffer head, 
                                       TempBuffer tail, 
                                       boolean isEnd)
  {
    if (head == null || head.length() == 0) {
      head = null;
    }
    
    if (_isChunked) {
      for (TempBuffer ptr = head; ptr != null; ptr = ptr.getNext()) {
        writeChunkHeader(ptr.buffer(), CHUNK_HEADER, ptr.length() - CHUNK_HEADER);
      }
    }
    
    if (! _isHeaders) {
      _isHeaders = true;
      
      // session flushing
      // XXX:
      /*
      RequestFacade request = request();

      if (request != null) {
        request.fillHeaders();
      }
      */

      if (! isEnd) {
        _isChunked = _request.calculateChunkedEncoding();
      }
      
      long length = 0;
      for (TempBuffer ptr = head; ptr != null; ptr = ptr.getNext()) {
        length += ptr.length();
      }
      
      _request.outProxy().writeFirst(_request, head, length, isEnd);
    }
    else {
      _request.outProxy().writeNext(_request, head, isEnd);
    }
    
    if (isEnd || head == null) {
      return tail;
    }
    else {
      return TempBuffer.allocate();
    }
  }
  
  protected void closeStream()
      throws IOException
  {
    /*
      RequestHttpBase req = _response.getRequest();
    
      if (req.isKeepalive() || req.isDuplex()) {
        //_nextStream.flushBuffer();
        _nextStream.flush();
      }
      else {
        _nextStream.close();
      }
      */
  }

  /**
   * Fills the chunk header.
   */
  private void writeChunkHeader(byte []buffer, int start, int length)
  {
    if (length == 0)
      throw new IllegalStateException();

    buffer[start - 8] = (byte) '\r';
    buffer[start - 7] = (byte) '\n';
    buffer[start - 6] = hexDigit(length >> 12);
    buffer[start - 5] = hexDigit(length >> 8);
    buffer[start - 4] = hexDigit(length >> 4);
    buffer[start - 3] = hexDigit(length);
    buffer[start - 2] = (byte) '\r';
    buffer[start - 1] = (byte) '\n';
  }

  /**
   * Returns the hex digit for the value.
   */
  private static byte hexDigit(int value)
  {
    value &= 0xf;

    if (value <= 9)
      return (byte) ('0' + value);
    else
      return (byte) ('a' + value - 10);
  }

  @Override
  public boolean isMmapEnabled()
  {
    //return _nextStream.isMmapEnabled();
    return false;
  }

  @Override
  public boolean isSendfileEnabled()
  {
    //return _nextStream.isSendfileEnabled();
    return false;
  }

  /**
   * Sends a file.
   *
   * @param path the path to the file
   * @param length the length of the file (-1 if unknown)
   */
  @Override
  public void sendFile(Path path, long offset, long length)
    throws IOException
  {
    RequestHttpBase request = _request;
    HttpContainer http = request.http();
    
    /*
    if (! isSendfileEnabled()
        || ! http.isSendfileEnabled()
        || (request.request().isCaching()
            && length < http.getSendfileMinLength())) {
      path.writeToStream(this);
      return;
    }
    */
    
    if (true) return;
    
    http.addSendfileCount();
    
    //path.sendfile(this, offset, length);
  }
  
  @Override
  public void writeMmap(long mmapAddress, long []mmapBlocks, 
                        long mmapOffset, long mmapLength)
    throws IOException
  {
    if (_isChunked) {
      throw new IllegalStateException(L.l("writeMmap cannot use chunked"));
    }
    
    //flushBuffer();
    
    //_nextStream.writeMmap(mmapAddress, mmapBlocks, mmapOffset, mmapLength);
  }

  @Override
  public void writeSendfile(byte []fileName, int nameLength, long fileLength)
    throws IOException
  {
    if (_isChunked) {
      throw new IllegalStateException(L.l("writeSendfile cannot use chunked"));
    }
    
    //flushBuffer();
    
    //_nextStream.writeSendfile(fileName, nameLength, fileLength);
  }
}
