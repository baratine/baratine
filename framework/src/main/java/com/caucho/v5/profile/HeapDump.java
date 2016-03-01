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
 */

package com.caucho.v5.profile;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.util.L10N;

/**
 * Heap dump.
 */
public class HeapDump {
  private static final L10N L = new L10N(HeapDump.class);
  private static final Logger log
    = Logger.getLogger(HeapDump.class.getName());

  private static HeapDump _heapDump;

  protected HeapDump() {}

  /**
   * Creates/returns the HeapDump instance. Will throw an IllegalStateException
   * if Resin Professional is not available.
   */
  public static HeapDump create()
  {
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();

      Class<?> heapDumpClass
        = Class.forName("com.caucho.v5.profile.ProHeapDump", false, loader);

      _heapDump = (HeapDump) heapDumpClass.newInstance();

      return _heapDump;
    } catch (ClassNotFoundException e) {
      log.log(Level.FINEST, e.toString(), e);

      throw new ConfigException(L.l("HeapDump requires Baratine with JNI"));
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
  }

  /**
   * Checks if the heap is available
   */
  public static boolean isAvailable()
  {
    try {
      return create() != null;
    } catch (Exception e) {
      log.log(Level.FINEST, e.toString(), e);

      return false;
    }
  }

  /**
   * Returns an array of the HeapDump entries.
   */
  public Object dump()
  {
    throw new ConfigException(L.l("HeapDump requires Resin Professional"));
  }

  /**
   * Returns the last heap dump
   */
  public Object getLastHeapDump()
  {
    throw new ConfigException(L.l("HeapDump requires Resin Professional"));
  }

  /**
   * Writes a text value of the heap dump to an output stream.
   */
  public void writeHeapDump(PrintWriter out)
    throws IOException
  {
    throw new ConfigException(L.l("HeapDump requires Resin Professional"));
  }

  /**
   * Writes a text value of the heap dump to an output stream.
   * Extended also write instance counts
   */
  public void writeExtendedHeapDump(PrintWriter out)
    throws IOException
  {
    throw new ConfigException(L.l("HeapDump requires Resin Professional"));
  }

  public void logHeapDump(Logger log,
                          Level level)
  {
    throw new ConfigException(L.l("HeapDump requires Resin Professional"));
  }
  
  public String jsonHeapDump()
  {
    throw new UnsupportedOperationException(L.l("HeapDump requires Resin Professional"));
  }
}
