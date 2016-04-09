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

import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.util.L10N;


/**
 * A row for the log store.
 */
public class QueryParserKelp
{
  private static final L10N L = new L10N(QueryParserKelp.class);
  
  private TableKelp _table;
  
  private String _sql;
  private int _index;
  private int _length;
  private int _peek;

  private String _lexeme;

  private QueryBuilderKelp _rootBuilder;
  
  private ExprBuilderKelp _topBuilder;
  
  public QueryParserKelp(TableKelp table)
  {
    Objects.requireNonNull(table);
    
    _table = table;
    
    _rootBuilder = new QueryBuilderKelp(_table);
    
    _topBuilder = _rootBuilder.field("value");
  }
  
  public PredicateKelp parse(String sql)
  {
    _sql = sql;
    _index = 0;
    _length = sql.length();
    _peek = -1;
    
    ExprBuilderKelp exprBuilder = parseExpr();
    
    EnvKelp query = _rootBuilder.build(exprBuilder);
    
    return query;
  }
  
  private ExprBuilderKelp parseExpr()
  {
    ExprBuilderKelp left = parseTerm();
    
    while (true) {
      Token token = parseToken();
      switch (token) {
      case EQ:
        left = new BinaryExprBuilder(BinaryOpKelp.EQ, left, parseTerm());
        break;
        
      case EOF:
        return left;
        
      default:
        throw error("Unknown token {0}\n  {1}", token, _sql);
      }
    }
  }
  
  private ExprBuilderKelp parseTerm()
  {
    Token token = parseToken();
    
    switch (token) {
    case IDENTIFIER:
      return _topBuilder.field(_lexeme);
      
    case STRING:
      return new StringValueBuilder(_lexeme);
      
    default:
      throw error("Unexpected token {0}\n  {1}", token, _sql);
    }
  }
  
  private Token parseToken()
  {
    int ch = skipWhitespace();
    
    switch (ch) {
    case -1:
      return Token.EOF;
      
    case '=':
      return Token.EQ;
      
    case '\'':
      return parseString();
      
    default:
      if (Character.isJavaIdentifierStart(ch)) {
        return parseIdentifier(ch);
      }
      
      throw error("Unexpected character {0}\n  {1}",
                  (char) ch, _sql);
    }
  }
  
  private Token parseString()
  {
    StringBuilder sb = new StringBuilder();
    
    int ch;
    
    while ((ch = read()) >= 0 && ch != '\'') {
      sb.append((char) ch);
    }
    
    _lexeme = sb.toString();
    
    return Token.STRING;
  }
  
  private Token parseIdentifier(int ch)
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append((char) ch);
    
    while (Character.isJavaIdentifierPart((ch = read()))) {
      sb.append((char) ch);
    }
    
    _peek = ch;
    
    _lexeme = sb.toString();
    
    return Token.IDENTIFIER;
  }
  
  private int skipWhitespace()
  {
    int ch;
    
    while (Character.isWhitespace((ch = read()))) {
    }
    
    return ch;
  }
  
  private int read()
  {
    int peek = _peek;
    
    if (peek > 0) {
      _peek = -1;
      return peek;
    }
    
    if (_index < _length) {
      return _sql.charAt(_index++);
    }
    else {
      return -1;
    }
  }
  
  private RuntimeException error(String msg, Object ...args)
  {
    throw new RuntimeException(L.l(msg, args));
  }
  
  private enum Token {
    EOF,
    IDENTIFIER,
    STRING,
    
    DOT,
    EQ;
  }
}
