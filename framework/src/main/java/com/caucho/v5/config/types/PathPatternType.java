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

package com.caucho.v5.config.types;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.util.L10N;

import javax.annotation.PostConstruct;

import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Abstract type building a path pattern.  The pattern follows ant.
 */
public class PathPatternType {
  static final L10N L = new L10N(PathPatternType.class);
  static final Logger log = Logger.getLogger(PathPatternType.class.getName());

  private String _prefix;
  private Pattern _pattern;

  public PathPatternType()
  {
  }

  public PathPatternType(String pattern)
    throws ConfigException, PatternSyntaxException
  {
    setName(pattern);
  }

  public PathPatternType(String prefix, String pattern)
    throws ConfigException, PatternSyntaxException
  {
    setPrefix(prefix);
    setName(pattern);
  }
  
  public void setPrefix(String prefix)
  {
    _prefix = prefix;
  }
  
  public String getPrefix()
  {
    return _prefix;
  }

  /**
   * Sets the pattern name.
   */
  public void setName(String pattern)
    throws ConfigException, PatternSyntaxException
  {
    CharBuffer cb = new CharBuffer();

    cb.append("^");

    int i = 0;
    int length = pattern.length();

    while (i < length && pattern.charAt(i) == '/')
      i++;

    for (; i < length; i++) {
      char ch = pattern.charAt(i);

      if (ch == '/') {
        cb.append('/');
      }
      else if (ch == '.') {
        cb.append("\\.");
      }
      else if (ch != '*') {
        cb.append(ch);
      }
      else if (length <= i + 1 || pattern.charAt(i + 1) != '*') {
        cb.append("[^/]*");
      }
      else if (i > 0 && pattern.charAt(i - 1) != '/') {
        throw new ConfigException(L.l("'{0}' is an invalid pattern at '**'",
                                      pattern));
      }
      else if (i + 2 < length && pattern.charAt(i + 2) == '/') {
        cb.append("([^/]*/)*");
        i += 2;
      }
      else if (i + 2 < length) {
        throw new ConfigException(L.l("'{0}' is an invalid pattern at '**'",
                                      pattern));
      }
      else {
        cb.append(".*");
        i++;
      }
    }

    cb.append("$");

    _pattern = Pattern.compile(cb.toString());
  }

  /**
   * Sets the pattern name.
   */
  public void addText(String text)
    throws ConfigException, PatternSyntaxException
  {
    text = text.trim();

    if (! text.equals(""))
      setName(text);
  }

  /**
   * initialize the pattern.
   */
  @PostConstruct
  public void init()
    throws ConfigException
  {
    if (_pattern == null)
      throw new ConfigException(L.l("pattern requires 'name' attribute."));
  }
  
  /**
   * Checks that the path is an allowable prefix.
   */
  
  public boolean isValidPrefix(String path)
  {
    if (_prefix == null)
      return true;
    else
      return path.startsWith(_prefix);
  }

  /**
   * Check for a match.
   */
  public boolean isMatch(String path)
  {
    return _pattern.matcher(path).matches();
  }

  public String toString()
  {
    return "PathPatternType[" + _pattern.pattern() + "]";
  }
}
