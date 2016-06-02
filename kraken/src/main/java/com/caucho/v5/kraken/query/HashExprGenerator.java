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
import java.util.HashSet;
import java.util.Objects;

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kraken.query.TableBuilderKraken.Col;
import com.caucho.v5.kraken.table.PodHashGenerator;
import com.caucho.v5.kraken.table.TablePod;
import com.caucho.v5.util.L10N;

/**
 * Builds a hash expression
 */
public class HashExprGenerator implements PodHashGenerator
{
  private static final L10N L = new L10N(HashExprGenerator.class);
  
  static final String HASH_COLUMN = ":hash";
  
  private TableBuilderKraken _factory;
  
  private ArrayList<HashExpr> _exprList = new ArrayList<>();
  private HashExpr []_exprArray;

  private TableKelp _table;

  private Column _columnHash;

  public HashExprGenerator(TableBuilderKraken factory)
  {
    _factory = factory;
  }

  HashExprGenerator literal(String value)
  {
    _exprList.add(new HashExprLiteral(value));
    
    return this;
  }
  
  HashExprGenerator column(String name)
  {
    if (! _factory.isColumnPresent(name)) {
      throw new RuntimeException(L.l("Unknown column '{0}' for hash.", name));
    }
    
    _exprList.add(new HashExprColumn(name));
    
    return this;
  }

  public boolean isSyntheticHash(TableBuilderKraken tableBuilder)
  {
    for (HashExpr expr : _exprList) {
      if (expr.isSyntheticHash(tableBuilder)) {
        return true;
      }
    }
    
    return false;
  }
  
  PodHashGenerator build(TableKelp tableKelp)
  {
    _table = tableKelp;
    
    _exprArray = new HashExpr[_exprList.size()];
    
    for (int i = 0; i < _exprList.size(); i++) {
      _exprArray[i] = _exprList.get(i).build(tableKelp);
    }
    
    _columnHash = tableKelp.getColumn(HASH_COLUMN);
    
    return this;
  }

  @Override
  public int getPodHash(byte[] buffer, int keyOffset, int keyLength,
                        TablePod tablePod)
  {
    if (_columnHash != null) {
      int hashOffset = keyOffset + keyLength - 2;
      
      int hash = ((buffer[hashOffset] & 0xff) * 256
                  + (buffer[hashOffset + 1] & 0xff));
      
      return hash;
    }
    
    int offset = keyOffset - _table.getKeyOffset();
    
    StringBuilder sb = new StringBuilder();
    
    for (HashExpr expr : _exprArray) {
      expr.buildKey(sb, buffer, offset);
    }
    
    return PodHashGenerator.Base.getPodHash(sb);
  }

  @Override
  public ExprKraken buildInsertExpr(ArrayList<Column> columns,
                                    ArrayList<ExprKraken> values)
  {
    ExprKraken []exprs = new ExprKraken[_exprArray.length];
    
    for (int i = 0; i < exprs.length; i++) {
      String name = _exprArray[i].getName();
      
      if (name != null) {
        int index = findColumn(columns, name);
        
        if (index >= 0) {
          exprs[i] = values.get(index);
        }
      }
    }
    
    return new HashExprKraken(_exprArray, exprs);
  }
  
  private int findColumn(ArrayList<Column> columns, String name)
  {
    for (int i = 0; i < columns.size(); i++) {
      Column column = columns.get(i);
      
      if (column.name().equals(name)) {
        return i;
      }
    }
    
    return -1;
  }
  
  @Override
  public ExprKraken buildKeyExpr(ExprKraken keyExpr, HashSet<String> keys)
  {
    ExprKraken []idExprs = new ExprKraken[_exprArray.length];
    
    for (int i = 0; i < idExprs.length; i++) {
      HashExpr expr = _exprArray[i];
      
      String name = expr.getName();
      
      if (name != null) {
        ExprKraken subKeyExpr = keyExpr.getKeyExpr(name);
    
        if (subKeyExpr == null) {
          return keyExpr;
        }
        
        idExprs[i] = subKeyExpr;
      }
    }

    return new HashKeyExpr(keyExpr, idExprs, this);
  }

  public void fillCursor(RowCursor rowCursor, 
                         Object[] args,
                         ExprKraken[] keyExprs)
  {
    StringBuilder sb = new StringBuilder();
    
    for (int i = 0; i < _exprArray.length; i++) {
      HashExpr expr = _exprArray[i];
      ExprKraken keyExpr = keyExprs[i];
      
      if (keyExpr != null) {
        Object value = keyExpr.evalObject(rowCursor, args);
        
        sb.append(value);
      }
      else {
        expr.buildKey(sb, null, 0);
      }
    }
    
    int hash = PodHashGenerator.Base.getPodHash(sb);
    
    // int hashOffset = _columnHash.getOffset();
    
    rowCursor.setInt(_columnHash.index(), hash);
  }

  public void fillPodHash(RowCursor cursor)
  {
    if (_columnHash == null) {
      return;
    }

    /*
    int offset = keyOffset - _table.getKeyOffset();
    
    System.out.println("GPH: " + new String(buffer, keyOffset, keyLength));
    
    StringBuilder sb = new StringBuilder();
    
    for (HashExpr expr : _exprArray) {
      expr.buildKey(sb, buffer, offset);
    }
    
    String hashString = sb.toString();

    System.out.println("HS: " + hashString);
    
    return PodHashGenerator.Base.getPodHash(sb);
    */
    
    System.out.println("GLORK:");
  }
  
  abstract private static class HashExpr
  {
    HashExpr build(TableKelp table)
    {
      return this;
    }
    
    public String getName()
    {
      return null;
    }

    public boolean isSyntheticHash(TableBuilderKraken tableBuilder)
    {
      return false;
    }

    abstract void buildKey(StringBuilder sb, byte []buffer, int offset);
  }
  
  private static class HashExprLiteral extends HashExpr {
    private final String _value;
    
    HashExprLiteral(String value)
    {
      _value = value;
    }
    
    @Override
    void buildKey(StringBuilder sb, byte []buffer, int offset)
    {
      sb.append(_value);
    }
  }
  
  private static class HashExprColumn extends HashExpr {
    private final String _name;
    private Column _col;
    
    HashExprColumn(String name)
    {
      _name = name;
    }
    
    @Override
    public String getName()
    {
      return _name;
    }
    
    @Override
    public boolean isSyntheticHash(TableBuilderKraken tableBuilder)
    {
      Col col = tableBuilder.getColumn(_name);
      
      return col.isBlob();
    }
    
    @Override
    HashExpr build(TableKelp table)
    {
      _col = table.getColumn(_name);
      
      Objects.requireNonNull(_col);
      
      return this;
    }
    
    @Override
    void buildKey(StringBuilder sb, byte []buffer, int offset)
    {
      _col.buildHash(sb, buffer, offset);
    }
  }
  
  private class HashExprKraken extends ExprKraken {
    private HashExpr []_exprArray;
    private ExprKraken []_paramArray;
    
    HashExprKraken(HashExpr []exprArray, ExprKraken []paramArray)
    {
      _exprArray = exprArray;
      _paramArray = paramArray;
    }
    
    @Override
    public int evalInt(RowCursor cursor, Object []params)
    {
      StringBuilder sb = new StringBuilder();
      
      for (int i = 0; i < _exprArray.length; i++) {
        HashExpr hashExpr = _exprArray[i];
        ExprKraken paramExpr = _paramArray[i];
        
        if (paramExpr != null) {
          sb.append(paramExpr.evalObject(cursor, params));
        }
        else {
          hashExpr.buildKey(sb, null, 0);
        }
      }
      
      return PodHashGenerator.Base.getPodHash(sb);
    }
  }
}
