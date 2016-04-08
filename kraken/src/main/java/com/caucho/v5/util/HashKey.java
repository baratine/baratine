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

import java.io.Serializable;

/**
 * Creates hashes for the identifiers.
 */
@SuppressWarnings("serial")
public class HashKey implements Serializable {
  private final byte []_hash;
  
  /**
   * Creates the manager
   */
  public HashKey(byte []hash)
  {
    if (hash == null)
      throw new NullPointerException();
    
    _hash = hash;
  }
  
  public static HashKey create(byte []hash)
  {
    if (hash != null)
      return new HashKey(hash);
    else
      return null;
  }

  public static byte[] getHash(HashKey testValue)
  {
    return testValue != null ? testValue.getHash() : null;
  }

  public byte []getHash()
  {
    return _hash;
  }
  
  public boolean isNull()
  {
    byte []hash = _hash;
    int length = hash.length;
    
    for (int i = length - 1; i >= 0; i--) {
      if (hash[i] != 0)
        return false;
    }
    
    return true;
  }
  
  public boolean isAny()
  {
    byte []hash = _hash;
    int length = hash.length;
    
    for (int i = length - 1; i >= 0; i--) {
      if (hash[i] != (byte) 0xff)
        return false;
    }
    
    return true;
  }

  public static String toString(byte []hash)
  {
    if (hash == null)
      return "null";
    
    return toString(hash, hash.length);
  }
  
  public static String toString(byte []hash, int len)
  {
    if (hash == null)
      return "null";
    
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < len; i++) {
      int d1 = (hash[i] >> 4) & 0xf;
      int d2 = (hash[i]) & 0xf;

      if (d1 < 10)
        sb.append((char) ('0' + d1));
      else
        sb.append((char) ('a' + d1 - 10));

      if (d2 < 10)
        sb.append((char) ('0' + d2));
      else
        sb.append((char) ('a' + d2 - 10));
    }

    return sb.toString();
  }

  /**
   * Calculates the key's hash
   */
  @Override
  public int hashCode()
  {
    byte []buf = _hash;
    
    return ((buf[0] << 24)
           + (buf[1] << 16)
           + (buf[2] << 8)
           + (buf[3]));
  }

  /**
   * Check for equality
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    else if (! (o instanceof HashKey))
      return false;

    HashKey key = (HashKey) o;

    byte []hashA = _hash;
    byte []hashB = key._hash;

    int len = hashA.length;

    if (len != hashB.length)
      return false;

    for (int i = 0; i < len; i++) {
      if (hashA[i] != hashB[i])
        return false;
    }

    return true;
  }

  /**
   * Check for equality
   */
  public static boolean equals(byte []hashA, byte []hashB)
  {
    if (hashA == hashB)
      return true;
    else if (hashA == null || hashB == null)
      return false;

    int len = hashA.length;

    if (len != hashB.length)
      return false;

    for (int i = len - 1; i >= 0; i--) {
      if (hashA[i] != hashB[i])
        return false;
    }

    return true;
  }

  public int compareTo(HashKey key)
  {
    if (key == null)
      return 1;

    byte []hashA = _hash;
    byte []hashB = key._hash;

    int len = hashA.length;

    for (int i = 0; i < len; i++) {
      int delta = hashA[i] - hashB[i];

      if (delta != 0)
        return delta;
    }

    return 0;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + toString(_hash, 4) + "]";
  }
}
