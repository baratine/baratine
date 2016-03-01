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

/**
 * Building a program for hessian.
 */
public class BetweenExprKelp extends ExprKelp
{
  private final ExprKelp _expr;
  private final ExprKelp _min;
  private final ExprKelp _max;
  
  BetweenExprKelp(ExprKelp expr, ExprKelp min, ExprKelp max)
  {
    _expr = expr;
    _min = min;
    _max = max;
  }
  
  @Override
  public boolean evalBoolean(EnvKelp cxt)
  {
    Object exprValue = _expr.eval(cxt);
    Object minValue = _min.eval(cxt);
    Object maxValue = _max.eval(cxt);
    
    long exprLong = ((Number) exprValue).longValue();
    long minLong = ((Number) minValue).longValue();
    long maxLong = ((Number) maxValue).longValue();
    
    return minLong <= exprLong && exprLong <= maxLong;
  }
  
  @Override
  public Object eval(EnvKelp cxt)
  {
    return evalBoolean(cxt);
  }
  

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _expr + "," + _min + "," + _max + "]";
  }
}
