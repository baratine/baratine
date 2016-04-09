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

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.Row;
import com.caucho.v5.util.L10N;

/**
 * Query based on SQL.
 */
public class RootHessianExprBuilder extends ExprBuilderKelp
{ 
  private static final L10N L = new L10N(RootHessianExprBuilder.class);
  
  private Row _row;
  
  RootHessianExprBuilder(Row row)
  {
    _row = row;
  }
  
  public ExprBuilderKelp field(String name)
  {
    Column column = _row.findColumn(name);
    
    if (column == null) {
      throw new RuntimeException(L.l("'{0}' is an unknown column", name));
    }
    
    return new ObjectColumnExprBuilder(column);
  }
}
