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
 * @author Alex Rojkov
 */

package com.caucho.v5.json.ser;

import com.caucho.v5.json.io.JsonReaderImpl;
import com.caucho.v5.json.io.JsonWriterImpl;
import com.caucho.v5.json.io.InJson.Event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ZonedDateTimeSerializer extends JsonSerializerBase<ZonedDateTime>
{
  static final ZonedDateTimeSerializer SER = new ZonedDateTimeSerializer();

  private DateTimeFormatter _formatter
    = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private ZonedDateTimeSerializer()
  {
  }

  /*
  @Override
  public void write(JsonWriter out, String name, ZonedDateTime value)
  {
    out.write(name, value.format(_formatter));
  }
  */

  @Override
  public void write(JsonWriterImpl out, ZonedDateTime value)
  {
    out.write(value.format(_formatter));
  }

  @Override
  public ZonedDateTime read(JsonReaderImpl in)
  {
    try {
      Event event = in.peek();

      switch (in.peek()) {
      case VALUE_NULL:
        return null;

      case VALUE_STRING:
        return parse(in.readString());

      case VALUE_LONG: {
        long time = in.readLong();

        if (time < Integer.MAX_VALUE) {
          time = (time * 1000L);
        }

        return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
      }

      default:
        throw error("Unexpected JSON {0} while parsing Date", event);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new JsonException(e);
    }
  }

  private ZonedDateTime parse(String value)
  {
    return LocalDateTime.parse(value, _formatter).atZone(ZoneId.systemDefault());
  }
}
