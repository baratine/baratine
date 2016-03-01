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

import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.ExprKelp;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.TablePod;

public class PodNodeExprKelp extends ExprKelp
{
  private TableKraken _table;

  public PodNodeExprKelp(TableKraken table)
  {
    _table = table;
  }
  
  //
  // builder methods
  //
  
  @Override
  public int evalInt(EnvKelp env)
  {
    int hash = _table.getPodHash(env.getCursor());
    
    TablePod pod = _table.getTablePod();
    
    return pod.getPodNode(hash).nodeIndex();
  }
  
  @Override
  public Object eval(EnvKelp env)
  {
    return evalInt(env);
  }
}
