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

package com.caucho.v5.vfs.i18n;

import java.io.IOException;

import com.caucho.v5.io.ByteAppendable;
import com.caucho.v5.io.OutputStreamWithBuffer;
import com.caucho.v5.io.i18n.EncodingWriter;

/**
 * Implements an encoding char-to-byte writer for the windows hack
 * factory.
 */
public class WindowsHackWriter extends EncodingWriter {
  private final static WindowsHackWriter _writer = new WindowsHackWriter();
  
  /**
   * Null-arg constructor for instantiation by com.caucho.v5.vfs.Encoding only.
   */
  public WindowsHackWriter()
  {
  }
  
  /**
   * Returns the Java encoding for the writer.
   */
  public String getJavaEncoding()
  {
    return "WindowsHack";
  }

  /**
   * Create a windows-hack writer using on the OutputStream to send bytes.
   *
   * @param os the write stream receiving the bytes.
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return the windows-hack writer.
   */
  public EncodingWriter create(String javaEncoding)
  {
    return _writer;
  }

  /**
   * Writes a character to the output stream with the correct encoding.
   *
   * @param ch the character to write.
   */
  public void write(ByteAppendable os, char ch)
    throws IOException
  {
    os.write(ch);
  }

  /**
   * Writes into a character buffer using the correct encoding.
   *
   * @param cbuf character array with the data to write.
   * @param off starting offset into the character array.
   * @param len the number of characters to write.
   */
  public int write(OutputStreamWithBuffer os, char []cbuf, int off, int len)
    throws IOException
  {
    for (int i = 0; i < len; i++) {
      os.write(cbuf[off + i]);
    }
    
    return len;
  }
}


