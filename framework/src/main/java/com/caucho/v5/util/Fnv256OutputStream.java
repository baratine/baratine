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

package com.caucho.v5.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Creates hashes for the large data sets.
 */
public class Fnv256OutputStream extends OutputStream {
  private final Fnv256 _digest;
  private final byte []_data = new byte[1];
  
  private OutputStream _os;
  
  

  public Fnv256OutputStream()
  {
    _digest = new Fnv256();
  }
  
  public Fnv256OutputStream(OutputStream os)
  {
    this();
    
    init(os);
  }
  
  /**
   * Creates the output
   */
  public void init(OutputStream os)
  {
    _digest.init();
    _os = os;
  }

  public void digest(byte []buffer, int offset, int length)
  {
    _digest.digest(buffer, offset, length);
  }
  
  public byte []getDigest()
  {
    byte []digest = new byte[32];
    
    _digest.digest(digest, 0, digest.length);
    
    return digest;
  }

  @Override
  public void write(int value)
    throws IOException
  {
    byte []data = _data;
    
    data[0] = (byte) value;

    _os.write(value);
    _digest.update(data, 0, 1);
  }

  @Override
  public void write(byte []buffer, int offset, int length)
    throws IOException
  {
    _os.write(buffer, offset, length);

    _digest.update(buffer, offset, length);
  }

  @Override
  public void flush()
    throws IOException
  {
    _os.flush();
  }

  /**
   * Close the stream
   */
  @Override
  public void close()
    throws IOException
  {
    OutputStream os = _os;
    _os = null;

    if (os != null)
      os.close();
  }
}
