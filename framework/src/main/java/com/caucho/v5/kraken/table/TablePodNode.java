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

import java.util.Arrays;
import java.util.Objects;

import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.bartender.pod.ServerPod;


/**
 * Backup for the distributed cache
 */
public class TablePodNode
{
  private final PodBartender _pod;
  private final int _nodeIndex;
  
  // private boolean _isSelf;

  private PodKraken _podKraken;

  private TablePodNodeStartup _state;
  private TablePod _table;
  private ServerBartender _serverSelf;

  private boolean _isSelf;

  private String[] _servers;

  public TablePodNode(TablePod tablePod, 
                      PodKraken podManager,
                      PodBartender pod,
                      int nodeIndex)
  {
    _table = tablePod;
    _podKraken = podManager;
    
    _pod = pod;
    _nodeIndex = nodeIndex;
    
    // boolean isSelf = false;
    
    _serverSelf = BartenderSystem.current().serverSelf();
    
    _isSelf = calculateIsSelf();
    
    _state = new TablePodNodeStartup(this);
    
    updateNodeState();
  }

  public TableKraken getTable()
  {
    return _table.getTable();
  }
  /**
   * Returns the index of the shard.
   */
  public int getIndex()
  {
    return _nodeIndex;
  }

  public NodePodAmp getPodNode()
  {
    return _pod.getNode(_nodeIndex);
  }
  
  private NodePodAmp getNode()
  {
    return _pod.getNode(_nodeIndex);
  }
  
  private ServerBartender getServerSelf()
  {
    return _serverSelf;
  }
  /**
   * Returns true if the current server handles the node values.
   */
  public boolean isSelf()
  {
    return _isSelf;
  }

  public void onUpdate()
  {
    if (updateNodeState()) {
      _state = new TablePodNodeStartup(this);
    }
    
  
    if (! isStartComplete()) {
      _table.addStartupNode(this);
    }
  }
  
  private boolean updateNodeState()
  {
    _isSelf = calculateIsSelf();
    
    NodePodAmp podNode = getNode();
    
    String []servers = new String[podNode.serverCount()];
    
    for (int i = 0; i < servers.length; i++) {
      ServerBartender server = podNode.server(i);
      
      if (server != null) {
        servers[i] = server.getId();
      }
    }
    
    String []oldServers = _servers;
    _servers = servers;
    
    if (! _isSelf) {
      return false;
    }
    
    if (oldServers == null || ! Arrays.equals(oldServers, servers)) {
      return true;
    }
    
    return false;
  }
  
  private boolean calculateIsSelf()
  {
    NodePodAmp podNode = getNode();

    for (int i = 0; i < podNode.serverCount(); i++) {
      ServerBartender server = podNode.server(i);
      
      if (_serverSelf.equals(server)) {
        return true;
      }
    }
    
    return false;
  }
  
  public int getReplicateSize()
  {
    return getNode().serverCount();
  }
  
  /**
   * @return
   */
  public boolean isSelfDataValid()
  {
    return isSelf() && isStartComplete();
  }

  /**
   * Returns true if the current server has valid data.
   */
  public boolean isNodeLocalValid()
  {
    /*
    System.out.println("ISF: " + isSelf() + " " + isStartComplete()
                       + " " + BartenderSystem.getCurrentSelfServer()
                       + " " + this);
                       */
    

    return isSelfDataValid();
    
    // return isSelf();
  }
  
  /**
   * The local node is the primary owner for the pod data.
   * 
   * This is a dynamic value. If the local node is the secondary, and the
   * true primary is down, this will return true until the primary is back up.
   */
  public boolean isSelfOwner()
  {
    return getNode().isServerOwner(getServerSelf());
  }
  
  /**
   * The local node has a copy of the pod data.
   */
  public boolean isSelfCopy()
  {
    return getNode().isServerCopy(getServerSelf());
  }
  
  /**
   * The local node is the primary owner for the pod data.
   * 
   * This is a static value. This will return false if the local node is the
   * secondary, even if the primary is down.
   * true primary is down, this will return true until the primary is back up.
   */
  public boolean isSelfPrimary()
  {
    return getNode().isServerPrimary(getServerSelf());
  }

  public ServerBartender getOwner()
  {
    return getNode().owner();
  }
  
  /**
   * The data for the local node is synchronized with the cluster. This will
   * be false during startup or resharding until the copies are available.
   */
  public boolean isLocalValid()
  {
    return isStartComplete();
  }
  
  /*
  public boolean isNodeLocal()
  {
    return isSelf();
  }
  */

  public boolean isServerOwner(ServerBartender server)
  {
    return getNode().isServerOwner(server);
  }

  public ServerBartender getServer(int index)
  {
    return getNode().server(index);
  }

  public ClusterServiceKraken getProxy(int index)
  {
    ClusterServiceKraken proxy = _podKraken.getProxy(getNode().server(index));
    
    Objects.requireNonNull(proxy);
    
    return proxy;
  }

  public void invoke(NodeTableContext nodeContext)
  {
    NodePodAmp podNode = getNode();
    
    while (true) {
      int index = nodeContext.getIndex();

      if (podNode.serverCount() <= index) {
        nodeContext.fallthru();
        nodeContext.close();
        return;
      }
    
      nodeContext.setIndex(index + 1);
    
      ServerBartender server = podNode.server(index);

      if (server == null || ! server.isUp()) {
        continue;
      }
      
      if (server.isSelf()) {
        continue;
      }
      
      ClusterServiceKraken proxy = getProxy(index);
      
      nodeContext.invoke(proxy);

      if (nodeContext.isSingleRequest()) {
        nodeContext.close();
        return;
      }
    }
  }
  
  public boolean isStartComplete()
  {
    return _state.isComplete() || ! isSelf();
  }

  boolean startRequestUpdates(TablePod tablePod,
                              long startupLastUpdateTime)
  {
    return _state.startRequestUpdates(tablePod, startupLastUpdateTime);
  }

  public boolean isStartFailed()
  {
    return _state.isFailed();
  }

  public void clearStartFailure()
  {
    _state.clearFailure();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _table.getName() + "," + getNode() + "]";
  }
  
  abstract static class NodeTableContext
  {
    private int _index;

    final int getIndex()
    {
      return _index;
    }
    
    public boolean isSingleRequest()
    {
      return true;
    }

    final void setIndex(int index)
    {
      _index = index;
    }
    
    abstract void invoke(ClusterServiceKraken proxy);
    
    /**
     * All servers have been sent the message. For a get(), this is a failure.
     * For a put(), it's completion of normal operation.
     */
    abstract void fallthru();
    
    public void close()
    {
    }
  }
}
