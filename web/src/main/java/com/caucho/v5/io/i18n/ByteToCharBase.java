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
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

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
abstract public class ByteToCharBase extends InputStream {
  private Reader _readEncoding;
  private String _readEncodingName;
  private int _specialEncoding;

  private final byte []_byteBuffer = new byte[256];
  private final char []_charBuffer = new char[1];
  private int _byteHead;
  private int _byteTail;

  /**
   * Creates an uninitialized converter. Use <code>init</code> to initialize.
   */ 
  protected ByteToCharBase()
  {
  }

  /**
   * Sets the encoding for the converter.
   */
  public void setEncoding(String encoding)
    throws UnsupportedEncodingException
  {
    if (encoding != null) {
      _readEncoding = Encoding.getReadEncoding(this, encoding);
      _readEncodingName = Encoding.getMimeName(encoding);
    }
    else {
      _readEncoding = null;
      _readEncodingName = null;
    }
  }

  /**
   * Clears the converted
   */
  public void clear()
  {
    _byteHead = 0;
    _byteTail = 0;
  }
  
  /**
   * Adds the next byte.
   *
   * @param b the byte to write
   */
  public void addByte(int b)
    throws IOException
  {
    int nextHead = (_byteHead + 1) % _byteBuffer.length;

    while (nextHead == _byteTail) {
      int ch = readChar();

      if (ch < 0) {
        break;
      }

      outputChar(ch);
    }

    _byteBuffer[_byteHead] = (byte) b;
    _byteHead = nextHead;
  }
  
  /**
   * Adds the next character.
   *
   * @param nextCh the character to write
   */
  public void addChar(char nextCh)
    throws IOException
  {
    int ch;
    
    while ((ch = readChar()) >= 0) {
      outputChar((char) ch);
    }

    outputChar(nextCh);
  }

  /**
   * Flushes the buffer.
   */
  public void flush()
    throws IOException
  {
    int ch;
    
    while ((ch = readChar()) >= 0) {
      outputChar((char) ch);
    }
  }

  /**
   * Reads the next converted character.
   */
  private int readChar()
    throws IOException
  {
    Reader readEncoding = _readEncoding;

    if (readEncoding == null)
      return read();
    else {
      if (readEncoding.read(_charBuffer, 0, 1) == 1) {
        return _charBuffer[0];
      }
      else {
        return -1;
      }
    }
  }

  /**
   * For internal use only.  Reads the next byte from the byte buffer.
   *
   * @return the next byte
   */
  public int read()
    throws IOException
  {
    if (_byteHead == _byteTail) {
      return -1;
    }

    int b = _byteBuffer[_byteTail] & 0xff;
    _byteTail = (_byteTail + 1) % _byteBuffer.length;

    return b;
  }

  abstract protected void outputChar(int ch)
    throws IOException;
}
