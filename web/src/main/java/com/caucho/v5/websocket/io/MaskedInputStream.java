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

package com.caucho.v5.websocket.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * User facade for http requests.
 */
public class MaskedInputStream extends InputStream
{
  private final byte []_mask = new byte[4];
  private InputStream _is;
  private int _offset;
  
  public void init(InputStream is)
  {
    _is = is;
  }
  
  public boolean readMask()
    throws IOException
  {
    InputStream is = _is;
    byte []mask = _mask;
    int ch;
    
    mask[0] = (byte) is.read();
    mask[1] = (byte) is.read();
    mask[2] = (byte) is.read();
    
    ch = is.read();
    
    mask[3] = (byte) ch;
    
    _offset = 0;
    
    return ch >= 0;
  }
  
  @Override
  public int read()
    throws IOException
  {
    int ch = _is.read();
    
    if (ch < 0)
      return ch;
    
    int offset = _offset;
    
    _offset = (offset + 1) & 0x3;
    
    return (ch ^ _mask[offset]) & 0xff;
  }
  
  @Override
  public int available()
    throws IOException
  {
    return _is.available();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _is + "]";
  }
}
