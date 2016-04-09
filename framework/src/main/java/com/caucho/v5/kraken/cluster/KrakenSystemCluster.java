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

package com.caucho.v5.kraken.cluster;

import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.kraken.KrakenSystem;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;

/**
 * Manages the distributed cache
 */
class KrakenSystemCluster extends SubSystemBase
{
  public static final int START_PRIORITY = BartenderSystem.START_PRIORITY + 1;
  private KrakenSystem _kraken;

  //private KrakenClusterClientImpl _clusterClient;

  private KrakenSystemCluster(KrakenSystem kraken)
  {
    _kraken = kraken;
  }
  
  static KrakenSystemCluster createAndAddSystem(KrakenSystem kraken)
  {
    SystemManager resinSystem = preCreate(KrakenSystemCluster.class);
      
    KrakenSystemCluster system = new KrakenSystemCluster(kraken);
    resinSystem.addSystem(system);
    
    return system;
  }
  
  /*
  @Override
  public KrakenClusterClientImpl getClusterEngine()
  {
    return _clusterClient;
  }
  */

  /*
  @Override
  public void setJdbcDataSource(DataSource dataSource)
  {
    JdbcCacheBackupEngine jdbcEngine
      = new JdbcCacheBackupEngine(dataSource, getDistCacheManager());
    
    getCacheEngine().setBackupEngine(jdbcEngine);
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
    _kraken.startCluster();

    /*
    KrakenManager krakenManager = getKrakenManager();
  
    _clusterClient = new KrakenClusterClientImpl(krakenManager);
  
    krakenManager.setClusterClient(_clusterClient);
    
    _clusterClient.start();
    */
  }
}
