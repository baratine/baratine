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

package com.caucho.v5.bartender.proc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import com.caucho.v5.baratine.ServiceApi;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ClusterBartender;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.files.FileServiceBind;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.io.WriteStream;

import io.baratine.files.BfsFileSync;
import io.baratine.service.OnActive;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

/**
 * Entry to the filesystem.
 */
@ServiceApi(BfsFileSync.class)
public class ProcServers extends ProcFileBase
{
  private final BartenderSystem _bartender;
  private ServiceRef _serviceRef;
  
  public ProcServers(BartenderSystem bartender)
  {
    super("/server");
    
    _bartender = bartender;
  }

  @OnActive
  public void onStart()
  {
    _serviceRef = ServiceRef.current();
  }
  
  @Override
  public void list(Result<String[]> result)
  {
    ArrayList<String> clusterNames = new ArrayList<>();
    
    for (ClusterBartender cluster :_bartender.getRoot().getClusters()) {
      clusterNames.add(cluster.id());
    }
    
    Collections.sort(clusterNames);
    
    String []clusterNameArray = new String[clusterNames.size()];
    clusterNames.toArray(clusterNameArray);
    
    result.ok(clusterNameArray);
  }

  @Override
  public BfsFileSync lookup(String path)
  {
    if (path == null || path.equals("")) {
      return null;
    }
    
    String name = path.substring(1);
    
    ServerBartender server = null;
    
    if ("self".equals(name)) {
      server = _bartender.serverSelf();
    }
    else {
      server = _bartender.findServerByName(name);
    }

    if (server instanceof ServerHeartbeat) {
      ServerHeartbeat serverHeartbeat = (ServerHeartbeat) server;
      
      return _serviceRef.pin(new ProcServersInstance(serverHeartbeat, name))
                        .as(FileServiceBind.class);
    }
    
    return null;
  }
  
  @Override
  protected boolean fillRead(WriteStream out)
    throws IOException
  {
    out.println("[");
    
    boolean isFirstCluster = true;
    for (ClusterBartender cluster : _bartender.getRoot().getClusters()) {
      if (! isFirstCluster) {
        out.println(",");
      }
      isFirstCluster = false;
      
      out.println("{ \"cluster\" : \"" + cluster.id() + "\",");
      out.println("  \"servers\" : [");
      
      ArrayList<ServerBartender> servers = new ArrayList<>();
      
      for (ServerBartender server : cluster.getServers()) {
        servers.add(server);
      }
      
      Collections.sort(servers, (x,y)->x.getId().compareTo(y.getId()));

      boolean isFirstServer = true;
      for (ServerBartender server : servers) {
        if (! isFirstServer) {
          out.println(",");
        }
        isFirstServer = false;
        
        ServerHeartbeat serverHeartbeat = (ServerHeartbeat) server;
        
        printServer(out, serverHeartbeat);
      }
      
      out.println("]");
      out.print("}");
    }
    
    out.println("]");
    
    return true;
  }
  
  private void printServer(WriteStream out, ServerHeartbeat server)
    throws IOException
  {
    out.print("  { \"server\" : \"" + server.getId() + "\"");

    String machine = server.getMachineHash();

    if (! machine.isEmpty()) {
      out.print(",\n    \"machine\" : \"" + machine + "\"");

    }

    if (server.getExternalId() != null) {
      out.print(",\n    \"ext\" : \"" + server.getExternalId() + "\"");
    }

    int seedIndex = server.getSeedIndex();
    if (seedIndex > 0) {
      out.print(",\n    \"seed\" : " + seedIndex);
    }

    //out.print(",\n    \"machine\" : \"" + Long.toHexString(serverHeartbeat.getMachineHash()) + "\"");
    //out.print(",\n    \"heartbeat\" : \"" + QDate.formatISO8601(serverHeartbeat.getLastHeartbeatTime()) + "\"");
    
    Set<String> podSet = server.getPodSet();
    
    if (podSet.size() > 0){
      out.print(",\n    \"pods\" : [");
      
      boolean isFirst = true;
      
      for (String pod : podSet) {
        if (! isFirst) {
          out.print(", ");;
        }
        isFirst = false;
        
        out.print("\"" + pod + "\"");
      }
      out.print("]");
    }
    
    if (! server.isPodAny()) {
      out.print(",\n    \"pod-any\" : false");
    }

    out.print(",\n    \"state\" : \"" + server.getState() + "\"");

    out.print("\n  }");
  }
}
