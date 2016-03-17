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

package com.caucho.v5.vault;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.L10N;

import io.baratine.service.Api;
import io.baratine.service.Result;

public class MethodParserVault<ID,T>
{
  private final static L10N L = new L10N(MethodParserVault.class);

  private final EntityInfo<ID,T> _entityInfo;
  private final Method _method;
  private final char[] _name;
  private int _parseIndex = 0;

  private String _lexeme;

  private VaultDriverDataImpl<ID,T> _driver;

  public MethodParserVault(VaultDriverDataImpl<ID,T> driver,
                              EntityInfo<ID,T> entityInfo,
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

    Token token = scanToken();

    switch (token) {
    case FIND: {
      return parseFind();
    }
    default: {
      //throw new IllegalArgumentException(_method.getName());
      return null;
    }
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

    FindQueryVault query;

    if (resultClass.isAssignableFrom(ArrayList.class)) {
      query = listResultQuery(where);
    }
    else if (resultClass.equals(_entityInfo.idType())) {
      query = new FindQueryVault.KeyResult(_driver, where);
    }
    else if (Modifier.isAbstract(resultClass.getModifiers())
             || resultClass.isAnnotationPresent(Api.class)) {
      query = new FindQueryVault.ProxyResult(_driver, where, resultClass);
    }
    else {
      query = new FindQueryVault.DataResult(_driver, where, resultClass);
    }

    return query;
  }

  private FindQueryVault listResultQuery(String where)
  {
    FindQueryVault query;

    TypeRef resultType = resultType().param(0);
    Class<?> resultClass = resultType.rawClass();

    if (resultClass.equals(_entityInfo.idType())) {
      query = new FindQueryVault.ListKeyResult(_driver, where);
    }
    else if (Modifier.isAbstract(resultClass.getModifiers())) {
      query = new FindQueryVault.ListProxyResult(_driver, where, resultClass);
    }
    else {
      query = new FindQueryVault.ListDataResult(_driver, where, resultClass);
    }

    return query;
  }

  private FindQueryVault createListResultFieldQuery(FieldInfo field, String where)
  {
    return new FindQueryVault.ListResultField(_driver,
                                             _entityInfo,
                                             field,
                                             where);
  }

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

  private FindQueryVault parseFind()
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

  private ByExpressionBuilder parseBy()
  {
    ByExpressionBuilder by = new ByExpressionBuilder();

    int x = _parseIndex;

    Token token = scanToken();

    if (token == null)
      throw new IllegalStateException(L.l("expected field name at {0} in {1}",
                                          x,
                                          _method.getName()));

    StringBuilder sb = null;

    do {
      switch (token) {
      case IDENTIFIER: {
        if (sb == null)
          sb = new StringBuilder();

        sb.append(_lexeme);

        break;
      }
      case AND: {
        by.setAnd();

        by.addField(fieldTerm(sb.toString()));

        sb = null;

        break;
      }
      case OR: {
        by.setOr();

        by.addField(fieldTerm(sb.toString()));

        sb = null;

        break;
      }
      default: {
        throw new IllegalStateException();
      }
      }
    }
    while ((token = scanToken()) != Token.EOF);

    if (token == Token.EOF) {
      by.addField(fieldTerm(sb.toString()));
    }

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

  private Token scanToken()
  {
    int ch = read();

    if (ch == -1)
      return Token.EOF;

    StringBuilder builder = new StringBuilder();

    builder.append((char) ch);

    for (ch = read(); ch > 0 && Character.isLowerCase(ch); ch = read())
      builder.append((char) ch);

    Token token = reserved.get(builder.toString());

    if (token != null) {
      unread();
    }
    else {
      token = Token.IDENTIFIER;
      _lexeme = builder.toString();

      if (ch != -1)
        unread();
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

  public static abstract class StoreQueryBuilder
  {
    public abstract FindQueryVault build();
  }

  static class ByExpressionBuilder
  {
    private Mode _mode = Mode.AND;
    private List<String> _fields = new ArrayList<>();
    private String _where = null;

    public void setAnd()
    {
      _mode = Mode.AND;
    }

    public void setOr()
    {
      _mode = Mode.OR;
    }

    public void addField(String field)
    {
      Objects.requireNonNull(field);
      _fields.add(field);
    }

    public String getWhere()
    {
      if (_where == null) {
        StringBuilder where = new StringBuilder(" where ");

        for (int i = 0; i < _fields.size(); i++) {
          String field = normalize(_fields.get(i));

          if (i > 0) {
            where.append(" ").append(_mode).append(" ");
          }

          where.append(field).append("=?");
        }

        return where.toString();
      }

      return _where;
    }

    @Override
    public String toString()
    {
      return ByExpressionBuilder.class.getSimpleName()
             + '['
             + _mode
             + ", "
             + _fields
             + ']';
    }

    enum Mode
    {
      AND,
      OR
    }
  }

  enum Token
  {
    AND("And"),
    BY("By"),
    EOF("eof"),
    FIND("find"),
    IDENTIFIER("identifier"),
    OR("Or"),
    ORDER("OrderBy");

    private String _literal;

    Token(String literal)
    {
      _literal = literal;
    }

    String getLiteral()
    {
      return _literal;
    }
  }

  private final static Map<String,Token> reserved = new HashMap<String,Token>()
  {
    {
      for (Token token : Token.values()) {
        put(token.getLiteral(), token);
      }
    }
  };
}
