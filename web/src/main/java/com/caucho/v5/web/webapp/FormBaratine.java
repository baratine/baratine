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
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.CharacterIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.i18n.ByteToChar;
import com.caucho.v5.util.CharCursor;
import com.caucho.v5.util.CharReader;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.StringCharCursor;

import io.baratine.web.MultiMap;

/**
 * Form handling.
 */
public class FormBaratine
{
  private static final L10N L = new L10N(FormBaratine.class);
  private static final Logger log = Logger.getLogger(FormBaratine.class.getName());

  public static MultiMap<String,String> parseQueryString(String query,
                                                  String enc)
    throws IOException
  {
    CharCursor is = new StringCharCursor(query);

    int max = 128;
    MultiMapImpl<String,String> map = new MultiMapImpl<>(max);
    
    parseQueryString(is, map, enc, true);
    
    return map;
  }

  public static void parseQueryString(MultiMapImpl<String,String> map,
                                      InputStream is,
                                      String enc)
    throws IOException
  {
    CharReader cursor = new CharCursorInputStream(is);

    parseQueryString(cursor, map, enc, false);
  }
  
  private static void parseQueryString(CharReader is,
                                       MultiMapImpl<String,String> map,
                                       String enc,
                                       boolean isTop)
    throws IOException
  {
    ByteToChar converter = ByteToChar.create();
    
    try {
      converter.setEncoding(enc);
    } catch (UnsupportedEncodingException e) {
      log.log(Level.FINE, e.toString(), e);
    }
    
    int ch;
    
    while ((ch = is.current()) != CharacterIterator.DONE) {
      for (; Character.isWhitespace((char) ch) || ch == '&'; ch = is.next()) {
      }

      converter.clear();
      for (; ch != CharacterIterator.DONE && ch != '=' && ch != '&'; ch = is.next()) {
        readChar(converter, is, ch, isTop);
      }

      String key = converter.getConvertedString();

      converter.clear();
      if (ch == '=') {
        ch = is.next();
      }
      
      for (; ch != CharacterIterator.DONE && ch != '&'; ch = is.next()) {
        readChar(converter, is, ch, isTop);
      }
      
      String value = converter.getConvertedString();

      if (log.isLoggable(Level.FINE)) {
        log.fine("query: " + key + "=" + value);
      }
      
      if (key == null || key.equals("")) {
      }
      else {
        map.add(key, value);
      }
    }
  }

  /**
   * Scans the next character from the input stream, adding it to the
   * converter.
   *
   * @param converter the byte-to-character converter
   * @param is the form's input stream
   * @param ch the next character
   */
  private static void readChar(ByteToChar converter, 
                               CharReader is,
                               int ch, boolean isTop)
    throws IOException
  {
    if (ch == '+') {
      if (isTop)
        converter.addByte(' ');
      else
        converter.addChar(' ');
    }
    else if (ch == '%') {
      int ch1 = is.next();

      if (ch1 == 'u') {
        ch1 = is.next();
        int ch2 = is.next();
        int ch3 = is.next();
        int ch4 = is.next();

        converter.addChar((char) ((toHex(ch1) << 12) +
                                  (toHex(ch2) << 8) + 
                                  (toHex(ch3) << 4) + 
                                  (toHex(ch4))));
      }
      else {
        int ch2 = is.next();
        
        converter.addByte(((toHex(ch1) << 4) + toHex(ch2)));
      }
    }
    else if (isTop) {
      converter.addByte((byte) ch);
    }
    else {
      converter.addChar((char) ch);
    }
  }

  /**
   * Converts a hex character to a byte
   */
  private static int toHex(int ch)
  {
    if (ch >= '0' && ch <= '9')
      return ch - '0';
    else if (ch >= 'a' && ch <= 'f')
      return ch - 'a' + 10;
    else if (ch >= 'A' && ch <= 'F')
      return ch - 'A' + 10;
    else
      return -1;
  }
  
  static class CharCursorInputStream implements CharReader
  {
    private InputStream _is;
    
    private char _current;
    
    CharCursorInputStream(InputStream is)
    {
      _is = is;
      
      next();
    }
    
    @Override
    public char current()
    {
      return _current;
    }
    
    @Override
    public char next()
    {
      char current = _current;
      int ch;
      
      try {
        ch = _is.read();
      } catch (IOException e) {
        throw new BodyException(e);
      }
      
      if (ch < 0) {
        _current = CharacterIterator.DONE;
      }
      else {
        _current = (char) ch;
      }
      
      return _current;
    }
  }
}
