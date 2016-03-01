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

package com.caucho.v5.amp.manager;

import java.util.Objects;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.RegistryAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * AmpRouter routes messages to mailboxes.
 */
public class LookupManagerWrapper implements RegistryAmp
{
  private final RegistryAmp _delegate;
  
  protected LookupManagerWrapper(RegistryAmp delegate)
  {
    Objects.requireNonNull(delegate);
    
    _delegate = delegate;
  }
  
  protected RegistryAmp getDelegate()
  {
    return _delegate;
  }
  
  @Override
  public ServiceRefAmp service(String to)
  {
    return getDelegate().service(to);
  }

  @Override
  public void bind(String address, ServiceRefAmp actorRef)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void unbind(String address)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public Iterable<ServiceRefAmp> getServices()
  {
    return getDelegate().getServices();
  }

  /*
  @Override
  public void publish(String url, LookupManager broker)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void removeDelegateBroker(String url)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
  }
}
