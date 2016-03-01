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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A print writer which allows for a changing writer.
 */
public class PrintWriterImpl extends PrintWriter implements FlushBuffer {
  private final static Logger log
    = Logger.getLogger(PrintWriterImpl.class.getName());
  
  private final static char []_nullChars = "null".toCharArray();
  private final static char []_newline = "\n".toCharArray();

  private final static Writer _dummyWriter = new StringWriter();
  
  private final char []_tempCharBuffer = new char[64];
  
  /**
   * Creates a new PrintWriterImpl
   */
  public PrintWriterImpl()
  {
    super((Writer) _dummyWriter);
  }

  /**
   * Creates a new PrintWriterImpl
   */
  public PrintWriterImpl(Writer out)
  {
    super(out);
  }

  /**
   * Sets the underlying writer.
   */
  public void setWriter(Writer out)
  {
    this.out = out;
  }

  /**
   * Writes a character.
   */
  final public void write(char ch)
  {
    Writer out = this.out;
    if (out == null)
      return;
    
    try {
      out.write(ch);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Writes a character.
   */
  final public void write(char []buf, int offset, int length)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      out.write(buf, offset, length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Writes a character buffer.
   */
  final public void write(char []buf)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      out.write(buf, 0, buf.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints a character.
   */
  final public void print(char ch)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      out.write(ch);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints an integer.
   */
  final public void print(int i)
  {
    Writer out = this.out;
    if (out == null)
      return;
    
    if (i == 0x80000000) {
      print("-2147483648");
      return;
    }

    try {
      if (i < 0) {
        out.write('-');
        i = -i;
      } else if (i < 9) {
        out.write('0' + i);
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

      out.write(_tempCharBuffer, j, 31 - j);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  /**
   * Prints a long.
   */
  final public void print(long v)
  {
    Writer out = this.out;
    if (out == null)
      return;
    
    if (v == 0x8000000000000000L) {
      print("-9223372036854775808");
      return;
    }

    try {
      if (v < 0) {
        out.write('-');
        v = -v;
      } else if (v == 0) {
        out.write('0');
        return;
      }

      int j = 31;
    
      while (v > 0) {
        _tempCharBuffer[--j] = (char) ((v % 10) + '0');
        v /= 10;
      }

      out.write(_tempCharBuffer, j, 31 - j);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  /**
   * Prints a double followed by a newline.
   *
   * @param v the value to print
   */
  final public void print(float v)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      String s = String.valueOf(v);
      out.write(s, 0, s.length());
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  /**
   * Prints a double followed by a newline.
   *
   * @param v the value to print
   */
  final public void print(double v)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      String s = String.valueOf(v);
      out.write(s, 0, s.length());
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints a character array
   */
  final public void print(char []s)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      out.write(s, 0, s.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints a string.
   */
  final public void print(String s)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      if (s == null)
        out.write(_nullChars, 0, _nullChars.length);
      else
        out.write(s, 0, s.length());
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints the value of the object.
   */
  final public void print(Object v)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      if (v == null)
        out.write(_nullChars, 0, _nullChars.length);
      else {
        String s = v.toString();
      
        out.write(s, 0, s.length());
      }
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints the newline.
   */
  final public void println()
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      out.write(_newline, 0, _newline.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  /**
   * Prints the boolean followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(boolean v)
  {
    Writer out = this.out;
    if (out == null)
      return;

    print(v);

    try {
      out.write(_newline, 0, _newline.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints a character followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(char v)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      out.write(v);
      out.write(_newline, 0, _newline.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  /**
   * Prints an integer followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(int v)
  {
    Writer out = this.out;
    if (out == null)
      return;

    print(v);

    try {
      out.write(_newline, 0, _newline.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  /**
   * Prints a long followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(long v)
  {
    Writer out = this.out;
    if (out == null)
      return;

    print(v);

    try {
      out.write(_newline, 0, _newline.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  /**
   * Prints a float followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(float v)
  {
    Writer out = this.out;
    if (out == null)
      return;

    String s = String.valueOf(v);
    
    try {
      out.write(s, 0, s.length());
      out.write(_newline, 0, _newline.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  /**
   * Prints a double followed by a newline.
   *
   * @param v the value to print
   */
  final public void println(double v)
  {
    Writer out = this.out;
    if (out == null)
      return;

    print(v);
    
    try {
      out.write(_newline, 0, _newline.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Writes a character array followed by a newline.
   */
  final public void println(char []s)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      out.write(s, 0, s.length);
      out.write(_newline, 0, _newline.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Writes a string followed by a newline.
   */
  final public void println(String s)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      if (s == null)
        out.write(_nullChars, 0, _nullChars.length);
      else
        out.write(s, 0, s.length());

      out.write(_newline, 0, _newline.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  /**
   * Writes an object followed by a newline.
   */
  final public void println(Object v)
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      if (v == null)
        out.write(_nullChars, 0, _nullChars.length);
      else {
        String s = v.toString();

        out.write(s, 0, s.length());
      }
    
      out.write(_newline, 0, _newline.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Flushes the writer.
   */
  public void flush()
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      out.flush();
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Flushes the writer.
   */
  public void flushBuffer()
  {
    Writer out = this.out;
    if (out == null)
      return;

    try {
      if (out instanceof FlushBuffer)
        ((FlushBuffer) out).flushBuffer();
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  public void close()
  {
    this.out = null;
  }
}
