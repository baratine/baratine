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
import java.util.function.Function;

import com.caucho.v5.h3.QueryBuilderH3.PathBuilderH3;

/**
 * Query based on SQL.
 */
public class ExprBuilderKelp
{
  public ExprBuilderKelp eq()
  {
    return new CmpBuilder(BinaryOpKelp.EQ, this);
  }

  public ExprBuilderKelp op(BinaryOpKelp op, ExprBuilderKelp right)
  {
    return new BinaryExprBuilder(op, this, right);
  }

  public ExprBuilderKelp op(UnaryOpKelp op)
  {
    return new UnaryExprBuilder(op, this);
  }

  public ExprBuilderKelp between(ExprBuilderKelp min, ExprBuilderKelp max)
  {
    return new BetweenExprBuilder(this, min, max);
  }
  
  public ExprBuilderKelp field(String name)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  public ExprBuilderKelp fun(Function<Object,Object> fun)
  {
    Objects.requireNonNull(fun);
    
    return new FunExprBuilder(this, fun);
  }
  
  protected ExprKelp buildValue(QueryBuilder builder)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public ExprKelp build(QueryBuilder builder)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public PathMapHessian buildPathMap(QueryBuilder builder)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  protected String getPathName()
  {
    return null;
  }

  protected PathHessian buildPath(QueryBuilder builder)
  {
    return null;
  }

  public PathMapHessian getPathMap()
  {
    return null;
  }

  protected PathHessian buildParentPath(QueryBuilder builder)
  {
    return null;
  }

  public PathBuilderH3 buildPathH3(QueryBuilder builder)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public PathBuilderH3 pathH3(String name, QueryBuilder builder)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
