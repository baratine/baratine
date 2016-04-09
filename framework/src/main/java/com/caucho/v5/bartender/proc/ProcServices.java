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

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.baratine.ServiceApi;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ClusterBartender;
import com.caucho.v5.http.pod.PodApp;
import com.caucho.v5.http.pod.PodAppHandle;
import com.caucho.v5.http.pod.PodContainer;
import com.caucho.v5.http.pod.PodManagerApp;
import com.caucho.v5.io.WriteBuffer;

import io.baratine.files.BfsFileSync;
import io.baratine.service.Result;

/**
 * /proc/services
 */
@ServiceApi(BfsFileSync.class)
public class ProcServices extends ProcFileBase
{
  private final BartenderSystem _bartender;
  
  public ProcServices(BartenderSystem bartender)
  {
    super("/services");
    
    _bartender = bartender;
  }

  @Override
  public void list(Result<String[]> result)
  {
    ArrayList<String> clusterNames = new ArrayList<>();
    
    for (ClusterBartender cluster : _bartender.getRoot().getClusters()) {
      clusterNames.add(cluster.id());
    }
    
    Collections.sort(clusterNames);
    
    String []clusterNameArray = new String[clusterNames.size()];
    clusterNames.toArray(clusterNameArray);
    
    result.ok(clusterNameArray);
  }

  @Override
  protected boolean fillRead(WriteBuffer out)
    throws IOException
  {
    PodContainer podContainer = PodContainer.getCurrent();
    
    if (podContainer == null) {
      out.println("[]");
      return true;
    }
    
    out.print("[");
    
    boolean isFirstPod = true;
    for (PodAppHandle handle : podContainer.getPodAppHandles()) {
      PodManagerApp podManagerApp = handle.getDeployInstance();
      
      if (! (podManagerApp instanceof PodApp)) {
        continue;
      }
      
      PodApp podApp = (PodApp) podManagerApp;
      
      if (! isFirstPod) {
        out.print(",");
      }
      isFirstPod = false;
      
      out.println("\n{ \"pod\" : \"" + podApp.getId() + "\",");
        
      out.print("  \"services\" : [");
      boolean isFirstService = true;
      for (ServiceRefAmp service : podApp.getServiceList()) {
        if (! isFirstService) {
          out.print(",");
        }
        isFirstService = false;
        
        String address = service.address();
        
        if (! service.isPublic() && address.startsWith("public:")) {
          address = "pod:" + address.substring("public:".length());
        }
        
        out.println("\n  { \"service\" : \"" + address + "\",");
    
        if (! Object.class.equals(service.api().getType())) {
          out.println("    \"api\" : \"" + service.api().getType() + "\",");
        }

        out.println("    \"queue-size\" : \"" + service.inbox().getSize() + "\"");
        out.print("  }");
      }

      out.println("]");
      out.print("}");
    }
    
    out.println("]");
    
    return true;
  }
}
