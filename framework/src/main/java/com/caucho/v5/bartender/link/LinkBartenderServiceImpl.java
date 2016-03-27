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

package com.caucho.v5.bartender.link;

import io.baratine.service.OnActive;
import io.baratine.service.Service;
import io.baratine.service.Services;

import java.util.HashMap;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.bartender.ServerBartender;

/**
 * Actor for system requests.
 */
@Service
//@NonBlocking
class LinkBartenderServiceImpl
{
  private final ServerBartender _selfServer;
  
  private LinkBartenderEvents _linkEventPublisher;
  
  private HashMap<String,LinkBartender> _linkMap = new HashMap<>();
  
  LinkBartenderServiceImpl(ServerBartender selfServer)
  {
    _selfServer = selfServer;
  }
  
  @OnActive
  public void onActive()
  {
    Services manager = AmpSystem.currentManager();
    
    _linkEventPublisher = manager.service(LinkBartenderEvents.ADDRESS)
                                 .as(LinkBartenderEvents.class);
  }

  public void onConnectionOpen(String peerHostName)
  {
    LinkBartender link = getLinkBartender(peerHostName);
    
    int count = link.incrementAndGet();
  }

  public void onConnectionClose(String peerHostName)
  {
    LinkBartender link = getLinkBartender(peerHostName);
    
    int count = link.decrementAndGet();

    if (count == 0) {
      _linkEventPublisher.onLinkClose(peerHostName, count);
    }
  }
  
  private LinkBartender getLinkBartender(String id)
  {
    LinkBartender link = _linkMap.get(id);
    
    if (link == null) {
      link = new LinkBartender(id);
      
      _linkMap.put(id, link);
    }
    
    return link;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _selfServer + "]";
  }
  
  private class LinkBartender {
    private final String _id;
    
    private int _count;
    
    LinkBartender(String id)
    {
      _id = id;
    }
    
    public int incrementAndGet()
    {
      return ++_count;
    }
    
    public int decrementAndGet()
    {
      return --_count;
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _id + "]";
    }
  }
 
}
