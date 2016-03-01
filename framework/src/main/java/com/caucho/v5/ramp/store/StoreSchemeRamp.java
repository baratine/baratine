/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.ramp.store;

import io.baratine.service.OnDestroy;
import io.baratine.service.OnLookup;
import io.baratine.service.Service;

import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.pod.PodBartender;


/**
 * Entry to the distributed database system.
 */
@Service
public class StoreSchemeRamp
{
  private ConcurrentHashMap<String,StoreRootRamp> _dbMap
    = new ConcurrentHashMap<>();
  
  public StoreSchemeRamp()
  {
  }
  
  @OnLookup
  public Object lookup(String path)
  {
    if (! path.startsWith("//")) {
      return null;
    }
    
    String hostName = "";
    String name;
    
    if (path.startsWith("///")) {
      name = path.substring(2);
      
      PodBartender pod = BartenderSystem.getCurrentPod();
      
      if (pod != null) {
        hostName = pod.name();
      }
    }
    else {
      int p = path.indexOf('/', 3);
      
      if (p > 0) {
        hostName = path.substring(2, p);
        name = path.substring(p);
      }
      else {
        hostName = path.substring(2);
        name = "";
      }
    }
    
    
    StoreRootRamp root = _dbMap.get(hostName);
    
    if (root == null) {
      StoreRootRamp dbRamp = new StoreRootRamp(this, hostName);
      
      // db = dbRamp.getDatabaseService();
      
      _dbMap.putIfAbsent(path, dbRamp);
      
      root = _dbMap.get(path);
    }
    
    if (name != null && ! name.isEmpty()) {
      return root.onLookup(name);
    }
    else {
      return root;
    }
  }
  
  @OnDestroy
  public void onShutdown()
  {
    TreeSet<String> dbNames = new TreeSet<>(_dbMap.keySet());
    
    for (String name : dbNames) {
      StoreRootRamp root = _dbMap.get(name);
      
      root.shutdown();
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
