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

import java.util.Arrays;


/**
 * Query based on SQL.
 */
public enum BinaryOpKelp
{ 
  EQ {
    @Override
    public boolean evalBool(ExprKelp left, 
                        ExprKelp right,
                        EnvKelp cxt)
    {
      Object leftValue = left.eval(cxt);
      Object rightValue = right.eval(cxt);

      if (leftValue == null || rightValue == null) {
        return false;
      }
      
      if (leftValue instanceof Number && rightValue instanceof Number) {
        Number leftNumber = (Number) leftValue;
        Number rightNumber = (Number) rightValue;
        
        if (leftValue instanceof Double || rightValue instanceof Double) {
          return leftNumber.doubleValue() == rightNumber.doubleValue();
        }
        
        return leftNumber.longValue() == rightNumber.longValue();
      }
      
      if (leftValue instanceof byte[] && rightValue instanceof byte[]) {
        byte []leftBytes = (byte []) leftValue;
        byte []rightBytes = (byte []) rightValue;
        
        return Arrays.equals(leftBytes, rightBytes);
      }

      return leftValue.equals(rightValue);
    }
  },
  
  NE {
    @Override
    public boolean evalBool(ExprKelp left, 
                        ExprKelp right,
                        EnvKelp cxt)
    {
      Object leftValue = left.eval(cxt);
      Object rightValue = right.eval(cxt);
      
      if (leftValue == null || rightValue == null) {
        return false;
      }
    
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() != rightNum.longValue();
    }
  },
  
  LE {
    @Override
    public boolean evalBool(ExprKelp left, 
                        ExprKelp right,
                        EnvKelp cxt)
    {
      Object leftValue = left.eval(cxt);
      Object rightValue = right.eval(cxt);
      
      if (leftValue == null || rightValue == null) {
        return false;
      }
    
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() <= rightNum.longValue();
    }
  },
  
  GE {
    @Override
    public boolean evalBool(ExprKelp left, 
                        ExprKelp right,
                        EnvKelp cxt)
    {
      Object leftValue = left.eval(cxt);
      Object rightValue = right.eval(cxt);
      
      if (leftValue == null || rightValue == null) {
        return false;
      }
    
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() >= rightNum.longValue();
    }
  },
  
  LT {
    @Override
    public boolean evalBool(ExprKelp left, 
                        ExprKelp right,
                        EnvKelp cxt)
    {
      Object leftValue = left.eval(cxt);
      Object rightValue = right.eval(cxt);
      
      if (leftValue == null || rightValue == null) {
        return false;
      }
    
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      if (leftNum instanceof Double || rightNum instanceof Double) {
        return leftNum.doubleValue() < rightNum.doubleValue();
      }
      else {
        return leftNum.longValue() < rightNum.longValue();
      }
    }
  },
  
  GT {
    @Override
    public boolean evalBool(ExprKelp left, 
                        ExprKelp right,
                        EnvKelp cxt)
    {
      Object leftValue = left.eval(cxt);
      Object rightValue = right.eval(cxt);
      
      if (leftValue == null || rightValue == null) {
        return false;
      }
      
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      if (leftNum instanceof Double || rightNum instanceof Double) {
        return leftNum.doubleValue() > rightNum.doubleValue();
      }
      else {
        return leftNum.longValue() > rightNum.longValue();
      }
    }
  },
  
  ADD {
    @Override
    public Object eval(ExprKelp left, 
                       ExprKelp right,
                       EnvKelp cxt)
    {
      Object leftValue = left.eval(cxt);
      Object rightValue = right.eval(cxt);
      
      if (leftValue == null || rightValue == null) {
        return false;
      }
    
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() + rightNum.longValue();
    }
  },
  
  SUB {
    @Override
    public Object eval(ExprKelp left, 
                       ExprKelp right,
                       EnvKelp cxt)
    {
      Object leftValue = left.eval(cxt);
      Object rightValue = right.eval(cxt);
      
      if (leftValue == null || rightValue == null) {
        return false;
      }
    
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() - rightNum.longValue();
    }
  },
  
  MUL {
    @Override
    public Object eval(ExprKelp left, 
                       ExprKelp right,
                       EnvKelp cxt)
    {
      Object leftValue = left.eval(cxt);
      Object rightValue = right.eval(cxt);
      
      if (leftValue == null || rightValue == null) {
        return false;
      }
    
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() * rightNum.longValue();
    }
  },
  
  DIV {
    @Override
    public Object eval(ExprKelp left, 
                       ExprKelp right,
                       EnvKelp cxt)
    {
      Object leftValue = left.eval(cxt);
      Object rightValue = right.eval(cxt);
      
      if (leftValue == null || rightValue == null) {
        return false;
      }
    
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() / rightNum.longValue();
    }
  },
  
  MOD {
    @Override
    public Object eval(ExprKelp left, 
                       ExprKelp right,
                       EnvKelp cxt)
    {
      Object leftValue = left.eval(cxt);
      Object rightValue = right.eval(cxt);
      
      if (leftValue == null || rightValue == null) {
        return false;
      }
    
      Number leftNum = (Number) leftValue;
      Number rightNum = (Number) rightValue;
      
      return leftNum.longValue() % rightNum.longValue();
    }
  },
  
  AND {
    @Override
    public boolean evalBool(ExprKelp left, 
                        ExprKelp right,
                        EnvKelp cxt)
    {
      boolean leftValue = left.evalBoolean(cxt);

      if (! leftValue) {
        return false;
      }
      
      return right.evalBoolean(cxt);
    }
  },
  
  OR {
    @Override
    public boolean evalBool(ExprKelp left, 
                            ExprKelp right,
                            EnvKelp cxt)
    {
      boolean leftValue = left.evalBoolean(cxt);
      
      if (leftValue) {
        return true;
      }
      
      return right.evalBoolean(cxt);
    }
  }
  ;

  public boolean evalBool(ExprKelp left, ExprKelp right, EnvKelp cxt)
  {
    return false;
  }

  public Object eval(ExprKelp left, ExprKelp right, EnvKelp cxt)
  {
    return evalBool(left, right, cxt);
  }
}
