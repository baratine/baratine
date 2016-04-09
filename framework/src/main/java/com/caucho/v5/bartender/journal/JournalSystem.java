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

package com.caucho.v5.bartender.journal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.db.journal.JournalStore;
import com.caucho.v5.db.journal.JournalStream;
import com.caucho.v5.db.journal.JournalStreamImpl;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.network.NetworkSystemBartender;
import com.caucho.v5.subsystem.RootDirectorySystem;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;


/**
 * The general low-level journal system.
 */
public class JournalSystem extends SubSystemBase
{
  protected static final Logger log
            = Logger.getLogger(JournalSystem.class.getName());

  private static final L10N L = new L10N(JournalSystem.class);

  // priority must be before network so it's available to handle incoming
  // messages
  public static final int START_PRIORITY = BartenderSystem.START_PRIORITY + 1;
  
  private final SystemManager _systemManager;

  private JournalStore _journalStore;

  // private FailoverService _failoverService;

  public JournalSystem()
  {
    _systemManager = SystemManager.getCurrent();
    
    if (_systemManager == null) {
      throw new ConfigException(L.l("{0} requires an active {1}",
                                    getClass().getSimpleName(),
                                    SystemManager.class.getSimpleName()));
    }
  }
  
  public static JournalSystem createAndAddSystem()
  {
    SystemManager system = preCreate(JournalSystem.class);
      
    JournalSystem service = new JournalSystem();
    system.addSystem(service);
    
    return service;
  }
  
  public static JournalSystem getCurrent()
  {
    return SystemManager.getCurrentSystem(JournalSystem.class);
  }
  
  //
  // journal streams
  //
  
  /**
   * Creates/opens a named journal stream. The environment name will be
   * added to the given name.
   */
  public JournalStream openJournal(String name)
  {
    String contextName = EnvLoader.getEnvironmentName();
    String fullName = contextName + ":" + name;

    return new JournalStreamImpl(_journalStore.openJournal(fullName));
  }
  
  /**
   * Creates/opens a named journal stream. The environment name will be
   * added to the given name.
   */
  public JournalStream openJournalFullName(String fullName)
  {
    return new JournalStreamImpl(_journalStore.openJournal(fullName));
  }
  
  public JournalStream openPeerJournal(String name,
                                       String peerName)
  {
    ServerBartender peerServer
      = BartenderSystem.current().findServerByName(peerName);
    
    if (peerServer == null) {
      return null;
    }
    
    String contextName = EnvLoader.getEnvironmentName();
    String fullName = contextName + ":" + name;
    
    ServerHeartbeat peerServerHeartbeat = (ServerHeartbeat) peerServer;
    
    return new PeerJournalStream(fullName, peerServerHeartbeat);
  }
  
  //
  // lifecycle
  //
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
  
  @Override
  public void start()
    throws IOException
  {
    Path dataDir = RootDirectorySystem.currentDataDirectory();
    
    ServicesAmp manager = AmpSystem.currentManager();
    
    Path path = dataDir.resolve("journal/journal.db");
    
    long segmentSize = 4 * 1024 * 1024;
    
    JournalStore.Builder builder = JournalStore.Builder.create(path);
    
    builder.segmentSize(segmentSize);
    // builder.rampManager(rampManager);
    builder.mmap(true);
    builder.rampManager(manager);
    
    _journalStore = builder.build();
    
    NetworkSystemBartender wsSystem = NetworkSystemBartender.current();
    
    if (wsSystem != null && wsSystem.isCluster()) {
      wsSystem.serviceBartender("/journal", 
                                new JournalFactory(this));
    
      /*
      _failoverService = manager.service(new FailoverServiceImpl())
                                .start()
                               .as(FailoverService.class);
                               */
    }
  }
 
  @Override
  public void stop(ShutdownModeAmp mode)
  {
    JournalStore journalStore = _journalStore;

    if (journalStore != null) {
      journalStore.close();
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
