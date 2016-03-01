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

/**
 * Fnv128 hash
 */
public class Fnv128
{
  private static int SEED_HASH[];
  
  private final byte _data[] = new byte[1];
  private final int _hash[] = new int[4];
  
  public Fnv128 init()
  {
    System.arraycopy(SEED_HASH, 0, _hash, 0, _hash.length);
    
    return this;
  }
  
  public Fnv128 update(byte value)
  {
    byte []data = _data;
    data[0] = value;
    
    update(data, 0, 1);
    
    return this;
  }
  
  public Fnv128 update(byte []buffer)
  {
    update(buffer, 0, buffer.length);
    
    return this;
  }
  
  public Fnv128 update(String value)
  {
    for (int i = 0; i < value.length(); i++) {
      int ch = value.charAt(i);
      
      update((byte) ch);
      
      if (ch > 0xff) {
        update((byte) (ch >> 8));
      }
    }
    
    return this;
  }
  
  public Fnv128 updateInt32(int value)
  {
    update((byte) (value >> 24));
    update((byte) (value >> 16));
    update((byte) (value >> 8));
    update((byte) (value >> 0));
    
    return this;
  }
  
  public Fnv128 update(byte []buffer, int offset, int length)
  {
    int end = offset + length;
    
    int hash[] = _hash;
    
    long intMask = 0xffff_ffffL;
    
    long a0 = hash[0] & intMask;
    long a1 = hash[1] & intMask;
    long a2 = hash[2] & intMask;
    long a3 = hash[3] & intMask;
    
    long primeTail = 0x13b;
    
    for (; offset < end; offset++) {
      a0 = a0 ^ (buffer[offset] & 0xffL);
      
      // prime = 2^88 + 0x100 + 0x3b
      long c2 = ((a0 << 24) & intMask);
      long c3 = ((a1 << 8) & intMask) | (a0 >> 8);
      
      long v = a0 * primeTail;
      a0 = v & intMask;
      v >>= 32;
      
      v += a1 * primeTail;
      a1 = v & intMask;
      v >>= 32;
    
      v += c2 + a2 * primeTail;
      a2 = v & intMask;
      v >>= 32;
    
      v += c3 + a3 * primeTail;
      a3 = v & intMask;
    }
    
    hash[0] = (int) a0;
    hash[1] = (int) a1;
    hash[2] = (int) a2;
    hash[3] = (int) a3;
    
    return this;
  }
  
  public byte []getDigest()
  {
    byte []digest = new byte[16];
    
    digest(digest, 0, 16);
    
    return digest;
  }
  
  public void digest(byte []digest, int offset, int length)
  {
    int []hash = _hash;
    
    BitsUtil.writeInt(digest, offset + 0, hash[0]);
    BitsUtil.writeInt(digest, offset + 4, hash[1]);
    BitsUtil.writeInt(digest, offset + 8, hash[2]);
    BitsUtil.writeInt(digest, offset + 12, hash[3]);
  }
  
  static {
    Fnv128 initFnv = new Fnv128();
    
    String initString = "The quick brown fox jumped over the lazy dog.";
    
    initFnv.update(initString.getBytes());
    
    SEED_HASH = initFnv._hash;
  }
}
