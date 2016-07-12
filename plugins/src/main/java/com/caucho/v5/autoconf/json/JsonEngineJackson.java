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

package com.caucho.v5.autoconf.json;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.json.JsonReader;
import com.caucho.v5.json.JsonEngine;
import com.caucho.v5.json.JsonWriter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonEngineJackson implements JsonEngine
{
  private static Logger LOG = Logger.getLogger(JsonEngineJackson.class.getName());

  private final ObjectMapper _mapper;

  public JsonEngineJackson(ObjectMapper mapper)
  {
    _mapper = mapper;
  }

  @Override
  public JsonWriter newWriter()
  {
    return new JsonWriterJackson(_mapper);
  }

  @Override
  public JsonReader newReader()
  {
    return new JsonReaderJackson(_mapper);
  }

  class JsonWriterJackson implements JsonWriter {
    private ObjectMapper _mapper;
    private Writer _writer;

    private CharArrayWriter _charWriter = new CharArrayWriter();

    public JsonWriterJackson(ObjectMapper mapper)
    {
      _mapper = mapper;
    }

    @Override
    public void init(Writer writer)
    {
      _writer = writer;
    }

    @Override
    public void write(Object value)
      throws IOException
    {
      try {
        _mapper.writeValue(_charWriter, value);

        _charWriter.writeTo(_writer);

        _charWriter.reset();
      }
      catch (Exception e) {
        LOG.log(Level.FINE, e.getMessage(), e);

        throw e;
      }
    }

    @Override
    public void flush()
    {
    }
  }

  static class JsonReaderJackson implements JsonReader {
    private ObjectMapper _mapper;
    private Reader _reader;

    public JsonReaderJackson(ObjectMapper mapper)
    {
      _mapper = mapper;
    }

    @Override
    public void init(Reader reader)
    {
      _reader = reader;
    }

    @Override
    public <T> T readObject(Class<T> cls)
      throws IOException
    {
      return _mapper.readValue(_reader, cls);
    }
  }
}
