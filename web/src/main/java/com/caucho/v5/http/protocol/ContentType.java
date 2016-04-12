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

package com.caucho.v5.http.protocol;


/**
 * Parsed content type
 */
class ContentType {
  private final String _userContentType;
  private String _contentType;
  private String _encoding;
  private String _encodingDefault;

  /**
   * Creates and parses the ContentType.
   */
  ContentType(String userContentType)
  {
    _userContentType = userContentType;

    parseContentType(userContentType);
    parseEncodingDefault();
  }
  
  public String userContentType()
  {
    return _userContentType;
  }

  public String contentType()
  {
    return _contentType;
  }

  public String encoding()
  {
    return _encoding;
  }

  public String encodingDefault()
  {
    return _encodingDefault;
  }

  private void parseContentType(String value)
  {
    int length = value.length();
    int ch;
    int i = 0;

    while (i < length) {
      for (;
           i < length && value.charAt(i) != ';';
           i++) {
      }
      
      if (length <= i)
        break;

      int semicolon = i;

      for (i++; i < length && isWhitespace(value.charAt(i)); i++) {
      }

      int keyIndex = i;

      for (;
           i < length
             && ! isWhitespace((ch = value.charAt(i)))
             && ch != '=';
           i++) {
      }

      if (length <= i)
        break;
      else if ((ch = value.charAt(keyIndex)) != 'c' && ch != 'C') {
        i++;
      }
      else if (! value.regionMatches(true, keyIndex,
                                     "charset", 0, i - keyIndex)) {
        i++;
      }
      else {
        for (; i < length && isWhitespace(value.charAt(i)); i++) {
        }

        if (length <= i || value.charAt(i) != '=')
          continue;

        for (i++; i < length && isWhitespace(value.charAt(i)); i++) {
        }

        if (i < length && value.charAt(i) == '"') {
          int encodingIndex = ++i;

          for (; i < length && value.charAt(i) != '"'; i++) {
          }

          _encoding = value.substring(encodingIndex, i).intern();
        }
        else {
          int encodingIndex = i;

          for (;
               i < length
                 && ! isWhitespace(ch = value.charAt(i))
                 && ch != ';';
               i++) {
          }

          _encoding = value.substring(encodingIndex, i).intern();
        }

        for (; i < length && value.charAt(i) != ';'; i++) {
        }
        
        if (i < length) {
          StringBuilder sb = new StringBuilder();
          sb.append(value, 0, semicolon);
          sb.append(value, i, value.length());

          _contentType = sb.toString().intern();
        }
        else
          _contentType = value.substring(0, semicolon).intern();

        return;
      }
    }

    _contentType = value.intern();
  }
  
  private void parseEncodingDefault()
  {
    if (_contentType.startsWith("text/")) {
      _encodingDefault = "utf-8";
      return;
    }
    else if (_contentType.contains("json")) {
      _encodingDefault = "utf-8";
      return;
    }
    else if (_contentType.contains("xml")) {
      _encodingDefault = "utf-8";
      return;
    }
  }
  
  private static boolean isWhitespace(int ch)
  {
    switch (ch) {
    case ' ':
    case '\t':
    case '\r':
    case '\n':
    case '\f':
      return true;
    default:
      return false;
    }
  }
}
