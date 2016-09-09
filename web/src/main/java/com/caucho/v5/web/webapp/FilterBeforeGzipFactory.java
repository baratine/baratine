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

package com.caucho.v5.web.webapp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import io.baratine.inject.Priority;
import io.baratine.io.Buffer;
import io.baratine.web.RequestWeb;
import io.baratine.web.RequestWeb.OutFilterWeb;
import io.baratine.web.ServiceWeb;

import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.BitsUtil;

/**
 * View with associated type meta-data
 */
@Priority(-100)
class FilterBeforeGzipFactory implements FilterFactory<ServiceWeb>
{
  @Override
  public ServiceWeb apply(RouteBuilderAmp builder)
  {
    return new FilterBeforeGzip();
  }
  
  private static class FilterBeforeGzip implements ServiceWeb
  {

    @Override
    public void service(RequestWeb request) throws Exception
    {
      String acceptEncoding = request.header("accept-encoding");
      
      if (acceptEncoding != null && acceptEncoding.indexOf("gzip") >= 0) {
        pushGzip(request);
      }
      
      request.ok();
    }
    
    protected void pushGzip(RequestWeb request)
    {
      request.push(new GzipFilter());
    }
    
  }
  
  private static class GzipFilter implements OutFilterWeb
  {
    private boolean _isDisable;
    private GzipOutput _outGzip;
    
    @Override
    public void length(RequestWeb request, long length)
    {
      if (_isDisable) {
        request.length(length);
      }
      else if (length < 100) {
        _isDisable = true;
        request.length(length);
      }
    }
    
    @Override
    public void type(RequestWeb request, String type)
    {
      if (type.startsWith("text/")) {
      }
      else if (type.indexOf("json") >= 0) {
      }
      else {
        _isDisable = true;
      }
      
      request.type(type);
    }

    @Override
    public void write(RequestWeb out, Buffer buffer)
    {
      if (_outGzip == null) {
        if (_isDisable) {
          out.write(buffer);
          return;
        }
        
        out.header("content-encoding", "gzip");
        _outGzip = new GzipOutput(out);
      }

      try {
        buffer.read(_outGzip);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    
    @Override
    public void ok(RequestWeb out)
    {
      if (_outGzip != null) {
        _outGzip.close();
      }
    }
  }
  
  private static class GzipOutput extends OutputStream
  {
    private static final byte []HEADER = new byte[]
        {
         31, (byte) 139, 8, 0, 0, 0, 0, 0, 0, 0
        };
    
    private Deflater _deflater =
      new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    private final CRC32 _crc = new CRC32();
    private TempBuffer _tBuf = TempBuffer.create();
    private RequestWeb _out;
    
    GzipOutput(RequestWeb out)
    {
      _out = out;
      
      writeHeader(out);
    }
    
    @Override
    public void write(int value)
    {
      throw new IllegalStateException();
    }

    @Override
    public void write(byte []buffer, int offset, int length)
      throws IOException
    {
      _deflater.setInput(buffer, offset, length);
      _crc.update(buffer, offset, length);
      
      byte []tBuffer = _tBuf.buffer();
      int sublen;
      
      while ((sublen = _deflater.deflate(tBuffer, 0, tBuffer.length)) > 0) {
        _out.write(tBuffer, 0, sublen);
      }
    }
    
    @Override
    public void close()
    {
      _deflater.finish();
      
      int sublen;
      byte []tBuffer = _tBuf.buffer();
      
      while ((sublen = _deflater.deflate(tBuffer, 0, tBuffer.length)) > 0) {
        _out.write(tBuffer, 0, sublen);
      }
      
      writeFooter(_out);
      
      _deflater.end();
    }
    
    private void writeHeader(RequestWeb out)
    {
      out.write(HEADER, 0, HEADER.length);
    }
    
    private void writeFooter(RequestWeb out)
    {
      try {
        BitsUtil.writeInt(out.output(), (int) _crc.getValue());
        BitsUtil.writeInt(out.output(), _deflater.getTotalIn());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
