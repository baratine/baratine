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

package com.caucho.v5.kraken.table;

import java.nio.file.Path;
import java.util.Objects;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.io.Vfs;
import com.caucho.v5.kraken.KrakenBuilder;
import com.caucho.v5.store.temp.TempStore;
import com.caucho.v5.store.temp.TempStoreBuilder;
import com.caucho.v5.web.server.ServerBuilder.ServerBartenderSelf;

/**
 * Standalone kraken builder.
 */
public class KrakenBuilderImpl implements KrakenBuilder
{
  private Path _root;
  private ServicesAmp _services;
  private ServerBartender _serverSelf;
  private TempStore _tempStore;
  
  @Override
  public Path root()
  {
    return _root;
  }
  
  @Override
  public void root(Path path)
  {
    Objects.requireNonNull(path);
    
    _root = path;
  }
  
  @Override
  public void services(ServicesAmp services)
  {
    Objects.requireNonNull(services);
    
    _services = services;
  }
  
  ServicesAmp services()
  {
    return _services;
  }
  
  @Override
  public void serverSelf(ServerBartender serverSelf)
  {
    Objects.requireNonNull(serverSelf);
    
    _serverSelf = serverSelf;
  }

  public ServerBartender serverSelf()
  {
    return _serverSelf;
  }
  
  public void tempStore(TempStore tempStore)
  {
    _tempStore = tempStore;
  }

  TempStore tempStore()
  {
    return _tempStore;
  }
  
  @Override
  public KrakenImpl get()
  {
    if (_services == null) {
      _services = ServicesAmp.newManager().get();
    }
    
    if (_serverSelf == null) {
      _serverSelf = new ServerBartenderSelf("localhost", 0);
    }
    
    if (_root == null) {
      // XXX: config
      String rootPath = System.getProperty("baratine.root");
      
      if (rootPath == null) {
        rootPath = "/tmp/baratine";
      }
      
      _root = Vfs.path(rootPath).resolve("kraken");
    }
    
    
    if (_tempStore == null) {
      TempStoreBuilder tempBuilder = new TempStoreBuilder(_root.resolve("temp"));
      
      tempBuilder.services(services());
      
      _tempStore = tempBuilder.build();
    }

    KrakenImpl kraken = new KrakenImpl(this);
    
    kraken.start();
    
    return kraken;
  }

  @Override
  public String toString()
  {
    return this.getClass().getSimpleName() + "["
           + _root
           + ']';
  }
}
