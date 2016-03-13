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

package com.caucho.v5.amp;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.deliver.QueueFullHandlerAmp;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;

import io.baratine.timer.TimerService;


/**
 * Baratine master system registered in the Resin network.
 */
public class AmpSystem extends SubSystemBase
{
  private static final Logger log = Logger.getLogger(AmpSystem.class.getName());
  private static final L10N L = new L10N(AmpSystem.class);
  
  // priority must be before network so it's available to handle incoming
  // messages
  public static final int START_PRIORITY = START_PRIORITY_ENV_SYSTEM;
  
  private String _address;
  
  private final SystemManager _systemManager;
  // private final HempBrokerManager _hempBrokerManager;
  // private final HempBroker _broker;
  private final ServiceManagerAmp _ampManager;
  
  private final TimerService _timerService;

  //private TaskManager _taskManager;
  
  // private ServerAuthManager _linkManager;

  public AmpSystem(String address)
  {
    _systemManager = SystemManager.getCurrent();
    
    if (_systemManager == null) {
      throw new ConfigException(L.l("{0} requires an active {1}",
                                    getClass().getSimpleName(),
                                    SystemManager.class.getSimpleName()));
    }
    
    _address = address;
    
    ServiceManagerBuilderAmp builder = ServiceManagerBuilderAmp.newManager();
    
    //builder.setJournalFactory(new JournalFactoryImpl());
    
    //addJournalFactory(builder);
    
    builder.autoServices(true);
    builder.name("amp-system");
    builder.debugId(address);
    builder.queueFullHandler(new QueueFullHandlerAmp());
    
    if (log.isLoggable(Level.FINER)) {
      builder.debug(true);
    }
    
    _ampManager = builder.start();
    
    /*
    new ServiceBindingProviderEvents().init(_rampManager);
    
    // RampEventsActor eventsActor = new RampEventsActor("event:", _rampManager);
     */

    _timerService = _ampManager.service("timer:")
                                .as(TimerService.class);
    
    //_taskManager = new TaskManager();

    _ampManager.inboxSystem().serviceRef().bind("/system");
    
    // Amp.setContextManager(_ampManager);
  }
  
  public static AmpSystem createAndAddSystem(String address)
  {
    SystemManager system = preCreate(AmpSystem.class);
      
    AmpSystem subSystem = new AmpSystem(address);
    system.addSystem(subSystem);
    
    return subSystem;
  }
  
  public static AmpSystem getCurrent()
  {
    return SystemManager.getCurrentSystem(AmpSystem.class);
  }
  
  public static ServiceManagerAmp currentManager()
  {
    AmpSystem system = getCurrent();
    
    if (system == null) {
      throw new IllegalStateException(L.l("{0} is not available in {1}", 
                                          AmpSystem.class.getName(),
                                          Thread.currentThread().getContextClassLoader()));
    }
    
    return system.getManager();
  }
  
  public String getAddress()
  {
    return _address;
  }
  
  public ServiceManagerAmp getManager()
  {
    return _ampManager;
  }

  public TimerService getTimerService()
  {
    return _timerService;
  }

  /*
  public TaskManager getTaskManager()
  {
    return _taskManager;
  }
  */

  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
  
  @Override
  public void start()
  {
    // new AmpSystemAdmin(this).register();
  }
  
  @Override
  public void stop(ShutdownModeAmp mode)
  {
    _ampManager.shutdown(mode);
  }
  
  public long getExternalMessageReadCount()
  {
    return _ampManager.getRemoteMessageReadCount();
  }
  
  public long getExternalMessageWriteCount()
  {
    return _ampManager.getRemoteMessageWriteCount();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _address + "]";
  }
}

