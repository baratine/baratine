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
import java.io.Writer;
import java.util.Objects;

import com.caucho.v5.json.value.JsonValue;
import com.caucho.v5.vfs.WriteStream;

/**
 * Abstract output stream for JSON requests.
 *
 * <pre>
 * OutputStream os = ...; // from http connection
 * AbstractOutput out = new HessianSerializerOutput(os);
 * String value;
 *
 * out.startCall("hello");  // start hello call
 * out.writeString("arg1"); // write a string argument
 * out.completeCall();      // complete the call
 * </pre>
 */
public class OutJsonImpl implements OutJson
{
  private static final char []NULL = new char[] { 'n', 'u', 'l', 'l' };
  private static final char []TRUE = new char[] { 't', 'r', 'u', 'e' };
  private static final char []FALSE = new char[] { 'f', 'a', 'l', 's', 'e' };

  private Writer _os;
  
  private StateJsonOut _state = StateJsonOut.FIRST;
  
  public OutJsonImpl()
  {
  }

  public OutJsonImpl(Writer os)
  {
    init(os);
  }

  public OutJsonImpl(WriteStream out)
  {
    init(out.getPrintWriter());
  }
  
  /**
   * Initialize the output with a new underlying stream.
   */
  public void init(Writer os)
  {
    _os = os;

    _state = StateJsonOut.FIRST;
  }
  
  /**
   * Initialize the output with a new underlying stream.
   */
  public void init()
  {
    Objects.requireNonNull(_os);
    
    _state = StateJsonOut.FIRST;
  }

  @Override
  public final OutJson writeNull()
  {
    _state = _state.write(this);
    //writeContext();
    
    write(NULL, 0, 4);
    
    return this;
  }

  public final void writeNullValue()
  {
    write(NULL, 0, 4);
  }

  @Override
  public final OutJson write(boolean value)
  {
    _state = _state.write(this);
    
    //writeContext();

    if (value) {
      write(TRUE, 0, 4);
    }
    else {
      write(FALSE, 0, 5);
    }
    
    return this;
  }

  public final OutJson writeBooleanValue(boolean value)
  {
    if (value) {
      write(TRUE, 0, 4);
    }
    else {
      write(FALSE, 0, 5);
    }
    
    return this;
  }

  @Override
  public final OutJson write(String value)
  {
    _state = _state.write(this);

    writeString(value);

    return this;
  }

  @Override
  public final OutJson write(int value)
  {
    _state = _state.write(this);
    
    writeStringValue(String.valueOf(value));

    return this;
  }

  @Override
  public final OutJson write(long value)
  {
    _state = _state.write(this);
    
    writeStringValue(String.valueOf(value));

    return this;
  }

  @Override
  public final OutJson write(double value)
  {
    _state = _state.write(this);
    
    writeStringValue(String.valueOf(value));

    return this;
  }

  @Override
  public final OutJson write(JsonValue value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public final OutJson writeStartObject()
  {
    _state = _state.write(this);
    
    _state = StateJsonOut.FIRST;
    
    write('{');

    return this;
  }
  
  public final OutJson writeEndObject()
  {
    write('}');
    
    _state = StateJsonOut.REST;
    
    return this;
  }

  @Override
  public final OutJsonImpl writeStartArray()
  {
    _state.write(this);
    
    _state = StateJsonOut.FIRST;
    
    write('[');
    
    return this;
  }

  @Override
  public final OutJsonImpl writeEndArray()
  {
    write(']');
    
    _state = StateJsonOut.REST;
    
    return this;
  }

  @Override
  public OutJson writeKey(String name)
  {
    _state.write(this);
    
    write('"');
    writeStringValue(name);
    write('"');
    write(':');
    
    _state = StateJsonOut.KEY;
    
    return this;
  }
  
  public void writeKey(char []key)
  {
    _state.write(this);
    
    write(key, 0, key.length);
    
    _state = StateJsonOut.KEY;
  }
  
  private void write(char []buffer, int offset, int length)
  {
    try {
      Writer os = _os;
      
      os.write(buffer, offset, length);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void writeString(String v)
  {
    if (v == null) {
      write(NULL, 0, 4);
      return;
    }
    
    int sLength = v.length();
    
    write('"');
    
    for (int i = 0; i < sLength; i++) {
      char ch = v.charAt(i);
      
      escapeChar(ch);
    }

    write('"');
  }

  private void escapeChar(char ch)
  {
    switch (ch) {
    case 0:
      write('\\');
      write('u');
      write('0');
      write('0');
      write('0');
      write('0');
      break;
    case '\n':
      write('\\');
      write('n');
      break;
    case '\r':
      write('\\');
      write('r');
      break;
    case '\t':
      write('\\');
      write('t');
      break;
    case '\b':
      write('\\');
      write('b');
      break;
    case '\f':
      write('\\');
      write('f');
      break;
    case '\\':
      write('\\');
      write('\\');
      break;
    case '"':
      write('\\');
      write('"');
      break;
    default:
      write(ch);
      break;
    }
  }

  private void writeStringValue(String s)
  {
    int len = s.length();

    for (int i = 0; i < len; i++) {
      char ch = s.charAt(i);

      write(ch);
    }
  }
  
  private void write(char ch)
  {
    try {
      Writer os = _os;
      
      os.write(ch);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void flush()
  {
  }

  public void close()
  {
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  static enum StateJsonOut
  {
    FIRST {
      @Override
      StateJsonOut write(OutJsonImpl out)
      {
        return REST;
      }
    },
    KEY {
      @Override
      StateJsonOut write(OutJsonImpl out)
      {
        return REST;
      }
    },
    REST {
      @Override
      StateJsonOut write(OutJsonImpl out)
      {
        out.write(',');
        
        return REST;
      }
    };
    
    StateJsonOut write(OutJsonImpl out)
    {
      return this;
    }
  }
}
