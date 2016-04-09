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

package com.caucho.v5.kraken.table;

import java.util.ArrayList;
import java.util.HashSet;

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kraken.query.ExprKraken;
import com.caucho.v5.util.Murmur64;


/**
 * Custom generator for hash functions
 */
public interface PodHashGenerator
{
  int getPodHash(byte[] buffer, int keyOffset, int keyLength, TablePod tablePod);

  ExprKraken buildInsertExpr(ArrayList<Column> columns,
                             ArrayList<ExprKraken> values);

  ExprKraken buildKeyExpr(ExprKraken keyExpr, HashSet<String> keys);
  
  public class Base implements PodHashGenerator
  {
    @Override
    public int getPodHash(byte []buffer, 
                          int keyOffset, 
                          int keyLength,
                          TablePod tablePod)
    {
      return getPodHash(buffer, keyOffset, keyLength);
    }

    @Override
    public ExprKraken buildInsertExpr(ArrayList<Column> columns,
                                      ArrayList<ExprKraken> values)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }
    
    @Override
    public ExprKraken buildKeyExpr(ExprKraken keyExpr, HashSet<String> keys)
    {
      return keyExpr;
    }
    
    public static int getPodHash(byte []buffer, int keyOffset, int keyLength)
    {
      long hash = Murmur64.generate(Murmur64.SEED, buffer, keyOffset, keyLength);

      return (int) (hash & 0xffff);
    }
    
    public static int getPodHash(CharSequence sb)
    {
      long hash = Murmur64.generate(Murmur64.SEED, sb);

      return (int) (hash & 0xffff);
    }
  }
}
