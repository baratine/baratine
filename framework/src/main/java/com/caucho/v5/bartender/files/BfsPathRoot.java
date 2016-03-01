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

import io.baratine.service.ServiceRef;


/**
 * Virtual path based on the bartender file system.
 */
public class BfsPathRoot extends BfsPath
{
  private ServiceRef _rootServiceRef;

  public BfsPathRoot(ServiceRef rootServiceRef)
  {
    super();
    
    _rootServiceRef = rootServiceRef;
  }

  @Override
  public String getScheme()
  {
    return "bfs";
  }

  @Override
  protected ServiceRef getRootServiceRef()
  {
    BartenderFileSystem fileSystem = BartenderFileSystem.getCurrent();
    
    if (fileSystem != null) {
      return fileSystem.getRootServiceRef();
    }
    else {
      return _rootServiceRef;
    }
  }
  
  @Override
  protected BfsPathRoot getRootPath()
  {
    return this;
  }
}