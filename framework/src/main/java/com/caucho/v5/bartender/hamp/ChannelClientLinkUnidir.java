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

package com.caucho.v5.bartender.hamp;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.remote.GatewayReply;
import com.caucho.v5.amp.remote.GatewayReplyBase;
import com.caucho.v5.amp.remote.OutAmpManager;
import com.caucho.v5.amp.service.ServiceRefNull;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * The BAM service registered in the Resin network.
 */
public class ChannelClientLinkUnidir extends ChannelClientBartender
{
  private HampManager _hampManager;
  private String _hostName;
  
  // private InboxAmp _writeMailbox;
  
  public ChannelClientLinkUnidir(HampManager hampManager, 
                                 ServiceManagerAmp manager,
                                 OutAmpManager channel)
  {
    super(manager, channel, "unidir:", manager.service("/system"));
    Thread.dumpStack();
    _hampManager = hampManager;
  }
  
  /*
  @Override
  public RampMailbox getWriteMailbox()
  {
    if (_writeMailbox != null) {
      return _writeMailbox;
    }
    else {
      return super.getWriteMailbox();
    }
  }
  */
  
  /**
   * Resin exports its internal services to the cluster.
   */
  @Override
  protected boolean isExported(ServiceRefAmp serviceRef)
  {
    return true;
  }

  @Override
  public GatewayReply createGatewayReply(String name)
  {
    if (! isLogin()) {
      return super.createGatewayReply(name);
    }
    
    String remoteName = null;

    if (_hostName != null) {
      int p = name.indexOf("://");
    
      if (p >= 0) {
        int q = name.indexOf("/", p + 3);
      
        if (q > 0) {
          remoteName = "bartender://" + _hostName + name.substring(q);
        }
      }
      else if (name.startsWith("/")) {
        remoteName = "bartender://" + _hostName + name;
      }
    
      if (remoteName == null) {
        remoteName = "bartender://" + _hostName + "/system";
      }
    }

    ServiceRefAmp serviceRef;
    
    if (remoteName != null) {
      serviceRef = getLookup().service(remoteName);
    }
    else {
      serviceRef = new ServiceRefNull(getManager(), "/unexpected/" + name);
    }
    
    return new GatewayReplyBase(serviceRef);
  }

  @Override
  public void setHostName(String hostName)
  {
    _hostName = hostName;
  }
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    super.shutdown(mode);

    HampManager hampManager = _hampManager;
    _hampManager = null;
    
    if (isLogin() && hampManager != null) {
      hampManager.onClose(_hostName);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _hostName + "]";
  }
}
