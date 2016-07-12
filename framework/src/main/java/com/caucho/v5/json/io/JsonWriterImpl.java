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

import java.io.Writer;
import java.util.Objects;

import com.caucho.v5.json.ser.SerializerJson;
import com.caucho.v5.json.JsonWriter;
import com.caucho.v5.json.ser.JsonFactory;
import com.caucho.v5.vfs.WriteStreamOld;

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
public class JsonWriterImpl extends OutJsonImpl implements JsonWriter
{
  private final JsonFactory _factory;

  public JsonWriterImpl()
  {
    this(new JsonFactory());
  }

  public JsonWriterImpl(JsonFactory factory)
  {
    Objects.requireNonNull(factory);

    _factory = factory;
  }

  public JsonWriterImpl(Writer out, JsonFactory factory)
  {
    this(factory);

    init(out);
  }

  public JsonWriterImpl(Writer os)
  {
    this();

    init(os);
  }

  public JsonWriterImpl(WriteStreamOld out)
  {
    this();

    init(out.getPrintWriter());
  }

  public void write(Object value)
  {
    if (value == null) {
      writeNull();
      return;
    }

    SerializerJson ser = _factory.serializer(value.getClass());

    ser.write(this, value);
  }

  public void writeObject(String name, Object value)
  {
    if (value == null) {
      writeKey(name);
      writeNull();
      return;
    }

    writeKey(name);

    SerializerJson ser = _factory.serializer(value.getClass());
    //ser.write(this, name, value);
    ser.write(this, value);
  }

  public void writeObjectValue(Object value)
  {
    if (value == null) {
      writeNullValue();
      return;
    }

    SerializerJson ser = _factory.serializer(value.getClass());
    ser.write(this, value);
  }

  public void writeObjectTop(Object value)
  {
    if (value == null) {
      writeStartArray();
      writeEndArray();
      return;
    }

    SerializerJson ser = _factory.serializer(value.getClass());

    ser.writeTop(this, value);
  }
}
