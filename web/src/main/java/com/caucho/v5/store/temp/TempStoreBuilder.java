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

import com.caucho.v5.amp.ServiceManagerAmp;

/**
 * Filesystem access for a random-access store.
 * 
 * The store is designed around a single writer thread and multiple
 * reader threads. When possible, it uses mmap.
 */
public class TempStoreBuilder
{
  private final Path _path;
  
  private int _blockSize = 64 * 1024;

  private ServiceManagerAmp _ampManager;
  
  public TempStoreBuilder(Path path)
  {
    Objects.requireNonNull(path);
    
    _path = path;
  }

  public ServiceManagerAmp ampManager()
  {
    return _ampManager;
  }
  
  public void ampManager(ServiceManagerAmp ampManager)
  {
    Objects.requireNonNull(ampManager);
    
    _ampManager = ampManager;
  }

  public Path path()
  {
    return _path;
  }
  
  public int blockSize()
  {
    return _blockSize;
  }
  
  public TempStore build()
  {
    return new TempStore(this);
  }
}
