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

package com.caucho.v5.store.temp;

import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.subsystem.RootDirectorySystem;
import com.caucho.v5.subsystem.SubSystem;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;

/**
 * Represents an inode to a temporary file.
 */
public class TempStoreSystem extends SubSystemBase
{
  private static final int START_PRIORITY = SubSystem.START_PRIORITY_ENV_SYSTEM;
  
  private static final L10N L = new L10N(TempStoreSystem.class);

  private static final Logger initLog = Logger.getLogger("com.baratine.init-log");
  
  private final TempFileManager _manager;
  
  public TempStoreSystem(TempFileManager manager)
  {
    initLog.log(Level.FINE, () -> L.l("new TempStoreSystem(${0})", manager));

    _manager = manager;
  }

  public static TempStoreSystem createAndAddSystem()
  {
    RootDirectorySystem rootService = RootDirectorySystem.getCurrent();
    if (rootService == null)
      throw new IllegalStateException(L.l("{0} requires an active {1}",
                                          TempStoreSystem.class.getSimpleName(),
                                          RootDirectorySystem.class.getSimpleName()));

    Path dataDirectory = rootService.dataDirectory();
    ServicesAmp ampManager = AmpSystem.currentManager();
    Objects.requireNonNull(ampManager);
    
    TempFileManager manager = new TempFileManager(dataDirectory.resolve("tmp"),
                                                  ampManager);

    return createAndAddSystem(manager);
  }
  
  public static TempStoreSystem createAndAddSystem(TempFileManager manager)
  {
    SystemManager system = preCreate(TempStoreSystem.class);

    TempStoreSystem service = new TempStoreSystem(manager);
    system.addSystem(TempStoreSystem.class, service);

    return service;
  }
  
  public static TempStoreSystem current()
  {
    return SystemManager.getCurrentSystem(TempStoreSystem.class);
  }
  
  public static TempStoreSystem getCurrent(ClassLoader loader)
  {
    return SystemManager.getCurrentSystem(TempStoreSystem.class, loader);
  }
  
  public TempFileManager getManager()
  {
    return _manager;
  }
  
  public TempStore tempStore()
  {
    return getManager().getTempStore();
  }
  
  public TempWriter openWriter()
  {
    return tempStore().openWriter();
  }
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
  
  @Override
  public void stop(ShutdownModeAmp mode)
    throws Exception
  {
    super.stop(mode);
    
    _manager.close();
    
  }
}
