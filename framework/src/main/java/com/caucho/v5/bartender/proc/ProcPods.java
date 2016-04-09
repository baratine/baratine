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

import com.caucho.v5.baratine.ServiceApi;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.files.FileServiceBind;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.io.WriteBuffer;

import io.baratine.files.BfsFileSync;
import io.baratine.service.OnActive;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

/**
 * Entry to the filesystem.
 */
@ServiceApi(BfsFileSync.class)
public class ProcPods extends ProcFileBase
{
  private final BartenderSystem _bartender;
  private ServiceRef _serviceRef;
  
  public ProcPods(BartenderSystem bartender)
  {
    super("/pods");
    
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
    ArrayList<String> podNames = new ArrayList<>();
    
    for (PodBartender pod : _bartender.getPodService().getPods()) {
      podNames.add(pod.getId());
    }
    
    Collections.sort(podNames);
    
    String []podNameArray = podNames.toArray(new String[podNames.size()]);
    
    result.ok(podNameArray);
  }

  @Override
  public BfsFileSync lookup(String path)
  {
    if (path == null || path.equals("")) {
      return null;
    }
    
    String name = path.substring(1);
    
    PodBartender pod = _bartender.findActivePod(name);

    if (pod == null) {
      return null;
    }
    
    return _serviceRef.pin(new ProcPodBartender(pod)).as(FileServiceBind.class);
  }

  @Override
  protected boolean fillRead(WriteBuffer out)
    throws IOException
  {
    out.println("[");
    
    ArrayList<PodBartender> pods = new ArrayList<>();
    
    for (PodBartender pod: _bartender.getPodService().getPods()) {
      pods.add(pod);
    }
    
    Collections.sort(pods, (x,y)->x.getId().compareTo(y.getId()));
    
    boolean isFirstPod = true;
    for (PodBartender pod: pods) {
      if (! isFirstPod) {
        out.println(",");
      }
      isFirstPod = false;
      
      String podId = pod.name();
      String clusterId = pod.getClusterId();
      
      if ("local".equals(podId)) {
        clusterId = "local";
      }
      
      ProcPodBartender.fillRead(pod, out);
      
      /*
      out.println("{ \"pod\" : \"" + podId + "." + clusterId + "\",");
      out.println("  \"type\" : \"" + pod.getType() + "\",");
      out.println("  \"sequence\" : " + pod.getSequence() + ",");
      out.println("  \"servers\" : [");
      
      int count = pod.getServerCount();
      
      for (int i = 0; i < count; i++) {
        if (i > 0) {
          out.println(",");
        }
        
        ServerPod server = pod.getServer(i);
        
        if (server != null) {
          out.print("    \"" + server.getServerId() + "\"");
        }
        else {
          out.print("null");
        }
      }
      out.println();
      out.println("  ],");
      
      out.println("  \"nodes\" : [");
      
      int nodeCount = pod.getNodeCount();
      for (int i = 0; i < nodeCount; i++) {
        if (i > 0) {
          out.println(",");
        }
        
        NodePod node = pod.getNode(i);
        out.print("    [");
        
        for (int j = 0; j < pod.getDepth(); j++) {
          if (j != 0) {
            out.print(", ");
          }
          
          out.print(node.getOwner(j));
        }
        
        out.print("]");
      }
      out.println();
      out.println("  ]");
      
      out.print("}");
      */
    }
    
    out.println("]");
    
    return true;
  }
}
