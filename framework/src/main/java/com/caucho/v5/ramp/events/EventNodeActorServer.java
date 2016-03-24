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
import java.util.logging.Logger;

/**
 * actor to handle inbound calls.
 */
class EventNodeActorServer extends EventNodeAsset
{
  private static final Logger log = Logger.getLogger(EventNodeActorServer.class.getName());
  
  private String _podName;

  private EventServerImpl _eventServer;
  
  private final ArrayList<EventNodeServer> _remoteList = new ArrayList<>();

  public EventNodeActorServer(SchemeEventImpl root,
                               String podName,
                               String address)
  {
    super(root, address);
    
    _podName = podName;
    
    _eventServer = root.getEventServer();
  }

  @Override
  void subscribe(EventNodeServer eventNodeRemote)
  {
    _remoteList.add(eventNodeRemote);
  }
  
  @Override
  void publishFromRemote(String methodName, Object[] args)
  {
    publish(methodName, args);
  }

  @Override
  public void publish(String methodName, Object[] args)
  {
    super.publish(methodName, args);
    
    for (EventNodeServer node : _remoteList) {
      node.publish(methodName, args);
    }
  }
}
