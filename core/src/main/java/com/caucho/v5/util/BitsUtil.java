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

package com.caucho.v5.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * bit/bytes utilities.
 */
public class BitsUtil
{
  public static int writeInt16(byte []buffer, int offset, int value)
  {
    buffer[0 + offset] = (byte) (value >> 8);
    buffer[1 + offset] = (byte) (value);
    
    return 2;
  }
  
  public static int readInt16(byte []buffer, int offset)
  {
    return (((buffer[0 + offset] & 0xff) << 8)
           + ((buffer[1 + offset] & 0xff)));
  }
  
  public static int writeInt(byte []buffer, int offset, int value)
  {
    buffer[0 + offset] = (byte) (value >> 24);
    buffer[1 + offset] = (byte) (value >> 16);
    buffer[2 + offset] = (byte) (value >> 8);
    buffer[3 + offset] = (byte) (value >> 0);
    
    return 4;
  }
  
  public static int readInt(byte []buffer, int offset)
  {
    return (((buffer[0 + offset] & 0xff) << 24)
           + ((buffer[1 + offset] & 0xff) << 16)
           + ((buffer[2 + offset] & 0xff) << 8)
           + ((buffer[3 + offset] & 0xff) << 0));
  }
  
  public static int writeLong(byte []buffer, int offset, long value)
  {
    buffer[0 + offset] = (byte) (value >> 56);
    buffer[1 + offset] = (byte) (value >> 48);
    buffer[2 + offset] = (byte) (value >> 40);
    buffer[3 + offset] = (byte) (value >> 32);
    buffer[4 + offset] = (byte) (value >> 24);
    buffer[5 + offset] = (byte) (value >> 16);
    buffer[6 + offset] = (byte) (value >> 8);
    buffer[7 + offset] = (byte) (value >> 0);
    
    return 8;
  }
  
  public static long readLong(byte []buffer, int offset)
  {
    return (((buffer[0 + offset] & 0xffL) << 56)
           + ((buffer[1 + offset] & 0xffL) << 48)
           + ((buffer[2 + offset] & 0xffL) << 40)
           + ((buffer[3 + offset] & 0xffL) << 32)
           + ((buffer[4 + offset] & 0xffL) << 24)
           + ((buffer[5 + offset] & 0xffL) << 16)
           + ((buffer[6 + offset] & 0xffL) << 8)
           + ((buffer[7 + offset] & 0xffL) << 0));
  }
  
  public static void writeLong(OutputStream os, long value)
    throws IOException
  {
    os.write((int) (value >> 56));
    os.write((int) (value >> 48));
    os.write((int) (value >> 40));
    os.write((int) (value >> 32));
    
    os.write((int) (value >> 24));
    os.write((int) (value >> 16));
    os.write((int) (value >> 8));
    os.write((int) (value));
  }
  
  public static void writeInt(OutputStream os, int value)
    throws IOException
  {
    os.write(value >> 24);
    os.write(value >> 16);
    os.write(value >> 8);
    os.write(value >> 0);
  }
  
  public static void writeInt16(OutputStream os, int value)
    throws IOException
  {
    os.write(value >> 8);
    os.write(value >> 0);
  }
  
  public static long readLong(InputStream is)
    throws IOException
  {
    long d0 = is.read();
    long d1 = is.read();
    long d2 = is.read();
    long d3 = is.read();
    
    long d4 = is.read();
    long d5 = is.read();
    long d6 = is.read();
    long d7 = is.read();
    
    if (d7 < 0) {
      return d7;
    }

    return ((d0 << 56)
           + (d1 << 48)
           + (d2 << 40)
           + (d3 << 32)
           + (d4 << 24)
           + (d5 << 16)
           + (d6 << 8)
           + (d7 << 0));
  }
  
  public static int readInt(InputStream is)
    throws IOException
  {
    int d0 = is.read();
    int d1 = is.read();
    int d2 = is.read();
    int d3 = is.read();
    
    if (d3 < 0) {
      return d3;
    }
    
    return ((d0 << 24)
           + (d1 << 16)
           + (d2 << 8)
           + (d3 << 0));
  }
  
  public static int readInt16(InputStream is)
    throws IOException
  {
    int d0 = is.read();
    int d1 = is.read();
    
    if (d1 < 0) {
      return d1;
    }
    
    return ((d0 << 8)
           + (d1 << 0));
  }
  
  public static int write(byte []buffer, int offset, byte []data)
  {
    System.arraycopy(data, 0, buffer, offset, data.length);
    
    return data.length;
  }
}
