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

package com.caucho.v5.kelp.query;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.io.TempOutputStream;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;

/**
 * Query program for hessian.
 */
public class EnvKelp implements PredicateKelp // , Hessian2Constants
{
  //private static final PathMapNullHessian _nullPathMap = PathMapNullHessian.MAP;
  
  private static final Double DOUBLE_ZERO = new Double(0);
  private static final Double DOUBLE_ONE = new Double(1);
  
  private TableKelp _table;
  private final PathKelp []_paths;
  private final ExprKelp _expr;
  private final Object []_values;
  
  private Object []_args;
  
  private PathMapHessian _topPathMap;
  private PathMapHessian _pathMap;

  private ArrayList<String[]> _classDefs = new ArrayList<>();
  
  private HashMap<String,Object> _attributeMap = new HashMap<>();
  
  private InputStream _is;

  private int _peek;

  private RowCursor _cursor;
  
  EnvKelp(TableKelp table,
          PathKelp []paths,
          ExprKelp expr,
          int valueLength)
  {
    Objects.requireNonNull(paths);
    Objects.requireNonNull(expr);
    
    _paths = paths;
    _expr = expr;
    
    _values = new Object[valueLength];
  }
  
  public EnvKelp(EnvKelp query, Object []args)
  {
    _paths = query._paths;
    _expr = query._expr;
    _values = new Object[query._values.length];
    _args = args;
  }
  
  public EnvKelp(Object []args)
  {
    _paths = new PathKelp[0];
    _expr = null;
    _values = new Object[0];
    _args = args;
  }
  
  public Object []getArgs()
  {
    return _args;
  }
  
  public Object []values()
  {
    return _values;
  }
  
  
  public RowCursor getCursor()
  {
    return _cursor;
  }
  
  public OutFactoryH3 serializationFactory()
  {
    return _table.serializer();
  }

  public Object getAttribute(String key)
  {
    return _attributeMap.get(key);
  }

  public void setAttribute(String key, Object value)
  {
    _attributeMap.put(key, value);
  }
  
  public void setCursor(RowCursor cursor)
  {
    _cursor = cursor;
  }

  
  @Override
  public boolean test(RowCursor cursor)
  {
    Arrays.fill(_values, null);
    
    _cursor = cursor;

    for (PathKelp path : _paths) {
      path.scan(this, _values, cursor);
    }

    return _expr.evalBoolean(this);
  }
  
  public boolean matchHessian(InputStream is)
    throws IOException
  {
    Arrays.fill(_values, null);
    
    for (PathKelp path : _paths) {
      path.scan(this, _values, is);
    }
    
    return _expr.evalBoolean(this);
  }
  
  public boolean match(PathMapHessian pathMap, InputStream is)
  {
    System.out.println("MAT: " + is);
    try {
      _topPathMap = pathMap;
      
      _is = is;
      _peek = -1;
      
      _classDefs.clear();
      _pathMap = _topPathMap;
      
      return matchImpl();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  private boolean matchImpl()
    throws IOException
  {
    //scanObject();
    
    return _expr.evalBoolean(this);
  }
  
  public void scan(PathMapHessian pathMap, InputStream is)
  {
    _topPathMap = pathMap;

    _is = is;
    _peek = -1;

    _classDefs.clear();
    _pathMap = _topPathMap;

    //scanObject();
  }
  
  /*
  void scanObject()
    throws IOException
  {
    int ch = read();
    
    switch (ch) {
    case 0x00: case 0x01: case 0x02: case 0x03:
    case 0x04: case 0x05: case 0x06: case 0x07:
    case 0x08: case 0x09: case 0x0a: case 0x0b:
    case 0x0c: case 0x0d: case 0x0e: case 0x0f:
      
    case 0x10: case 0x11: case 0x12: case 0x13:
    case 0x14: case 0x15: case 0x16: case 0x17:
    case 0x18: case 0x19: case 0x1a: case 0x1b:
    case 0x1c: case 0x1d: case 0x1e: case 0x1f:
      skip(ch - 0x00);
      break;
      
    case BC_MAP: {
      String type = readType();
      scanMap(type);
      break;
    }
    
    case BC_MAP_UNTYPED: {
      scanMap(null);
      break;
    }
    
    case BC_OBJECT_DEF: {
      scanObjectDef();
      scanObject();
      break;
    }
    
    case 0x60: case 0x61: case 0x62: case 0x63:
    case 0x64: case 0x65: case 0x66: case 0x67:
    case 0x68: case 0x69: case 0x6a: case 0x6b:
    case 0x6c: case 0x6d: case 0x6e: case 0x6f:
    {
      int index = ch - 0x60;
      
      String []fields = _classDefs.get(index);
      String type = fields[fields.length - 1]; 
      
      scanObjectInstance(type, fields, fields.length - 1);
      break;
    }
    
    case BC_OBJECT:
    {
      int index = scanInt();
      
      String []fields = _classDefs.get(index);
      String type = fields[fields.length - 1]; 
      
      scanObjectInstance(type, fields, fields.length - 1);
      break;
    }
    
    default:
      _peek = ch;
      skipObject();
      break;
    }
  }
  */
  
  private void scanObjectDef()
    throws IOException
  {
    StringBuilder sb = new StringBuilder();

    readString(sb);
    String type = sb.toString();
    
    //String type = readType();
    
    int len = scanInt();
    
    String []fields = new String[len + 1];
    fields[fields.length - 1] = type;
    
    for (int i = 0; i < len; i++) {
      fields[i] = scanString();
    }
    
    _classDefs.add(fields);
  }

  /**
   * Reads the Hessian value as an object.
   */
  Object readObject()
    throws IOException
  {
    int ch = read();

    switch (ch) {
    case 0x00: case 0x01: case 0x02: case 0x03:
    case 0x04: case 0x05: case 0x06: case 0x07:
    case 0x08: case 0x09: case 0x0a: case 0x0b:
    case 0x0c: case 0x0d: case 0x0e: case 0x0f:
      
    case 0x10: case 0x11: case 0x12: case 0x13:
    case 0x14: case 0x15: case 0x16: case 0x17:
    case 0x18: case 0x19: case 0x1a: case 0x1b:
    case 0x1c: case 0x1d: case 0x1e: case 0x1f:
      return readString(ch - 0x00);
      
    case 0x20: case 0x21: case 0x22: case 0x23:
    case 0x24: case 0x25: case 0x26: case 0x27:
    case 0x28: case 0x29: case 0x2a: case 0x2b:
    case 0x2c: case 0x2d: case 0x2e: case 0x2f:
      return readBinary(ch - 0x20);
      
    case 0x30: case 0x31: case 0x32: case 0x33:
      return readString(((ch - 0x30) << 8) + read());
      
    case 0x34: case 0x35: case 0x36: case 0x37:
      return readBinary(((ch - 0x34) << 8) + read());
      
      // long three-byte
    case 0x3c: case 0x3d: case 0x3e: case 0x3f:
      return new Long(((ch - 0x3c) << 16) + (read() << 8) + read());

    case 0x41:
      _peek = ch;
      return readBinary();
      
    case 0x42: {
      int len = readShort();
      return readBinary(len);
    }
    
    // class def
    case 0x43:
      scanObjectDef();
      return readObject();
      
    case 0x44: { /* double */
      long value = readLong();
      
      return Double.longBitsToDouble(value);
    }
      
    case 'E':
      throw new UnsupportedOperationException("Invalid Hessian 'E' error code.");
      
    case 0x46:
      return Boolean.FALSE;
      
    case 0x47: {
      int type = scanInt();
      return readObject();
    }
    
    // untyped variable map
    case 0x48: {
      HashMap<Object,Object> map = new HashMap<>();
    
      while ((ch = read()) != 'Z') {
        _peek = ch;
        map.put(readObject(), readObject());
      }
      return map;
    }
      
    case 0x49: { // 'I' int
      int value = readInt32();
      
      return new Integer(value);
    }
    
    case 0x4a: // time
      return new Date(readLong());
      
    case 0x4b: // time in seconds
      return new Date(readInt32() * 1000L);
    
    case 0x4c: { // 'L' long
      long value = readLong();
    
      return new Long(value);
    }
    
    // typed variable map
    case 0x4d: {
      HashMap<Object,Object> map = new HashMap<>();
      
      readType();
    
      while ((ch = read()) != 'Z') {
        _peek = ch;
        map.put(readObject(), readObject());
      }
      return map;
    }
    
    case 0x4e: // 'N' null
      return null;
      
    case 0x4f: // 'O' object instance
      {
        int type = scanInt();
        String []def = _classDefs.get(type);
        int len = def.length - 1;
        HashMap<Object,Object> map = new HashMap<>();
        
        for (int i = 0; i < len; i++) {
          map.put(def[i], readObject());
        }
        
        return map;
      }
      
    case 0x50: { // ext type
      String type = readType();
      return readObject();
    }
    
    case 0x51: { // backref
      int ref = readInt32();
      return null;
    }
      
      // string non-tail chunk
    case 0x52: {
      int len = readShort();
      StringBuilder sb = new StringBuilder();
      readString(sb, len);
      readString(sb);
      return sb.toString();
    }
      
      // string tail chunk
    case 0x53: {
      int len = readShort();
      return readString(len);
    }
      
    case 0x54:
      return Boolean.TRUE;
      
      // typed variable list
    case 0x55: {
      ArrayList<Object> list = new ArrayList<>();
      
      readType();
      while ((ch = read()) != 'Z') {
        _peek = ch;
        list.add(readObject());
      }
      return list;
    }
      
      // typed fixed list
    case 0x56: {
      ArrayList<Object> list = new ArrayList<>();

      readType();
      int len = scanInt();
      for (int i = 0; i < len; i++) {
        list.add(readObject());
      }
      return list;
    }
      
      // untyped variable list
    case 0x57: {
      ArrayList<Object> list = new ArrayList<>();
      
      while ((ch = read()) != 'Z') {
        _peek = ch;
        list.add(readObject());
      }
      return list;
    }

      // untyped fixed list
    case 0x58: {
      ArrayList<Object> list = new ArrayList<>();
      int len = scanInt();
      for (int i = 0; i < len; i++) {
        list.add(readObject());
      }
      return list;
    }
    
    case 0x59: { // (long) int
      long value = readInt32();
    
      return new Long(value);
    }
    
    case 0x5b: // 0.0
      return DOUBLE_ZERO;
      
    case 0x5c: // 1.0
      return DOUBLE_ONE;
      
    case 0x5d: // double(b0)
      return new Double((byte) read());
      
    case 0x5e: // double(short)
      return new Double((short) readShort());
      
    case 0x5f: // double mill
      return new Double(readInt32() * 0.001);
      
      // object instance
    case 0x60: case 0x61: case 0x62: case 0x63:
    case 0x64: case 0x65: case 0x66: case 0x67:
    case 0x68: case 0x69: case 0x6a: case 0x6b:
    case 0x6c: case 0x6d: case 0x6e: case 0x6f:
    {
      int type = ch - 0x60;
      String []def = _classDefs.get(type);
      int len = def.length - 1;
      HashMap<Object,Object> map = new HashMap<>();
      
      for (int i = 0; i < len; i++) {
        map.put(def[i], readObject());
      }
      
      return map;
    }

      // direct fixed typed list
    case 0x70: case 0x71: case 0x72: case 0x73:
    case 0x74: case 0x75: case 0x76: case 0x77: {
      ArrayList<Object> list = new ArrayList<>();
      readType();
      int len = ch - 0x70;
      for (int i = 0; i < len; i++) {
        list.add(readObject());
      }
      return list;
    }
      
      // direct fixed untyped list
    case 0x78: case 0x79: case 0x7a: case 0x7b:
    case 0x7c: case 0x7d: case 0x7e: case 0x7f: {
      ArrayList<Object> list = new ArrayList<>();
      int len = ch - 0x78;
      for (int i = 0; i < len; i++) {
        list.add(readObject());
      }
      return list;
    }
      
      // int single-byte
    case 0x80: case 0x81: case 0x82: case 0x83:
    case 0x84: case 0x85: case 0x86: case 0x87:
    case 0x88: case 0x89: case 0x8a: case 0x8b:
    case 0x8c: case 0x8d: case 0x8e: case 0x8f:
      
    case 0x90: case 0x91: case 0x92: case 0x93:
    case 0x94: case 0x95: case 0x96: case 0x97:
    case 0x98: case 0x99: case 0x9a: case 0x9b:
    case 0x9c: case 0x9d: case 0x9e: case 0x9f:
      
    case 0xa0: case 0xa1: case 0xa2: case 0xa3:
    case 0xa4: case 0xa5: case 0xa6: case 0xa7:
    case 0xa8: case 0xa9: case 0xaa: case 0xab:
    case 0xac: case 0xad: case 0xae: case 0xaf:
      
    case 0xb0: case 0xb1: case 0xb2: case 0xb3:
    case 0xb4: case 0xb5: case 0xb6: case 0xb7:
    case 0xb8: case 0xb9: case 0xba: case 0xbb:
    case 0xbc: case 0xbd: case 0xbe: case 0xbf:
      return new Integer(ch - 0x90);
      
      // int two-byte
    case 0xc0: case 0xc1: case 0xc2: case 0xc3:
    case 0xc4: case 0xc5: case 0xc6: case 0xc7:
    case 0xc8: case 0xc9: case 0xca: case 0xcb:
    case 0xcc: case 0xcd: case 0xce: case 0xcf:
      return new Integer(((ch - 0xc8) << 8) + read());
      
      // int three-byte
    case 0xd0: case 0xd1: case 0xd2: case 0xd3:
    case 0xd4: case 0xd5: case 0xd6: case 0xd7:
      return new Integer(((ch - 0xd4) << 16) + (read() << 8) + read());
      
      // long single byte
    case 0xd8: case 0xd9: case 0xda: case 0xdb:
    case 0xdc: case 0xdd: case 0xde: case 0xdf:
      
    case 0xe0: case 0xe1: case 0xe2: case 0xe3:
    case 0xe4: case 0xe5: case 0xe6: case 0xe7:
    case 0xe8: case 0xe9: case 0xea: case 0xeb:
    case 0xec: case 0xed: case 0xee: case 0xef:
      return new Long(ch - 0xe0);
      
      // int two-byte
    case 0xf0: case 0xf1: case 0xf2: case 0xf3:
    case 0xf4: case 0xf5: case 0xf6: case 0xf7:
    case 0xf8: case 0xf9: case 0xfa: case 0xfb:
    case 0xfc: case 0xfd: case 0xfe: case 0xff:
      return new Long(((ch - 0xf8) << 8) + read());
      
    default:
      throw new UnsupportedOperationException("0x" + Integer.toHexString(ch));
    }
  }
  
  /**
   * Skips over the next object and all its children.
   */
  boolean skipObject()
    throws IOException
  {
    int ch = read();
    int len;
    
    switch (ch) {
     // inline string (1 byte)
    case 0x00: case 0x01: case 0x02: case 0x03:
    case 0x04: case 0x05: case 0x06: case 0x07:
    case 0x08: case 0x09: case 0x0a: case 0x0b:
    case 0x0c: case 0x0d: case 0x0e: case 0x0f:
      
    case 0x10: case 0x11: case 0x12: case 0x13:
    case 0x14: case 0x15: case 0x16: case 0x17:
    case 0x18: case 0x19: case 0x1a: case 0x1b:
    case 0x1c: case 0x1d: case 0x1e: case 0x1f:
      skip(ch - 0x00);
      return true;
      
      // inline binary (1 byte)
    case 0x20: case 0x21: case 0x22: case 0x23:
    case 0x24: case 0x25: case 0x26: case 0x27:
    case 0x28: case 0x29: case 0x2a: case 0x2b:
    case 0x2c: case 0x2d: case 0x2e: case 0x2f:
      skip(ch - 0x20);
      return true;
      
      // string (1 byte)
    case 0x30: case 0x31: case 0x32: case 0x33:
      len = 256 * (ch - 0x30) + read();
      skip(len);
      return true;
      
      // binary (1 byte)
    case 0x34: case 0x35: case 0x36: case 0x37:
      len = 256 * (ch - 0x34) + read();
      skip(len);
      return true;
      
      // 0x38-0x3b are reserved
      
      // long three-byte
    case 0x3c: case 0x3d: case 0x3e: case 0x3f:
      skip(2);
      return true;
      
      // 0x40 is reserved
      
      // binary non-tail chunk
    case 0x41:
      len = readShort();
      skip(len);
      return skipObject();
      
      // binary tail chunk
    case 0x42:
      len = readShort();
      skip(len);
      return true;
      
      // class def
    case 0x43:
      scanObjectDef();
      return skipObject();
      
      // 64-bit double
    case 0x44:
      skip(8);
      return true;
      
      // error marker
    case 0x45:
      throw new IllegalStateException("Invalid Hessian bytecode 'E'");
      
      // false
    case 0x46:
      return true;
      
      // ext type (fixed int)
    case 0x47:
      skipObject();
      skipObject();
      return true;
      
    case 0x48: { // untyped map 'H'
      skipMap();
      return true;
    }
      
      // 32-bit int
    case 0x49:
      skip(4);
      return true;
      
      // 64-bit date
    case 0x4a:
      skip(8);
      return true;
      
      // 32-bit date
    case 0x4b:
      skip(4);
      return true;
      
      // 64-bit long
    case 0x4c:
      skip(8);
      return true;
      
    case 0x4d: { // typed map 'M'
      skipObject();
      skipMap();
      return true;
    }
      
      // null
    case 0x4e:
      return true;
      
    case 0x4f: // object instance
      {
        int type = scanInt();
        String []def = _classDefs.get(type);
        len = def.length - 1;
        for (int i = 0; i < len; i++) {
          skipObject();
        }
        return true;
      }
      
      // ext type (named)
    case 0x50:
      skipObject();
      skipObject();
      return true;
      
      // backref
    case 0x51:
      skipObject();
      return true;
      
      // string non-tail chunk
    case 0x52:
      len = readShort();
      skip(len);
      return skipObject();
      
      // string tail chunk
    case 0x53:
      len = readShort();
      skip(len);
      return true;
      
      // true
    case 0x54:
      return true;
      
      // typed variable list
    case 0x55:
      readType();
      while (skipObject()) {
      }
      return true;
      
      // typed fixed list
    case 0x56:
      readType();
      len = scanInt();
      for (int i = 0; i < len; i++) {
        skipObject();
      }
      return true;
      
      // untyped variable list
    case 0x57:
      while (skipObject()) {
      }
      return true;
      
      // untyped fixed list
    case 0x58:
      len = scanInt();
      for (int i = 0; i < len; i++) {
        skipObject();
      }
      return true;
      
      // 32-bit long
    case 0x59:
      skip(4);
      return true;
      
    case 0x5a: // 'Z' end
      return false;
      
      // 0.0, 1.0
    case 0x5b: case 0x5c:
      return true;
      
      // (double) b0
    case 0x5d:
      skip(1);
      return true;
      
      // (double) b1 b0
    case 0x5e:
      skip(2);
      return true;
      
      // (double) int
    case 0x5f:
      return skipObject();
      
      // object instance
    case 0x60: case 0x61: case 0x62: case 0x63:
    case 0x64: case 0x65: case 0x66: case 0x67:
    case 0x68: case 0x69: case 0x6a: case 0x6b:
    case 0x6c: case 0x6d: case 0x6e: case 0x6f:
    {
      int type = ch - 0x60;
      String []def = _classDefs.get(type);
      len = def.length - 1;
      for (int i = 0; i < len; i++) {
        skipObject();
      }
      return true;
    }

      // direct fixed typed list
    case 0x70: case 0x71: case 0x72: case 0x73:
    case 0x74: case 0x75: case 0x76: case 0x77:
      skipObject();
      len = ch - 0x70;
      for (int i = 0; i < len; i++) {
        skipObject();
      }
      return true;
      
      // direct fixed untyped list
    case 0x78: case 0x79: case 0x7a: case 0x7b:
    case 0x7c: case 0x7d: case 0x7e: case 0x7f:
      len = ch - 0x78;
      for (int i = 0; i < len; i++) {
        skipObject();
      }
      return true;
      
      // int single-byte
    case 0x80: case 0x81: case 0x82: case 0x83:
    case 0x84: case 0x85: case 0x86: case 0x87:
    case 0x88: case 0x89: case 0x8a: case 0x8b:
    case 0x8c: case 0x8d: case 0x8e: case 0x8f:
      
    case 0x90: case 0x91: case 0x92: case 0x93:
    case 0x94: case 0x95: case 0x96: case 0x97:
    case 0x98: case 0x99: case 0x9a: case 0x9b:
    case 0x9c: case 0x9d: case 0x9e: case 0x9f:
      
    case 0xa0: case 0xa1: case 0xa2: case 0xa3:
    case 0xa4: case 0xa5: case 0xa6: case 0xa7:
    case 0xa8: case 0xa9: case 0xaa: case 0xab:
    case 0xac: case 0xad: case 0xae: case 0xaf:
      
    case 0xb0: case 0xb1: case 0xb2: case 0xb3:
    case 0xb4: case 0xb5: case 0xb6: case 0xb7:
    case 0xb8: case 0xb9: case 0xba: case 0xbb:
    case 0xbc: case 0xbd: case 0xbe: case 0xbf:
      return true;
      
      // int two-byte
    case 0xc0: case 0xc1: case 0xc2: case 0xc3:
    case 0xc4: case 0xc5: case 0xc6: case 0xc7:
    case 0xc8: case 0xc9: case 0xca: case 0xcb:
    case 0xcc: case 0xcd: case 0xce: case 0xcf:
      skip(1);
      return true;
      
      // int three-byte
    case 0xd0: case 0xd1: case 0xd2: case 0xd3:
    case 0xd4: case 0xd5: case 0xd6: case 0xd7:
      skip(2);
      return true;
      
      // long single-byte
    case 0xd8: case 0xd9: case 0xda: case 0xdb:
    case 0xdc: case 0xdd: case 0xde: case 0xdf:
      
    case 0xe0: case 0xe1: case 0xe2: case 0xe3:
    case 0xe4: case 0xe5: case 0xe6: case 0xe7:
    case 0xe8: case 0xe9: case 0xea: case 0xeb:
    case 0xec: case 0xed: case 0xee: case 0xef:
      return true;

      // long two-byte
    case 0xf0: case 0xf1: case 0xf2: case 0xf3:
    case 0xf4: case 0xf5: case 0xf6: case 0xf7:
    case 0xf8: case 0xf9: case 0xfa: case 0xfb:
    case 0xfc: case 0xfd: case 0xfe: case 0xff:
      skip(1);
      return true;
      
    
    default:
      throw new UnsupportedOperationException("0x" + Integer.toHexString(ch));
    }
  }
  
  /*
  private void scanMap(String type)
    throws IOException
  {
    String key;

    PathMapHessian pathMap = _pathMap;
    
    while ((key = scanKey()) != null) {
      PathHessian path = pathMap.get(key);
   
      if (path != null) {
        _pathMap = path.getPathMap();
        
        path.scan(this, _values);
        
        _pathMap = pathMap;
      }
      else {
        skipObject();
      }
    }
  }
  */
  
  private void scanObjectInstance(String type, String []fields, int fieldLen)
    throws IOException
  {
    PathMapHessian pathMap = _pathMap;
    
    for (int i = 0; i < fieldLen; i++) {
      String key = fields[i];
      
      PathHessian path = pathMap.get(key);

      if (path == null) {
        skipObject();
      }
      else {
        _pathMap = path.getPathMap();

        path.scan(this, _values);
        
        _pathMap = pathMap;
      }
    }
  }

  /**
   * Reads the Hessian value as an object.
   */
  String scanString()
    throws IOException
  {
    int ch = read();
    
    switch (ch) {
    case 'N':
      return null;
      
    case 0x00: case 0x01: case 0x02: case 0x03:
    case 0x04: case 0x05: case 0x06: case 0x07:
    case 0x08: case 0x09: case 0x0a: case 0x0b:
    case 0x0c: case 0x0d: case 0x0e: case 0x0f:
      
    case 0x10: case 0x11: case 0x12: case 0x13:
    case 0x14: case 0x15: case 0x16: case 0x17:
    case 0x18: case 0x19: case 0x1a: case 0x1b:
    case 0x1c: case 0x1d: case 0x1e: case 0x1f:
      return readString(ch - 0x00);
      
    default:
      throw new UnsupportedOperationException(getClass().getName());
    }
  }
  
  private int scanInt()
    throws IOException
  {
    int ch = read();
    
    switch (ch) {
    case -1:
      return -1;
      
    case 0x80: case 0x81: case 0x82: case 0x83:
    case 0x84: case 0x85: case 0x86: case 0x87:
    case 0x88: case 0x89: case 0x8a: case 0x8b:
    case 0x8c: case 0x8d: case 0x8e: case 0x8f:
      
    case 0x90: case 0x91: case 0x92: case 0x93:
    case 0x94: case 0x95: case 0x96: case 0x97:
    case 0x98: case 0x99: case 0x9a: case 0x9b:
    case 0x9c: case 0x9d: case 0x9e: case 0x9f:
      
    case 0xa0: case 0xa1: case 0xa2: case 0xa3:
    case 0xa4: case 0xa5: case 0xa6: case 0xa7:
    case 0xa8: case 0xa9: case 0xaa: case 0xab:
    case 0xac: case 0xad: case 0xae: case 0xaf:
      
    case 0xb0: case 0xb1: case 0xb2: case 0xb3:
    case 0xb4: case 0xb5: case 0xb6: case 0xb7:
    case 0xb8: case 0xb9: case 0xba: case 0xbb:
    case 0xbc: case 0xbd: case 0xbe: case 0xbf:
      return ch - 0x90;
      
    case 0x49:
      return readInt32();
      
    default:
      throw new IllegalStateException("Expected integer code at " + Integer.toHexString(ch));
    }
    
  }
  
  /*
  private String scanKey()
    throws IOException
  {
    int ch = read();

    switch (ch) {
    case -1:
      return null;
      
    case 0x00: case 0x01: case 0x02: case 0x03:
    case 0x04: case 0x05: case 0x06: case 0x07:
    case 0x08: case 0x09: case 0x0a: case 0x0b:
    case 0x0c: case 0x0d: case 0x0e: case 0x0f:
      
    case 0x10: case 0x11: case 0x12: case 0x13:
    case 0x14: case 0x15: case 0x16: case 0x17:
    case 0x18: case 0x19: case 0x1a: case 0x1b:
    case 0x1c: case 0x1d: case 0x1e: case 0x1f:
    {
      int len = ch - 0x00;
      
      return readString(len);
    }
      
    case BC_END:
      return null;
      
    default:
      _peek = ch;
      scanObject();
      return "";
    }
  }
  */
  
  private void skipMap()
    throws IOException
  {
    while (skipObject()) {
      skipObject();
    }
  }

  /**
   * Reads the Hessian value as an object.
   */
  void readString(StringBuilder sb)
    throws IOException
  {
    while (true) {
      int ch = read();
      int len;
    
      switch (ch) {
      default:
        _peek = ch;
        return;
      
      case 0x00: case 0x01: case 0x02: case 0x03:
      case 0x04: case 0x05: case 0x06: case 0x07:
      case 0x08: case 0x09: case 0x0a: case 0x0b:
      case 0x0c: case 0x0d: case 0x0e: case 0x0f:

      case 0x10: case 0x11: case 0x12: case 0x13:
      case 0x14: case 0x15: case 0x16: case 0x17:
      case 0x18: case 0x19: case 0x1a: case 0x1b:
      case 0x1c: case 0x1d: case 0x1e: case 0x1f:
        len = ch - 0x00;
        readString(sb, len);
        return;
      
      case 0x30: case 0x31: case 0x32: case 0x33:
        len = ((ch - 0x30) << 8) + read();
        readString(sb, len);
        return;
        
      case 'S':
        len = readShort();
        readString(sb, len);
        return;
        
      case 'R':
        len = readShort();
        readString(sb, len);
        break;
      }
    }
  }

  /**
   * Reads the Hessian value as an object.
   */
  byte []readBinary()
    throws IOException
  {
    TempOutputStream tos = new TempOutputStream();
    
    while (true) {
      int ch = read();
      int len;
    
      switch (ch) {
      default:
        _peek = ch;
        return tos.toByteArray();
      
      case 0x20: case 0x21: case 0x22: case 0x23:
      case 0x24: case 0x25: case 0x26: case 0x27:
      case 0x28: case 0x29: case 0x2a: case 0x2b:
      case 0x2c: case 0x2d: case 0x2e: case 0x2f:
        len = ch - 0x20;
        readBinary(tos, len);
        return tos.toByteArray();
      
      case 0x34: case 0x35: case 0x36: case 0x37:
        len = ((ch - 0x34) << 8) + read();
        readBinary(tos, len);
        return tos.toByteArray();
        
      case 'B':
        len = readShort();
        readBinary(tos, len);
        return tos.toByteArray();
        
      case 'A':
        len = readShort();
        readBinary(tos, len);
        break;
      }
    }
  }
  
  void readBinary(TempOutputStream tos, int len)
    throws IOException
  {
    for (int i = 0; i < len; i++) {
      tos.write(read());
    }
  }

  private String readString(int len)
    throws IOException
  {
    StringBuilder sb = new StringBuilder();
    
    readString(sb, len);
    
    return sb.toString();
  }
  
  private void readString(StringBuilder sb, int len)
    throws IOException
  {
    for (; len > 0; len--) {
      int ch = readUTF8();
      
      // XXX: UTF-8
      
      sb.append((char) ch);
    }
  }
  
  private int readUTF8()
    throws IOException
  {
    return read();
  }
  
  private byte []readBinary(int len)
    throws IOException
  {
    byte []buffer = new byte[len];
    
    for (int i = 0; i < len; i++) {
      buffer[i] = (byte) read();
    }
    
    return buffer;
  }
  
  private String readType()
    throws IOException
  {
    int ch = read();
    
    switch (ch) {
    case 0x00: case 0x01: case 0x02: case 0x03:
    case 0x04: case 0x05: case 0x06: case 0x07:
    case 0x08: case 0x09: case 0x0a: case 0x0b:
    case 0x0c: case 0x0d: case 0x0e: case 0x0f:
      
    case 0x10: case 0x11: case 0x12: case 0x13:
    case 0x14: case 0x15: case 0x16: case 0x17:
    case 0x18: case 0x19: case 0x1a: case 0x1b:
    case 0x1c: case 0x1d: case 0x1e: case 0x1f:
      return readString(ch - 0);
      
    case 'N':
      return null;
      
    case 0x90: case 0x91: case 0x92: case 0x93:
    case 0x94: case 0x95: case 0x96: case 0x97:
    case 0x98: case 0x99: case 0x9a: case 0x9b:
    case 0x9c: case 0x9d: case 0x9e: case 0x9f:
      
    case 0xa0: case 0xa1: case 0xa2: case 0xa3:
    case 0xa4: case 0xa5: case 0xa6: case 0xa7:
    case 0xa8: case 0xa9: case 0xaa: case 0xab:
    case 0xac: case 0xad: case 0xae: case 0xaf:
      
    case 0xb0: case 0xb1: case 0xb2: case 0xb3:
    case 0xb4: case 0xb5: case 0xb6: case 0xb7:
    case 0xb8: case 0xb9: case 0xba: case 0xbb:
    case 0xbc: case 0xbd: case 0xbe: case 0xbf:
      return null;
      
    default:
      throw new UnsupportedOperationException(Integer.toHexString(ch));
    }
  }
  
  private void skip(int len)
    throws IOException
  {
    for (; len > 0; len--) {
      read();
    }
  }
  
  private int readShort()
    throws IOException
  {
    return 256 * read() + read();
  }
  
  private int readInt32()
    throws IOException
  {
      return ((read() << 24L)
          + (read() << 16L)
          + (read() << 8L)
          + (read()));
  }
  
  private long readLong()
    throws IOException
  {
    return (((long) read() << 56L)
        + ((long) read() << 48L)
        + ((long) read() << 40L)
        + ((long) read() << 32L)
        
        + ((long) read() << 24L)
        + ((long) read() << 16L)
        + ((long) read() << 8L)
        + ((long) read()));
  }
  
  private int read()
    throws IOException
  {
    int peek = _peek;
    
    if (peek >= 0) {
      _peek = -1;
      return peek;
    }
    
    return _is.read();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _expr + "]";
  }
}
