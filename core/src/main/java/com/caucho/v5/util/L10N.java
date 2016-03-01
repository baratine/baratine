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

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

/**
 * Localization
 */
public class L10N
{
  private static final HashMap<String,Properties> _l10nMap
    = new HashMap<>();

  private Class<?> _cl;
  
  private Boolean _isMessageFileAvailable;
  private Boolean _isStringFileAvailable;
  
  private Properties _messages;
  private Properties _strings;
  
  public L10N(Class<?> cl)
  {
    _cl = cl;
  }
  
  public String l(String msg)
  {
    msg = getTranslated(msg);
    
    StringBuilder cb = new StringBuilder();

    int length = msg.length();
    int i = 0;

    while (i < length) {
      char ch = msg.charAt(i);

      if (ch != '{' || i + 2 >= length) {
        cb.append(ch);
        i++;
      }
      else {
        ch = msg.charAt(i + 1);
        
        if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else {
          i = parseString(msg, i + 1, cb);
        }
      }
    }
    
    return cb.toString();
  }
  
  public String l(String msg, long l)
  {
    return l(msg, String.valueOf(l));
  }
  
  public String l(String msg, Object o)
  {
    msg = getTranslated(msg);

    StringBuilder cb = new StringBuilder();

    int length = msg.length();
    int i = 0;

    while (i < length) {
      char ch = msg.charAt(i);

      if (ch != '{' || i + 2 >= length) {
        cb.append(ch);
        i++;
      }
      else {
        ch = msg.charAt(i + 1);
        
        if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else if (ch == '0') {
          cb.append(o);
          i += 3;
        }
        else if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else {
          i = parseString(msg, i + 1, cb);
        }
      }
    }
    
    return cb.toString();
  }
  
  public String l(String msg, Object o1, Object o2)
  {
    msg = getTranslated(msg);
    
    StringBuilder cb = new StringBuilder();

    int length = msg.length();
    int i = 0;

    while (i < length) {
      char ch = msg.charAt(i);

      if (ch != '{' || i + 2 >= length) {
        cb.append(ch);
        i++;
      }
      else {
        ch = msg.charAt(i + 1);
        
        if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else if (ch == '0') {
          cb.append(o1);
          i += 3;
        }
        else if (ch == '1') {
          cb.append(o2);
          i += 3;
        }
        else if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else {
          i = parseString(msg, i + 1, cb);
        }
      }
    }
    
    return cb.toString();
  }

  /*
  public String l(String msg, Object o1, int i2)
  {
    return l(msg, o1, String.valueOf(i2));
  }

  public String l(String msg, int i1, int i2)
  {
    return l(msg, String.valueOf(i1), String.valueOf(i2));
  }
  */
  
  public String l(String msg, Object o1, Object o2, Object o3)
  {
    msg = getTranslated(msg);
    
    StringBuilder cb = new StringBuilder();

    int length = msg.length();
    int i = 0;

    while (i < length) {
      char ch = msg.charAt(i);

      if (ch != '{' || i + 2 >= length) {
        cb.append(ch);
        i++;
      }
      else {
        ch = msg.charAt(i + 1);
        
        if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else if (ch == '0') {
          cb.append(o1);
          i += 3;
        }
        else if (ch == '1') {
          cb.append(o2);
          i += 3;
        }
        else if (ch == '2') {
          cb.append(o3);
          i += 3;
        }
        else if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else {
          i = parseString(msg, i + 1, cb);
        }
      }
    }
    
    return cb.toString();
  }
  
  public String l(String msg, Object o1, Object o2, Object o3, Object o4)
  {
    msg = getTranslated(msg);
    
    StringBuilder cb = new StringBuilder();

    int length = msg.length();
    int i = 0;

    while (i < length) {
      char ch = msg.charAt(i);

      if (ch != '{' || i + 2 >= length) {
        cb.append(ch);
        i++;
      }
      else {
        ch = msg.charAt(i + 1);
        
        if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else if (ch == '0') {
          cb.append(o1);
          i += 3;
        }
        else if (ch == '1') {
          cb.append(o2);
          i += 3;
        }
        else if (ch == '2') {
          cb.append(o3);
          i += 3;
        }
        else if (ch == '3') {
          cb.append(o4);
          i += 3;
        }
        else if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else {
          i = parseString(msg, i + 1, cb);
        }
      }
    }
    
    return cb.toString();
  }
  
  public String l(String msg, Object o1, Object o2,
                  Object o3, Object o4, Object o5)
  {
    msg = getTranslated(msg);
    
    StringBuilder cb = new StringBuilder();

    int length = msg.length();
    int i = 0;

    while (i < length) {
      char ch = msg.charAt(i);

      if (ch != '{' || i + 2 >= length) {
        cb.append(ch);
        i++;
      }
      else {
        ch = msg.charAt(i + 1);
        
        if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else if (ch == '0') {
          cb.append(o1);
          i += 3;
        }
        else if (ch == '1') {
          cb.append(o2);
          i += 3;
        }
        else if (ch == '2') {
          cb.append(o3);
          i += 3;
        }
        else if (ch == '3') {
          cb.append(o4);
          i += 3;
        }
        else if (ch == '4') {
          cb.append(o5);
          i += 3;
        }
        else if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else {
          i = parseString(msg, i + 1, cb);
        }
      }
    }
    
    return cb.toString();
  }
  
  public String l(String msg, Object o1, Object o2,
                  Object o3, Object o4, Object o5, Object o6)
  {
    msg = getTranslated(msg);
    
    StringBuilder cb = new StringBuilder();

    int length = msg.length();
    int i = 0;

    while (i < length) {
      char ch = msg.charAt(i);

      if (ch != '{' || i + 2 >= length) {
        cb.append(ch);
        i++;
      }
      else {
        ch = msg.charAt(i + 1);
        
        if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else if (ch == '0') {
          cb.append(o1);
          i += 3;
        }
        else if (ch == '1') {
          cb.append(o2);
          i += 3;
        }
        else if (ch == '2') {
          cb.append(o3);
          i += 3;
        }
        else if (ch == '3') {
          cb.append(o4);
          i += 3;
        }
        else if (ch == '4') {
          cb.append(o5);
          i += 3;
        }
        else if (ch == '5') {
          cb.append(o6);
          i += 3;
        }
        else if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else {
          i = parseString(msg, i + 1, cb);
        }
      }
    }
    
    return cb.toString();
  }
  
  public String l(String msg, Object ...objects)
  {
    msg = getTranslated(msg);

    return fillMessage(getStringMap(), msg, objects);
  }
  
  public static String fillMessage(String msg,
                                   Object ...objects)
  {
    return fillMessage(null, msg, objects);
  }
    
  public static String fillMessage(Properties stringMap,
                                   String msg,
                                   Object ...objects)
  {
    StringBuilder cb = new StringBuilder();

    int length = msg.length();
    int i = 0;

    while (i < length) {
      char ch = msg.charAt(i);

      if (ch != '{' || i + 2 >= length) {
        cb.append(ch);
        i++;
      }
      else {
        ch = msg.charAt(i + 1);
        
        if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else if ('0' <= ch && ch <= '9') {
          int index = ch - '0';
          
          Object value = objects[index];
          
          if (value != null && (value instanceof Object[]))
            cb.append(Arrays.asList((Object[]) value));
          else
            cb.append(objects[index]);
          
          i += 3;
        }
        else if (ch == '{') {
          cb.append('{');
          i += 2;
        }
        else {
          i = parseString(msg, i + 1, cb, stringMap);
        }
      }
    }
    
    return cb.toString();
  }

  private int parseString(String msg, 
                          int i, 
                          StringBuilder sb)
  {
    return parseString(msg, i, sb, getStringMap());
  }

  private static int parseString(String msg, 
                                 int i, 
                                 StringBuilder sb, 
                                 Properties stringMap)
  {
    StringBuilder arg = new StringBuilder();
    int len = msg.length();
    char ch;

    for (; i < len && (ch = msg.charAt(i)) != '}'; i++) {
      arg.append(ch);
    }

    // Properties stringMap = getStringMap();

    if (stringMap != null) {
      String string = (String) stringMap.get(arg.toString());

      if (string != null) {
        sb.append(string);
        return i + 1;
      }
    }

    sb.append('{');
    sb.append(arg);
    
    return i;
  }

  private String getTranslated(String msg)
  {
    Properties messages = getMessageMap();
    
    if (messages == null) {
      return msg;
    }
    
    String translated = (String) messages.get(msg);

    if (translated == null) {
      return msg;
    }
    else
      return translated;
  }

  private Properties getMessageMap()
  {
    if (_messages != null)
      return _messages;
    
    if (Boolean.FALSE.equals(_isMessageFileAvailable))
      return null;
    
    String name = _cl.getName().replace('.', '/');
    int p = name.lastIndexOf('/');

    if (p > 0)
      name = name.substring(0, p);

    name = name + "/messages";

    _messages = _l10nMap.get(name);

    if (_messages != null) {
      _isMessageFileAvailable = Boolean.TRUE;
      
      return _messages;
    }

    _messages = loadProperties(name);

    if (_messages != null) {
      _isMessageFileAvailable = Boolean.TRUE;
      _l10nMap.put(name, _messages);
      return _messages;
    }
    else {
      _isMessageFileAvailable = Boolean.FALSE;
      return null;
    }
  }

  private Properties getStringMap()
  {
    if (_strings != null)
      return _strings;
    
    if (Boolean.FALSE.equals(_isStringFileAvailable))
      return null;
    
    String name = _cl.getName().replace('.', '/');
    int p = name.lastIndexOf('/');

    if (p > 0)
      name = name.substring(0, p);

    name = name + "/strings";

    _strings = _l10nMap.get(name);

    if (_strings != null) {
      _isStringFileAvailable = Boolean.TRUE;
      
      return _strings;
    }

    _strings = loadProperties(name);

    if (_strings != null) {
      _isStringFileAvailable = Boolean.TRUE;
      _l10nMap.put(name, _strings);
      return _strings;
    }
    else {
      _isStringFileAvailable = Boolean.FALSE;
      return null;
    }
  }

  private Properties loadProperties(String name)
  {
    name = name + ".properties";
    
    try (InputStream is = _cl.getClassLoader().getResourceAsStream(name)) {
      if (is != null) {
        Properties messages = new Properties();
        messages.load(is);

        return messages;
      }
    } catch (Exception e) {
      //e.printStackTrace();
      // can't log in logging routines
    }

    return null;
  }
}
