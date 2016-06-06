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

import io.baratine.service.Result;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.caucho.v5.kraken.table.KrakenImpl;
import com.caucho.v5.util.L10N;


public class CreateQueryBuilder extends QueryBuilderKraken
{
  private static final L10N L = new L10N(CreateQueryBuilder.class);
  
  private KrakenImpl _tableManager;
  private TableBuilderKraken _factory;
  private HashMap<String, String> _propMap;
  
  public CreateQueryBuilder(KrakenImpl tableManager,
                            TableBuilderKraken factory,
                            String sql,
                            HashMap<String,String> propMap)
  {
    super(sql);
    
    _tableManager = tableManager;
    _factory = factory;

    _propMap = propMap;
  }
  
  @Override
  public CreateQuery build()
  {
    for (Map.Entry<String, String> entry : _propMap.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      
      switch (key) {
      case "hash":
        buildHash(value);
        break;

      case "class":
        buildScheme(value);
        break;
      }
    }
    
    return new CreateQuery(_tableManager, _factory, sql());
  }
  
  private void buildHash(String value)
  {
    if (value.indexOf('/') >= 0 || value.indexOf('$') > 0) {
      buildHashExpression(value);
    }
    else {
      try {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> cl = Class.forName(value, false, loader);
    
        _factory.setHashClass(cl);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
  
  private void buildHashExpression(String value)
  {
    HashExprGenerator gen = new HashExprGenerator(_factory);
    
    int p = 0;
    int len = value.length();
    
    while (p < len) {
      int q = value.indexOf('$', p);
      
      if (q < 0) {
        gen.literal(value.substring(p));
        
        _factory.setHashBuilder(gen);
        return;
      }
      
      if (p < q) {
        gen.literal(value.substring(p, q));
      }
      
      if (value.charAt(q + 1)== '{') {
        int r = value.indexOf('}', q + 1);
        
        if (r < 0) {
          throw new RuntimeException(L.l("Invalid hash expression {0}", value));
        }
        
        gen.column(value.substring(q + 2, r));
        
        p = r + 1;
      }
      else {
        int r = value.indexOf('/', q + 1);
        
        if (r < 0) {
          r = value.indexOf('$', q + 1);
        }
          
        if (r < 0) {
          gen.column(value.substring(q + 1));
          
          _factory.setHashBuilder(gen);
          return;
        }
        else {
          gen.column(value.substring(q + 1, r));
          p = r;
        }
      }
    }
  }
  
  private void buildScheme(String value)
  {
    Objects.requireNonNull(value);
    
    String []classNames = value.split("[,\\s]+");
    
    for (String schemeName : classNames) { 
      try {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> type = Class.forName(schemeName, false, loader);
    
        _factory.schema(type);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
  
  @Override
  public void build(Result<QueryKraken> result)
  {
    result.ok(build());
  }
}
