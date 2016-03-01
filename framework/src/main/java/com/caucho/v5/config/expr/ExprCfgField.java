/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.config.expr;

import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * String literal expression
 */
public class ExprCfgField implements ExprCfg
{
  private static final Logger log = Logger.getLogger(ExprCfgField.class.getName());
  
  private final ExprCfg _left;
  private final String _fieldName;
  private final String _getter;
  
  ExprCfgField(ExprCfg left, String fieldName)
  {
    _left = left;
    _fieldName = fieldName;
    
    _getter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
  }
  
  @Override
  public Object eval(Function<String,Object> env)
  {
    Object value = _left.eval(env);
    
    if (value == null) {
      return null;
    }
    
    try {
      Method method = value.getClass().getMethod(_getter);
      
      return method.invoke(value);
    } catch (Exception e) {
      log.log(Level.FINEST, e.toString(), e);
      
      return null;
    }
  }
  
  @Override
  public ExprCfg createField(String name)
  {
    return new ExprCfgField(this, name);
  }
  
  @Override
  public String toString()
  {
    return _left.toString() + "." + _fieldName;
  }
}

