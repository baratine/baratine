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

package com.caucho.v5.ramp.jamp;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.link.PodAppSupplier;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.http.pod.PodApp;
import com.caucho.v5.http.pod.PodContainer;
import com.caucho.v5.util.L10N;

/**
 * Manages the supported pods for the jamp servlet.
 */
public class JampPodManagerPods extends JampPodManager
{
  private static final L10N L = new L10N(JampPodManagerPods.class);
  
  JampPodManagerPods()
  {
  }

  /**
   * pod://pod-name is the default authority for a request to /my-service.
   */
  @Override
  protected String getAuthority(String podName)
  {
    return "pod://" + podName;
  }

  @Override
  protected ServiceManagerAmp createAmpManager(String podName)
  {
    if (podName == null || "".equals(podName) || "null".equals(podName)) {
      throw new ConfigException(L.l("'{0}' is an invalid pod-name in {1}",
                                    podName, getClass().getSimpleName()));
    }
    
    BartenderSystem bartender = BartenderSystem.current();
    
    PodBartender pod = bartender.findPod(podName);
    
    NodePodAmp localNode = findLocalNode(pod);

    if (localNode == null) {
      // baratine/8112
      
      //return super.createAmpManager(podName);
      
      return null;
      //throw new IllegalStateException("pod forwarding is disabled");
    }

    int podIndex = localNode.nodeIndex();

    PodContainer podContainer = PodContainer.getCurrent();

    String podNodeName = podName + "." + podIndex;

    PodAppSupplier podAppSupplier
      = new PodAppSupplier(podContainer, podNodeName);

    return podAppSupplier.get();
  }
  
  private NodePodAmp findLocalNode(PodBartender pod)
  {
    ServerBartender selfServer = BartenderSystem.getCurrentSelfServer();
    
    if (pod == null) {
      return null;
    }
    
    int nodeCount = pod.nodeCount();
    
    // baratine/a140
    for (int i = 0; i < nodeCount; i++) {
      NodePodAmp node = pod.getNode(i);

      if (isLocalNode(node, selfServer)) {
        return node;
      }
    }
    
    return null;
  }
  
  private boolean isLocalNode(NodePodAmp node, ServerBartender selfServer)
  {
    for (int j = 0; j < node.serverCount(); j++) {
      ServerBartender server = node.server(j);

      if (server == null) {
        continue;
      }
      else if (server.isSameServer(selfServer)) {
        return true;
      }
      else if (server.isUp()) {
        return false;
      }
    }
    
    return false;
  }

  @Override
  protected String getPodName(String pathInfo)
  {
    if (pathInfo == null) {
      return "";
    }
    
    int p = pathInfo.indexOf('/', 1);
    
    if (p > 0) {
      return pathInfo.substring(1, p);
    }
    else {
      return pathInfo.substring(1);
    }
  }
}
