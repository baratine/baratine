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

package com.caucho.v5.ramp.events;

import java.util.ArrayList;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.bartender.pod.PodBartender;

/**
 * Pod server for remote event calls.
 */
class EventNodeServer {
  private PodBartender _pod;
  
  private ArrayList<EventNodeClient> _clientList = new ArrayList<>();
  
  EventNodeServer(PodBartender pod)
  {
    _pod = pod;
  }

  public void addClient(String podName, int nodeIndex, String address)
  {
    addClient(new EventNodeClient(podName, nodeIndex, address));
  }

  private void addClient(EventNodeClient client)
  {
    if (! _clientList.contains(client)) {
      _clientList.add(client);
    }
  }

  public void publish(String methodName, Object[] args)
  {
    for (EventNodeClient client : _clientList) {
      client.publish(methodName, args);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _pod.getId() + "]";
  }

  private class EventNodeClient {
    private String _podName;
    private String _address;
    private int _nodeIndex;
    private EventServer _eventServer;

    EventNodeClient(String podName,
                    int nodeIndex,
                    String path)
    {
      _podName = podName;
      _nodeIndex = nodeIndex;
      _address = path;

      String eventsPath = "pod://" + podName + EventServerImpl.PATH;

      ServicesAmp manager = ServicesAmp.current();

      _eventServer = manager.service(eventsPath)
          .node(_nodeIndex)
          .as(EventServer.class);
    }

    public void publish(String methodName, Object[] args)
    {
      _eventServer.publish(_pod.getId(), _address, methodName, args);
    }
    
    @Override
    public int hashCode()
    {
      int hash = _podName.hashCode();
      
      hash = hash * 65521 + _address.hashCode();
      hash = hash * 65521 + _nodeIndex;
      
      return hash;
    }
    
    public boolean equals(Object o)
    {
      if (! (o instanceof EventNodeClient)) {
        return false;
      }
      
      EventNodeClient client = (EventNodeClient) o;
      
      if (! _podName.equals(client._podName)) {
        return false;
      }
      
      if (! _address.equals(client._address)) {
        return false;
      }
      
      if (_nodeIndex != client._nodeIndex) {
        return false;
      }
      
      return true;
    }
  }
}
