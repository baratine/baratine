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

package com.caucho.v5.bytecode;

import com.caucho.v5.bytecode.cpool.ClassConstant;
import com.caucho.v5.bytecode.cpool.ConstantPool;
import com.caucho.v5.bytecode.cpool.Utf8Constant;
import com.caucho.v5.util.ByteArrayBuffer;
import com.caucho.v5.util.L10N;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Interface to the bytecode compiler.
 */
public class ByteCodeWriter {
  private static final L10N L = new L10N(ByteCodeWriter.class);
  
  private OutputStream _os;
  private JavaClass _javaClass;
  private ByteArrayBuffer _bb = new ByteArrayBuffer();

  public ByteCodeWriter(OutputStream os, JavaClass javaClass)
  {
    _os = os;
    _javaClass = javaClass;
  }

  /**
   * Returns the java class for the writer.
   */
  public JavaClass getJavaClass()
  {
    return _javaClass;
  }

  /**
   * Writes a class constant.
   */
  public void writeClass(String className)
    throws IOException
  {
    ConstantPool pool =  _javaClass.getConstantPool();
    ClassConstant classConst = pool.getClass(className);

    if (classConst != null)
      writeShort(classConst.getIndex());
    else
      writeShort(0);
  }

  /**
   * Writes a UTF8 constant.
   */
  public void writeUTF8Const(String value)
    throws IOException
  {
    ConstantPool pool =  _javaClass.getConstantPool();
    Utf8Constant entry = pool.getUTF8(value);

    if (entry != null)
      writeShort(entry.getIndex());
    else
      throw new NullPointerException(L.l("utf8 constant {0} does not exist", value));
  }

  /**
   * Writes a byte
   */
  public void write(int v)
    throws IOException
  {
    _os.write(v);
  }

  /**
   * Writes a buffer
   */
  public void write(byte []buffer, int offset, int length)
    throws IOException
  {
    _os.write(buffer, offset, length);
  }

  /**
   * Writes a short.
   */
  public void writeShort(int v)
    throws IOException
  {
    _os.write(v >> 8);
    _os.write(v);
  }

  /**
   * Writes an int
   */
  public void writeInt(int v)
    throws IOException
  {
    _os.write(v >> 24);
    _os.write(v >> 16);
    _os.write(v >> 8);
    _os.write(v);
  }

  /**
   * Writes an int
   */
  public void writeLong(long v)
    throws IOException
  {
    _os.write((int) (v >> 56));
    _os.write((int) (v >> 48));
    _os.write((int) (v >> 40));
    _os.write((int) (v >> 32));
    
    _os.write((int) (v >> 24));
    _os.write((int) (v >> 16));
    _os.write((int) (v >> 8));
    _os.write((int) v);
  }

  /**
   * Writes a float
   */
  public void writeFloat(float v)
    throws IOException
  {
    int bits = Float.floatToIntBits(v);
    
    _os.write(bits >> 24);
    _os.write(bits >> 16);
    _os.write(bits >> 8);
    _os.write(bits);
  }

  /**
   * Writes a double
   */
  public void writeDouble(double v)
    throws IOException
  {
    long bits = Double.doubleToLongBits(v);
    
    _os.write((int) (bits >> 56));
    _os.write((int) (bits >> 48));
    _os.write((int) (bits >> 40));
    _os.write((int) (bits >> 32));
    
    _os.write((int) (bits >> 24));
    _os.write((int) (bits >> 16));
    _os.write((int) (bits >> 8));
    _os.write((int) bits);
  }

  /**
   * Writes UTF-8
   */
  public void writeUTF8(String value)
    throws IOException
  {
    writeUTF8(_bb, value);

    writeShort(_bb.size());
    _os.write(_bb.getBuffer(), 0, _bb.size());
  }

  /**
   * Writes UTF-8
   */
  public void writeIntUTF8(String value)
    throws IOException
  {
    writeUTF8(_bb, value);

    writeInt(_bb.size());
    _os.write(_bb.getBuffer(), 0, _bb.size());
  }

  /**
   * Writes UTF-8
   */
  public void writeUTF8(ByteArrayBuffer bb, String value)
  {
    bb.clear();

    for (int i = 0; i < value.length(); i++) {
      int ch = value.charAt(i);

      if (ch > 0 && ch < 0x80)
        bb.append(ch);
      else if (ch < 0x800) {
        bb.append(0xc0 + (ch >> 6));
        bb.append(0x80 + (ch & 0x3f));
      }
      else {
        bb.append(0xe0 + (ch >> 12));
        bb.append(0x80 + ((ch >> 6) & 0x3f));
        bb.append(0x80 + ((ch) & 0x3f));
      }
    }
  }
}
