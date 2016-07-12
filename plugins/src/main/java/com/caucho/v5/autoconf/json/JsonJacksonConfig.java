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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonJacksonConfig
{
  private boolean _enabled;

  // SerializationFeature
  private boolean WRITE_DATES_AS_TIMESTAMPS = true;
  private boolean INDENT_OUTPUT = false;
  private boolean FAIL_ON_EMPTY_BEANS = true;
  private boolean CLOSE_CLOSEABLE = false;
  private boolean WRITE_BIGDECIMAL_AS_PLAIN = false;
  private boolean WRITE_CHAR_ARRAYS_AS_JSON_ARRAYS = false;
  private boolean WRITE_ENUMS_USING_TO_STRING = false;
  private boolean WRITE_ENUMS_USING_INDEX = false;
  private boolean WRITE_NULL_MAP_VALUES = true;
  private boolean WRAP_ROOT_VALUE = false;
  private boolean FLUSH_AFTER_WRITE_VALUE = true;
  private boolean WRITE_EMPTY_JSON_ARRAYS = true;
  private boolean WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED = true;
  //private boolean WRITE_NULL_PROPERTIES = true;

  // DeserializationFeature
  private boolean USE_BIG_DECIMAL_FOR_FLOATS = false;
  private boolean USE_BIG_INTEGER_FOR_INTS = false;
  private boolean READ_ENUMS_USING_TO_STRING = false;
  private boolean ACCEPT_EMPTY_STRING_AS_NULL_OBJECT = false;
  private boolean ACCEPT_SINGLE_VALUE_AS_ARRAY = false;
  private boolean USE_JAVA_ARRAY_FOR_JSON_ARRAY = false;

  private boolean FAIL_ON_UNKNOWN_PROPERTIES = true;
  private boolean FAIL_ON_NULL_FOR_PRIMITIVES = false;
  private boolean FAIL_ON_NUMBERS_FOR_ENUMS = false;
  private boolean FAIL_ON_INVALID_SUBTYPE = true;

  // MapperFeature
  private boolean USE_ANNOTATIONS = true;

  private boolean AUTO_DETECT_CREATORS = true;
  private boolean AUTO_DETECT_FIELDS = true;
  private boolean AUTO_DETECT_GETTERS = true;
  private boolean AUTO_DETECT_IS_GETTERS = true;
  private boolean AUTO_DETECT_SETTERS = true;
  private boolean REQUIRE_SETTERS_FOR_GETTERS = false;
  private boolean USE_GETTERS_AS_SETTERS = true;
  private boolean CAN_OVERRIDE_ACCESS_MODIFIERS = true;
  private boolean INFER_PROPERTY_MUTATORS = true;
  private boolean ALLOW_FINAL_FIELDS_AS_MUTATORS = true;

  private boolean SORT_PROPERTIES_ALPHABETICALLY = false;
  private boolean USE_WRAPPER_NAME_AS_PROPERTY_NAME = false;

  private boolean USE_STATIC_TYPING = true;
  private boolean DEFAULT_VIEW_INCLUSION = true;

  public boolean enabled()
  {
    return _enabled;
  }

  public void configure(ObjectMapper m)
  {
    // SerializationFeature
    m.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, WRITE_DATES_AS_TIMESTAMPS);
    m.configure(SerializationFeature.INDENT_OUTPUT, INDENT_OUTPUT);
    m.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, FAIL_ON_EMPTY_BEANS);
    m.configure(SerializationFeature.CLOSE_CLOSEABLE, CLOSE_CLOSEABLE);
    m.configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, WRITE_BIGDECIMAL_AS_PLAIN);
    m.configure(SerializationFeature.WRITE_CHAR_ARRAYS_AS_JSON_ARRAYS, WRITE_CHAR_ARRAYS_AS_JSON_ARRAYS);
    m.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, WRITE_ENUMS_USING_TO_STRING);
    m.configure(SerializationFeature.WRITE_ENUMS_USING_INDEX, WRITE_ENUMS_USING_INDEX);
    m.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, WRITE_NULL_MAP_VALUES);
    m.configure(SerializationFeature.WRAP_ROOT_VALUE, WRAP_ROOT_VALUE);
    m.configure(SerializationFeature.FLUSH_AFTER_WRITE_VALUE, FLUSH_AFTER_WRITE_VALUE);
    m.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, WRITE_EMPTY_JSON_ARRAYS);
    m.configure(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED, WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED);
    //m.configure(SerializationFeature.WRITE_NULL_PROPERTIES, WRITE_NULL_PROPERTIES);

    // DeserializationFeature
    m.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, USE_BIG_DECIMAL_FOR_FLOATS);
    m.configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, USE_BIG_INTEGER_FOR_INTS);
    m.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, READ_ENUMS_USING_TO_STRING);
    m.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
    m.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, ACCEPT_SINGLE_VALUE_AS_ARRAY);
    m.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, USE_JAVA_ARRAY_FOR_JSON_ARRAY);
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, FAIL_ON_UNKNOWN_PROPERTIES);
    m.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, FAIL_ON_NULL_FOR_PRIMITIVES);
    m.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, FAIL_ON_NUMBERS_FOR_ENUMS);
    m.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, FAIL_ON_INVALID_SUBTYPE);

    // MapperFeature
    m.configure(MapperFeature.USE_ANNOTATIONS, USE_ANNOTATIONS);

    m.configure(MapperFeature.AUTO_DETECT_CREATORS, AUTO_DETECT_CREATORS);
    m.configure(MapperFeature.AUTO_DETECT_FIELDS, AUTO_DETECT_FIELDS);
    m.configure(MapperFeature.AUTO_DETECT_GETTERS, AUTO_DETECT_GETTERS);
    m.configure(MapperFeature.AUTO_DETECT_IS_GETTERS, AUTO_DETECT_IS_GETTERS);
    m.configure(MapperFeature.AUTO_DETECT_SETTERS, AUTO_DETECT_SETTERS);
    m.configure(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS, REQUIRE_SETTERS_FOR_GETTERS);
    m.configure(MapperFeature.USE_GETTERS_AS_SETTERS, USE_GETTERS_AS_SETTERS);
    m.configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, CAN_OVERRIDE_ACCESS_MODIFIERS);
    m.configure(MapperFeature.INFER_PROPERTY_MUTATORS, INFER_PROPERTY_MUTATORS);
    m.configure(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS, ALLOW_FINAL_FIELDS_AS_MUTATORS);

    m.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, SORT_PROPERTIES_ALPHABETICALLY);
    m.configure(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME, USE_WRAPPER_NAME_AS_PROPERTY_NAME);

    m.configure(MapperFeature.USE_STATIC_TYPING, USE_STATIC_TYPING);
    m.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, DEFAULT_VIEW_INCLUSION);
  }
}
