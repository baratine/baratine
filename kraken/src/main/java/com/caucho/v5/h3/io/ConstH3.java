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

  //new list
  public static final int DEF_INT8 = 1; //int8
  public static final int DEF_INT16 = 2; //int16
  public static final int DEF_INT32 = 3; //int32
  public static final int DEF_UINT8 = 4; //uint8
  public static final int DEF_UINT16 = 5; //uint16
  public static final int DEF_UNIT32 = 6; //unit 32
  public static final int DEF_UINT64 = 7; //uint64
  //public static final int DEF_CHAR = 8; //char
  public static final int DEF_FLOAT32 = 9; //float32
  public static final int DEF_FLOAT64 = 10; //float64
  //public static final int DEF_MAP = 11; //map
  //public static final int DEF_LIST = 12; //list
  public static final int DEF_OBJECT_ARRAY = 13; //Object[]
  public static final int DEF_CHAR_ARRAY = 14; //char[]
  public static final int DEF_STRING_ARRAY = 15; //String[]
  public static final int DEF_BOOL_ARRAY = 16; //bool[]
  public static final int DEF_INT8_ARRAY = 17; //int8[]
  public static final int DEF_INT16_ARRAY = 18; //int16[]
  public static final int DEF_INT32_ARRAY = 19; //int32[]
  public static final int DEF_INT64_ARRAY = 20; //int64[]
  public static final int DEF_UINT8_ARRAY = 21; //uint8[]
  public static final int DEF_UINT16_ARRAY = 22; //uint16[]
  public static final int DEF_UINT32_ARRAY = 23; //uint32[]
  public static final int DEF_UINT64_ARRAY = 24; //uint64[]
  public static final int DEF_INSTANT = 25; //Instant
  public static final int DEF_DURATION = 26; //Duration
  public static final int DEF_CLASS = 27; //Class
  public static final int DEF_BIGINTEGER = 28; //BigInteger
  public static final int DEF_BIGDECIMAL = 29; //BigDecimal
  public static final int DEF_STREAMSOURCE = 30; //StreamSource (blob)
  public static final int DEF_SET = 31; //Set
  public static final int DEF_ENUMMAP = 32; //EnumMap
  public static final int DEF_LINKEDMAP = 33; //LinkedMap
  public static final int DEF_TREEMAP = 34; //TreeMap
  public static final int DEF_ENUMSET = 35; //EnumSet
  public static final int DEF_HASHSET = 36; //HashSet
  public static final int DEF_LINKEDSET = 37; //LinkedSet
  public static final int DEF_TREESET = 38; //TreeSet
  public static final int DEF_LINKEDLIST = 39; //LinkedList
  public static final int DEF_QUEUE = 40; //Queue
  public static final int DEF_STACK = 41; //Stack
  public static final int DEF_DEQUE = 42; //Deque
  public static final int DEF_ZONEDDATETIME = 43; //ZonedDateTime
  public static final int DEF_ZONEOFFSETDATETIME = 44; //ZoneOffsetDateTime
  public static final int DEF_URL = 45; //URL
  public static final int DEF_UUID = 46; //UUID
  public static final int DEF_INETADDRESS = 47; //InetAddress
  public static final int DEF_INETADDRESS_WITH_PORT = 48; //InetAddress with Port
  public static final int DEF_PROXY = 49; //Proxy (address + api with custom resolver)
  public static final int DEF_REGEXP = 50; //Regexp

  //
  public static final int DEF_URI = 51; //URI
  public static final int DEF_LOCALDATE = 52; //local date
  public static final int DEF_LOCALDATETIME = 53; //local date time
  public static final int DEF_LOCALTIME = 54; //local date time

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
