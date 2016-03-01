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

package com.caucho.v5.vfs;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A print writer which writes to a specific WriteStream.
 */
public class StreamPrintWriter extends PrintWriter
  implements FlushBuffer, EnclosedWriteStream {
  private final static Logger log
    = Logger.getLogger(PrintWriterImpl.class.getName());
  
  private final static char []_nullChars = "null".toCharArray();
  private final static char []_newline = "\n".toCharArray();

  private final static Writer _dummyWriter = new StringWriter();

  private final WriteStream _out;

  /**
   * Creates a new PrintWriterImpl
   */
  public StreamPrintWriter(WriteStream out)
  {
    super((Writer) _dummyWriter);

    _out = out;
  }

  /**
   * Writes a character.
   */
  final public void write(int ch)
  {
    try {
      _out.print((char) ch);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Writes a character.
   */
  final public void write(char []buf, int offset, int length)
  {
    try {
      _out.print(buf, offset, length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Writes a character buffer.
   */
  final public void write(char []buf)
  {
    try {
      _out.print(buf, 0, buf.length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Writes a string
   */
  final public void write(String v)
  {
    try {
      _out.print(v);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Writes a string
   */
  final public void write(String v, int offset, int length)
  {
    try {
      _out.print(v, offset, length);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints a character.
   */
  final public void print(char ch)
  {
    try {
      _out.print(ch);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints an integer.
   */
  final public void print(int v)
  {
    try {
      _out.print(v);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  /**
   * Prints a long.
   */
  final public void print(long v)
  {
    try {
      _out.print(v);
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
    try {
      _out.print(v);
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
    try {
      _out.print(v);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints a character array
   */
  final public void print(char []v)
  {
    try {
      _out.print(v);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints a string.
   */
  final public void print(String v)
  {
    try {
      _out.print(v);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints the value of the object.
   */
  final public void print(Object v)
  {
    try {
      _out.print(v);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Prints the newline.
   */
  final public void println()
  {
    try {
      _out.println();
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
    try {
      _out.println(v);
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
    try {
      _out.println(v);
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
    try {
      _out.println(v);
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
    try {
      _out.println(v);
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
    try {
      _out.println(v);
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
    try {
      _out.println(v);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Writes a character array followed by a newline.
   */
  final public void println(char []v)
  {
    try {
      _out.println(v);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Writes a string followed by a newline.
   */
  final public void println(String v)
  {
    try {
      _out.println(v);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  /**
   * Writes an object followed by a newline.
   */
  final public void println(Object v)
  {
    try {
      _out.println(v);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Flushes the writer.
   */
  public void flush()
  {
    try {
      _out.flush();
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Flushes the writer.
   */
  public void flushBuffer()
  {
    try {
      _out.flushBuffer();
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  public WriteStream getWriteStream()
  {
    return _out;
  }

  public void close()
  {
  }
}
