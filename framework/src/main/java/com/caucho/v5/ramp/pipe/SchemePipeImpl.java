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

package com.caucho.v5.ramp.pipe;

import java.util.HashMap;
import java.util.Objects;

import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.util.L10N;

import io.baratine.service.Cancel;
import io.baratine.service.OnDestroy;
import io.baratine.service.OnInit;
import io.baratine.service.OnLookup;
import io.baratine.service.Pin;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceRef;

/**
 * Implementation of the pipes
 */
@Service
public class SchemePipeImpl
{
  private static final L10N L = new L10N(SchemePipeImpl.class);
    
  private String _address = "pipe://";
  
  public SchemePipeImpl()
  {
    this("pipe://");
  }
  
  public SchemePipeImpl(String address)
  {
    Objects.requireNonNull(address);
    
    _address = address;
  }
  
  public String getName()
  {
    return _address;
  }

  @OnLookup
  public Object onLookup(String path)
  {
    if (path.equals("//")) {
      return this;
    }
    
    Object value = lookupPath(_address + path);

    return value;
  }

  public Object lookupPath(String path)
  {
    return lookupPipeNode(path);
  }
  
  private Object lookupPipeNode(String path)
  {
    return new PipeNode(path);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
