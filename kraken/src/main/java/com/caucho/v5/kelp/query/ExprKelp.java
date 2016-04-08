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

package com.caucho.v5.kelp.query;

import io.baratine.db.BlobReader;

import java.io.InputStream;

import com.caucho.v5.util.L10N;

/**
 * Building a program for hessian.
 */
public class ExprKelp
{
  private static final L10N L = new L10N(ExprKelp.class);
  
  public Object eval(EnvKelp cxt)
  {
    return null;
  }
  
  public boolean evalBoolean(EnvKelp cxt)
  {
    return false;
  }

  public int evalInt(EnvKelp env)
  {
    Object value = eval(env);
    
    if (value instanceof Number) {
      Number number = (Number) value;
      
      return number.intValue();
    }
    else if (value == null) {
      return 0;
    }
    else {
      throw new UnsupportedOperationException(L.l("Unexpected value {0} for {1}",
                                                  value, this));
    }
  }

  public long evalLong(EnvKelp env)
  {
    Object value = eval(env);
    
    if (value instanceof Number) {
      Number number = (Number) value;
      
      return number.longValue();
    }
    else if (value == null) {
      return 0;
    }
    else {
      throw new UnsupportedOperationException(L.l("Unexpected value {0} for {1}",
                                                  value, this));
    }
  }

  public double evalDouble(EnvKelp env)
  {
    Object value = eval(env);
    
    if (value instanceof Number) {
      Number number = (Number) value;
      
      return number.doubleValue();
    }
    else if (value == null) {
      return 0;
    }
    else {
      throw new UnsupportedOperationException(L.l("Unexpected value {0} for {1}",
                                                  value, this));
    }
  }

  public String evalString(EnvKelp env)
  {
    Object value = eval(env);
    
    return String.valueOf(value);
  }

  public Object evalObject(EnvKelp env)
  {
    return eval(env);
  }

  public byte[] evalBytes(EnvKelp env)
  {
    return (byte[]) eval(env);
  }

  public InputStream evalInputStream(EnvKelp env)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public BlobReader evalBlobReader(EnvKelp env)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
