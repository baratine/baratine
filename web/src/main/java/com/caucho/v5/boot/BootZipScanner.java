/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.boot;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scans a zip file, returning the names. The ZipScanner only works with
 * central directory style zip files.
 */
public final class BootZipScanner implements AutoCloseable
{
  private static Logger _log;
  
  private char []_cbuf = new char[256];
  private int _nameLen;

  private Supplier<InputStream> _streamFactory;
  private InputStream _is;
  
  private boolean _isValid;

  private int _entries;
  private int _offset;

  private int _index;
  
  private int _fileOffset;
  
  private String _name;

  private int _method;

  private int _compressedSize;

  private int _uncompressedSize;

  /**
   * Creates a new Jar.
   *
   * @param path canonical path
   */
  public BootZipScanner(String fileName,
                        Supplier<InputStream> streamFactory,
                        int fileLength)
  {
    _streamFactory = streamFactory;
    
    try (InputStream is = streamFactory.get()) {
      if (fileName == null) {
        fileName = String.valueOf(is);
      }
      
      int length = fileLength;
      
      if (length < 22 + 7) {
        return;
      }
      
      // PACK200 is a standard comment, so try skipping it first
      is.skip(length - 22 - 7);

      if (is.read() != 0x50) {
        is.skip(6);

        if (is.read() != 0x50) {
          return;
        }
      }

      if (is.read() == 0x4b
          && is.read() == 0x05
          && is.read() == 0x06) {
        _isValid = true;
      }

      if (_isValid) {
        is.skip(6);

        _entries = is.read() + (is.read() << 8);
        is.skip(4);

        _offset = readInt(is);
      }
    } catch (Exception e) {
      log().log(Level.FINER, e.toString(), e);
    } finally {
      if (! _isValid) {
        log().fine("Invalid Zip scan for " + fileName + " " + _entries);
        //Thread.dumpStack();
      }
    }
  }

  public final boolean open()
    throws IOException
  {
    if (! _isValid) {
      return false;
    }

    _is = _streamFactory.get();
    _is.skip(_offset);
    _index = 0;
    
    return true;
  }

  public final boolean next()
    throws IOException
  {
    if (! _isValid || _entries <= _index) {
      return false;
    }

    _index++;

    InputStream is = _is;
    
    int magic;

    if ((magic = readInt(is)) != 0x02014b50) {
      throw new IOException("invalid zip format "+ Integer.toHexString(magic));
    }

    is.skip(2 + 2 + 2);
    
    _method = is.read() + (is.read() << 8);
    
    is.skip(2 + 2 + 4);
    
    _compressedSize = readInt(is);
    _uncompressedSize = readInt(is);

    int nameLen = is.read() + (is.read() << 8);
    int extraLen = is.read() + (is.read() << 8);
    int commentLen = is.read() + (is.read() << 8);

    is.skip(2 + 2 + 4);
    
    _fileOffset = readInt(is);

    _nameLen = nameLen;
    
    if (_cbuf.length < nameLen) {
      _cbuf = new char[nameLen];
    }

    char []cbuf = _cbuf;

    int k = readUtf8(is, cbuf, nameLen);
    
    for (int i = k - 1; i >= 0; i--) {
      char ch = cbuf[i];

      // win32 canonicalize 
      if (ch == '\\') {
        cbuf[i] = '/';
      }
    }

    _name = new String(cbuf, 0, k);
    _nameLen = k;

    if (extraLen + commentLen > 0) {
      is.skip(extraLen + commentLen);
    }

    return true;
  }
  
  public static void skipLocalHeader(InputStream is)
    throws IOException
  {
    int magic;

    if ((magic = readInt(is)) != 0x04034b50) {
      throw new IOException("invalid zip format "+ Integer.toHexString(magic));
    }
    
    is.skip(2 + 2 + 2 + 2 + 2 + 4 + 4 + 4);
    
    int nameLen = readShort(is);
    int extraLen = readShort(is);
    
    is.skip(nameLen + extraLen);
  }

  public final String name()
  {
    /*
    if (_name == null) {
      _name = new String(_cbuf, 0, _nameLen);
    }
    */

    return _name;
  }

  public int offset()
  {
    return _fileOffset;
  }

  public int size()
  {
    return _uncompressedSize;
  }

  public int sizeCompressed()
  {
    return _compressedSize;
  }

  public int method()
  {
    return _method;
  }

  public final char []getNameBuffer()
  {
    return _cbuf;
  }

  public final int getNameLength()
  {
    return _nameLen;
  }
  
  private static int readUtf8(InputStream is, 
                              char []buffer, int byteLen)
    throws IOException
  {
    int offset = 0;
    
    while (byteLen > 0) {
      int ch = is.read() & 0xff;
      byteLen--;
      
      if (ch < 0x80) {
        buffer[offset++] = (char) ch;
      }
      else {
        throw new UnsupportedOperationException("CH: " + Integer.toHexString(ch));
      }
    }
    
    return offset;
  }
  
  private static int readInt(InputStream is)
    throws IOException
  {
    int value;
    
    value = ((is.read() & 0xff) 
             + ((is.read() & 0xff) << 8) 
             + ((is.read() & 0xff) << 16) 
             + ((is.read() & 0xff) << 24));
    
    return value;
  }
  
  private static int readShort(InputStream is)
    throws IOException
  {
    int value;
    
    value = ((is.read() & 0xff) 
             + ((is.read() & 0xff) << 8));
    
    return value;
  }

  public void close()
  {
    InputStream is = _is;
    _is = null;

    if (is != null) {
      try {
        is.close();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static Logger log()
  {
    if (_log == null)
      _log = Logger.getLogger(BootZipScanner.class.getName());

    return _log;
  }
}
