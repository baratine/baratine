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

import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.ramp.hamp.InHamp;

/**
 * HmtpReader stream handles client packets received from the server.
 */
public class InHampWebSocket extends InHamp
  implements InAmpWebSocket //, MessageHandler.Whole<InputStream>
{
  private static final Logger log
    = Logger.getLogger(InHampWebSocket.class.getName());
  
  private SessionContextJamp _channelContext;
  //private ChannelContextJamp _channelEnv;
  
  public InHampWebSocket(ServiceManagerAmp rampManager,
                         ChannelAmp channel,
                         SessionContextJamp context)
  {
    super(rampManager, channel);
    
    _channelContext = context;
    //_channelEnv = env;
  }
  
  //@Override
  public void onMessage(InputStream is)
  {
    // baratine/2725
    //_channelContext.start(_channelEnv);
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
      readMessage(is, outbox);
      //readMessage(is);
    } catch (Exception e) {
      e.printStackTrace();
      
      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, e.toString(), e);
      }
      else {
        log.fine(e.toString());
      }
    } finally {
      //_channelContext.start(null);
    }
  }
}
