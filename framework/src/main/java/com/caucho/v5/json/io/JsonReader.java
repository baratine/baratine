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

package com.caucho.v5.json.io;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

import com.caucho.v5.json.ser.JsonDeserializer;
import com.caucho.v5.json.ser.JsonException;
import com.caucho.v5.json.ser.JsonSerializerFactory;
import com.caucho.v5.util.L10N;

/**
 * Input stream for JSON requests.
 */
public class JsonReader extends InJsonImpl implements AutoCloseable
{
  private static final L10N L = new L10N(JsonReader.class);
  
  private JsonSerializerFactory _factory;
  
  private Reader _is;
  private int _peek = -1;
  
  public JsonReader(Reader is)
  {
    this(is, new JsonSerializerFactory());
  }
  
  public JsonReader(Reader is, JsonSerializerFactory factory)
  {
    super(is);
    
    _factory = factory;
  }
  
  //Added this hack to be able to read object, assume it will get ripped out later.
  public void setPeek(char peek) 
  {
    _peek = peek;
  }
  
  /*
  public void init(InputStream is)
  {
    init(Vfs.openRead(is).getReader());
  }
  */

  public void parseBeanMap(Object bean, JsonDeserializer deser)
  {
    Event event;
    
    while ((event = next()) == Event.KEY_NAME) {
      String fieldName = getString();
      
      deser.readField(this, bean, fieldName);
    }
    
    if (event != Event.END_OBJECT) {
      throw new JsonParsingException(L.l("Unexpected JSON {0} while parsing Java object {1}",
                                        event,
                                        bean));
    }
  }
  
  public Object readObject()
  {
    return readObject(Object.class);
  }

  public Object readObject(Type type)
  {
    JsonDeserializer deser = _factory.deserializer(type);

    return deser.read(this);
  }
  
  public boolean readBoolean()
  {
    Event event = next();
    
    if (event == null) {
      return false;
    }
    
    switch (event) {
    case VALUE_NULL:
      return false;
      
    case VALUE_FALSE:
      return false;
      
    case VALUE_TRUE:
      return true;
      
    case VALUE_LONG:
      if (isIntegralNumber()) {
        return getLong() != 0;
      }
      else {
        return getDoubleValue() != 0;
      }
      
    case VALUE_STRING:
    {
      String sValue = getString();
      
      if (sValue == null 
          || sValue.equals("")
          || sValue.equals("null")
          || sValue.equals("false")
          || sValue.equals("0")) {
        return false;
      }
      else {
        return true;
      }
    }
      
    default:
      throw new JsonException(L.l("{0} is unexpected where a boolean was expected",
                                  event));
    }
  }
  
  public long readLong()
  {
    Event event = next();
    
    if (event == null) {
      return 0;
    }
    
    switch (event) {
    case VALUE_NULL:
      return 0;
      
    case VALUE_FALSE:
      return 0;
      
    case VALUE_TRUE:
      return 1;
      
    case VALUE_LONG:
      if (isIntegralNumber()) {
        return getLong();
      }
      else {
        return (long) getDoubleValue();
      }
      
    case VALUE_STRING:
      return Long.parseLong(getString());
      
    default:
      throw new JsonException(L.l("{0} is unexpected where a long was expected",
                                  event));
    }
  }
  
  public double readDouble()
  {
    Event event = next();
    
    if (event == null) {
      return 0;
    }
    
    switch (event) {
    case VALUE_NULL:
      return 0;
      
    case VALUE_FALSE:
      return 0;
      
    case VALUE_TRUE:
      return 1;
      
    case VALUE_LONG:
      if (isIntegralNumber()) {
        return getLong();
      }
      else {
        return getDoubleValue();
      }        
      
    case VALUE_STRING:
      return Double.parseDouble(getString());
      
    default:
      throw new JsonException(L.l("{0} is unexpected where a double was expected",
                                  event));
    }
  }

  public String readString()
  {
    Event event = next();
    
    if (event == null) {
      return null;
    }
    
    switch (event) {
    case VALUE_NULL:
      return null;
      
    case VALUE_TRUE:
      return "true";
      
    case VALUE_FALSE:
      return "false";
      
    case VALUE_STRING:
      return getString();
      
    case VALUE_LONG:
      if (isIntegralNumber()) {
        return String.valueOf(getLong());
      }
      else {
        return String.valueOf(getDoubleValue());
      }
      
    default:
      throw new JsonException(L.l("Unexpected JSON {0} where a string was expected",
                                  event));
    }
  }

  //
  // utility
  //
  
  private int read(Reader is)
  {
    try {
      return is.read();
    } catch (IOException e) {
      throw new JsonException(e);
    }
  }
  
  private JsonParsingException error(String msg)
  {
    return new JsonParsingException(msg);
  }

  private String parseString()
  {
    int ch;
    
    StringBuilder sb = new StringBuilder();

    while ((ch = read()) >= 0 && ch != '"') {
      if (ch == '\\') {
        ch = read();

        switch (ch) {
        case 'r':
          sb.append('\r');
          break;
        case 'n':
          sb.append('\n');
          break;
        case 't':
          sb.append('\t');
          break;
        case 'f':
          sb.append('\f');
          break;
        default:
          sb.append((char) ch);
        }
      }
      else {
        sb.append((char) ch);
      }
    }

    if (ch == -1) {
      throw error(this + " closing '\"' expected at <EOF>");
    }

    return sb.toString();
  }

  private int read()
  {
    try {
      int peek = _peek;
      if (peek >= 0) {
        _peek = -1;
        return peek;
      }

      return _is.read();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close()
  {
    Reader is = _is;
    _is = null;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
