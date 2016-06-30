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

package com.caucho.v5.kraken;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.kraken.table.KrakenImpl;
import com.caucho.v5.store.temp.TempStoreSystem;
import com.caucho.v5.subsystem.RootDirectorySystem;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;

/**
 * The local cache repository.
 */
public class KrakenSystem extends SubSystemBase 
{
  public static final int START_PRIORITY = START_PRIORITY_KRAKEN;

  private static final L10N L = new L10N(KrakenSystem.class);

  private static final Logger initLog = Logger.getLogger("com.baratine.init-log");

  private KrakenImpl _tableManager;

  private KrakenBuilder _builder;


  // private DataSource _jdbcDataSource;
  
  private KrakenSystem(ServerBartender serverSelf)
  {
    initLog.log(Level.FINE, () -> L.l("new KrakenSystem(${0})", serverSelf));

    _builder = Kraken.newDatabase();
    
    _builder.services(AmpSystem.currentManager());
    _builder.serverSelf(serverSelf);
    
    Path path = RootDirectorySystem.currentDataDirectory();
    _builder.root(path.resolve("kraken"));
    
    //_tableManager = new TableManagerKraken(systemManager, serverSelf);
    
    // XXX:
    /*
    if (BartenderSystem.current() != null) {
      KrakenSystemCluster.createAndAddSystem(this);
    }
    */
  }
  
  /*
  public static KrakenSystem createAndAddSystem()
  {
    ServerBartender serverSelf = BartenderSystem.getCurrentSelfServer();
    
    return createAndAddSystem(serverSelf);
  }
  */
  
  public static KrakenSystem createAndAddSystem(ServerBartender serverSelf)
  {
    SystemManager system = preCreate(KrakenSystem.class);

    KrakenSystem krakenSystem = new KrakenSystem(serverSelf);
    
    system.addSystem(KrakenSystem.class, krakenSystem);

    return krakenSystem;
  }

  public static KrakenSystem current()
  {
    return SystemManager.getCurrentSystem(KrakenSystem.class);
  }
  
  public long getMemoryMax()
  {
    // return _krakenManager.getMemoryMax();
    
    return 0;
  }
  
  public void setMemoryMax(long memoryMax)
  {
    // _krakenManager.setMemoryMax(memoryMax);
  }

  public KrakenImpl getTableManager()
  {
    return _tableManager;
  }
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
  
  @Override
  public void start()
  {
    _builder.tempStore(TempStoreSystem.current().tempStore());
    
    _tableManager = (KrakenImpl) _builder.get();
    //_tableManager = new TableManagerKraken(systemManager, serverSelf);
    
    _tableManager.start();
  }
  
  public void startCluster()
  {
    _tableManager.startCluster();
  }
  
  @Override
  public void stop(ShutdownModeAmp mode)
  {
    KrakenImpl tableManager = _tableManager;
    
    if (tableManager != null) {
      _tableManager.close();
    }
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
