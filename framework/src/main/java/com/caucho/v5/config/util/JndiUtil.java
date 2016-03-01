/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.config.util;

import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.util.L10N;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingException;

import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Static utility functions.
 */
public class JndiUtil
{
  private static final Logger log = Logger.getLogger(JndiUtil.class.getName());
  private JndiUtil() {}

  /**
   * Returns the full name.
   */
  public static String getFullName(String shortName)
  {
    if (shortName.startsWith("java:"))
      return shortName;
    else
      return "java:comp/env/" + shortName;
  }

  // For EL
  public static Object lookup(String name)
  {
    Exception ex = null;

    try {
      Object value = new InitialContext().lookup(name);
        
      if (value != null)
        return value;
    } catch (NamingException e) {
      ex = e;
    }

    if (! name.startsWith("java:")) {
      try {
        Object value = new InitialContext().lookup("java:comp/env/" + name);
        
        if (value != null)
          return value;
      } catch (NamingException e) {
        ex = e;
      }
    }

    if (ex != null && log.isLoggable(Level.FINEST))
      log.log(Level.FINEST, ex.toString(), ex);

    return null;
  }
}

