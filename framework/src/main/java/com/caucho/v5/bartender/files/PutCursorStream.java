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

package com.caucho.v5.bartender.files;

import java.io.IOException;
import java.io.OutputStream;

import com.caucho.v5.kelp.RowCursor;

/**
 * Entry to the filesystem.
 */
class PutCursorStream extends OutputStream
{
  private final FileServiceRoot _root;
  private final String _path;
  private final RowCursor _cursor;
  private final OutputStream _delegate;
  private final boolean _isEphemeral;
  
  private int _length;
  
  PutCursorStream(FileServiceRoot root, 
                  String path,
                  RowCursor cursor,
                  OutputStream delegate,
                  boolean isEphemeral)
  {
    _root = root;
    _path = path;
    _cursor = cursor;
    _delegate = delegate;
    _isEphemeral = isEphemeral;
  }
  
  private OutputStream getDelegate()
  {
    return _delegate;
  }
  
  @Override
  public void write(int value)
    throws IOException
  {
    getDelegate().write(value);
    
    _length++;
  }
  
  @Override
  public void write(byte []buffer, int offset, int length)
    throws IOException
  {
    getDelegate().write(buffer, offset, length);
    
    _length += length;
  }
  
  @Override
  public void flush()
    throws IOException
  {
    getDelegate().flush();
  }
  
  @Override
  public void close()
    throws IOException
  {
    getDelegate().close();
    
    int p = _path.lastIndexOf('/');
    
    String tail = _path.substring(p + 1);
    String dir = _path.substring(0, p);
    
    _root.addFile(dir, tail, _cursor, _length, _isEphemeral);
  }
}
