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

package com.caucho.v5.config.yaml;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.caucho.v5.config.Configs;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;
import io.baratine.config.Config.ConfigBuilder;

/**
 * YamlParser parses an environment.
 */
public class YamlParser 
{
  private static final L10N L = new L10N(YamlParser.class);
  
  private final List<Config> _configList = new ArrayList<>();
  
  private ConfigBuilder _config;
  
  private InputStream _is;
  private int _peek = -1;
  
  private YamlParser(InputStream is)
  {
    Objects.requireNonNull(is);
    
    _is = new BufferedInputStream(is);
  }
  
  public static List<Config> parse(Path path)
    throws IOException
  {
    try (InputStream is = Files.newInputStream(path)) {
      return new YamlParser(is).parse();
    }
  }
  
  private List<Config> parse()
    throws IOException
  {
    _config = Configs.config();
    
    parseTop();
    
    _configList.add(_config.get());
    
    return _configList;
  }
  
  private void parseTop()
      throws IOException
  {
    while (parse("", -1) >= 0) {
    }
  }
  
  private int parse(String prefix, int depth)
      throws IOException
  {
    int ch;
    
    int subDepth = 0;
    
    while ((ch = read()) > 0) {
      switch (ch) {
      case ' ':
        subDepth++;
        break;
        
      case '\n':
        return 0;
        
      case '-':
        if (subDepth == 0
            && (ch = read()) == '-'
            && (ch = read()) == '-') {
          for (; ch > 0 && ch != '\n'; ch = read()) {
          }

          _configList.add(_config.get());
          _config = Configs.config();
          break;
        }

        throw error("depth: " + subDepth + " '" + ch + "'");
        
      default:
        if (isIdentifierStart(ch)) {
          if (subDepth <= depth) {
            _peek = ch;
            
            return subDepth;
          }
          
          String key = parseIdentifier(ch);
          
          ch = read();
          
          if (ch != ':') {
            throw error(L.l("expected ':' at '{0}'", (char) ch));
          }
          
          String fullKey;
          
          if (prefix.isEmpty()) {
            fullKey = key;
          }
          else {
            fullKey = prefix + "." + key;
          }
          
          parseValues(fullKey);
          
          subDepth = parse(fullKey, subDepth);
        }
        else {
          throw error(ch);
        }
      }
    }
    
    return -1;
  }
  
  private void parseValues(String fullKey)
    throws IOException
  {
    for (int ch = read(); ch > 0; ch = read()) {
      switch (ch) {
      case '\r':
        ch = read();
        if (ch != '\n') {
          _peek = ch;
        }
        return;

      case '\n':
        return;
        
      case ' ': case '\t':
        break;
        
      default:
        if (isIdentifierPart(ch)) {
          StringBuilder sb = new StringBuilder();
          
          sb.append((char) ch);
          
          for (ch = read(); ch > 0 && ch != '\r' && ch != '\n'; ch = read()) {
            sb.append((char) ch);
          }
          
          if (ch == '\r') {
            ch = read();
            
            if (ch != '\n') {
              _peek = ch;
            }
          }
          
          _config.add(fullKey, sb.toString().trim());
          
          return;
        }
        else {
          throw error((char) ch);
        }
      }
    }
  }
  
  private String parseIdentifier(int ch)
    throws IOException
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append((char) ch);
    
    for (ch = read(); isIdentifierPart(ch); ch = read()) {
      sb.append((char) ch);
    }
    
    _peek = ch;
    
    return sb.toString();
  }
  
  private boolean isIdentifierStart(int ch)
  {
    return Character.isJavaIdentifierStart(ch);
  }
  
  private boolean isIdentifierPart(int ch)
  {
    return (Character.isJavaIdentifierPart(ch)
            || ch == '.'
            || ch == '['
            || ch == ']');
  }
  
  private IOException error(int ch)
  {
    return new IOException(L.l("unexpected character '{0}'", String.valueOf((char) ch)));
  }
  
  private IOException error(String msg)
  {
    return new IOException(msg);
  }
  
  private int read()
    throws IOException
  {
    if (_peek > 0) {
      int value = _peek;
      _peek = -1;
      
      return value;
    }
    
    return _is.read();
  }
}
