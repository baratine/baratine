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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;

/**
 * Base64 decoding.
 */
public class Base64Util {
  private static final int _decode[];
  private static final char _encode[];
  
  private static final int _decodeUrl[];
  private static final char _encodeUrl[];

  /*
  public static void encode(StringBuilder cb, long data)
  {
    cb.append(Base64Util.encode(data >> 60));
    cb.append(Base64Util.encode(data >> 54));
    cb.append(Base64Util.encode(data >> 48));
    cb.append(Base64Util.encode(data >> 42));
    cb.append(Base64Util.encode(data >> 36));
    cb.append(Base64Util.encode(data >> 30));
    cb.append(Base64Util.encode(data >> 24));
    cb.append(Base64Util.encode(data >> 18));
    cb.append(Base64Util.encode(data >> 12));
    cb.append(Base64Util.encode(data >> 6));
    cb.append(Base64Util.encode(data));
  }
  */

  public static void encode(StringBuilder sb, long data)
  {
    for (int i = 58; i > 0; i -= 6) {
      sb.append(encode(data >> i));
    }

    sb.append(encode(data << 2));
  }

  public static void encode24(StringBuilder cb, int data)
  {
    cb.append(Base64Util.encode(data >> 18));
    cb.append(Base64Util.encode(data >> 12));
    cb.append(Base64Util.encode(data >> 6));
    cb.append(Base64Util.encode(data));
  }

  public static String encode(byte []buffer)
  {
    StringBuilder sb = new StringBuilder();
    
    encode(sb, buffer, 0, buffer.length);
    
    return sb.toString();
  }

  public static void encode(StringBuilder cb,
                            byte []buffer)
  {
    encode(cb, buffer, 0, buffer.length);
  }
  
  /*
  public static void encode(StringBuilder cb,
                            byte []buffer,
                            int offset, 
                            int length)
  {
    while (length >= 3) {
      int data = (buffer[offset] & 0xff) << 16;
      data += (buffer[offset + 1] & 0xff) << 8;
      data += (buffer[offset + 2] & 0xff);
      
      cb.append(Base64Util.encode(data >> 18));
      cb.append(Base64Util.encode(data >> 12));
      cb.append(Base64Util.encode(data >> 6));
      cb.append(Base64Util.encode(data));

      offset += 3;
      length -= 3;
    }

    if (length == 2) {
      int b1 = buffer[offset] & 0xff;
      int b2 = buffer[offset + 1] & 0xff;
      
      int data = (b1 << 16) + (b2 << 8);
      
      cb.append(Base64Util.encode(data >> 18));
      cb.append(Base64Util.encode(data >> 12));
      cb.append(Base64Util.encode(data >> 6));
      cb.append('=');
    }
    else if (length == 1) {
      int data = (buffer[offset] & 0xff) << 16;
      
      cb.append(Base64Util.encode(data >> 18));
      cb.append(Base64Util.encode(data >> 12));
      cb.append('=');
      cb.append('=');
    }
  }
  */

  public static void encode(StringBuilder cb, byte []buffer,
                            int offset, int length)
  {
    while (length >= 3) {
      int data = (buffer[offset] & 0xff) << 16;
      data += (buffer[offset + 1] & 0xff) << 8;
      data += (buffer[offset + 2] & 0xff);
      
      cb.append(Base64Util.encode(data >> 18));
      cb.append(Base64Util.encode(data >> 12));
      cb.append(Base64Util.encode(data >> 6));
      cb.append(Base64Util.encode(data));

      offset += 3;
      length -= 3;
    }

    if (length == 2) {
      int b1 = buffer[offset] & 0xff;
      int b2 = buffer[offset + 1] & 0xff;
      
      int data = (b1 << 16) + (b2 << 8);
      
      cb.append(Base64Util.encode(data >> 18));
      cb.append(Base64Util.encode(data >> 12));
      cb.append(Base64Util.encode(data >> 6));
      cb.append('=');
    }
    else if (length == 1) {
      int data = (buffer[offset] & 0xff) << 16;
      
      cb.append(Base64Util.encode(data >> 18));
      cb.append(Base64Util.encode(data >> 12));
      cb.append('=');
      cb.append('=');
    }
  }

  public static String encode(String value)
  {
    try {
      StringWriter sw = new StringWriter();
      encode(sw, new ByteArrayInputStream(value.getBytes("iso-8859-1")));
      return sw.toString();
    }
    catch (IOException e) {
      throw new RuntimeException("this should not be possible: " + e);
    }
  }

  public static String encodeFromByteArray(byte[] value)
  {
    try {
      StringWriter sw = new StringWriter();
      encode(sw, new ByteArrayInputStream(value));
      return sw.toString();
    }
    catch (IOException e) {
      throw new RuntimeException("this should not be possible " + e);
    }
  }

  public static String encodeFromByteArray(byte[] value,
                                           int offset,
                                           int length)
  {
    try {
      StringWriter sw = new StringWriter();
      encode(sw, new ByteArrayInputStream(value, offset, length));
      return sw.toString();
    }
    catch (IOException e) {
      throw new RuntimeException("this should not be possible " + e);
    }
  }

  public static void encode(Writer w, InputStream i)
    throws IOException
  {
    while(true) {
      int value1 = i.read();
      int value2 = i.read();
      int value3 = i.read();

      if (value3 >= 0) {
        long chunk = (value1 & 0xff);
        chunk = (chunk << 8) + (value2 & 0xff);
        chunk = (chunk << 8) + (value3 & 0xff);
        
        w.write(encode(chunk >> 18));
        w.write(encode(chunk >> 12));
        w.write(encode(chunk >> 6));
        w.write(encode(chunk));
        continue;
      }
    
      if (value2 >= 0) {
        long chunk = (value1 & 0xff);
        chunk = (chunk << 8) + (value2 & 0xff);
        chunk <<= 8;

        w.write(encode(chunk >> 18));
        w.write(encode(chunk >> 12));
        w.write(encode(chunk >> 6));
        w.write('=');
      }
      else if (value1 >= 0) {
        long chunk = (value1 & 0xff);
        chunk <<= 16;

        w.write(encode(chunk >> 18));
        w.write(encode(chunk >> 12));
        w.write('=');
        w.write('=');
      }
      break;
    }
    w.flush();
  }

  public static String decode(String value)
  {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      decode(new StringReader(value), baos);
      return new String(baos.toByteArray(), "iso-8859-1");
    }
    catch (IOException e) {
      throw new RuntimeException("this should not be possible: " + e);
    }
  }

  public static byte[] decodeToByteArray(String value)
  {
    try {
      if (value == null)
        return new byte[0];
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      decode(new StringReader(value), baos);
      return baos.toByteArray();
    }
    catch (IOException e) {
      throw new RuntimeException("this should not be possible: " + e);
    }
  }

  private static int readNonWhitespace(Reader r)
    throws IOException
  {
    while(true) {
      int ret = r.read();
      // skip whitespace
      switch (ret) {
      
      case 0xf0: case 0xf1: case 0xf2: case 0xf3:
      case 0xf4: case 0xf5: case 0xf6: case 0xf7:
      case 0xf8: case 0xf9: case 0xfa: case 0xfb:
      case 0xfc: case 0xfd: case 0xfe: case 0xff:
      case ' ': case '\n': case '\r': case '\t': 
      case '\f':
        break;
      default:
        return ret;
      }
    }
  }

  public static void decode(Reader r, OutputStream os)
    throws IOException
  {
    while (true) {
      int ch0 = readNonWhitespace(r);
      int ch1 = r.read();
      int ch2 = r.read();
      int ch3 = r.read();

      if (ch1 < 0)
        break;
      if (ch2 < 0)
        ch2 = '=';
      if (ch3 < 0)
        ch3 = '=';
      
      int chunk = ((_decode[ch0] << 18)
                   + (_decode[ch1] << 12)
                   + (_decode[ch2] << 6)
                   + (_decode[ch3]));
      
      os.write((byte) ((chunk >> 16) & 0xff));
      
      if (ch2 != '='  && ch2 != -1)
        os.write((byte) ((chunk >> 8) & 0xff));
      if (ch3 != '=' && ch3 != -1)
        os.write((byte) ((chunk & 0xff)));
      else
        break;
    }
    os.flush();
  }

  public static void decode(OutputStream os,
                            int ch0,
                            int ch1,
                            int ch2,
                            int ch3)
    throws IOException
  {
    if (ch1 < 0) {
      return;
    }
    
    if (ch2 < 0) {
      ch2 = '=';
    }
    
    if (ch3 < 0) {
      ch3 = '=';
    }
      
    int chunk = ((_decode[ch0] << 18)
                 + (_decode[ch1] << 12)
                 + (_decode[ch2] << 6)
                 + (_decode[ch3]));
      
    os.write((byte) ((chunk >> 16) & 0xff));
      
    if (ch2 != '=') {
      os.write((byte) ((chunk >> 8) & 0xff));
    }
    
    if (ch3 != '=') {
      os.write((byte) ((chunk & 0xff)));
    }
  }

  /**
   * XXX: decode() vs decodeIgnoreWhitespace(), check RFC
   */
  public static void decodeIgnoreWhitespace(Reader r, OutputStream os)
    throws IOException
  {
    while (true) {
      int ch0 = readNonWhitespace(r);
      int ch1 = readNonWhitespace(r);
      int ch2 = readNonWhitespace(r);
      int ch3 = readNonWhitespace(r);

      if (ch1 < 0)
        break;
      if (ch2 < 0)
        ch2 = '=';
      if (ch3 < 0)
        ch3 = '=';

      int chunk = ((_decode[ch0] << 18)
          + (_decode[ch1] << 12)
          + (_decode[ch2] << 6)
          + (_decode[ch3]));

      os.write((byte) ((chunk >> 16) & 0xff));

      if (ch2 != '='  && ch2 != -1)
        os.write((byte) ((chunk >> 8) & 0xff));
      if (ch3 != '=' && ch3 != -1)
        os.write((byte) ((chunk & 0xff)));
      else
        break;
    }
    os.flush();
  }

  public static long decodeUrlToLong(String v)
  {
    int size = v.length();
    
    if (size != 11) {
      throw new IllegalArgumentException(v);
    }
    
    long value = 0;
    
    for (int i = 0; i < size; i++) {
      int ch = v.charAt(i);
      
      if (ch > 0xff) {
        throw new IllegalArgumentException(v);
      }
      
      int digit = _decodeUrl[ch];
      
      if (digit < 0) {
        throw new IllegalArgumentException(v);
      }
      
      if (i < 10) {
        value = (value << 6) + digit;
      }
      else {
        value = (value << 2) + (digit & 0x3);
      }
    }
    
    return value;
  }

  public static char encode(long d)
  {
    return _encode[(int) (d & 0x3f)];
  }

  public static int decode(int d)
  {
    return _decode[d];
  }

  public static char encodeUrlDigit(long d)
  {
    return _encodeUrl[(int) (d & 0x3f)];
  }

  public static int decodeUrlDigit(int d)
  {
    return _decodeUrl[d];
  }

  static {
    _decode = new int[256];
    
    Arrays.fill(_decode, -1);
    
    for (int i = 'A'; i <= 'Z'; i++) {
      _decode[i] = i - 'A';
    }
    
    for (int i = 'a'; i <= 'z'; i++) {
      _decode[i] = i - 'a' + 26;
    }
    
    for (int i = '0'; i <= '9'; i++) {
      _decode[i] = i - '0' + 52;
    }
    
    _decode['+'] = 62;
    _decode['/'] = 63;
    
    _decode['='] = 0;
    
    _encode = new char[64];
    
    for (int ch = 'A'; ch <= 'Z'; ch++) {
      _encode[ch - 'A'] = (char) ch;
    }
    
    for (int ch = 'a'; ch <= 'z'; ch++) {
      _encode[ch - 'a' + 26] = (char) ch;
    }
    
    for (int ch = '0'; ch <= '9'; ch++) {
      _encode[ch - '0' + 52] = (char) ch;
    }

    _encode[62] = '+';
    _encode[63] = '/';
    
    _decodeUrl = new int[256];
    System.arraycopy(_decode, 0, _decodeUrl, 0, _decodeUrl.length);
    
    _encodeUrl = new char[64];
    System.arraycopy(_encode, 0, _encodeUrl, 0, _encodeUrl.length);
    
    _decodeUrl['-'] = 62;
    _decodeUrl['_'] = 63;
    
    _encodeUrl[62] = '-';
    _decodeUrl[63] = '_';
  }
}
