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

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.service.ServiceRefNull;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LookupAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * Registry for a remoting read link.
 */
public interface ChannelAmp extends LookupAmp
{
  /**
   * Returns the underlying service manager.
   */
  ServicesAmp getManager();
  
  /**
   * Creates an outbox to the channel.
   */
  /*
  default OutboxAmp createOutbox()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
  
  /**
   * Creates an inbox to the channel.
   */
  default InboxAmp getInbox()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  /**
   * Lookup a serviceRef specific to the channel.
   */
  @Override
  default ServiceRefAmp service(String address)
  {
    return getManager().service(address);
  }
  
  /**
   * Lookup a methodRef specific to the channel.
   */
  default MethodRefAmp method(String address, String methodName)
  {
    return service(address).getMethod(methodName);
  }
  
  /**
   * Creates a reply to the channel.
   */
  default GatewayReply createGatewayReply(String address)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  /**
   * Creates a service ref across the gateway.
   */
  default ServiceRefAmp createGatewayRef(String remotePath)
  {
    return new ServiceRefNull(getManager(), "remote://" + remotePath);
  }

  /**
   * Creates a result stream to the channel.
   */
  default GatewayResultStream createGatewayResultStream(String from, long qid)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default GatewayResultStream getGatewayResultStream(String from, long qid)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default GatewayResultStream removeGatewayResultStream(String from, long qid)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Close the channel when done
   */
  default void shutdown(ShutdownModeAmp mode)
  {
  }

  default ServiceRefAmp getServiceRefOut()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default ServiceRefAmp getCallerRef()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
