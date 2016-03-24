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

package com.caucho.v5.amp.remote;

import io.baratine.service.Direct;

import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Singleton;

import com.caucho.v5.util.Base64Web;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.RandomUtil;

/**
 * Manages session
 */
@Singleton
public class ChannelManagerServiceImpl implements ChannelManagerService
{
  private final AtomicLong _sequence;
  
  public ChannelManagerServiceImpl()
  {
    _sequence = new AtomicLong(CurrentTime.currentTime());
  }
  
  @Direct
  public String generateAddress(String path)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("//");
    
    sb.append(path);
    
    sb.append("/");
    
    Base64Web.encode(sb, RandomUtil.getRandomLong());
    Base64Web.encode(sb, _sequence.incrementAndGet());
    
    String address = sb.toString();
    
    return "session:" + address;
  }

  public String generateSessionId()
  {
    StringBuilder sb = new StringBuilder();
    
    Base64Web.encode(sb, RandomUtil.getRandomLong());
    Base64Web.encode(sb, _sequence.incrementAndGet());

    return sb.toString();
  }
  
  /*
  @Direct
  public String register(String address, ServiceRef serviceRef)
  {
    if (address.startsWith("session:")) {
      address = address.substring("session:".length());
    }
    
    _channelMap.put(address, serviceRef);

    return address;
  }
  
  public void unregister(String address)
  {
    if (address.startsWith("session:")) {
      address = address.substring("session:".length());
    }
    
    _channelMap.remove(address);
  }
    
  @OnLookup
  public ServiceRef lookup(String path)
  {
    int p = path.length();

    while (p > 0) {
      String prefix = path.substring(0, p);
      String suffix = path.substring(p);
      
      ServiceRef service = _channelMap.get(prefix);
      
      if (service != null) {
        if (suffix.length() > 0) {
          return service.onLookup(suffix);
        }
        else {
          return service;
        }
      }
      
      p = path.lastIndexOf('/', p - 1);
    }
    
    return null;
  }
  */
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
