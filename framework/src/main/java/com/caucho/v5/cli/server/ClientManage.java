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
 * @author Alex Rojkov
 */

package com.caucho.v5.cli.server;

import java.util.Objects;

import com.caucho.v5.baratine.client.BaratineClient;
import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.hamp.ClientBartenderFactory;
import com.caucho.v5.cli.daemon.ArgsDaemon;
import com.caucho.v5.cli.daemon.ArgsDaemon.ClusterId;
import com.caucho.v5.cli.daemon.ArgsDaemon.ServerAddress;
import com.caucho.v5.cli.daemon.ArgsDaemon.ServerId;
import com.caucho.v5.cli.daemon.ArgsDaemon.ServerPort;
import com.caucho.v5.cli.daemon.DaemonCommandBase;
import com.caucho.v5.util.L10N;

import io.baratine.service.ServiceExceptionConnect;
import io.baratine.service.ServiceExceptionUnavailable;

/**
 * Remote-based commands such as 'deploy' and 'cat' contact the server
 * through champ.
 */
public class ClientManage implements AutoCloseable
{
  private static final L10N L = new L10N(ClientManage.class);
  
  private ServiceManagerClient _client;

  // private ServerConfigBoot _server;

  /*
  public ClientManage(ArgsDaemon args) // , ConfigBoot boot)
  {
    this(args, findServer(args, boot));
  }    
    
  public ClientManage(ArgsDaemon args, ServerConfigBoot server)
  {
    if (server == null) {
      throw new IllegalArgumentException(L.l("Cannot find local server to manage"));
    }
    
    Objects.requireNonNull(server);
    
    _server = server;
    
    _client = createRampClient(args, server);
  }
  */
  
  public ClientManage(ArgsDaemon args)
  {
    _client = createRampClient(args);
  }

  public static void addBootOptions(DaemonCommandBase<?> command)
  {
    command.addOption(new ServerId()).tiny("s");
    command.addOption(new ServerAddress()).alias("address").tiny("sa").hide();
    command.addOption(new ServerPort()).alias("server-port").tiny("p").tiny("sp");
    command.addOption(new ClusterId());
  }
  
  /*
  public ServerConfigBoot getServer()
  {
    return _server;
  }
  */
  
  public ServiceManagerClient getAmp()
  {
    return _client;
  }

  private ServiceManagerClient createRampClient(ArgsDaemon args) // ServerConfigBoot server)
  {
    String user = args.getArg("user");
    String password = args.getArg("password");
    
    if (user == null || "".equals(user)) {
      user = "";
      // server.getClusterSystemKey();
      password = args.config().get("bartender.clusterKey");
    }
    
    return createHampClient(args, user, password);
  }
  
  private ServiceManagerClient createHampClient(ArgsDaemon args,
                                                String userName,
                                                String password)
  {
    ServiceManagerClient hampClient = null;
    
    String address = args.config().get("server.address", "127.0.0.1");
    int port = args.config().get("server.port", int.class, 8080);
    
    hampClient = createChampClient(address,  port, userName, password);
    
    if (hampClient != null) {
      return hampClient;
    }
    
    return createHampClient(address, port, userName, password);
  }
  
  private BaratineClient createHampClient(String address,
                                           int port,
                                           String userName,
                                           String password)
  {
    Objects.requireNonNull(address);
    /*
    if (address == null) {
      address = "127.0.0.1";
    }
    */

    String url = "http://" + address + ":" + port + "/hamp";
    
    BaratineClient client = new BaratineClient(url, userName, password);
    try {
      //client.setVirtualHost("admin.resin");

      // client.connect(userName, password);

      return client;
    } catch (ServiceExceptionConnect e) {
      throw new ServiceExceptionConnect(L.l("Connection to '{0}' failed for remote administration.\n  Ensure the local server has started, or include --server and --port parameters to connect to a remote server.\n  {1}",
                                                    url, e.getMessage()), e);
    } catch (ServiceExceptionUnavailable e) {
      throw new ServiceExceptionUnavailable(L.l("Connection to '{0}' failed for remote administration because RemoteAdminService (HMTP) is not enabled.\n  Ensure 'remote_admin_enable' is set true in resin.properties.\n  {1}",
                                                       url, e.getMessage()), e);
    }
  }
  
  
  private ServiceManagerClient createChampClient(String address,
                                                 int port,
                                       String userName,
                                       String password)
  {
    /*
    String address = server.getAddress();
    int port = server.getPort();
    */
    
    if (address == null || "".equals(address)) {
      address = "127.0.0.1";
    }
    
    String url = "http://" + address + ":" + port;

    ClientBartenderFactory champFactory
      = new ClientBartenderFactory(url, userName, password);

    return champFactory.create();
  }
  
  /*
  private static ServerConfigBoot findServer(ArgsDaemon args,
                                             ConfigBoot boot)
  {
    ArrayList<ServerConfigBoot> serverList = boot.findRemoteServers(args);

    if (serverList.size() > 0) {
      return serverList.get(0);
    }
    else {
      return null;
    }
  }
  */
  
  @Override
  public void close()
  {
    ServiceManagerClient client = _client;
    _client = null;
    
    if (client != null) {
      client.close();
    }
  }
}
