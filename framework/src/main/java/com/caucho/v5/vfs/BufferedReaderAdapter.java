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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.vfs;

import java.io.BufferedReader;
import java.io.IOException;

  /**
   * Trivial adapter so readers and input streams can be used in the
   * same servlet.  This adapter also saves GC because it only needs
   * allocation once.
   */
public class BufferedReaderAdapter extends BufferedReader {
  private ReadStreamOld _rs;
  private BufferedReader _bufferedReader;

  public BufferedReaderAdapter(ReadStreamOld rs)
  {
    // this is only a dummy because the rs.getReader is never actually called
    super(rs.getReader(), 1);
  }

  public void init(ReadStreamOld rs)
  {
    _rs = rs;
    _bufferedReader = null;
  }

  @Override
  public int read() throws IOException
  {
    BufferedReader reader = _bufferedReader;
    
    if (reader != null)
      return reader.read();
    else
      return _rs.readChar();
  }

  @Override
  public int read(char []cbuf, int offset, int length) throws IOException
  {
    BufferedReader reader = _bufferedReader;
    
    if (reader != null)
      return reader.read(cbuf, offset, length);
    else
      return _rs.read(cbuf, offset, length);
  }

  @Override
  public String readLine() throws IOException
  {
    BufferedReader reader = _bufferedReader;
    
    if (reader != null)
      return reader.readLine();
    else
      return _rs.readln();
  }

  @Override
  public long skip(long n) throws IOException
  {
    BufferedReader reader = _bufferedReader;
    
    if (reader != null)
      return reader.skip(n);
    
    long count = 0;

    for (; count < n && _rs.readChar() >= 0; count++) {
    }

    return count;
  }

  @Override
  public boolean ready() throws IOException
  {
    BufferedReader reader = _bufferedReader;
    
    if (reader != null)
      return reader.ready();
    else
      return _rs.available() > 0;
  }
  
  public boolean markSupported()
  {
    return true;
  }
  
  @Override
  public void mark(int readAhead)
    throws IOException
  {
    BufferedReader reader = _bufferedReader;
    
    if (reader == null) {
      reader = new BufferedReader(_rs.getReader());
      _bufferedReader = reader;
    }
    
    reader.mark(readAhead);
  }
  
  @Override
  public void reset()
    throws IOException
  {
    BufferedReader reader = _bufferedReader;
    
    if (reader != null)
      reader.reset();
  }

  @Override
  public void close() throws IOException
  {
    BufferedReader reader = _bufferedReader;
    _bufferedReader = null;
    
    if (reader != null)
      reader.close();

  }
}
