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

package com.caucho.v5.deploy2;

import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.loader.DynamicClassLoader;

/**
 * Builder for deployed instances.
 */
public interface DeployInstance2
{
  /**
   * Returns the deployment class loader.
   */
  DynamicClassLoader classLoader();
  
  /**
   * Sets any configuration exception.
   */
  //void setConfigException(Throwable e);

  /**
   * Gets the configuration exception.
   */
  Throwable configException();
  
  /**
   * When the instance dependencies have changed.
   */
  default boolean isModified()
  {
    return classLoader().isModified();
  }
  
  /**
   * When the instance dependencies have changed, avoiding timeouts.
   */
  default boolean isModifiedNow()
  {
    return classLoader().isModifiedNow();
  }
  
  /**
   * Log the modification cause.
   */
  default boolean logModified(Logger log)
  {
    return classLoader().logModified(log);
  }
  
  /**
   * Starts the instance
   */
  /*
  default void start()
  {
  }
  */
  
  /**
   * Destroys the instance
   */
  default void shutdown(ShutdownModeAmp mode)
  {
    
  }
}
