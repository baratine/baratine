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

import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.util.L10N;

public class EnumDeserializer<T extends Enum<T>> extends JsonSerializerBase<T>
{
  private static final L10N L = new L10N(EnumDeserializer.class);
  private Class<T> _type;
  
  EnumDeserializer(Class<T> type)
  {
    if (! Enum.class.isAssignableFrom(type)) {
      throw new IllegalArgumentException(L.l("'{0}' must be an enumeration class",
                                             type.getName()));
    }
    
    _type = type;
  }

  @Override
  public T read(JsonReader in)
  {
    String v = in.readString();
    
    if (v == null || v.equals("")) {
      return null;
    }
    else {
      return Enum.valueOf(_type, v);
    }
  }
}
