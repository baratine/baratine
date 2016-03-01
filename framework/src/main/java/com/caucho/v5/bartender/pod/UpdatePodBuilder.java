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

package com.caucho.v5.bartender.pod;

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.bartender.ClusterBartender;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.heartbeat.ClusterHeartbeat;
import com.caucho.v5.bartender.heartbeat.RackHeartbeat;
import com.caucho.v5.bartender.pod.PodBartender.PodType;
import com.caucho.v5.bartender.pod.UpdatePod.UpdateNode;
import com.caucho.v5.util.L10N;


/**
 * Creates a pod update.
 */
public class UpdatePodBuilder
{
  private static final L10N L = new L10N(UpdatePodBuilder.class);
  
  private String _name = "default";

  private ClusterHeartbeat _cluster;
  
  private String _tag;
  
  //private ArrayList<String> _serverList = new ArrayList<>();
  private ArrayList<ServerPod> _serverList = new ArrayList<>();

  private int _primaryServerCount;
  private int _nodeCount = 1;
  private int _vnodeCount;
  private int _depth = 3;
  private int _backupSpread = 2;

  private ServerPod[] _servers;

  private int _roundRobin;

  private PodBartender.PodType _type = PodBartender.PodType.lazy;

  private long _sequence;

  
  public UpdatePodBuilder()
  {
  }
  
  //
  // configuration methods
  //
  
  /**
   * name is the name of the pod
   */
  public UpdatePodBuilder name(String name)
  {
    Objects.requireNonNull(name);
    
    if (name.indexOf('.') >= 0) {
      throw new IllegalArgumentException(name);
    }
    
    _name = name;
    
    return this;
  }

  public String getName()
  {
    return _name;
  }
  
  /**
   * Cluster configures the cluster the pod belongs to.
   */
  public UpdatePodBuilder cluster(ClusterBartender cluster)
  {
    _cluster = (ClusterHeartbeat) cluster;
    
    return this;
  }
  
  public ClusterHeartbeat getCluster()
  {
    return _cluster;
  }

  public String getTag()
  {
    return _tag;
  }
  
  public UpdatePodBuilder tag(String tag)
  {
    _tag = tag;
    
    return this;
  }

  public int getDepth()
  {
    return _depth;
  }
  
  public UpdatePodBuilder pod(ServerPod[] servers)
  {
    _servers = servers;
    
    return this;
  }

  public void setRoundRobin(int roundRobinIndex)
  {
    _roundRobin = roundRobinIndex;
  }

  public void server(String address, int port)
  {
    server(address, port, "");
    /*
    if (address == null || "".equals(address)) {
      address = "127.0.0.1";
    }
    
    _serverList.add(address + ":" + port);
    */
  }

  public void server(String address, int port, String machine)
  {
    if (address == null || "".equals(address)) {
      address = "127.0.0.1";
    }
    
    String serverId = address + ":" + port;
    
    ServerBartender server = _cluster.findServerByName(serverId);
    
    if (server == null && machine != null && ! machine.isEmpty()) {
      server = _cluster.findServerByMachine(machine);
    }
    
    ServerPod serverPod = new ServerPod(_serverList.size());
    serverPod.setServer(server);
    /*
    serverPod.setHintServerId(address + ":" + port);
    serverPod.setHintMachineHash(machine);
    */
        
    //_serverList.add(address + ":" + port);
    _serverList.add(serverPod);
  }
  
  ServerPod []buildServers(RackHeartbeat rack, int count)
  {
    if (_serverList.size() > 0) {
      ServerPod []servers = new ServerPod[count];
      
      for (int i = 0; i < count; i++) {
        if (i < _serverList.size()) {
          servers[i] = _serverList.get(i);
        }
        else {
          servers[i] = new ServerPod(i);
        }
      }
      
      return servers;
    }
    
    if (rack.getServerLength() == 0) {
      throw new IllegalStateException(L.l("Uninitialized rack"));
    }
    
    ServerPod []servers = new ServerPod[count];
    
    for (int i = 0; i < count; i++) {
      int indexServer = (i + _roundRobin) % count;
      
      ServerBartender server = rack.getServer(indexServer);

      if (server != null) {
        servers[i] = new ServerPod(i, server);
      }
      else {
        servers[i] = new ServerPod(i);
      }
    }
    
    return servers;
  }

  public ServerPod []getServers()
  {
    return _servers;
  }

  public void type(PodType type)
  {
    _type = type;
    
    if (_primaryServerCount <= 0) {
      switch (type) {
      case auto:
      case off:
        primaryCount(0);
        break;
      case web:
        primaryCount(1);
        _depth = 1;
        _vnodeCount = 1;
        break;
      case solo:
      case lazy:
        primaryCount(1);
        break;
      case pair:
        primaryCount(2);
        break;
      case triad:
        primaryCount(3);
        break;
      case cluster:
        primaryCount(3);
        break;
      default:
        System.out.println(getClass().getSimpleName() + " UNKNOWN TYPE: " + type);
        break;
      }
    }
  }

  public PodBartender.PodType getType()
  {
    return _type ;
  }

  public long getSequence()
  {
    return _sequence;
  }
  
  public void sequence(long sequence)
  {
    _sequence = sequence;
  }
  
  public UpdatePodBuilder primaryCount(int count)
  {
    if (count < 1) {
      _primaryServerCount = 0;
      _nodeCount = 0;
      _type = PodType.off;
      return this;
    }
    
    _primaryServerCount = count;
    
    if (count < 3) {
      _nodeCount = _primaryServerCount;
    }
    else {
      _nodeCount = 2 * _primaryServerCount;
    }
    
    PodType type = _type;
    
    if ((type == null || type == PodType.lazy)
        && "web".equals(_name)) {
      _type = type = PodType.web;
    }
    
    if (type == null || type == PodType.lazy) {
      switch (count) {
      case 1:
        _type = PodType.solo;
        break;
      case 2:
        _type = PodType.pair;
        break;
      case 3:
        _type = PodType.triad;
        break;
      default:
        _type = PodType.cluster;
        break;
      }
    }
    
    return this;
  }
  
  public UpdatePodBuilder depth(int depth)
  {
    if (depth < 1) {
      throw new IllegalArgumentException(String.valueOf(depth));
    }
    
    _depth = depth;
    
    return this;
  }
  
  //
  // building code
  //
  
  /**
   * Builds the configured pod.
   */
  public UpdatePod build()
  {
    if (getServers() == null) {
      int count = Math.max(_primaryServerCount, _depth);

      if (_type == PodType.off) {
        count = 0;
      }
      
      _servers = buildServers(count);
    }
    
    Objects.requireNonNull(getServers());
    /*
    if (getServers().length < _primaryServerCount) {
      throw new IllegalStateException();
    }
    */
    
    return new UpdatePod(this);
  }
  
  /**
   * Create cluster pods using the configuration as a hint. Both the cluster
   * and cluster_hub pods use this.
   */
  private ServerPod []buildServers(int serverCount)
  {
    ArrayList<ServerPod> serversPod = new ArrayList<>();

    for (int i = 0; i < serverCount; i++) {
      //ServerPod serverPod = new ServerPod(i);
      
      //serversPod.add(serverPod);
      
      if (i < _serverList.size()) {
        serversPod.add(_serverList.get(i));
      }
      else {
        serversPod.add(new ServerPod(i));
      }
      
      /*        ServerPod serverHint = _serverList.get(i);
        
        serverPod.setHint(serverHint.getHint());

        if (server == null) {
        }
        else if (server.startsWith("127")) {
          int p = server.indexOf(':');
          int port = Integer.parseInt(server.substring(p + 1));
          
          serverPod.setHintPort(port);
        }
        else {
          serverPod.setHintServerId(server);
        }
        
        ServerBartender server = _cluster.findServerByName(serverHint.getServerId());
        System.out.println("BS22: " + server + " " + serverHint + " " + serverHint.getServerId() + " " + serverHint.getHint());
        serverPod.setServer(server);
      }
        */
    }
    
    ServerPod []serverArray = new ServerPod[serverCount];
    for (int i = 0; i < serverCount; i++) {
      serverArray[i] = serversPod.get(i);
    }
    
    return serverArray;
  }

  int getNodeCount()
  {
    return _nodeCount;
  }

  int getVnodeCount()
  {
    if (_vnodeCount > 0) {
      return _vnodeCount;
    }
    
    switch (_type) {
    case solo:
    case lazy:
    case off:
      return 1;
    case pair:
      return 4;
    case triad:
      return 64;
    default:
      return 256;
    }
  }
  
  UpdateNode[] buildNodes()
  {
    UpdateNode []nodes = new UpdateNode[_nodeCount];
    
    int [][]vnodes = calculateVnodes();
    
    for (int i = 0; i < nodes.length; i++) {
      int []owners = calculateOwners(i); 
      
      nodes[i] = new UpdateNode(owners, vnodes[i]);
    }
    
    return nodes;
  }
  
  private int [][] calculateVnodes()
  {
    ArrayList<Integer> []vnodes = new ArrayList[_nodeCount];
    
    for (int i = 0; i < vnodes.length; i++) {
      vnodes[i] = new ArrayList<>();
    }
    
    for (int i = 0; i < getVnodeCount(); i++) {
      addVnode(vnodes, i);
    }
    
    int [][]vnodeArray = new int[_nodeCount][];
    
    for (int i = 0; i < _nodeCount; i++) {
      int []nodeVnodes = new int[vnodes[i].size()];
      vnodeArray[i] = nodeVnodes;
      
      for (int j = 0; j < nodeVnodes.length; j++) {
        nodeVnodes[j] = vnodes[i].get(j);
      }
    }
    
    return vnodeArray;
  }
  
  private void addVnode(ArrayList<Integer> []vnodes, int index)
  {
    int bestCost = Integer.MAX_VALUE;
    int bestIndex = 0;
    
    for (int i = 0; i < vnodes.length; i++) {
      if (vnodes[i].size() < bestCost) {
        bestIndex = i;
        bestCost = vnodes[i].size();
      }
    }
    
    vnodes[bestIndex].add(index);
  }
  
  private int []calculateOwners(int i)
  {
    int primaryServerCount = _primaryServerCount;
    
    int owners[] = new int[_depth];
    int serverCount = _servers.length;
    
    if (serverCount == 0) {
      return owners;
    }
    
    
    int primary = i % primaryServerCount;
    owners[0] = primary;
    
    int hash = i / primaryServerCount;
    
    int secondary = (primary + ((hash % 2) == 0 ? 1 : (serverCount - 1))) % serverCount;
    int tertiary = (primary + ((hash % 2) != 0 ? 1 : (serverCount - 1))) % serverCount;

    if (primaryServerCount < 2) {
      secondary = 1;
      tertiary = 2;
    }
    else if (primaryServerCount < 3) {
      secondary = (i + 1) % 2;
      tertiary = 2;
    }
    
    int len = owners.length;
    
    if (len > 1) {
      owners[1] = secondary % serverCount;
    
      if (len > 2) {
        owners[2] = tertiary % serverCount;
      }
    }
    
    for (int j = 3; j < owners.length; j++) {
      owners[j] = (i + j) % owners.length;
    }
    
    return owners;
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName() + "[]");
  }
}
