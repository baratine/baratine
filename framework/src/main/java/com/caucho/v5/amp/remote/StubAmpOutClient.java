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

import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * The proxy for a client registered in the ramp server.
 */
public class StubAmpOutClient extends StubAmpOut
{
  private static final Logger log
    = Logger.getLogger(StubAmpOutClient.class.getName());
  
  private final OutAmpManager _outManager;
  private final ChannelClient _channel;
  
  public StubAmpOutClient(ServiceManagerAmp ampManager,
                           OutAmpManager outManager,
                           String remoteAddress,
                           ServiceRefAmp selfServiceRef,
                           ChannelClient channel)
  {
    super(ampManager, remoteAddress, selfServiceRef);
    
    Objects.requireNonNull(outManager);
    Objects.requireNonNull(channel);

    _outManager = outManager;
    _channel = channel;
  }

  @Override
  OutAmp getOut()
  {
    OutAmp out = _outManager.getOut(_channel);
    
    return out;
  }

  @Override
  OutAmp getCurrentOut()
  {
    return _outManager.getCurrentOut();
  }
  
  @Override
  public boolean isUp()
  {
    return _outManager.isUp();
  }
  
  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
    super.onShutdown(mode);
    
    _outManager.close();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _outManager + "]";
  }
}
