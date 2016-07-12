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

import java.util.UUID;

import com.caucho.v5.json.io.JsonReaderImpl;
import com.caucho.v5.json.io.JsonWriterImpl;
import com.caucho.v5.json.io.InJson.Event;

public class UuidSerializer extends JsonSerializerBase<UUID>
{
  static final UuidSerializer SER = new UuidSerializer();

  private UuidSerializer() {}

  @Override
  public void write(JsonWriterImpl out, UUID value)
  {
    out.write(value.toString());
  }
  
  @Override
  public UUID read(JsonReaderImpl in)
  {
    try {
      Event event = in.peek();
      
      switch (event) {
      case VALUE_NULL:
        return null;
      
      case VALUE_STRING: {
        String s = in.readString();
        
        return UUID.fromString(s);
      }

      default:
        throw error("Unexpected JSON {0} while parsing UUID", event);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new JsonException(e);
    }
  }
}
