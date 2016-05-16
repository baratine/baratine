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

package com.caucho.v5.amp.ensure;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmpBean;

import io.baratine.db.Cursor;
import io.baratine.db.DatabaseService;
import io.baratine.db.DatabaseServiceSync;
import io.baratine.service.Result;

/**
 * Factory for opening and restoring reliable messaging.
 */
public class EnsureDriverImpl implements EnsureDriverAmp
{
  private ConcurrentHashMap<MethodAmp,MethodEnsureImpl> _methodMap
    = new ConcurrentHashMap<>();
  
  private DatabaseService _db;
  
  private String _sqlPut;
  private String _sqlDelete;
  private String _sqlStart;
  
  @Override
  public void init(ServicesAmp services)
  {
    DatabaseServiceSync db = services.service("bardb://local/ensure")
                                     .as(DatabaseServiceSync.class);
    
    String sql = "create table ensure (\n" +
      "  id int64,\n" +
      "  method_id int64,\n" +
      "  address string,\n" +
      "  args object,\n" +
      "  primary key (id, method_id)\n" +
      ")";
    
    db.exec(sql);
    
    _db = db;
    
    _sqlPut = "insert into ensure (id,method_id,address,args) values(?,?,?,?)";
    _sqlDelete = "delete from ensure where id=? and method_id=?";
    _sqlStart = "select id,address,args from ensure where method_id=?";
  }
  
  @Override
  public MethodEnsureAmp ensure(MethodAmp method)
  {
     MethodEnsureImpl methodEnsure = _methodMap.get(method);
     
     if (methodEnsure == null) {
       methodEnsure = new MethodEnsureImpl(this, method);
       
       _methodMap.putIfAbsent(method, methodEnsure);
       
       methodEnsure = _methodMap.get(method);
     }
    
    return methodEnsure;
  }

  public void put(long id, long methodId, String address, Object[] args)
  {
    _db.exec(_sqlPut, Result.ignore(), id, methodId, address, args);
  }
  
  public void remove(long id, long methodId)
  {
    _db.exec(_sqlDelete,
             (x,exn)->{ if (exn != null) exn.printStackTrace(); },
             id, 
             methodId);
  }

  public void onActive(long methodId, 
                       MethodEnsureImpl method,
                       StubAmpBean stub)
  {
    _db.findAll(_sqlStart, (x,e)->onActive(x, e, method, stub), methodId);
  }
  
  private void onActive(Iterable<Cursor> iter,
                        Throwable exn,
                        MethodEnsureImpl method,
                        StubAmpBean stub)
  {
    if (exn != null) {
      exn.printStackTrace();
      return;
    }
    
    for (Cursor cursor : iter) {
      long id = cursor.getLong(1);
      String address = cursor.getString(2);
      Object []args = (Object []) cursor.getObject(3);

      method.onActive(id, stub, address, args);
    }
  }
}
