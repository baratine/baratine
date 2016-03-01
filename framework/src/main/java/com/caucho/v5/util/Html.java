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

/**
 * Html utils
 */
public class Html {

  /**
   * Escapes special symbols in a string.  For example '<' becomes '&lt;'
   */
  public static String escapeHtml(String s)
  {
    if (s == null)
      return null;

    StringBuilder cb = new StringBuilder();
    int lineCharacter = 0;
    boolean startsWithSpace = false;
    
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);

      lineCharacter++;
      
      if (ch == '<')
        cb.append("&lt;");
      else if (ch == '&')
        cb.append("&amp;");
      else if (ch == '\n' || ch == '\r') {
        lineCharacter = 0;
        cb.append(ch);
        startsWithSpace = false;
      }
      else if (lineCharacter > 70 && ch == ' ' && ! startsWithSpace) {
        lineCharacter = 0;
        cb.append('\n');
        for (; i + 1 < s.length() && s.charAt(i + 1) == ' '; i++) {
        }
      }
      else if (lineCharacter == 1 && (ch == ' ' || ch == '\t')) {
        cb.append((char) ch);
        startsWithSpace = true;
      }
      else
        cb.append(ch);
    }

    return cb.toString();
  }
}
