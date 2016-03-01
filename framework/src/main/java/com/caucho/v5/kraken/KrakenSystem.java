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

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.kraken.table.TableManagerKraken;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;

/**
 * The local cache repository.
 */
public class KrakenSystem extends SubSystemBase 
{
  public static final int START_PRIORITY = START_PRIORITY_KRAKEN;
    
  private final TableManagerKraken _tableManager;


  // private DataSource _jdbcDataSource;
  
  private KrakenSystem(ServerBartender serverSelf)
  {
    SystemManager systemManager = SystemManager.getCurrent();
    
    _tableManager = new TableManagerKraken(systemManager, serverSelf);
    
    if (BartenderSystem.current() != null) {
      KrakenSystemCluster.createAndAddSystem(this);
    }
  }
  
  public static KrakenSystem createAndAddSystem()
  {
    ServerBartender serverSelf = BartenderSystem.getCurrentSelfServer();
    
    return createAndAddSystem(serverSelf);
  }
  
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

  public TableManagerKraken getTableManager()
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
    _tableManager.start();
  }
  
  public void startCluster()
  {
    _tableManager.startCluster();
  }
  
  @Override
  public void stop(ShutdownModeAmp mode)
  {
    _tableManager.close();
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
