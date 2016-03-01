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

package com.caucho.v5.ramp.db;

import io.baratine.db.DatabaseService;
import io.baratine.service.OnLookup;
import io.baratine.service.Service;

import java.util.HashMap;


/**
 * Entry to the distributed database system.
 */
@Service
public class DatabaseSchemeServiceRamp
{
  private HashMap<String,DatabaseService> _dbMap = new HashMap<>();
  
  @OnLookup
  public DatabaseService lookup(String path)
  {
    if (! path.startsWith("//")) {
      return null;
    }
    
    String hostName = "";
    String name;
    
    if (path.startsWith("///")) {
      name = path.substring(3);
    }
    else {
      int p = path.indexOf('/', 3);
      
      hostName = path.substring(2, p);
      name = path;
    }
    
    
    DatabaseService db = _dbMap.get(path);
    
    if (db == null) {
      db = new DatabaseServiceRamp(name, hostName);
      
      _dbMap.put(path, db);
    }
    
    return db;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
