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
import com.caucho.v5.util.L10N;

public class StringSerializer extends JsonSerializerBase<String>
{
  private static final L10N L = new L10N(StringSerializer.class);
  
  static final StringSerializer SER = new StringSerializer();

  private StringSerializer() {}

  @Override
  public Object read(JsonReader in)
  {
    Event event = in.next();
    
    switch (event) {
    case VALUE_NULL:
      return null;
      
    case VALUE_FALSE:
      return "false";
      
    case VALUE_TRUE:
      return "true";
      
    case VALUE_STRING:
      return in.getString();
      
    case VALUE_LONG:
      if (in.isIntegralNumber()) {
        return String.valueOf(in.getLong());
      }
      else {
        return String.valueOf(in.getDoubleValue());
      }
      
    default:
      throw new JsonException(L.l("Unexpected JSON {0} while expecing a JSON string.",
                                  event));
    }
  }

  @Override
  public void write(JsonWriter out, 
                    String value)
  {
    out.write(value);
  }

  /*
  @Override
  public void write(JsonWriter out, 
                    String name,
                    String value)
  {
    out.write(name, value);
  }
  */
}
