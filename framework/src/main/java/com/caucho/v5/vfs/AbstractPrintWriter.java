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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

/**
 * An abstract print writer.
 */
public abstract class AbstractPrintWriter extends PrintWriter {
  private static final char []_trueChars = "true".toCharArray();
  private static final char []_falseChars = "false".toCharArray();
  private static final char []_nullChars = "null".toCharArray();

  private static final Writer _dummyWriter = new StringWriter();

  private final char []_tempCharBuffer = new char[64];

  /**
   * Creates the print writer.
   */
  protected AbstractPrintWriter()
  {
    super(_dummyWriter);
  }

  /**
   * Writes a character array to the writer.
   *
   * @param buf the buffer to write.
   * @param off the offset into the buffer
   * @param len the number of characters to write
   */
  abstract public void write(char []buf, int offset, int length);
  
  /**
   * Writes a character to the output.
   *
   * @param buf the buffer to write.
   */
  abstract public void write(int ch);

  /**
   * Writes a subsection of a string to the output.
   */
  abstract public void write(String s, int off, int len);

  /**
   * Writes a char buffer to the output.
   *
   * @param buf the buffer to write.
   */
  final public void write(char []buf)
  {
    write(buf, 0, buf.length);
  }

  /**
   * Writes a string to the output.
   */
  final public void write(String s)
  {
    write(s, 0, s.length());
  }

  /**
   * Writes the newline character.
   */
  public void newLine()
  {
    write('\n');
  }
  
  /**
   * Prints a boolean.
   */
  final public void print(boolean b)
  {
    write(b ? _trueChars : _falseChars);
  }

  /**
   * Prints a character.
   */
  public void print(char ch)
  {
    write(ch);
  }

  /**
   * Prints an integer value.
   */
  public void print(int i)
  {
    if (i == 0x80000000) {
      print("-2147483648");
      return;
    }

    if (i < 0) {
      write('-');
      i = -i;
    } else if (i < 9) {
      write('0' + i);
      return;
    }

    int length = 0;
    int exp = 10;

    if (i >= 1000000000)
      length = 9;
    else {
      for (; i >= exp; length++)
        exp = 10 * exp;
    }

    int j = 31;
    
    while (i > 0) {
      _tempCharBuffer[--j] = (char) ((i % 10) + '0');
      i /= 10;
    }

    write(_tempCharBuffer, j, 31 - j);
  }
  
  /**
   * Prints a long value.
   */
  public void print(long v)
  {
    if (v == 0x8000000000000000L) {
      print("-9223372036854775808");
      return;
    }

    if (v < 0) {
      write('-');
      v = -v;
    } else if (v == 0) {
      write('0');
      return;
    }

    int j = 31;
    
    while (v > 0) {
      _tempCharBuffer[--j] = (char) ((v % 10) + '0');
      v /= 10;
    }

    write(_tempCharBuffer, j, 31 - j);
  }
  
  final public void print(float f)
  {
    write(String.valueOf(f));
  }
  
  final public void print(double d)
  {
    write(String.valueOf(d));
  }

  /**
   * Prints a character array
   */
  final public void print(char []s)
  {
    write(s, 0, s.length);
  }

  /**
   * Prints a string.
   */
  final public void print(String s)
  {
    if (s == null)
      write(_nullChars, 0, _nullChars.length);
    else
      write(s, 0, s.length());
  }

  /**
   * Prints the value of the object.
   */
  final public void print(Object v)
  {
    if (v == null)
      write(_nullChars, 0, _nullChars.length);
    else {
      String s = v.toString();
      
      write(s, 0, s.length());
    }
  }

  /**
   * Prints the newline.
   */
  public void println()
  {
    write('\n');
  }
  
  /**
   * Prints the boolean followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(boolean v)
  {
    print(v);
    println();
  }

  /**
   * Prints a character followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(char v)
  {
    print(v);
    println();
  }
  
  /**
   * Prints an integer followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(int v)
  {
    print(v);
    println();
  }
  
  /**
   * Prints a long followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(long v)
  {
    print(v);
    println();
  }
  
  /**
   * Prints a float followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(float v)
  {
    String s = String.valueOf(v);
    
    write(s, 0, s.length());
    println();
  }
  
  
  /**
   * Prints a double followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(double v)
  {
    String s = String.valueOf(v);
    
    write(s, 0, s.length());
    
    println();
  }

  /**
   * Writes a character array followed by a newline.
   */
  final public void println(char []s)
  {
    write(s, 0, s.length);
    println();
  }

  /**
   * Writes a string followed by a newline.
   */
  final public void println(String s)
  {
    if (s == null)
      write(_nullChars, 0, _nullChars.length);
    else
      write(s, 0, s.length());

    println();
  }
  
  /**
   * Writes an object followed by a newline.
   */
  final public void println(Object v)
  {
    if (v == null)
      write(_nullChars, 0, _nullChars.length);
    else {
      String s = v.toString();

      write(s, 0, s.length());
    }
    
    println();
  }
}
