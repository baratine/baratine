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

import java.util.Objects;

import com.caucho.v5.kraken.query.FunExpr;

/**
 * Building a program for hessian.
 */
public class FunArgsExprKelp extends ExprKelp
{
  private final ExprKelp []_args;
  
  private final FunExpr _fun;
  
  FunArgsExprKelp(ExprKelp []args, FunExpr fun)
  {
    Objects.requireNonNull(args);
    Objects.requireNonNull(fun);
    
    _args = args;
    _fun = fun;
    
  }
  
  @Override
  public Object eval(EnvKelp cxt)
  {
    Object []args = new Object[_args.length];

    for (int i = 0; i < args.length; i++) {
      args[i] = _args[i].eval(cxt);
    }
    
    return _fun.apply(cxt, args);
  }
  
  @Override
  public boolean evalBoolean(EnvKelp cxt)
  {
    return Boolean.TRUE.equals(eval(cxt));
  }
 
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _fun + "]";
  }
}
