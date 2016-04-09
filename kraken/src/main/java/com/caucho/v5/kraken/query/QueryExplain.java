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

import java.util.Objects;
import java.util.TreeSet;

import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.TablePodNodeAmp;
import com.caucho.v5.util.Hex;

import io.baratine.service.Result;


public class QueryExplain extends QueryKraken
{
  private final SelectQueryBase _select;
  
  QueryExplain(String sql,
               SelectQueryBase select)
  {
    super(sql);
    
    Objects.requireNonNull(select);
    
    _select = select;
  }
  
  protected SelectQueryBase getDelegate()
  {
    return _select;
  }

  @Override
  public TableKraken table()
  {
    return getDelegate().table();
  }

  @Override
  public void exec(Result<Object> result,
                   Object ...args)
  {
    if (result != null) {
      String plan = explainPlan(args);
      
      result.ok(plan);
    }
  }
  
  private String explainPlan(Object []args)
  {
    ExprKraken whereExpr = _select.getWhere();
    ExprKraken keyExpr = _select.getKeyExpr();
    
    String plan = String.valueOf(whereExpr);
    
    TableKelp tableKelp = table().getTableKelp();
    
    TreeSet<String> keys = new TreeSet<>();
    
    whereExpr.fillAssignedKeys(keys);
    
    if (_select.isLocal()) {
      plan += "\n  local: true";
      
    }
    if (_select.isStaticNode()) {
      plan += "\n  static node: true";
      
      if (args != null && args.length > 0) {
        RowCursor cursor = tableKelp.cursor();
        
        int hash = keyExpr.fillNodeHash(table(), cursor, args);
        
        plan += "\n    hash: " + hash;
        
        TablePodNodeAmp node = table().getTablePod().getNode(hash);
        
        //plan += "\n    node: " + node.getPodNode();
        plan += "\n    node: " + node.index();
        
        // the self server is the current owner for the data
        if (node.isSelfOwner()) {
          plan += "\n    node-owner: true";
        }
        
        // the self server has a copy of the data
        if (node.isSelfCopy()) {
          plan += "\n    node-copy: true";
        }
      }
    }
    
    if (keys.size() > 0) {
      plan += "\n  static keys: " + keys;
    
      // ArrayList<Column> keyColumns = getTable().getKeyColumns();
    
      if (_select.isStaticNode()) {
        plan += "\n  static-keys-complete: true";
        
        if (args != null && args.length > 0) {
          RowCursor cursor = table().getTableKelp().cursor();
          
          whereExpr.fillMinCursor(cursor, args);
          
          plan += "\n    key: " + Hex.toHex(cursor.getKey());
        }
        
      }
    }
    
    return plan;
  }
}
