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

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.subsystem.RootDirectorySystem;
import com.caucho.v5.subsystem.SubSystem;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;

/**
 * Represents an inode to a temporary file.
 */
public class TempFileSystem extends SubSystemBase
{
  private static final int START_PRIORITY = SubSystem.START_PRIORITY_ENV_SYSTEM;
  
  private static final L10N L = new L10N(TempFileSystem.class);
  
  private final TempFileManager _manager;
  
  public TempFileSystem(TempFileManager manager)
  {
    _manager = manager;
  }

  public static TempFileSystem createAndAddSystem()
  {
    RootDirectorySystem rootService = RootDirectorySystem.getCurrent();
    if (rootService == null)
      throw new IllegalStateException(L.l("{0} requires an active {1}",
                                          TempFileSystem.class.getSimpleName(),
                                          RootDirectorySystem.class.getSimpleName()));

    Path dataDirectory = rootService.dataDirectory();
    ServiceManagerAmp ampManager = AmpSystem.currentManager();
    Objects.requireNonNull(ampManager);
    
    TempFileManager manager = new TempFileManager(dataDirectory.resolve("tmp"),
                                                  ampManager);

    return createAndAddSystem(manager);
  }
  
  public static TempFileSystem createAndAddSystem(TempFileManager manager)
  {
    SystemManager system = preCreate(TempFileSystem.class);

    TempFileSystem service = new TempFileSystem(manager);
    system.addSystem(TempFileSystem.class, service);

    return service;
  }
  
  public static TempFileSystem getCurrent()
  {
    return SystemManager.getCurrentSystem(TempFileSystem.class);
  }
  
  public static TempFileSystem getCurrent(ClassLoader loader)
  {
    return SystemManager.getCurrentSystem(TempFileSystem.class, loader);
  }
  
  public TempFileManager getManager()
  {
    return _manager;
  }
  
  public TempStore getTempStore()
  {
    return getManager().getTempStore();
  }
  
  public TempWriter openWriter()
  {
    return getTempStore().openWriter();
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
