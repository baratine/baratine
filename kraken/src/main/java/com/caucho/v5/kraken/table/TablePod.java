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
public interface TablePod
{
  /**
   * The table name.
   */
  String getName();

  /**
   * The pod name.
   */
  String getPodName();
  /**
   * The kraken table.
   */
  TableKraken getTable();
  
  //
  // pod information
  //

  /**
   * The table node for the given hash.
   * 
   * The table node is the Kraken table's partition matching the Bartender pod
   * node.
   */
  TablePodNodeAmp getNode(int hash);
  
  /**
   * The Bartender pod node for the given hash.
   */
  //NodePodAmp getPodNode(int hash);
  int nodeIndex();

  /**
   * If the pod is for the local server, return true.
   */
  boolean isLocal();
  
  int getServerCount();
  int getNodeCount();
  int getVirtualNodeCount();
  
  BackupKelp getReplicationCallback();

  void startRequestUpdates();
  
  void put(byte []key,
           StreamSource data,
           Result<Boolean> result);
  
  void get(byte[] key,
           Result<Boolean> result);

  void getIfUpdate(byte[] key, 
                   long version, 
                   Result<Boolean> result);
  
  void findByName(String name,
                 Result<byte[]> result);
  
  /*
  void getLocal(byte[] key,
                Result<Boolean> result);
                */
  
  void remove(byte []key, long version, Result<? super Boolean> result);

  /**
   * @param podIndex
   * @param accessTime
   * @param updateCompletion
   */
  void getUpdatesFromLocal(int podIndex, 
                  long accessTime, 
                  Result<Boolean> result);
  /**
   * @param tablePodNodeStartup
   * @param i
   */
  //void startUpdates(TablePodNodeStartup tablePodNodeStartup, int i);
  
  /**
   * @param tablePodNode
   */
  //void addStartupNode(TablePodNode podNode);
  
  void notifyWatch(byte []key);
  
  //
  // pod management
  //
  
  
  ArrayList<ServerBartender> findServersQueryCover();
  ArrayList<ServerBartender> getUpdateServers();
  
  ServerBartender getServerSelf();
  
  void findOne(Result<byte[]> subResult, 
               ServerBartender server, 
               String sql, 
               Object []args);
  
  void findAll(Result<Iterable<byte[]>> subResult, 
               ServerBartender server, 
               String sql,
               Object[] args);

  void update(Result<Integer> result, 
              int node,
              String sql,
              Object[] args);
  // void updateTime(byte []key);
  
  void addRemoteWatch(byte[] key, int hash);
  void notifyForeignWatch(byte[] key, String serverId);

  // void startUpdates(TablePodNodeStartup tablePodNodeStartup, int i);

}
