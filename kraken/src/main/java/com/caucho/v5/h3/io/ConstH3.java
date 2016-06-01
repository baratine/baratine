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

package com.caucho.v5.h3.io;

/**
 * 0x00 - 0x7f - int
 * 0x80 - 0xbf - string
 * 0xc0 - 0xcf - binary
 * 0xd0        - object def
 * 0xd1 - 0xef - object
 * 
 * 0xf0 - null
 * 0xf1 - false
 * 0xf2 - true
 * 0xf3 - double
 * 0xf4 - float
 * 0xf5 - chunked string
 * 0xf6 - chunked binary
 * 0xf7 - ref
 * 0xf8 - graph-next
 * 0xf9 - graph-rest
 * 0xfa-fe - reserved
 * 0xff - invalid
 * 
 * object types:
 *   0 - invalid
 *   1 - class
 *   2 - array
 *   3 - map
 *   4 - enum (values are listed as fields)
 *   
 * object def:
 *   uint - id
 *   string - name
 *   uint - type
 *   uint - fields
 *     fields*
 *     
 * field def:
 *   string - name
 *   uint - type
 *   object - data
 * 
 * predef types:
 * 1: byte, 2: short, 3: int, 4: double  
 */
public class ConstH3
{
  public static final int NULL = 0xf0;
  public static final int FALSE = 0xf1;
  public static final int TRUE = 0xf2;
  public static final int DOUBLE = 0xf3;
  public static final int FLOAT = 0xf4;
  public static final int CHUNKED_STRING = 0xf5;
  public static final int CHUNKED_BINARY = 0xf6;
  public static final int REF = 0xf7;
  public static final int GRAPH_NEXT = 0xf8;
  public static final int GRAPH_ALL = 0xf9;
  // 0xfa-0xfe are reserved
  public static final int INVALID = 0xff;
  
  public static final int INTEGER = 0x00;
  public static final int INTEGER_BITS = 7;
  public static final int INTEGER_MASK = (1 << (INTEGER_BITS - 1)) - 1;
  public static final int INTEGER_OPMASK = ~((1 << INTEGER_BITS) - 1);
  
  public static final int STRING = 0x80;
  public static final int STRING_BITS = 6;
  public static final int STRING_MASK = (1 << (STRING_BITS - 1)) - 1;
  public static final int STRING_OPMASK = ~((1 << STRING_BITS) - 1);
  
  public static final int BINARY = 0xc0;
  public static final int BINARY_BITS = 4;
  public static final int BINARY_MASK = (1 << (BINARY_BITS - 1)) - 1;
  public static final int BINARY_OPMASK = ~((1 << BINARY_BITS) - 1);
  
  public static final byte OBJECT_DEF = (byte) 0xd0;
  
  public static final int OBJECT = 0xd0;
  public static final int OBJECT_BITS = 5;
  public static final int OBJECT_MASK = (1 << (OBJECT_BITS - 1)) - 1;
  public static final int OBJECT_OPMASK = ~((1 << OBJECT_BITS) - 1);
  
  // reserved/predefined object types
  public static final int PREDEF_TYPE = 64;
  
  public static final int DEF_BYTE = 1;
  public static final int DEF_SHORT = 2;
  public static final int DEF_INT = 3;
  public static final int DEF_FLOAT = 4;
  public static final int DEF_DOUBLE = 5;
  public static final int DEF_CHAR = 6;
  
  // HashMap<Object,Object>
  public static final int DEF_MAP = 8;
  
  // ArrayList<Object>
  public static final int DEF_LIST = 9;
  public static final int DEF_ARRAY_OBJECT = 10;
  
  public static final int DEF_UBYTE = 16;
  public static final int DEF_USHORT = 17;
  public static final int DEF_UINT = 18;
  public static final int DEF_ULONG = 19;
  
  public static final int DEF_ARRAY_BOOLEAN = 20;
  public static final int DEF_ARRAY_CHAR = 21;
  public static final int DEF_ARRAY_SHORT = 22;
  public static final int DEF_ARRAY_INT = 23;
  public static final int DEF_ARRAY_LONG = 24;
  public static final int DEF_ARRAY_FLOAT = 25;
  public static final int DEF_ARRAY_DOUBLE = 26;
  public static final int DEF_ARRAY_STRING = 27;

  public static final int DEF_INSTANT = 30;
  public static final int DEF_LOCAL_DATE = 31;
  public static final int DEF_LOCAL_TIME = 32;
  public static final int DEF_LOCAL_DATE_TIME = 33;
  public static final int DEF_ZONED_DATE_TIME = 34;

  public static final int DEF_PATTERN = 35;
  public static final int DEF_BIG_INT = 36;
  public static final int DEF_BIG_DEC = 37;

  public static final int DEF_SET = 38;

  public static final int DEF_UUID = 39;

  public static final int DEF_URI = 40;
  public static final int DEF_URL = 41;

  public static final int DEF_RESERVED = 64;
  
  public static enum ClassTypeH3
  {
    NULL,
    CLASS,
    LIST,
    MAP,
    ENUM;
  }
}
