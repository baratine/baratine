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

package com.caucho.v5.vfs;

import java.io.*;
import java.util.logging.*;

/**
 * Scans a zip file, returning the names. The ZipScanner only works with
 * central directory style zip files.
 */
public final class ZipScanner
{
  private static Logger _log;
  
  private char []_cbuf = new char[256];
  private int _nameLen;

  private PathImpl _path;
  private ReadStreamOld _is;
  
  private ReadStreamOld _fileIs;
  
  private boolean _isValid;

  private int _entries;
  private int _offset;

  private int _index;
  
  private int _localFileOffset;
  
  private String _name;

  /**
   * Creates a new Jar.
   *
   * @param path canonical path
   */
  public ZipScanner(PathImpl path)
  {
    try {
      _path = path;
    
      int length = (int) path.length();
    
      ReadStreamOld is = path.openRead();

      try {
        // PACK200 is a standard comment, so try skipping it first
        is.skip(length - 22 - 7);

        if (is.read() != 0x50) {
          is.skip(6);

          if (is.read() != 0x50)
            return;

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
      } finally {
        is.close();
      }
    } catch (Exception e) {
      log().log(Level.FINER, e.toString(), e);
    }
  }

  public final boolean open()
    throws IOException
  {
    if (! _isValid)
      return false;

    _is = _path.openRead();
    _is.skip(_offset);
    _index = 0;

    return true;
  }

  public final boolean next()
    throws IOException
  {
    if (_entries <= _index)
      return false;

    _index++;

    ReadStreamOld is = _is;

    if (is.readInt() != 0x504b0102) {
      throw new IOException("illegal zip format");
    }

    is.skip(2 + 2 + 2 + 2 + 2 + 2 + 4);
    
    int compressedSize = readInt(is);
    int uncompressedSize = readInt(is);

    int nameLen = is.read() + (is.read() << 8);
    int extraLen = is.read() + (is.read() << 8);
    int commentLen = is.read() + (is.read() << 8);

    is.skip(2 + 2 + 4);
    
    _localFileOffset = readInt(is);

    _nameLen = nameLen;
    if (_cbuf.length < nameLen)
      _cbuf = new char[nameLen];

    char []cbuf = _cbuf;

    int k = is.readUTF8ByByteLength(cbuf, 0, nameLen);

    for (int i = k - 1; i >= 0; i--) {
      char ch = cbuf[i];

      // win32 canonicalize 
      if (ch == '\\')
        cbuf[i] = '/';
    }

    _name = null; // new String(cbuf, 0, k);
    _nameLen = k;

    if (extraLen + commentLen > 0)
      is.skip(extraLen + commentLen);

    return true;
  }

  public final String getName()
  {
    if (_name == null)
      _name = new String(_cbuf, 0, _nameLen);

    return _name;
  }

  public final char []getNameBuffer()
  {
    return _cbuf;
  }

  public final int getNameLength()
  {
    return _nameLen;
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

  public void close()
  {
    InputStream is = _is;
    _is = null;
    
    InputStream fileIs = _fileIs;
    _fileIs = null;

    if (is != null) {
      try {
        is.close();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    if (fileIs != null) {
      try {
        fileIs.close();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static Logger log()
  {
    if (_log == null)
      _log = Logger.getLogger(ZipScanner.class.getName());

    return _log;
  }
}
