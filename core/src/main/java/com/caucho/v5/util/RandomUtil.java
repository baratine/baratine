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

import java.security.SecureRandom;
import java.util.Random;

/**
 * System-wide random number generator.
 */
public class RandomUtil {
  private static FreeList<Random> _freeRandomList
    = new FreeList<Random>(64);
  
  private static Random _testRandom;
  
  private static boolean _isTest;

  /**
   * Returns the next random long.
   */
  public static long getRandomLong()
  {
    Random random = getRandom();

    long value = random.nextLong();

    if (! _isTest) {
      _freeRandomList.free(random);
    }

    return value;
  }

  /**
   * Returns the next random int.
   */
  public static int nextInt(int n)
  {
    Random random = getRandom();

    int value = random.nextInt(n);

    if (! _isTest)
      _freeRandomList.free(random);

    return value;
  }

  /**
   * Returns the next random double between 0 and 1
   */
  public static double nextDouble()
  {
    Random random = getRandom();

    double value = random.nextDouble();

    if (! _isTest)
      _freeRandomList.free(random);

    return value;
  }

  /**
   * Returns the random generator.
   */
  private static Random getRandom()
  {
    if (_isTest) {
      return _testRandom;
    }

    Random random = _freeRandomList.allocate();

    if (random == null)
      random = new SecureRandom();

    return random;
  }

  /**
   * Sets the specific seed.  Only for testing.
   */
  public static void setTestSeed(long seed)
  {
    _isTest = true;
    _testRandom = new Random(seed);
  }
}
