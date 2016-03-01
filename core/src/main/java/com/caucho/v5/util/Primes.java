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
 * Base64 decoding.
 */
public class Primes {
  public static final int []PRIMES = {
    1,       /* 1<< 0 = 1 */
    2,       /* 1<< 1 = 2 */
    3,       /* 1<< 2 = 4 */
    7,       /* 1<< 3 = 8 */
    13,      /* 1<< 4 = 16 */
    31,      /* 1<< 5 = 32 */
    61,      /* 1<< 6 = 64 */
    127,     /* 1<< 7 = 128 */
    251,     /* 1<< 8 = 256 */
    509,     /* 1<< 9 = 512 */
    1021,    /* 1<<10 = 1024 */
    2039,    /* 1<<11 = 2048 */
    4093,    /* 1<<12 = 4096 */
    8191,    /* 1<<13 = 8192 */
    16381,   /* 1<<14 = 16384 */
    32749,   /* 1<<15 = 32768 */
    65521,   /* 1<<16 = 65536 */
    131071,  /* 1<<17 = 131072 */
    262139,  /* 1<<18 = 262144 */
    524287,  /* 1<<19 = 524288 */
    1048573, /* 1<<20 = 1048576 */
    2097143, /* 1<<21 = 2097152 */
    4194301, /* 1<<22 = 4194304 */
    8388593, /* 1<<23 = 8388608 */
    16777213, /* 1<<24 = 16777216 */
    33554393, /* 1<<25 = 33554432 */
    67108859, /* 1<<26 = 67108864 */
    134217689, /* 1<<27 = 134217728 */
    268435399, /* 1<<28 = 268435456 */
  };

  public static int getBiggestPrime(int value)
  {
    for (int i = PRIMES.length - 1; i >= 0; i--) {
      if (PRIMES[i] <= value)
        return PRIMES[i];
    }

    return 2;
  }
}
