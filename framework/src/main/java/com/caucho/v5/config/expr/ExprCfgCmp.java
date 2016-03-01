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

package com.caucho.v5.config.expr;

import java.util.function.Function;

/**
 * ?: null conditional expression
 */
public class ExprCfgCmp implements ExprCfg
{
  private final ExprCfg _left;
  private final ExprCfg _right;
  
  ExprCfgCmp(ExprCfg left, 
             ExprCfg right)
  {
    _left = left;
    _right = right;
  }
  
  @Override
  public boolean evalBoolean(Function<String,Object> env)
  {
    Object left = _left.eval(env);
    Object right = _right.eval(env);
    
    if (left == right) {
      return true;
    }
    else if (left == null || right == null) {
      return false;
    }
    else {
      return left.equals(right);
    }
  }

  @Override
  public Object eval(Function<String, Object> env)
  {
    return Boolean.valueOf(evalBoolean(env));
  }
  
  @Override
  public String toString()
  {
    return _left.toString() + "==" + _right.toString();
  }
}

