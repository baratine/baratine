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

package com.caucho.v5.util;

import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * Static convenience methods.
 */
public class JmxUtil {
  private static final L10N L = new L10N(JmxUtil.class);
  private static final Logger log 
    = Logger.getLogger(JmxUtil.class.getName());
  public static final String DOMAIN = "caucho";

  /**
   * Creates the clean name
   */
  public static ObjectName getObjectName(String domain,
                                         Map<String,String> properties)
    throws MalformedObjectNameException
  {
    StringBuilder cb = new StringBuilder();
    cb.append(domain);
    cb.append(':');

    boolean isFirst = true;

    Pattern escapePattern = Pattern.compile("[,=:\"*?]");

    // sort type first

    String type = properties.get("type");
    if (type != null) {
      cb.append("type=");
      if (escapePattern.matcher(type).find())
        type = ObjectName.quote(type);
      cb.append(type);

      isFirst = false;
    }

    for (String key : properties.keySet()) {
      if (key.equals("type")) {
        continue;
      }
      
      if (! isFirst)
        cb.append(',');
      isFirst = false;

      cb.append(key);
      cb.append('=');

      String value = properties.get(key);
      
      if (value == null) {
        throw new NullPointerException(String.valueOf(key));
      }

      if (value.length() == 0
          || (escapePattern.matcher(value).find()
              && ! (value.startsWith("\"") && value.endsWith("\"")))) {
        value = ObjectName.quote(value);
      }
      
      cb.append(value);
    }

    return new ObjectName(cb.toString());
  }

  /**
   * Returns the local view.
   */
  /*
  public static MBeanView getLocalView()
  {
    MBeanContext context = MBeanContext.getLocal();

    return context.getView();
  }
  */

  public static MBeanServer getMBeanServer()
  {
    // TODO Auto-generated method stub
    return null;
  }

  public static String attribute(String string, String string2)
  {
    // TODO Auto-generated method stub
    return null;
  }

  // static
  private JmxUtil() {}
}

