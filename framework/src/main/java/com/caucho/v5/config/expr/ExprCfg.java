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

import java.util.function.Function;

/**
 * Configuration expression
 */
public interface ExprCfg
{
  static ExprCfgParser newParser(String text)
  {
    return new ExprCfgParserImpl(text);
  }
  
  Object eval(Function<String,Object> env);
  
  default boolean evalBoolean(Function<String, Object> env)
  {
    Object value = eval(env);
    
    if (value == null) {
      return false;
    }
    else if (value instanceof String) {
      return ! value.equals("") && ! value.equals("false");
    }
    else {
      return ! value.equals(Boolean.FALSE);
    }
  }

  default String evalString(Function<String,Object> env)
  {
    return String.valueOf(eval(env));
  }

  default ExprCfg createField(String name)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default ExprCfg createMethod(ExprCfg[] args)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default String getExpressionString()
  {
    return toString();
  }
  
  interface ExprCfgParser
  {
    ExprCfg parse();
  }
}

