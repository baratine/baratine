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

package com.caucho.v5.io.i18n;

import java.io.IOException;
import java.util.logging.Logger;

import com.caucho.v5.io.ByteAppendable;
import com.caucho.v5.io.OutputStreamWithBuffer;

/**
 * Abstract class for a character-to-byte encoding writer.
 *
 * <p/>Implementations need to implement <code>create</code>
 * and <code>write()</code> at minimum.  Efficient implementations will
 * also implement the <code>write</code> into a buffer.
 *
 * <p/>Implementations should not buffer the bytes.
 */
abstract public class EncodingWriter {
  protected static final Logger log
    = Logger.getLogger(EncodingWriter.class.getName());
  
  /**
   * Returns the Java encoding for the writer.
   */
  public String getJavaEncoding()
  {
    return "unknown";
  }
  
  /**
   * Sets the Java encoding for the writer.
   */
  public void setJavaEncoding(String encoding)
  {
  }
  
  /**
   * Returns a new encoding writer for the given stream and javaEncoding.
   *
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return the encoding writer
   */
  public abstract EncodingWriter create(String javaEncoding);
  
  /**
   * Returns a new encoding writer using the saved writer.
   *
   * @return the encoding writer
   */
  public EncodingWriter create()
  {
    return create(getJavaEncoding());
  }

  /**
   * Writes the next character using the correct encoding.
   *
   * @param ch the character to write
   */
  public abstract void write(ByteAppendable os, char ch)
    throws IOException;

  /**
   * Writes a character buffer using the correct encoding.
   *
   * @param cbuf character buffer receiving the data.
   * @param off starting offset into the buffer.
   * @param len number of characters to write
   * @return 
   */
  public int write(OutputStreamWithBuffer os, char []cbuf, int off, int len)
    throws IOException
  {
    for (int i = 0; i < len; i++) {
      write(os, cbuf[off + i]);
    }
    
    return len;
  }  
}
