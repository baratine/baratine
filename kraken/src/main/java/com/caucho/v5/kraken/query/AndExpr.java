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

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.query.BinaryOpKelp;
import com.caucho.v5.kelp.query.ExprBuilderKelp;

public class AndExpr extends ExprKraken
{
  //private BinaryOp _op;
  private ArrayList<ExprKraken> _exprs = new ArrayList<>();

  public AndExpr()
  {
  }

  public AndExpr add(ExprKraken expr)
  {
    _exprs.add(expr);
    
    return this;
  }
  
  public ExprKraken getSingleExpr()
  {
    if (_exprs.size() == 1) {
      return _exprs.get(0);
    }
    
    return this;
  }
  
  //
  // bind
  //
  
  @Override
  public ExprKraken bind(QueryBuilderKraken builder)
  {
    AndExpr andExpr = new AndExpr();
    
    for (ExprKraken expr : _exprs) {
      andExpr.add(expr.bind(builder));
    }
    
    return andExpr;
  }
  
  @Override
  public ExprKraken bindKey(QueryBuilderKraken builder)
  {
    ArrayList<ExprKraken> keyExprs = new ArrayList<>();
    
    for (ExprKraken expr : _exprs) {
      ExprKraken keyExpr = expr.bindKey(builder);
      
      if (keyExpr != null) {
        keyExprs.add(keyExpr);
      }
    }
    
    if (keyExprs.size() == 0) {
      return null;
    }
    else if (keyExprs.size() == 1) {
      return keyExprs.get(0);
    }
    
    AndExpr andExpr = new AndExpr();
    
    for (ExprKraken keyExpr : keyExprs) {
      andExpr.add(keyExpr);
    }
    
    return andExpr; 
  }
  
  /**
   * Returns all the keys assigned statically, e.g. by param or literal.
   */
  @Override
  public void fillAssignedKeys(Set<String> keys)
  {
    TreeSet<String> subKeys = new TreeSet<>();
    
    for (ExprKraken expr : _exprs) {
      subKeys.clear();
      
      expr.fillAssignedKeys(subKeys);
      
      keys.addAll(subKeys);
    }
  }

  @Override
  public ExprBuilderKelp buildKelp(QueryBuilderKraken builder)
  {
    ExprBuilderKelp expr = _exprs.get(0).buildKelp(builder);
    
    for (int i = 1; i < _exprs.size(); i++) {
      ExprBuilderKelp right = _exprs.get(i).buildKelp(builder);
      
      expr = expr.op(BinaryOpKelp.AND, right);
    }
    
    return expr;
  }

  @Override
  public void fillCursor(RowCursor rowCursor, Object []args)
  {
    for (ExprKraken expr : _exprs) {
      expr.fillCursor(rowCursor, args);
    }
  }

  @Override
  public void fillMinCursor(RowCursor rowCursor, Object []args)
  {
    for (ExprKraken expr : _exprs) {
      expr.fillMinCursor(rowCursor, args);
    }
  }

  @Override
  public void fillMaxCursor(RowCursor rowCursor, Object []args)
  {
    for (ExprKraken expr : _exprs) {
      expr.fillMaxCursor(rowCursor, args);
    }
  }

  @Override
  public int evalInt(RowCursor cursor, Object []args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + _exprs;
  }
}
