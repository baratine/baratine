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

import java.io.IOException;
import java.io.Writer;

import com.caucho.v5.io.i18n.ByteToCharBase;

/**
 * Utility class for converting a byte stream to a character stream.
 *
 * <pre>
 * ByteToChar converter = new ByteToChar();
 * converter.setEncoding("utf-8");
 * converter.clear();
 *
 * converter.addChar('H');
 * converter.addByte(0xc0);
 * converter.addByte(0xb8);
 *
 * String value = converter.getConvertedString();
 * </pre>
 */
public class ByteToCharWriter extends ByteToCharBase
{
  private Writer _writer;

  /**
   * Creates an uninitialized converter. Use <code>init</code> to initialize.
   */ 
  ByteToCharWriter()
  {
  }

  /**
   * Sets the writer.
   */
  public void setWriter(Writer writer)
  {
    _writer = writer;
  }

  protected void outputChar(int ch)
    throws IOException
  {
    _writer.write(ch);
  }
}
