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

package com.caucho.v5.kraken.table;

import java.util.ArrayList;

import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.kelp.BackupKelp;

import io.baratine.service.Result;

/**
 * Table client to the distributed store.
 */
public class TablePodLocal implements TablePod
{

  public TablePodLocal(TableManagerKraken tableManager, 
                       TableKraken tableKraken,
                       PodKrakenAmp podManager)
  {
    // TODO Auto-generated constructor stub
  }

  @Override
  public String getName()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getPodName()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public TableKraken getTable()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public TablePodNodeAmp getNode(int hash)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int nodeIndex()
  {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public boolean isLocal()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public int getServerCount()
  {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getNodeCount()
  {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getVirtualNodeCount()
  {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public BackupKelp getReplicationCallback()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void startRequestUpdates()
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void put(byte[] key, StreamSource data, Result<Boolean> result)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void get(byte[] key, Result<Boolean> result)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void getIfUpdate(byte[] key, long version, Result<Boolean> result)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void findByName(String name, Result<byte[]> result)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void remove(byte[] key, long version, Result<? super Boolean> result)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void getUpdatesFromLocal(int podIndex, long accessTime,
                                  Result<Boolean> result)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void notifyWatch(byte[] key)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public ArrayList<ServerBartender> findServersQueryCover()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ArrayList<ServerBartender> getUpdateServers()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServerBartender getServerSelf()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void findOne(Result<byte[]> subResult, ServerBartender server,
                      String sql, Object[] args)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void findAll(Result<Iterable<byte[]>> subResult,
                      ServerBartender server, String sql, Object[] args)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void update(Result<Integer> result, int node, String sql,
                     Object[] args)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void addRemoteWatch(byte[] key, int hash)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void notifyForeignWatch(byte[] key, String serverId)
  {
    // TODO Auto-generated method stub
    
  }
}
