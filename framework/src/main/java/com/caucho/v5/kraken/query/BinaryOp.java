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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.kraken.query;

import com.caucho.v5.kelp.query.BinaryOpKelp;
import com.caucho.v5.kelp.query.ExprBuilderKelp;

public enum BinaryOp
{
  EQ {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.EQ, right);
    }
  },
  
  NE {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.NE, right);
    }
  },
  
  LT {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.LT, right);
    }
  },
  
  LE {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.LE, right);
    }
  },
  
  GT {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.GT, right);
    }
  },
  
  GE {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.GE, right);
    }
  },
  
  ADD {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.ADD, right);
    }

    @Override
    public long evalLong(Object leftValue, Object rightValue)
    {
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() + rightNum.longValue();
    }
  },
  
  SUB {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.SUB, right);
    }

    @Override
    public long evalLong(Object leftValue, Object rightValue)
    {
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() - rightNum.longValue();
    }
  },
  
  MUL {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.MUL, right);
    }

    @Override
    public long evalLong(Object leftValue, Object rightValue)
    {
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() * rightNum.longValue();
    }
  },
  
  DIV {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.DIV, right);
    }

    @Override
    public long evalLong(Object leftValue, Object rightValue)
    {
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() / rightNum.longValue();
    }
  },
  
  MOD {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.MOD, right);
    }

    @Override
    public long evalLong(Object leftValue, Object rightValue)
    {
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() % rightNum.longValue();
    }
  },
  
  OR {
    @Override
    public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                     ExprBuilderKelp right)
    {
      return left.op(BinaryOpKelp.OR, right);
    }
  },
  
  ;
  
  abstract public ExprBuilderKelp buildKelp(ExprBuilderKelp left,
                                            ExprBuilderKelp right);

  public long evalLong(Object leftValue, Object rightValue)
  {
    throw new UnsupportedOperationException(toString());
  }

  public long evalObject(Object leftValue, Object rightValue)
  {
    throw new UnsupportedOperationException(toString());
  }
}
