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

import java.io.Serializable;
import java.util.Objects;

import com.caucho.v5.bartender.pod.PodBartender.PodType;
import com.caucho.v5.util.Crc64;
import com.caucho.v5.util.L10N;


@SuppressWarnings("serial")
public class UpdatePod implements Serializable, Comparable<UpdatePod>
{
  private static final L10N L = new L10N(UpdatePod.class);
  
  private final String _id;
  private final String _podName;
  private final String _clusterId;
  
  private final String _tag;
  
  private final String []_servers;
  
  private final int _vnodeCount;
  
  private final UpdateNode []_nodes;
  
  private final int []_ownersActive;
  
  private final long _sequence;
  private final long _crc;
  private final int _depth;
  private final PodType _type;
  
  @SuppressWarnings("unused")
  private UpdatePod()
  {
    _id = null;
    _podName = null;
    _clusterId = null;
    _tag = null;
    _servers = null;
    _nodes = null;
    _ownersActive = null;
    _sequence = 0;
    _depth = 0;
    _vnodeCount = 0;
    _type = PodBartender.PodType.off;
    _crc = 0;
  }
  
  UpdatePod(UpdatePodBuilder builder)
  {
    String podName = builder.getName();
    
    if (podName.indexOf('.') >= 0) {
      throw new IllegalStateException();
    }
    
    _podName = podName;

    if ("local".equals(podName)) {
      _clusterId = "local";
    }
    else {
      _clusterId = builder.getCluster().id();
    }
    
    _id = _podName + '.' + _clusterId;
    
    _tag = builder.getTag();
    
    _type = builder.getType();
    
    int depth = builder.getDepth();
    long sequence = builder.getSequence();
    
    if (PodType.off == _type) {
      _servers = new String[0];
      _nodes = new UpdateNode[0];
      _ownersActive = new int[0];
      _vnodeCount = 0;
      _depth = 0;
    }
    else {
      int serverCount = builder.getServers().length;
    
      _servers = new String[serverCount];

      _nodes = builder.buildNodes();
      _ownersActive = new int[_nodes.length];

      _vnodeCount = builder.getVnodeCount();
      
      _depth = depth;
    }
    
    for (int i = 0; i < _servers.length; i++) {
      ServerPod server = builder.getServers()[i];
      
      String serverId = server.getServerId();
      
      _servers[i] = serverId;
    }
    
    _sequence = sequence;
    _crc = calculateCrc();
  }
  
  public UpdatePod(UpdatePod updateOld, String[] serversNew, long sequence)
  {
    _id = updateOld._id;
    _podName = updateOld._podName;
    _clusterId = updateOld._clusterId;
    _tag = updateOld._tag;
    _nodes = updateOld._nodes;
    _ownersActive = updateOld._ownersActive;
    _depth = updateOld._depth;
    _type = updateOld._type;
    _vnodeCount = updateOld._vnodeCount;

    _servers = serversNew;
    _sequence = sequence;
    _crc = calculateCrc();
  }
  
  public UpdatePod(UpdatePod updateOld, long sequence)
  {
    this(updateOld, updateOld._servers, sequence);
  }

  private long calculateCrc()
  {
    long crc = 0;
    
    crc = Crc64.generate(crc, _podName);
    crc = Crc64.generate(crc, _clusterId);
    crc = Crc64.generate(crc, _tag);
    crc = Crc64.generate(crc, _type);
    
    crc = Crc64.generate(crc, _servers.length);
    
    for (String server : _servers) {
      crc = Crc64.generate(crc, server);
    }
    
    crc = Crc64.generate(crc, _nodes.length);
    crc = Crc64.generate(crc, _depth);
    
    for (int i = 0; i < _nodes.length; i++) {
      UpdateNode node = _nodes[i];
      int []owners = node.getServers();
      
      for (int j = 0; j < owners.length; j++) {
        crc = Crc64.generate(crc, owners[j]);
      }
      
      // crc = Crc64.generate(crc, _ownersActive[i]);
    }

    return crc;
  }
  
  public String getId()
  {
    return _id;
  }
  
  public String getPodName()
  {
    return _podName;
  }
  
  public String getClusterId()
  {
    return _clusterId;
  }

  public String getTag()
  {
    return _tag;
  }

  public PodBartender.PodType getType()
  {
    return _type;
  }
  
  public String []getServers()
  {
    return _servers;
  }

  public int getDepth()
  {
    return _depth;
  }

  public int getNodeCount()
  {
    return _nodes.length;
  }

  public int getVnodeCount()
  {
    return _vnodeCount;
  }
  
  public int indexOfVnode(int vnode)
  {
    for (int i = 0; i < _nodes.length; i++) {
      if (_nodes[i].containsVnode(vnode)) {
        return i;
      }
    }
    
    throw new IllegalArgumentException(L.l("vnode {0} is not in any node",
                                           vnode));
  }

  public int[] getShard(int i)
  {
    return getNode(i).getServers();
  }

  public UpdateNode getNode(int i)
  {
    return _nodes[i];
  }

  public long getCrc()
  {
    return _crc;
  }

  public long getSequence()
  {
    return _sequence;
  }
  
  @Override
  public int compareTo(UpdatePod pod)
  {
    if (pod == null) {
      return -1;
    }
    
    int cmp = Long.signum(_sequence - pod._sequence);
    
    if (cmp != 0) {
      return cmp;
    }
    
    return Long.signum(_crc - pod._crc);
  }
  
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append(getClass().getSimpleName());
    sb.append("[");
    
    sb.append(_podName);
    sb.append('.');
    sb.append(_clusterId);
    
    sb.append(",").append(_type);
    sb.append(",nodes=").append(_nodes.length);
    sb.append(",seq=").append(_sequence);
    sb.append(",crc=").append(Long.toHexString(_crc));
    
    sb.append("]");
    
    return sb.toString();
  }
  
  public static class UpdateNode implements Serializable {
    private final int []_servers;
    private final int []_vnodes;
    
    UpdateNode(int []servers, int []vnodes)
    {
      Objects.requireNonNull(servers);
      _servers = servers;
      
      Objects.requireNonNull(vnodes);
      _vnodes = vnodes;
    }
    
    public boolean containsVnode(int vnode)
    {
      for (int i = 0; i < _vnodes.length; i++) {
        if (_vnodes[i] == vnode) {
          return true;
        }
      }
      
      return false;
    }

    public int []getServers()
    {
      return _servers;
    }
    
    public int []getVnodes()
    {
      return _vnodes;
    }
  }
}
