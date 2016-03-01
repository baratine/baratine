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

package javax.json.stream;

import java.io.Closeable;
import java.io.Flushable;
import java.math.BigDecimal;
import java.math.BigInteger;

import javax.json.JsonValue;

public interface JsonGenerator extends Flushable, Closeable
{
  JsonGenerator writeStartObject();
  
  JsonGenerator writeStartObject(String name);
  
  JsonGenerator writeStartArray();
  
  JsonGenerator writeStartArray(String name);
  
  JsonGenerator write(String name, JsonValue value);
  
  JsonGenerator write(String name, String value);
  
  JsonGenerator write(String name, BigInteger value);
  
  JsonGenerator write(String name, BigDecimal value);
  
  JsonGenerator write(String name, int value);
  
  JsonGenerator write(String name, long value);
  
  JsonGenerator write(String name, double value);
  
  JsonGenerator write(String name, boolean value);
  
  JsonGenerator writeNull(String name);
  
  JsonGenerator writeEnd();
  
  JsonGenerator write(JsonValue value);
  
  JsonGenerator write(String value);
  
  JsonGenerator write(BigDecimal value);
  
  JsonGenerator write(BigInteger value);
  
  JsonGenerator write(int value);
  
  JsonGenerator write(long value);
  
  JsonGenerator write(double value);
  
  JsonGenerator write(boolean value);
  
  JsonGenerator writeNull();
  
  void close();
  
  void flush();
}