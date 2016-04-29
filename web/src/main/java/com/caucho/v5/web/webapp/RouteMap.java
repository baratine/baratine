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

package com.caucho.v5.web.webapp;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.baratine.web.MultiMap;



/**
 * A route item.
 */
public class RouteMap
{
  private static final Logger log = Logger.getLogger(RouteMap.class.getName());
  private String _pattern;
  private RouteBaratine _route;
  
  private Pattern _regexp;
  private HashSet<String> _names = new HashSet<>();
  private boolean _isPathInfo;
  
  RouteMap(String path,
           RouteBaratine route)
  {
    _pattern = path;
    _route = route;

    _regexp = calculateRegexp(path);
  }

  public String pattern()
  {
    return _pattern;
  }

  public boolean match(InvocationBaratine invocation, String uri)
  {
    Matcher matcher = _regexp.matcher(uri);
    
    if (matcher.matches()) {
      Map<String,String> params = Collections.EMPTY_MAP;
      
      int count = matcher.groupCount();
      
      if (_isPathInfo && count > 1) {
        String pathInfo = matcher.group(count - 1);
        String path = uri.substring(0, uri.length() - pathInfo.length());
        
        invocation.path(path);
        invocation.pathInfo(pathInfo);
      }
      
      if (_names.size() > 0) {
        params = new HashMap<>();
        
        for (String name : _names) {
          params.put(name, matcher.group(name));
        }
      }
      
      invocation.pathMap(params);
      
      invocation.queryMap(parseQuery(invocation.queryString()));
      
      return true;
    }
    else {
      return false;
    }
  }

  private MultiMap<String,String> parseQuery(String query)
  {
    try {
      if (query == null) {
        return MultiMapImpl.EMPTY_MAP;
      }
      
      String enc = "utf-8";

      return FormBaratine.parseQueryString(query, enc);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
      
      return MultiMapImpl.EMPTY_MAP;
    }
  }

  public RouteBaratine route()
  {
    return _route;
  }
  
  private Pattern calculateRegexp(String path)
  {
    StringBuilder sb = new StringBuilder();
    
    for (int i = 0; i < path.length(); i++) {
      char ch = path.charAt(i);
      
      if (ch == '*') {
        if (i + 1 < path.length() && path.charAt(i + 1) == '*') {
          if (i > 0 && path.charAt(i - 1) != '/') {
            throw new IllegalArgumentException(path);
          }
          else if (i + 2 < path.length() && path.charAt(i + 2) != '/') {
            throw new IllegalArgumentException(path);
          }
          else {
            sb.append("(.*)");
            i++;
          }
        }
        else {
          sb.append("([^/]*)");
        }
      }
      else if (ch == '/' 
               && i + 2 < path.length()
               && path.charAt(i + 1) == '*'
               && path.charAt(i + 2) == '*'
               && (i + 3 == path.length() || path.charAt(i + 3) == '/')) {
        if (i + 3 == path.length()) {
          sb.append("(|(/.*))");
          
          _isPathInfo = true;
        }
        else {
          sb.append("(|(/.*))");
        }
        
        i += 2;
      }
      else if (ch == '{') {
        int start = i + 1;
        
        for (; i < path.length() && path.charAt(i) != '}'; i++) {
          
        }
        
        String name = path.substring(start, i);
        
        sb.append("(?<" + name + ">[^/]*)");
        
        _names.add(name);
      }
      else {
        sb.append(ch);
      }
    }
    
    String regexp = sb.toString();
    
    return Pattern.compile(regexp);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _pattern + "," + _route + "]";
  }
}
