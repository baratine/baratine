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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A wrapper for Caucho system variables, allowing tests to override
 * the default variables.
 */
public class JniCauchoSystem {
  private static Logger log
    = Logger.getLogger(JniCauchoSystem.class.getName());

  private static JniCauchoSystem _system;

  protected JniCauchoSystem()
  {
  }

  public static JniCauchoSystem create()
  {
    synchronized (JniCauchoSystem.class) {
      if (_system == null) {
        try {
          Class cl = Class.forName("com.caucho.server.util.JniCauchoSystemImpl");

          _system = (JniCauchoSystem) cl.newInstance();
        } catch (Throwable e) {
          log.log(Level.FINEST, e.toString(), e);
        }

        if (_system == null)
          _system = new JniCauchoSystem();
      }

      return _system;
    }
  }

  /**
   * Returns true if we're currently running a test.
   */
  public double getLoadAvg()
  {
    return 0;
  }

  /**
   * Initialize any JNI code
   */
  public void initJniBackground()
  {
  }
}
