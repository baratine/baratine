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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.ExprKelp;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.TablePod;
import com.caucho.v5.kraken.table.TablePodNode;
import com.caucho.v5.util.CurrentTime;

import io.baratine.db.Cursor;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;


public class SelectQuery extends SelectQueryBase
{
  private final TableKraken _table;
  private ExprKraken _keyExpr;
  private ExprKraken _whereKraken;
  private EnvKelp _whereKelp;
  private ExprKelp [] _results;
  private boolean _isStaticNode;
  
  private SelectQueryLocal _selectQueryLocal;
  
  SelectQuery(String sql,
              SelectQueryBuilder builder,
              TableKraken tableKraken,
              ExprKraken keyExpr,
              ExprKraken whereKraken,
              EnvKelp envKelp,
              ExprKelp [] results)
  {
    super(sql);
    
    Objects.requireNonNull(whereKraken);
    Objects.requireNonNull(envKelp);
    
    _selectQueryLocal = new SelectQueryLocal(sql, builder, tableKraken,
                                             keyExpr, whereKraken, envKelp,
                                             results);
    
    _table = tableKraken;
    _keyExpr = keyExpr;
    _whereKraken = whereKraken;
    _whereKelp = envKelp;
    _results = results;
    
    _isStaticNode = builder.isStaticNode(whereKraken);
  }
  
  public TableKraken table()
  {
    return _table;
  }

  @Override
  ExprKraken getWhere()
  {
    return _whereKraken;
  }

  @Override
  ExprKraken getKeyExpr()
  {
    return _keyExpr;
  }

  @Override
  public boolean isStaticNode()
  {
    return _isStaticNode;
  }
  
  @Override
  public void fillKey(RowCursor cursor, Object []args)
  {
    _keyExpr.fillMinCursor(cursor, args);
  }

  /**
   * Returns the hash code for the owning partition. If the hash is negative,
   * the query does not have an owning partition.
   */
  /*
  @Override
  public int partitionHash(Object[] args)
  {
    if (_keyExpr == null) {
      return -1;
    }
    
    return _keyExpr.partitionHash(args);
  }
  */
  
  @Override
  public int calculateHash(RowCursor cursor)
  {
    return table().getPodHash(cursor);
  }

  @Override
  public void findOne(Result<Cursor> result, Object ...args)
  {
    findOneImpl(false, result, args);
  }

  @Override
  public void findOneDirect(Result<Cursor> result, Object ...args)
  {
    findOneImpl(true, result, args);
  }

  private void findOneImpl(boolean isDirect, Result<Cursor> result, Object ...args)
  {
    //TableKelp tableKelp = _table.getTableKelp();
    TablePod tablePod = _table.getTablePod();
    
    // XXX: potential issues with remote
    //args = normalizeArgs(args);

    if (tablePod.isLocal()) {
      if (isDirect) {
        _selectQueryLocal.findOneDirect(result, args);
      }
      else {
        _selectQueryLocal.findOne(result, args);
      }
      return;
    }
    else if (isStaticNode()) {
      RowCursor cursor = _table.cursor();
      
      fillKey(cursor, args);
      
      int hash = calculateHash(cursor);
    
      TablePodNode node = tablePod.getNode(hash);

      if (node.isSelfCopy() && node.isLocalValid()) {
        if (isDirect) {
          _selectQueryLocal.findOneDirect(result, args);
        }
        else {
          _selectQueryLocal.findOne(result, args);
        }
        return;
      }
      else {
        // XXX: local with timeout
        // tablePod.get(cursor.getKey(), new FindGetResult(result, args));
        findOneGet(result, cursor, args);
        return;
      }
    }
    
    findOneCluster(result, args);
  }
  
  private Object []normalizeArgs(Object []args)
  {
    if (args == null) {
      return null;
    }
    
    // XXX: avoid replace?
    for (int i = 0; i < args.length; i++) {
      args[i] = normalizeArg(args[i]);
    }
    
    return args;
  }
  
  private Object normalizeArg(Object value)
  {
    if (value == null) {
      return value;
    }
    
    Class<?> type = value.getClass();
    
    if (type.isArray() || type.isPrimitive()) {
      return value;
    }
    
    if (type.getName().startsWith("java")) {
      return value;
    }
    
    if (Enum.class.isAssignableFrom(type)) {
      Enum<?> enumValue = (Enum<?>) value;
      HashMap<String,Object> fields = new HashMap<>();
      
      fields.put("name", enumValue.name());
      
      return fields;
    }
    
    try {
      HashMap<String,Object> fields = new HashMap<>();
    
      for (Field field : type.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        if (Modifier.isTransient(field.getModifiers())) {
          continue;
        }

        field.setAccessible(true);
        
        fields.put(field.getName(), field.get(value));
      }
      
      return fields;
    } catch (Exception e) {
      e.printStackTrace();
      
      return value;
    }
  }
  
  private void findOneGet(Result<Cursor> result,
                          RowCursor cursor,
                          Object []args)
  {
    _selectQueryLocal.getDirect(result.of((v,r)->findAfterLocal(r, cursor, args, v)),
                                cursor, args);
                                
    //_table.get(cursor, result.from((v,r)->findAfterLocal(r, cursor, args, v)));
    
    //tablePod.get(cursor.getKey(), 
    //                 result.from((table,r)->_selectQueryLocal.findOne(r, args)));
    
    //_selectQueryLocal.findtablePod.get(cursor.getKey(), 
      //               result.from((table,r)->_selectQueryLocal.findOne(r, args)));
  }
  
  /**
   * After finding the local cursor, if it's expired, check with the cluster
   * to find the most recent value. 
   */
  private void findAfterLocal(Result<Cursor> result,
                              RowCursor cursor,
                              Object []args,
                              Cursor cursorLocal)
  {
    long version = 0;
    
    if (cursorLocal != null) {
      version = cursorLocal.getVersion();
      long time = cursorLocal.getUpdateTime();
      long timeout = cursorLocal.getTimeout();
      
      long now = CurrentTime.currentTime();
      
      if (now <= time + timeout) {
        result.ok(cursorLocal);
        return;
      }
    }
    
    TablePod tablePod = _table.getTablePod();
    
    tablePod.getIfUpdate(cursor.getKey(), version,
                         result.of((table,r)->_selectQueryLocal.findOne(r, args)));
  }
  
  private void findOneCluster(Result<Cursor> result, Object []args)
  {
    ArrayList<ServerBartender> servers = findServers();
    
    ServerBartender serverSelf = _table.getTablePod().getServerSelf();
    
    if (serverSelf != null) {
      servers.remove(serverSelf);

      Result<Cursor> clusterResult
        = new FindOneClusterResult(result, servers, args);
      
      _selectQueryLocal.findOne(clusterResult, args);
    }
    else {
      result.ok(null);
    }
  }
  
  private ArrayList<ServerBartender> findServers()
  {
    TablePod tablePod = _table.getTablePod();
    
    ArrayList<ServerBartender> servers = tablePod.findServersQueryCover();
    
    return servers;
  }
  
  //
  // find all
  //

  @Override
  public void findAll(Result<Iterable<Cursor>> result, Object ...args)
  {
    //TableKelp tableKelp = _table.getTableKelp();
    TablePod tablePod = _table.getTablePod();

    if (isStaticNode()) {
      RowCursor cursor = _table.cursor();
      
      fillKey(cursor, args);
      
      _selectQueryLocal.findAll(result, args);
      return;
    }
    
    /*
    int hash = partitionHash(args);
    
    if (hash >= 0 && tablePod.getNode(hash).isSelfDataValid()) {
      _selectQueryLocal.findAll(result, args);
      return;
    }
    */
    
    ArrayList<ServerBartender> servers = findServers();
    
    ServerBartender serverSelf = _table.getTablePod().getServerSelf();

    if (servers.size() == 0) {
      _selectQueryLocal.findAll(result, args);
      // result.completed(new ArrayList<Cursor>());
      return;
    }

    if (servers.size() == 1 && servers.get(0).isSameServer(serverSelf)) {
      _selectQueryLocal.findAll(result, args);
      return;
    }
    
    List<List<byte[]>> partialKeys = new ArrayList<>();
    
    for (ServerBartender server : servers) {
      FindAnyReduceResult subResult
        = new FindAnyReduceResult(result, servers, server, args, partialKeys);
    
      tablePod.findAll(subResult, server, getSql(), args);
    }
  }

  @Override
  public void findAllLocal(Result<Iterable<Cursor>> result, Object ...args)
  {
    _selectQueryLocal.findAll(result, args);
  }

  @Override
  public void findAllLocalKeys(Result<Iterable<byte[]>> result, Object []args)
  {
    _selectQueryLocal.findAllLocalKeys(result, args);
  }
  
  //
  // callbacks
  //

  /*
  private class FindGetResult extends Result.Wrapper<Boolean,Cursor> {
    private Object []_args;
    
    FindGetResult(Result<Cursor> result, Object []args)
    {
      super(result);
      
      _args = args;
    }
    
    @Override
    public void complete(Boolean result)
    {
      try {
        _selectQueryLocal.findOne(getNext(), _args);
      } catch (Throwable e) {
        fail(e);
      }
    }
  }
  */
  
  private class FindOneClusterResult implements Result<Cursor>
  {
    private Result<Cursor> _result;
    private ArrayList<ServerBartender> _servers;
    private Object []_args;
    
    FindOneClusterResult(Result<Cursor> result,
                         ArrayList<ServerBartender> servers,
                         Object []args)
    {
      _result = result;
      _servers = servers;
      _args = args;
    }
    
    @Override
    public void handle(Cursor cursor, Throwable exn)
    {
      if (exn != null) {
        _result.fail(exn);
        return;
      }
      
      if (cursor != null) {
        _result.ok(cursor);
      }
      else if (_servers.size() == 0) {
        _result.ok(null);
      }
      else {
        TablePod tablePod = _table.getTablePod();
        
        for (ServerBartender server : _servers) {
          //FindOneReduceResult subResult
          //  = new FindOneReduceResult(_result, _servers, server, _args);
        
          tablePod.findOne(of((key,r)->reduceResult(key, server)), 
                           server, getSql(), _args);
        }
      }
    }
    
    private void reduceResult(byte []key, ServerBartender server)
    {
      if (! _servers.remove(server)) {
        // other server already found the result
        return;
      }
      
      if (key != null) {
        _servers.clear();
        
        _selectQueryLocal.getOne(_result, key, _args);
      }
      else if (_servers.size() == 0) {
        _result.ok(null);
      }
      else {
        System.out.println("  WAIT_FOR_PEER: " + server);
      }
    }
    
    @Override
    public void fail(Throwable exn)
    {
      _result.fail(exn);
    }
  }
  
  /*
  private class FindOneReduceResult implements Result<byte[]>
  {
    private Result<Cursor> _result;
    private ArrayList<ServerPod> _servers;
    private ServerPod _server;
    private Object []_args;
    
    FindOneReduceResult(Result<Cursor> result,
                         ArrayList<ServerPod> servers,
                         ServerPod server,
                         Object []args)
    {
      _result = result;
      _servers = servers;
      _server = server;
      _args = args;
    }
    
    @Override
    public void complete(byte []key)
    {
      if (! _servers.remove(_server)) {
        // other server already found the result
        return;
      }
      
      if (key != null) {
        _servers.clear();
        
        _selectQueryLocal.getOne(_result, key, _args);
      }
      else if (_servers.size() == 0) {
        _result.complete(null);
      }
      else {
        System.out.println("  WAIT_FOR_PEER: " + _server);
      }
    }
    
    public void fail(Throwable exn)
    {
      Result.Adapter.failed(_result, exn);
    }
  }
  */
  
  private class FindAnyReduceResult implements Result<Iterable<byte[]>>
  {
    private Result<Iterable<Cursor>> _result;
    private ArrayList<ServerBartender> _servers;
    private ServerBartender _server;
    private Object []_args;
    
    private List<List<byte[]>> _partialKeys;
    
    FindAnyReduceResult(Result<Iterable<Cursor>> result,
                         ArrayList<ServerBartender> servers,
                         ServerBartender server,
                         Object []args,
                         List<List<byte[]>> partialKeys)
    {
      _result = result;
      _servers = servers;
      _server = server;
      _args = args;
      _partialKeys = partialKeys;
    }
    
    @Override
    public void handle(Iterable<byte[]> iter, Throwable exn)
    {
      if (exn != null) {
        _result.fail(exn);
        return;
      }
      
      _servers.remove(_server);

      if (iter != null) {
        ArrayList<byte[]> keys = new ArrayList<>();
        
        for (byte []key : iter) {
          keys.add(key);
        }
        
        _partialKeys.add(keys);
      }
      
      if (_servers.size() > 0) {
        return;
      }
      
      ArrayList<byte[]> mergeKeys = mergeKeys(_partialKeys);
      
      _result.ok(new FindAnyIterable(mergeKeys, _args));
    }
    
    private ArrayList<byte[]> mergeKeys(List<List<byte[]>> list)
    {
      ArrayList<byte[]> mergeKeys = new ArrayList<>();
      byte []lastKey = null;
      byte []key;
      
      while ((key = firstKey(list)) != null) {
        if (lastKey == null || ! Arrays.equals(key, lastKey)) {
          mergeKeys.add(key);
        }
        
        lastKey = key;
      }
      
      return mergeKeys;
    }
    
    private byte []firstKey(List<List<byte[]>> list)
    {
      byte []minKey = null;
      int index = -1;
      
      for (int i = 0; i < list.size(); i++) {
        List<byte[]> keys = list.get(i);
        
        if (keys.size() == 0) {
          continue;
        }
        
        byte []key = keys.get(0);
        
        if (minKey == null || compareKey(key, minKey) < 0) {
          minKey = key;
          index = i;
        }
      }
      
      if (minKey != null) {
        list.get(index).remove(0);
      }
      
      return minKey;
    }
    
    private int compareKey(byte []keyA, byte []keyB)
    {
      int len = keyA.length;
      
      int cmp = keyA.length - keyB.length;
      
      if (cmp != 0) {
        return cmp;
      }
      
      for (int i = 0; i < len; i++) {
        cmp = keyA[i] - keyB[i];
        
        if (cmp != 0) {
          return cmp;
        }
      }
      
      return cmp;
    }

    @Override
    public void fail(Throwable exn)
    {
      _result.fail(exn);
    }
  }
  
  private class FindAnyIterable implements Iterable<Cursor> {
    private ArrayList<byte[]> _keys;
    private Object []_args;
    
    FindAnyIterable(ArrayList<byte[]> keys, Object []args)
    {
      _keys = keys;
      _args = args;
    }

    @Override
    public Iterator<Cursor> iterator()
    {
      return new FindAnyIterator(_keys, _args);
    }
  }
  
  private class FindAnyIterator implements Iterator<Cursor> {
    private ArrayList<byte[]> _keys;
    private int _index;
    private Object []_args;
    
    FindAnyIterator(ArrayList<byte[]> keys, Object []args)
    {
      _keys = keys;
      _args = args;
    }

    @Override
    public boolean hasNext()
    {
      return _index < _keys.size();
    }

    @Override
    public Cursor next()
    {
      if (_keys.size() <= _index) {
        return null;
      }
      
      byte []key = _keys.get(_index++);

      ResultFuture<Cursor> future = new ResultFuture<>();
      
      _selectQueryLocal.getOne(future, key, _args);
      
      return future.get(10, TimeUnit.SECONDS);
    }

    @Override
    public void remove()
    {

    }
    
  }
}
