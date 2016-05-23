/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.v5.json;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.json.io.JsonWriter;
import com.caucho.v5.json.ser.JsonFactory;

public class JsonEngineDefault implements JsonEngine
{
  private JsonFactory _factory = new JsonFactory();
  private JsonWriter _jOut = _factory.out();

  private JsonSerializer _serializer = new JsonSerializerDefault();
  private JsonDeserializer _deserializer = new JsonDeserializerDefault();

  @Override
  public JsonSerializer getSerializer()
  {
    return _serializer;
  }

  @Override
  public JsonDeserializer getDeserializer()
  {
    return _deserializer;
  }

  class JsonSerializerDefault implements JsonSerializer {
    public void serialize(Writer writer, Object value)
      throws IOException
    {
      _jOut.init(writer);

      _jOut.write(value);
    }
  }

  class JsonDeserializerDefault implements JsonDeserializer {
    public <T> T deserialize(Reader reader, Class<T> cls)
      throws IOException
    {
      try (JsonReader r = new JsonReader(reader)) {

        Object obj = r.readObject(cls);

        return (T) obj;
      }
    }
  }
}
