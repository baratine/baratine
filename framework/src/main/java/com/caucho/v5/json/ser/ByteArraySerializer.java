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

package com.caucho.v5.json.ser;

import com.caucho.v5.json.io.InJson.Event;
import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.json.io.JsonWriter;
import com.caucho.v5.util.Base64Util;
import com.caucho.v5.util.L10N;

public class ByteArraySerializer extends JsonSerializerBase<byte[]>
{
  private static final L10N L = new L10N(ByteArraySerializer.class);
  
  static final ByteArraySerializer SER = new ByteArraySerializer();

  private ByteArraySerializer() {}

  /*
  @Override
  public void write(JsonWriter out, String name, byte []value)
  {
    StringBuilder sb = new StringBuilder();

    Base64Util.encode(sb, value, 0, value.length);

    out.write(name, sb.toString());
  }
  */

  @Override
  public void write(JsonWriter out, byte []value)
  {
    StringBuilder sb = new StringBuilder();

    Base64Util.encode(sb, value, 0, value.length);

    out.write(sb.toString());
  }

  @Override
  public byte []read(JsonReader in)
  {
    Event event = in.next();
    
    if (event == null || event == Event.VALUE_NULL) {
      return null;
    }
    
    if (event != Event.START_ARRAY) {
      throw new JsonException(L.l("expected array at {0}", event));
    }
    
    byte []values = new byte[8];
    int i = 0;
    
    while ((event = in.peek()) != Event.END_ARRAY && event != null) {
      values = addValue(values, i++, (byte) in.readLong());
    }
    
    in.next();

    byte []result = new byte[i];
    
    System.arraycopy(values, 0, result, 0, i);
    
    return result;
  }
  
  private byte []addValue(byte []values, int i, byte value)
  {
    if (values.length <= i) {
      byte []newValues = new byte[values.length * 2];
      
      System.arraycopy(values, 0, newValues, 0, i);
      
      values = newValues;
    }
    
    values[i] = value;
    
    return values;
  }
}
