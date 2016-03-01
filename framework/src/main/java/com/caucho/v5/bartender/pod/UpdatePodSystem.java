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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

import com.caucho.v5.util.Crc64;
import com.caucho.v5.util.CurrentTime;


@SuppressWarnings("serial")
public class UpdatePodSystem implements Serializable
{
  private final UpdatePod []_pods;
  
  private final long _sequence;
  private final long _crc;
  
  @SuppressWarnings("unused")
  private UpdatePodSystem()
  {
    _pods = null;
    _sequence = 0;
    _crc = 0;
  }
  
  public UpdatePodSystem(Collection<UpdatePod> pods,
                         UpdatePodSystem oldSystem)
  {
    ArrayList<UpdatePod> updatePods = new ArrayList<>();
    
    long now = CurrentTime.getCurrentTime();
    
    long sequence = now;
    
    if (oldSystem != null) {
      sequence = Math.max(oldSystem.getSequence() + 1, sequence);
    }
    
    updatePods.addAll(pods);
    /*
    for (PodBartenderImpl pod : pods) {
      if ("local".equals(pod.getName())) {
        continue;
      }
      
      updatePods.add(pod.getUpdate());
    }
    */
    
    // for a canonical crc, the pods are sorted.
    Collections.sort(updatePods, new UpdatePodComparator());
    
    _pods = new UpdatePod[updatePods.size()];
    updatePods.toArray(_pods);
    
    long crc = 0;
    
    for (UpdatePod update : _pods) {
      crc = Crc64.generate(crc, update.getCrc());
    }
    
    _crc = crc;
    
    _sequence = sequence;
  }

  public UpdatePod []getPods()
  {
    return _pods;
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
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append(getClass().getSimpleName());
    sb.append("[");
    
    sb.append(",seq=").append(_sequence);
    sb.append(",crc=").append(Long.toHexString(_crc));
    
    sb.append("]");
    
    return sb.toString();
  }
  
  private static class UpdatePodComparator implements Comparator<UpdatePod>
  {
    public int compare(UpdatePod a, UpdatePod b)
    {
      return a.getPodName().compareTo(b.getPodName());
    }
  }
}
