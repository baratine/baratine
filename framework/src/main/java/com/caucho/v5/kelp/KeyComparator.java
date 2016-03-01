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
 * @author Scott Ferguson
 */

package com.caucho.v5.kelp;

import java.util.Comparator;

/**
 * A row for the log store.
 */
final class KeyComparator implements Comparator<byte[]> {
  public static final KeyComparator INSTANCE = new KeyComparator();
  
  private KeyComparator()
  {
  }

  @Override
  public final int compare(byte[] a, byte[] b)
  {
    final int aLen = a.length;

    for (int i = 0; i < aLen; i++) {
      int cmp = (a[i] & 0xff) - (b[i] & 0xff);

      if (cmp != 0) {
        return Integer.signum(cmp);
      }
    }

    return 0;
  }

  public final int compare(byte[] a, int aOffset,
                           byte[] b, int bOffset, 
                           int length)
  {
    for (int i = 0; i < length; i++) {
      int cmp = (a[i + aOffset] & 0xff) - (b[i + bOffset] & 0xff);

      if (cmp != 0) {
        return Integer.signum(cmp);
      }
    }

    return 0;
  }
}
