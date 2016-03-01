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

package com.caucho.v5.io.i18n;

import java.io.IOException;

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
public class ByteToChar extends ByteToCharBase
{
  private StringBuilder _charBuffer;

  /**
   * Creates an uninitialized converter. Use <code>init</code> to initialize.
   */ 
  ByteToChar()
  {
    _charBuffer = new StringBuilder();
  }

  public static ByteToChar create()
  {
    return new ByteToChar();
  }

  /**
   * Clears the converted
   */
  public void clear()
  {
    super.clear();
    
    _charBuffer.setLength(0);
  }

  /**
   * Gets the converted string.
   */
  public String getConvertedString()
    throws IOException
  {
    flush();
    
    return _charBuffer.toString();
  }

  @Override
  protected void outputChar(int ch)
  {
    _charBuffer.append((char) ch);
  }

  /**
   * Prints the object.
   */
  public String getString()
  {
    try {
      return getConvertedString();
    } catch (IOException e) {
      throw new RuntimeException(String.valueOf(e));
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
