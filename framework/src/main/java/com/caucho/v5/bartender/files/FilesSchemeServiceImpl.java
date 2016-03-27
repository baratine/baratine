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

package com.caucho.v5.bartender.files;

import io.baratine.service.OnLookup;

import java.util.HashMap;
import java.util.Objects;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.loader.ContextClassLoader;
import com.caucho.v5.loader.EnvLoader;

/**
 * Entry to the filesystem.
 */
public class FilesSchemeServiceImpl
{
  private final FileServiceRoot _root;
  private final HashMap<String,FileServiceRoot> _podMap = new HashMap<>();
  private ServicesAmp _ampManager;
  
  FilesSchemeServiceImpl(FileServiceRoot root)
  {
    Objects.requireNonNull(root);
    
    _root = root;
    
    _podMap.put(_root.getPodName(), root);
    
    _ampManager = AmpSystem.currentManager();
    
    Objects.requireNonNull(_ampManager);
  }
  
  @OnLookup
  public Object lookup(String path)
  {
    if (! path.startsWith("//")) {
      return _root.lookup(path);
    }
    
    int p = path.indexOf('/', 2);
    
    String podName;
    String tail;
    
    if (p >= 0) {
      podName = path.substring(2, p);
      tail = path.substring(p);
    }
    else {
      podName = path.substring(2);
      tail = "";
    }
    
    String podId = podName;
    
    if (podId.equals("")) {
      return _root.lookup(tail);
    }
    
    if (podId.indexOf('.') < 0) {
      PodBartender pod = BartenderSystem.current().findPod(podId);
      
      podId = podId + "." + pod.getClusterId();
    }
    
    FileServiceRoot rootPod = _podMap.get(podId);
    
    if (rootPod != null) {
      ServiceRefAmp rootPodRef = ServiceRefAmp.toServiceRef(rootPod);

      return rootPodRef.onLookup(tail); 
    }
    
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(_ampManager.classLoader());

      FileServiceBuilder builder = new FileServiceBuilder();
      builder.address("bfs://" + podName);
      builder.pod(podId);
      
      FileServiceRootImpl fileRootPodImpl = builder.build();
    
      FileServiceRoot fileRoot = fileRootPodImpl.getService();
    
      _podMap.put(fileRoot.getPodName(), fileRoot);
      
      FileServiceBuilder builderNodes = new FileServiceBuilder();
      builderNodes.address("bfs://" + podName + "/nodes");
      builderNodes.prefix("/nodes");
      builderNodes.pod(podId);
      builderNodes.hash(new FileHashNodes("/nodes/"));
      
      FileServiceRootImpl fileRootNodes = builderNodes.build();
    
      //FileServiceRoot fileRoot = fileRootPodImpl.getService();
      // return fileRoot.lookup(tail);
      return _ampManager.service("bfs:" + path);
    } finally{
      thread.setContextClassLoader(oldLoader);
    }
  }

  public void addPod(String pod, FileServiceRoot service)
  {
    _podMap.put(pod, service);
  }
}
