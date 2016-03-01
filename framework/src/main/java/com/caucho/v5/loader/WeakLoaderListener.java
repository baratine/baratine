/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.loader;

import java.lang.ref.WeakReference;
import java.util.logging.Logger;

import com.caucho.v5.util.L10N;

/**
 * Waits for the close event and calls a destroy() method.
 */
public class WeakLoaderListener implements EnvLoaderListener {
  private static final L10N L = new L10N(CloseListener.class);
  private static final Logger log
    = Logger.getLogger(CloseListener.class.getName());

  private WeakReference<EnvLoaderListener> _listenerRef;

  /**
   * Creates the new close listener.
   *
   * @param resource the resource which needs closing
   */
  public WeakLoaderListener(EnvLoaderListener listener)
  {
    _listenerRef = new WeakReference<>(listener);
  }

  /**
   * Handles the case where a class loader is activated.
   */
  public void classLoaderInit(DynamicClassLoader loader)
  {
    EnvLoaderListener listener = _listenerRef.get();
    
    if (listener != null)
      listener.classLoaderInit(loader);
  }
  
  /**
   * Handles the case where a class loader is dropped.
   */
  public void classLoaderDestroy(DynamicClassLoader loader)
  {
    EnvLoaderListener listener = _listenerRef.get();
    
    if (listener != null)
      listener.classLoaderDestroy(loader);
  }
}

