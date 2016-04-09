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

package com.caucho.v5.bartender.files;

import java.util.Objects;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.kraken.KrakenSystem;
import com.caucho.v5.kraken.table.KrakenImpl;
import com.caucho.v5.store.temp.TempStoreSystem;

/**
 * Root of a filesystem. Each filesystem belongs to a pod and has a 
 * consistent hashing function and its own kraken table.
 */
public class FileServiceBuilder
{
  private String _podName;
  private String _tableName = "caucho_bfs_file";
  private KrakenImpl _tableManager;
  private String _address;
  private ServicesAmp _ampManager;
  private BartenderFileSystem _system;
  private FileHash _hash = FileHashStandard.STANDARD;
  private String _prefix = "";

  public FileServiceBuilder()
  {
    _tableManager = KrakenSystem.current().getTableManager();
    Objects.requireNonNull(_tableManager);
    
    _ampManager = AmpSystem.currentManager();
    Objects.requireNonNull(_ampManager);
    
    _system = BartenderFileSystem.getCurrent();
    Objects.requireNonNull(_system);
  }
  
  public ServicesAmp getManager()
  {
    return _ampManager;
  }
  
  public FileServiceBuilder address(String address)
  {
    Objects.requireNonNull(address);
    
    _address = address;
    
    return this;
  }

  public String getAddress()
  {
    return _address;
  }
  
  public FileServiceBuilder prefix(String prefix)
  {
    Objects.requireNonNull(prefix);
    
    _prefix  = prefix;
    
    return this;
  }

  public String getPrefix()
  {
    return _prefix;
  }
  
  public FileServiceBuilder pod(String podName)
  {
    Objects.requireNonNull(podName);
    
    _podName = podName;
    
    return this;
  }

  public String getPodName()
  {
    return _podName;
  }
  
  public FileServiceBuilder table(String tableName)
  {
    Objects.requireNonNull(tableName);
    
    _tableName = tableName;
    
    return this;
  }

  public String getTable()
  {
    return _tableName;
  }

  public FileHash getHash()
  {
    return _hash;
  }

  public FileServiceBuilder hash(FileHash hash)
  {
    Objects.requireNonNull(hash);
    
    _hash = hash;
    
    return this;
  }

  public BartenderFileSystem getSystem()
  {
    return _system;
  }

  public TempStoreSystem getTempFileSystem()
  {
    TempStoreSystem tempFileSystem
      = TempStoreSystem.getCurrent(getManager().classLoader());
    Objects.requireNonNull(tempFileSystem);

    return tempFileSystem;
  }
  
  public FileServiceRootImpl build()
  {
    return new FileServiceRootImpl(this);
  }
}
