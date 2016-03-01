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

package com.caucho.v5.bartender.heartbeat;

import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.service.Result.Fork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.hamp.ClientBartenderFactory;

/**
 * Handles pinging the remote IP interface of the servers the internal
 * name.
 */
class JoinClient
{
  private static final Logger log
    = Logger.getLogger(JoinClient.class.getName());
  
  private ServerSelf _selfServer;

  private RootHeartbeat _root;
  
  // private int _joinCount;

  //private HeartbeatLocalImpl _heartbeatImpl;

  public JoinClient(ServerSelf serverSelf,
                    RootHeartbeat root,
                    HeartbeatLocalImpl heartbeatImpl)
  {
    Objects.requireNonNull(serverSelf);
    Objects.requireNonNull(root);
    Objects.requireNonNull(heartbeatImpl);
    
    _selfServer = serverSelf;
    _root = root;
    //_heartbeatImpl = heartbeatImpl;
  }
  
  /*
  public int getJoinServerCount()
  {
    return _joinCount;
  }
  */
  
  public void start(Consumer<UpdateRackHeartbeat> consumer,
                    Result<Integer> result)
  {
    if (_selfServer.port() < 0) {
      result.ok(0);
      return;
    }
    
    // boolean isPendingStart = false;
    
    ArrayList<ClusterHeartbeat> clusterList = new ArrayList<>();
    for (ClusterHeartbeat cluster : _root.getClusters()) {
      clusterList.add(cluster);
    }
    
    if (clusterList.size() == 0) {
      result.ok(0);
      return;
    }
    
    /*
    Result<Integer>[]resultFork = result.fork(clusterList.size(),
                                              (x,r)->addInt(x,null,r),
                                              (x,y,r)->addInt(x,y,r));
                                              */
    Fork<Integer,Integer> fork = result.fork();
    
    for (int i = 0; i < clusterList.size(); i++) {
      ClusterHeartbeat cluster = clusterList.get(i);
      
      startCluster(cluster, consumer, fork.branch()); // resultFork[i]);
    }
    
    fork.fail((v,f,r)->addInt(v,f,r));
    fork.join((v,r)->addInt(v,null,r));
    
    /*
    for (ClusterHeartbeat cluster : _root.getClusters()) {
      if (! _selfServer.getClusterId().equals(cluster.getId())) {
        startCluster(cluster, cont);
      }
    }
    */
  }
  
  private static void addInt(List<Integer> values, 
                             List<Throwable> fails, 
                             Result<Integer> result)
  {
    int count = 0;
    
    for (int i = 0; i < values.size(); i++) {
      Integer value = values.get(i);
      
      if (value != null) {
        count += value;
      }
    }
    
    result.ok(count);
  }
  
  private void startCluster(ClusterHeartbeat cluster, 
                            Consumer<UpdateRackHeartbeat> consumer,
                            Result<Integer> cont)
  {
    ArrayList<ServerHeartbeat> servers = new ArrayList<>();
    
    for (ServerHeartbeat server : cluster.getSeedServers()) {
      if (server.port() > 0) {
        servers.add(server);
      }
    }
    
    if (servers.size() == 0) {
      cont.ok(0);
      return;
    }
    
    Collections.sort(servers, new StartComparator());
    JoinFuture future = new JoinFuture(servers.size(), cont);

    for (ServerHeartbeat server : servers) {
      startServer(server, cluster, consumer, future);
    }
  }

  /*
  private boolean isSeedServer(ServerBartender server)
  {
    return (! "".equals(server.getAddress()) && server.getPort() > 0);
  }
  */
  
  private void startServer(ServerHeartbeat server,
                           ClusterHeartbeat cluster,
                           Consumer<UpdateRackHeartbeat> consumer,
                           JoinFuture future)
  {
    String address = server.getAddress();
    int port = server.port();

    if (port <= 0) {
      throw new IllegalStateException(String.valueOf(server));
    }
    
    if ("".equals(address)) {
      address = "127.0.0.1";
    }
    
    int seedIndex = getSeedIndex(server, cluster);
    
    ServiceManagerClient client = createBartenderClient(server); // address, port);
    
    /*
    ServerHeartbeat serverHeartbeat = (ServerHeartbeat) server;
    int portBartender = serverHeartbeat.getPortBartender();
    */

    JoinServerResult result = new JoinServerResult(future,
                                                   client,
                                                   consumer);
    
    try {
      HeartbeatService heartbeat = client.service("remote://" + HeartbeatService.PATH)
                                         .as(HeartbeatService.class);

      heartbeat.join(address, port,
                     _selfServer.getClusterId(),
                     _selfServer.getAddress(),
                     _selfServer.port(),
                     _selfServer.getPortBartender(),
                     _selfServer.getDisplayName(),
                     _selfServer.getMachineHash(),
                     seedIndex,
                     result);
    } catch (Throwable e) {
      result.fail(e);
    }
  }
  
  private int getSeedIndex(ServerBartender server, ClusterHeartbeat cluster)
  {
    int index = 0;
    
    for (ServerBartender seedServer : cluster.getSeedServers()) {
      if (seedServer.getAddress().isEmpty()
          || seedServer.getAddress().startsWith("127")) {
        continue;
      }
      
      index++;
      
      if (seedServer == server) {
        return index; 
      }
    }
    
    return 0;
  }
  
  private ServiceManagerClient createBartenderClient(ServerHeartbeat server)
  {
    String url;
    
    if (server.getPortBartender() > 0) {
      url = "bartender://" + server.getAddress() + ":" + server.getPortBartender() + "/bartender";
    }
    else {
      String scheme = "http";
      
      if (server.isSSL()) {
        scheme = "https";
      }
      
      url = scheme + "://" + server.getAddress() + ":" + server.port() + "/bartender";
    }
    
    return new ClientBartenderFactory(url).create();
  }
  
  private class StartComparator implements Comparator<ServerHeartbeat> {
    @Override
    public int compare(ServerHeartbeat a, ServerHeartbeat b)
    {
      int portA = a.port();
      int portB = b.port();
      
      int portBarA = a.getPortBartender();
      int portBarB = b.getPortBartender();
      
      String addressA = a.getAddress();
      String addressB = b.getAddress();
      
      if (portBarA == _selfServer.port()) {
        portBarA = 0;
      }
      else if (portBarA <= 0) {
        portBarA = Integer.MAX_VALUE / 4;
      }
      
      if (portBarB == _selfServer.port()) {
        portBarB = 0;
      }
      else if (portBarB <= 0) {
        portBarB = Integer.MAX_VALUE / 4;
      }
      
      int cmp = portBarA - portBarB;
      
      if (cmp != 0) {
        return cmp;
      }
      
      if (portA == _selfServer.port()) {
        portA = 0;
      }
      
      if (portB == _selfServer.port()) {
        portB = 0;
      }
      
      cmp = portA - portB;
      
      if (cmp != 0) {
        return cmp;
      }
      
      if (addressA.equals(_selfServer.getAddress())) {
        addressA = "";
      }
      
      if (addressB.equals(_selfServer.getAddress())) {
        addressB = "";
      }
      
      return addressA.compareTo(addressB);
    }
  }
  
  private class JoinServerResult extends Result.Wrapper<UpdateRackHeartbeat, UpdateRackHeartbeat> {
    private final ServiceManagerClient _client;
    private final Consumer<UpdateRackHeartbeat> _consumer;
    
    JoinServerResult(JoinFuture future, 
                     ServiceManagerClient client,
                     Consumer<UpdateRackHeartbeat> consumer)
    {
      super(future);

      _client = client;
      _consumer = consumer;
    }

    @Override
    public void ok(UpdateRackHeartbeat result)
    {
      // update rack and count only for foreign (non-self) join
      if (result != null) {
        // _joinCount++;
      
        _consumer.accept(result);
      }
      
      // System.out.println("COMPLETE: " + result);
      try {
        getNext().ok(result);
      } finally {
        _client.close();
      }
    }

    @Override
    public void fail(Throwable exn)
    {
      log.finer("Join to " + _client + " failed: " + exn);
      // exn.printStackTrace();
      
      try {
        super.fail(exn);
      } finally {
        _client.close();
      }
    }
  }
  
  private static class JoinFuture implements Result<UpdateRackHeartbeat> {
    private final int _serverCount;
    private final Result<Integer> _cont;
    
    private boolean _isCompleted;
    
    private int _successCount;
    private int _remoteCount;
    private int _failedCount;
    
    JoinFuture(int serverCount, Result<Integer> cont)
    {
      _serverCount = serverCount;
      _cont = cont;
    }
    
    @Override
    public void ok(UpdateRackHeartbeat result)
    {
      _successCount++;
      
      if (result != null) {
        _remoteCount++;
      }
      
      int count = _successCount + _failedCount;
      
      if ((_successCount > 1 || _serverCount <= count) && ! _isCompleted) {
        _isCompleted = true;
        
        if (_cont != null) {
          _cont.ok(_remoteCount);
        }
      }
    }
    
    @Override
    public void fail(Throwable exn)
    {
      _failedCount++;
      
      int count = _successCount + _failedCount;
      
      if (_serverCount <= count && ! _isCompleted) {
        _isCompleted = true;
        
        if (_cont != null) {
          _cont.fail(exn);
        }
      }
      
      log.finer(exn.toString());
      log.log(Level.FINEST, exn.toString(), exn);
    }
    
    @Override
    public void handle(UpdateRackHeartbeat result, Throwable exn)
    {
      if (exn != null) {
        fail(exn);
      }
      else {
        ok(result);
      }
    }
  }
}
