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

package io.baratine.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;


/**
 * Data buffer
 */
public interface Bytes
{
  /**
   * Returns the current size of the buffer.
   */
  int length();

  default boolean isDirect()
  {
    return false;
  }
  
  default ByteBuffer direct()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  Bytes set(int pos, byte []buffer, int offset, int length);
  
  /**
   * adds bytes from the buffer
   */
  Bytes write(byte []buffer, int offset, int length);
  
  /**
   * adds bytes from the buffer from a consumer
   */
  Bytes write(InputStream is)
    throws IOException;
  
  /**
   * gets bytes from the buffer
   */
  Bytes get(int pos, byte []buffer, int offset, int length);
  
  int read(byte []buffer, int offset, int length);

  default void read(ByteBuffer buffer)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /*
  default void allocate()
  {
  }
  */

  default void free()
  {
  }
  
  public interface InputStreamConsumer
  {
    int read(byte []buffer, int offset, int length)
      throws IOException;
  }
}
