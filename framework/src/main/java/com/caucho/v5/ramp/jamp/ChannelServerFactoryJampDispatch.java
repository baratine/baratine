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

import java.util.function.Supplier;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.remote.ChannelManagerService;
import com.caucho.v5.amp.remote.ChannelServer;
import com.caucho.v5.amp.remote.ChannelServerFactoryImpl;
import com.caucho.v5.amp.remote.OutAmp;

/**
 * Creates connection brokers for server connections.
 */
public class ChannelServerFactoryJampDispatch 
  extends ChannelServerFactoryImpl
{
  private String _addressPod;

  public ChannelServerFactoryJampDispatch(Supplier<ServicesAmp> ampManagerRef,
                                          ChannelManagerService sessionManager,
                                          String addressPod,
                                          String podName)
  {
    super(ampManagerRef, sessionManager, podName);
    
    _addressPod = addressPod;
  }
  
  @Override
  protected ChannelServer createChannel(OutAmp out, 
                                               String address,
                                               String chanId)
  {
    /*
    // XXX: need the channel id
    long value = RandomUtil.getRandomLong();
    StringBuilder sb = new StringBuilder();
    Base64.encode(sb, value);
    String channelId = sb.toString();
    */
    
    return new ChannelServerHamp(getRampManagerRef(),
                                       getRegistry(),
                                       out, 
                                       address,
                                       _addressPod,
                                       chanId);
  }
}
