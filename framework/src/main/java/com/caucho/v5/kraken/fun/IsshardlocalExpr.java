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

package com.caucho.v5.kraken.fun;

import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kraken.query.FunExpr;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.TablePodNodeAmp;


public class IsshardlocalExpr extends FunExpr
{
  private NodePodAmp _shard;

  public IsshardlocalExpr()
  {
    _shard = BartenderSystem.getCurrentShard();
    
    //System.out.println("ISS: " + _shard);

    //Objects.requireNonNull(_shard);
  }

  @Override
  public Object apply(EnvKelp env, Object []argObject)
  {
    // System.out.println("APPLY: " + _shard + " " + env.getAttribute("krakenTable"));
    /*
    if (_shard == null) {
      return false;
    }
    */
    
    TableKraken table = (TableKraken) env.getAttribute("krakenTable");
    
    if (table == null) {
      return true;
    }
    else {
      int hash = table.getPodHash(env.getCursor());
      
      TablePodNodeAmp node = table.getTablePod().getNode(hash);
      
      //System.out.println("GPH: " + hash + " " + table.getTablePod().getNode(hash));
      /*
      int node = hash % table.getTablePod().getNodeCount();

      return (node == shard.getIndex());
      */
      
      if (_shard != null) {
        return node.index() == _shard.nodeIndex();
      }
      else {
        //System.out.println("ND: " + node.isSelfOwner() + " " + node);
        
        return node.isSelfOwner();
      }
    }
  }
}
