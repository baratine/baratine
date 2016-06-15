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
 * @author Alex Rojkov
 */

package com.caucho.v5.ramp.vault;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.L10N;

import io.baratine.service.Api;
import io.baratine.service.Result;

public class MethodParserVault<ID,T>
{
  private final static L10N L = new L10N(MethodParserVault.class);

  private static HashMap<String, Token> RESERVED;

  private final AssetInfo<ID,T> _entityInfo;
  private final Method _method;
  private final char[] _name;
  private int _parseIndex = 0;

  private String _lexeme;

  private VaultDriverDataImpl<ID,T> _driver;

  private Token _token;

  public MethodParserVault(VaultDriverDataImpl<ID,T> driver,
                              AssetInfo<ID,T> entityInfo,
                              Method method)
  {
    _driver = driver;
    _entityInfo = entityInfo;
    _method = method;
    _name = method.getName().toCharArray();
    
    if (! Modifier.isAbstract(method.getModifiers())) {
      throw new IllegalStateException(_method.toString());
    }
  }

  public <V> FindQueryVault<ID,T,V> parse()
  {
    if (_method.getAnnotation(Sql.class) != null) {
      return parseQuery();
    }
    
    if (_method.getName().startsWith("find")) {
      return parseFind();
    }
    else {
      return null;
    }
  }

  public <V> FindQueryVault<ID,T,V> parseQuery()
  {
    Sql query = _method.getAnnotation(Sql.class);

    Objects.requireNonNull(query);

    String where = query.value();

    return build(where);
  }

  private <V> FindQueryVault<ID,T,V> build(String where)
  {
    TypeRef resultType = resultType();
    Class<V> resultClass = (Class<V>) resultType.rawClass();

    FindQueryVault<ID,T,V> query;

    if (resultClass.isAssignableFrom(ArrayList.class)) {
      query = listResultQuery(where);
    }
    else if (resultClass.equals(_entityInfo.idType())) {
      query = new FindQueryVault.FindOneId(_driver, where);
    }
    else if (Modifier.isAbstract(resultClass.getModifiers())
             || resultClass.isAnnotationPresent(Api.class)) {
      query = new FindQueryVault.FindOneProxy<>(_driver, where, resultClass);
    }
    else {
      query = new FindQueryVault.FindOneBean<>(_driver, where, resultClass);
    }

    return query;
  }

  private <V> FindQueryVault<ID,T,V> listResultQuery(String where)
  {
    FindQueryVault<ID,T,V> query;

    TypeRef resultType = resultType().param(0);
    Class<?> resultClass = resultType.rawClass();

    if (resultClass.equals(_entityInfo.idType())) {
      query = new FindQueryVault.FindListIds(_driver, where);
    }
    else if (Modifier.isAbstract(resultClass.getModifiers())) {
      query = new FindQueryVault.FindListProxy(_driver, where, resultClass);
    }
    else {
      query = new FindQueryVault.FindListBean(_driver, where, resultClass);
    }

    return query;
  }

  /*
  private <V> FindQueryVault<ID,T,V> 
  createListResultFieldQuery(FieldInfo field, String where)
  {
    return new FindQueryVault.ListResultField<>(_driver,
                                               _entityInfo,
                                               field,
                                               where);
  }
  */

  /*
  private FindQueryVault createSingleResultQuery(String where)
  {
    FindQueryVault query = null;

    TypeRef resultType = resultType();

    if (resultType.rawClass().isAssignableFrom(_entityInfo.type())) {
      query = new FindQueryVault.ProxyResult(_driver, where);
    }
    else {
      FieldDesc field = _entityInfo.findFieldByType(resultType);

      query = createSingeResultFieldQuery(field, where);
    }

    return query;
  }

  private FindQueryVault createSingeResultFieldQuery(FieldDesc field,
                                                    String where)
  {
    return new FindQueryVault.SingleFieldResult(_driver,
                                               _entityInfo,
                                               field,
                                               where);
  }
  */

  private TypeRef resultType()
  {
    Type[] parameters = _method.getGenericParameterTypes();

    for (Type parameter : parameters) {
      TypeRef type = TypeRef.of(parameter);

      if (Result.class.equals(type.rawClass())) {
        TypeRef resultTypeRef = type.param(0);

        return resultTypeRef;
      }
    }

    return null;
  }

  private <V> FindQueryVault<ID,T,V> parseFind()
  {
    Token token = scanToken();

    while (token != Token.BY && token != Token.EOF) {
      token = scanToken();
    }

    String where = null;

    switch (token) {
    case BY: {
      ByExpressionBuilder by = parseBy();

      where = by.getWhere();
      
      break;
    }
    case EOF: {
      break;
    }
    default: {

    }
    }

    return build(where);
  }

  /**
   * Parse the "by" expression in the method name.
   */
  private ByExpressionBuilder parseBy()
  {
    ByExpressionBuilder by = new ByExpressionBuilder();

    int x = _parseIndex;

    Token token = scanToken();

    if (token == null)
      throw new IllegalStateException(L.l("expected field name at {0} in {1}",
                                          x,
                                          _method.getName()));

    do {
      switch (token) {
      case IDENTIFIER: {
        StringBuilder sb = new StringBuilder();
        sb.append(_lexeme);
        
        while (peekToken() == Token.IDENTIFIER) {
          token = scanToken();
          
          sb.append(_lexeme);
        }
        
        String term = fieldTerm(sb.toString());
        by.addField(term);

        break;
      }
      
      case AND: {
        by.addAnd();
        break;
      }
      
      case EQ:
      case NE:
      case LT:
      case LE:
      case GT:
      case GE:
        by.term(token);
        break;
        
      case GREATER: {
        if (peekToken() == Token.THAN) {
          scanToken();
          by.term(Token.GT);
        }
        else if (peekToken() == Token.EQ) {
          scanToken();
          by.term(Token.GE);
        }
        else {
          by.term(Token.GT);
        }
        break;
      }
        
      case LESS: {
        if (peekToken() == Token.THAN) {
          scanToken();
          by.term(Token.LT);
        }
        else if (peekToken() == Token.EQ) {
          scanToken();
          by.term(Token.LE);
        }
        else {
          by.term(Token.LT);
        }
        break;
      }
      
    case NOT: {
      if (peekToken() == Token.EQ) {
        scanToken();
        by.term(Token.NE);
      }
      else {
        by.term(Token.NE);
      }
      break;
    }
      
      case OR: {
        by.addOr();
        break;
      }
      default: {
        throw new IllegalStateException(_method.getName());
      }
      }
    }
    while ((token = scanToken()) != Token.EOF);

    return by;
  }

  private String fieldTerm(String fieldName)
  {
    fieldName = normalize(fieldName);
    
    FieldInfo<?,?> field = _entityInfo.field(fieldName);

    if (field != null) {
      return field.sqlTerm();
    }
    else {
      throw error("'{0}' is an unknown field in {1}", fieldName, _entityInfo);
    }
  }
  
  private IllegalArgumentException error(String msg, Object ...args)
  {
    return new IllegalArgumentException(L.l(msg, args));
  }
  
  private Token peekToken()
  {
    Token token = scanToken();
    
    _token = token;
    
    return token;
  }

  private Token scanToken()
  {
    Token token = _token;
    _token = null;
    
    if (token != null) {
      return token;
    }
    
    int ch = read();

    if (ch == -1) {
      return Token.EOF;
    }

    StringBuilder builder = new StringBuilder();

    builder.append((char) ch);

    for (ch = read(); ch > 0 && Character.isLowerCase(ch); ch = read()) {
      builder.append((char) ch);
    }
    
    if (ch > 0) {
      unread();
    }
    
    _lexeme = builder.toString();

    token = RESERVED.get(_lexeme.toLowerCase());
    
    if (token == null) {
      token = Token.IDENTIFIER;
      _lexeme = builder.toString();
    }

    return token;
  }

  int read()
  {
    if (_parseIndex < _name.length) {
      return _name[_parseIndex++];
    }
    else {
      return -1;
    }
  }

  void unread()
  {
    _parseIndex--;
  }

  private static String normalize(String field)
  {
    char ch = field.charAt(0);

    if (Character.isUpperCase(ch)) {
      return "" + Character.toLowerCase(ch) + field.substring(1);
    }
    else {
      return field;
    }
  }

  /*
  public static abstract class StoreQueryBuilder<ID,T>
  {
    public abstract <V> FindQueryVault<ID,T,V> build();
  }
  */
  
  static class ByTerm {
    private Token _token;
    private String _lexeme;
    
    ByTerm(Token token, String lexeme)
    {
      _token = token;
      _lexeme = lexeme;
    }
    
    void where(StringBuilder sb)
    {
      _token.where(sb, _lexeme);
    }

    public void term(Token token)
    {
      _token = _token.term(token);
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _token + "," + _lexeme + "]";
    }
  }

  static class ByExpressionBuilder
  {
    private List<ByTerm> _fields = new ArrayList<>();
    private String _where = null;
    private boolean _isConjunction = true;

    public void addAnd()
    {
      _fields.add(new ByTerm(Token.AND, ""));
      _isConjunction = true;
    }

    public void term(Token token)
    {
      ByTerm lastTerm = _fields.get(_fields.size() - 1);
      
      lastTerm.term(token);
    }

    public void addOr()
    {
      _fields.add(new ByTerm(Token.OR, ""));
      _isConjunction = true;
    }

    public void addField(String field)
    {
      Objects.requireNonNull(field);
      
      if (! _isConjunction) {
        _fields.add(new ByTerm(Token.AND, ""));
      }
      
      _isConjunction = false;
      
      field = normalize(field);
      
      _fields.add(new ByTerm(Token.IDENTIFIER, field));
    }
    
    public void eq()
    {
    }

    public String getWhere()
    {
      if (_where == null) {
        StringBuilder where = new StringBuilder(" where ");

        for (ByTerm term : _fields) {
          term.where(where);
        }

        return where.toString();
      }

      return _where;
    }

    @Override
    public String toString()
    {
      return ByExpressionBuilder.class.getSimpleName()
             + _fields;
    }

    enum Mode
    {
      AND,
      OR
    }
  }

  enum Token
  {
    AND {
      @Override
      public void where(StringBuilder sb, String lexeme)
      {
        sb.append(" and ");
      }
    },
    BY,
    EOF,
    EQ {
      @Override
      public void where(StringBuilder sb, String lexeme)
      {
        sb.append(lexeme).append("=?");
      }
    },
    FIND,
    GREATER,
    GT {
      @Override
      public void where(StringBuilder sb, String lexeme)
      {
        sb.append(lexeme).append(">?");
      }
    },
    GE {
      @Override
      public void where(StringBuilder sb, String lexeme)
      {
        sb.append(lexeme).append(">=?");
      }
    },
    IDENTIFIER {
      @Override
      public void where(StringBuilder sb, String lexeme)
      {
        sb.append(lexeme).append("=?");
      }

      @Override
      public Token term(Token token)
      {
        return token;
      }
    },
    LESS,
    LE {
      @Override
      public void where(StringBuilder sb, String lexeme)
      {
        sb.append(lexeme).append("<=?");
      }
    },
    LT {
      @Override
      public void where(StringBuilder sb, String lexeme)
      {
        sb.append(lexeme).append("<?");
      }
    },
    NE {
      @Override
      public void where(StringBuilder sb, String lexeme)
      {
        sb.append(lexeme).append("<>?");
      }
    },
    NOT,
    OR {
      @Override
      public void where(StringBuilder sb, String lexeme)
      {
        sb.append(" OR ");
      }
    },
    ORDER,
    THAN;

    public void where(StringBuilder sb, String lexeme)
    {
      throw new UnsupportedOperationException(toString());
    }

    public Token term(Token token)
    {
      throw new UnsupportedOperationException(toString() + " term " + token);
    }
  }

  static {
    RESERVED = new HashMap<String,Token>();
    
    RESERVED.put("and", Token.AND);
    RESERVED.put("by", Token.BY);
    RESERVED.put("equals", Token.EQ);
    RESERVED.put("eq", Token.EQ);
    RESERVED.put("ge", Token.GE);
    RESERVED.put("greater", Token.GREATER);
    RESERVED.put("gt", Token.GT);
    RESERVED.put("less", Token.LESS);
    RESERVED.put("le", Token.LE);
    RESERVED.put("lt", Token.LT);
    RESERVED.put("ne", Token.NE);
    RESERVED.put("neq", Token.NE);
    RESERVED.put("not", Token.NOT);
    RESERVED.put("than", Token.THAN);
  }
}
