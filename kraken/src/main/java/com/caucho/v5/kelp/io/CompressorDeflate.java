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

package com.caucho.v5.kelp.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.util.FreeList;

/**
 * Compression factory.
 */
public class CompressorDeflate implements CompressorKelp
{
  private FreeList<Inflater> _freeInflater = new FreeList<>(64);
  private FreeList<Deflater> _freeDeflater = new FreeList<>(64);
  
  @Override
  public OutputStream out(WriteStream os) throws IOException
  {
    return new OutDeflater(os, allocateDeflater());
  }
  
  @Override
  public InputStream in(ReadStream is, long offset, int length) throws IOException
  {
    return new InInflater(is, allocateInflater(), offset, length);
  }
  
  private Deflater allocateDeflater()
  {
    Deflater deflater = _freeDeflater.allocate();
    
    if (deflater == null) {
      deflater = new Deflater();
    }
    
    deflater.reset();
    deflater.setLevel(1);
    
    return deflater;
  }
  
  private void freeDeflater(Deflater deflater)
  {
    if (! _freeDeflater.free(deflater)) {
      deflater.end();
    }
  }
  
  private Inflater allocateInflater()
  {
    Inflater inflater = _freeInflater.allocate();
    
    if (inflater == null) {
      inflater = new Inflater();
    }
    
    inflater.reset();
    //inflater.setLevel(1);
    
    return inflater;
  }
  
  private void freeInflater(Inflater inflater)
  {
    if (! _freeInflater.free(inflater)) {
      inflater.end();
    }
  }
  
  private class OutDeflater extends OutputStream
  {
    private WriteStream _os;
    private Deflater _deflater;
    private TempBuffer _tBuf;
    private byte []_buffer;
    private int _offset;
    
    OutDeflater(WriteStream os, Deflater deflater)
    {
      _os = os;
      _deflater = deflater;
      
      _tBuf = TempBuffer.create();
      _buffer = _tBuf.buffer();
    }
    
    @Override
    public void write(int ch)
      throws IOException
    {
      if (_buffer.length <= _offset) {
        flush();
      }
      
      _buffer[_offset++] = (byte) ch;
    }
    
    @Override
    public void write(byte []buffer, int offset, int length)
      throws IOException
    {
      while (length > 0) {
        byte []tBuffer = _buffer;
        int tOffset = _offset;
        
        int sublen = Math.min(length, tBuffer.length - tOffset);
        
        System.arraycopy(buffer, offset, tBuffer, tOffset, sublen);
        _offset = tOffset + sublen;
        offset += sublen;
        length -= sublen;
        
        if (length > 0) {
          flush();
        }
      }
    }
    
    @Override
    public void flush()
      throws IOException
    {
      while (_offset > 0) {
        if (_deflater.needsInput()) {
          _deflater.setInput(_buffer, 0, _offset);
          _offset = 0;
        }
        
        flushDeflater(_deflater, _os);
      }
      
      _os.flushBuffer();
    }
    
    private void flushDeflater(Deflater deflater, WriteStream os)
      throws IOException
    {
      while (true) {
        byte []buffer = os.buffer();
        int offset = os.offset();
        
        int sublen = buffer.length - offset;
        
        if (sublen == 0) {
          os.flushBuffer();
          buffer = os.buffer();
          offset = os.offset();
        }
        
        sublen = deflater.deflate(buffer, offset, sublen);
        
        os.offset(offset + sublen);
        
        if (sublen == 0) {
          return;
        }
      }
    }
    
    @Override
    public void close()
      throws IOException
    {
      flush();
      
      Deflater deflater = _deflater;
      _deflater = null;
      
      if (deflater != null) {
        deflater.finish();
        flushDeflater(deflater, _os);
        
        freeDeflater(deflater);
      }
      
      TempBuffer tBuf = _tBuf;
      _tBuf = null;
      _buffer = null;
      
      if (tBuf != null) {
        tBuf.free();
      }
    }
  }
  
  private class InInflater extends InputStream
  {
    private ReadStream _is;
    private int _rawLength;
    
    private Inflater _inflater;
    private TempBuffer _tBuf;
    private byte []_buffer;
    private int _offset;
    private int _length;
    
    InInflater(ReadStream is, Inflater inflater, long offset, int length)
      throws IOException
    {
      _is = is;
      _inflater = inflater;
      
      _tBuf = TempBuffer.create();
      _buffer = _tBuf.buffer();
      
      _is.position(offset);
      _rawLength = length;
    }
    
    @Override
    public int read()
      throws IOException
    {
      int offset = _offset;
      int length = _length;

      if (length <= offset) {
        if (! fill()) {
          return -1;
        }
        
        offset = _offset;
        length = _length;
      }
      
      int value = _buffer[offset++] & 0xff;
      
      _offset = offset;
      
      return value;
    }
    
    @Override
    public int read(byte []buffer, int offset, int length)
      throws IOException
    {
      int tOffset = _offset;
      int tLength = _length;
      
      if (tLength <= tOffset) {
        if (! fill()) {
          return -1;
        }
        
        tOffset = _offset;
        tLength = _length;
      }
      
      int sublen = Math.min(length, tLength - tOffset);
      
      System.arraycopy(_buffer, tOffset, buffer, offset, sublen);
      
      _offset = tOffset + sublen;
      
      return sublen;
    }
    
    private boolean fill()
      throws IOException
    {
      if (_rawLength <= 0) {
        return false;
      }
      
      try {
        _offset = 0;

        while (true) {
          _length = _inflater.inflate(_buffer, 0, _buffer.length);
        
          if (_length > 0) {
            return true;
          }
          else if (_rawLength <= 0) {
            return false;
          }
          
          int offset = _is.offset();
          int length = _is.length();
          
          if (length <= offset) {
            if (_is.fillBuffer() <= 0) {
              return false;
            }
            
            offset = _is.offset();
            length = _is.length();
          }
          
          int sublen = Math.min(_rawLength, length - offset);
          
          _inflater.setInput(_is.buffer(), offset, sublen);
          
          _is.offset(offset + sublen);
          _rawLength -= sublen;
        }
      } catch (DataFormatException e) {
        throw new IOException(e);
      }
      
      /*
      while (_offset > 0) {
        if (_deflater.needsInput()) {
          System.out.println("IN: " + _offset);
          System.out.println("HEX: " + Hex.toHex(_buffer, 0, _offset));
          _deflater.setInput(_buffer, 0, _offset);
          _offset = 0;
        }
        
        flushDeflater(_deflater, _os);
      }
      
      _os.flushBuffer();
      */
    }
    /*
    private void flushDeflater(Deflater deflater, WriteStream os)
      throws IOException
    {
      while (true) {
        byte []buffer = os.getBuffer();
        int offset = os.getBufferOffset();
        
        int sublen = buffer.length - offset;
        
        if (sublen == 0) {
          os.flushBuffer();
          buffer = os.getBuffer();
          offset = os.getBufferOffset();
        }
        
        sublen = deflater.deflate(buffer, offset, sublen);
        
        os.setBufferOffset(offset + sublen);
        System.out.println("FDL: " + sublen);
        
        if (sublen == 0) {
          return;
        }
      }
    }
    */
    
    @Override
    public void close()
      throws IOException
    {
      Inflater inflater = _inflater;
      _inflater = null;
      
      if (inflater != null) {
        freeInflater(inflater);
      }
      
      TempBuffer tBuf = _tBuf;
      _tBuf = null;
      _buffer = null;
      
      if (tBuf != null) {
        tBuf.free();
      }
    }
  }
}
