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
 * @author Emil Ong
 */

package com.caucho.v5.vfs;

import java.io.IOException;

/**
 * A WriteStream that writes to nothing.
 */
public class NullWriteStream extends WriteStreamOld {
  public boolean canWrite()
  {
    return true;
  }

  /**
   * Writes a byte.
   */
  public void write(int ch) 
    throws IOException
  {
  }

  /**
   * Writes a byte array
   */
  public void write(byte []buf, int offset, int length) 
    throws IOException
  {
  }

  /**
   * Writes a byte array.
   */
  public void write(byte []buf) 
    throws IOException
  {
  }

  /**
   * Flushes the buffer to the source.
   */
  public void flush() 
    throws IOException
  {
  }
}
