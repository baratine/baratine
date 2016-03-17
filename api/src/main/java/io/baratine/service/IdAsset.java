/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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
 * @author Alex Rojkov
 */

package io.baratine.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

@SuppressWarnings("serial")
public final class IdAsset implements Serializable
{
  public static final int TIME_BITS = 34;
  
  private static final int _decode[];
  private static final char _encode[];
  
  private final long _id;
  
  private final transient String _address;
  
  public IdAsset(long assetId)
  {
    _id = assetId;
    
    _address = encode(assetId);
  }
  
  public IdAsset(String assetId)
  {
    this(decode(assetId));
  }
  
  public final long id()
  {
    return _id;
  }
  
  public final long millis()
  {
    return toMillis(id());
  }
  
  public final LocalDateTime dateTime()
  {
    return toDateTime(id());
  }
  
  public final long sequence()
  {
    return toSequence(id());
  }
  
  @Override
  public String toString()
  {
    return _address;
  }
  
  public static long toMillis(long id)
  {
    return (id >> (64 - TIME_BITS)) * 1000;
  }
  
  public static long toSeconds(long id)
  {
    return (id >> (64 - TIME_BITS));
  }
  
  public static long toSequence(long id)
  {
    return (id & ((1L << (64 - TIME_BITS)) - 1));
  }
  
  public static LocalDateTime toDateTime(long id)
  {
    return LocalDateTime.ofEpochSecond(toSeconds(id), 0, ZoneOffset.UTC);
  }
  
  public static IdAsset valueOf(String code)
  {
    return new IdAsset(decode(code));
  }
  
  public static String encode(long id)
  {
    StringBuilder sb = new StringBuilder();

    for (int i = 58; i > 0; i -= 6) {
      sb.append(encodeDigit(id >> i));
    }

    sb.append(encodeDigit(id << 2));
    
    return sb.toString();
  }

  public static long decode(String v)
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
      
      int digit = _decode[ch];
      
      if (digit < 0) {
        throw new IllegalArgumentException(v);
      }

      if (i < 10) {
        value = (value << 6) + digit;
      }
      else {
        value = (value << 4) + (digit >> 2);
      }
    }
    
    return value;
  }

  private static char encodeDigit(long d)
  {
    return _encode[(int) (d & 0x3f)];
  }
  
  static {
    _decode = new int[256];
    _encode = new char[64];
    
    Arrays.fill(_decode, -1);
    
    for (char ch = 'A'; ch <= 'Z'; ch++) {
      int i = (ch - 'A');
      
      _decode[ch] = i;
      _encode[i] = ch;
    }
    
    for (char ch = 'a'; ch <= 'z'; ch++) {
      int i = (ch - 'a' + 26);
      
      _decode[ch] = i;
      _encode[i] = ch;
    }
    
    for (char ch = '0'; ch <= '9'; ch++) {
      int i = (ch - '0' + 52);
      
      _decode[ch] = i;
      _encode[i] = ch;
    }
    
    _decode['-'] = 62;
    _encode[62] = '-';
    
    _decode['_'] = 63;
    _encode[63] = '_';
  }
}
