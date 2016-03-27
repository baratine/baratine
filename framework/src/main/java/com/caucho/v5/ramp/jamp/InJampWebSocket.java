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

import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.json.ser.JsonFactory;

/**
 * HmtpReader stream handles client packets received from the server.
 */
public class InJampWebSocket extends InJamp
  implements InAmpWebSocket //, MessageHandler.Whole<Reader>
{
  private static final Logger log
    = Logger.getLogger(InJampWebSocket.class.getName());
  
  private SessionContextJamp _channelContext;
  //private ChannelContextJamp _channelEnv;
  private ServicesAmp _ampManager;

  private ClassLoader _classLoader;
  
  public InJampWebSocket(ServicesAmp rampManager,
                             ChannelAmp broker,
                             SessionContextJamp context,
                             JsonFactory jsonFactory)
  {
    super(broker, jsonFactory);
    
    _ampManager = rampManager; 
    _channelContext = context;
    //_channelEnv = env;
    
    _classLoader = rampManager.classLoader();
  }
  
  private ServicesAmp getManager()
  {
    return _ampManager;
  }
  
  // @Override
  public void onMessage(Reader is)
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
      thread.setContextClassLoader(_classLoader);
      
      super.readMessage(is);
      
      // outbox.flush();
    } catch (Exception e) {
      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, e.toString(), e);
      }
      else {
        log.fine(e.toString());
      }
    } finally {
      //outbox.flush();
      
      thread.setContextClassLoader(oldLoader);
      //OutboxThreadLocal.setCurrent(null);
      
      //_channelContext.start(null);
    }
  }
}
