/*
 * Copyright (c) 2001-2016 Caucho Technology, Inc.  All rights reserved.
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.h3.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import com.caucho.v5.h3.query.PathH3Amp;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.L10N;

/**
 * H3 output interface
 */
public class InRawH3Impl implements InRawH3
{
  private static final L10N L = new L10N(InRawH3Impl.class);
  
  private InputStream _is;
  
  private byte []_buffer;
  private int _offset;
  private int _length;
  
  private char []_charBuffer = new char[256];

  private TempBuffer _tBuf;
  
  public InRawH3Impl(InputStream is)
  {
    Objects.requireNonNull(is);
    _is = is;
    
    _tBuf = TempBuffer.allocate();
    _buffer = _tBuf.buffer();
    _length = 0;
  }

  @Override
  public void readNull()
  {
    int ch;
    
    switch ((ch = read())) {
    case ConstH3.NULL:
      return;
      
    default:
      throw error(L.l("Unexpected opcode 0x{0} while reading null",
                      Integer.toHexString(ch)));
    }
  }

  @Override
  public boolean readBoolean()
  {
    int ch;
    
    switch ((ch = read())) {
    case ConstH3.FALSE:
      return Boolean.FALSE;
      
    case ConstH3.TRUE:
      return Boolean.TRUE;
      
    default:
      throw error(L.l("Unexpected opcode 0x{0} while reading boolean",
                      Integer.toHexString(ch)));
    }
  }

  @Override
  public long readLong()
  {
    int ch = read();
    
    if ((ch & ConstH3.INTEGER_OPMASK) != ConstH3.INTEGER) {
      throw error(L.l("unexpected end of file while reading long"));
    }
    
    return readLong(ch);
  }
  
  private long readLong(int ch)
  {
    ch -= ConstH3.INTEGER;
    
    boolean sign = (ch & 1) == 0;
    
    if (ch <= ConstH3.INTEGER_MASK) {
      return sign ? (ch >> 1) : -(ch >> 1) - 1;
    }
    
    long value = (ch & ConstH3.INTEGER_MASK) >> 1;
    
    int shift = ConstH3.INTEGER_BITS - 2;
    
    value = readLong(value, shift);
    
    return sign ? value : -value - 1; 
  }
  
  @Override
  public long readUnsigned()
  {
    return readLong(0, 0);
  }
  
  @Override
  public float readFloat()
  {
    return (float) readDouble();
  }
  
  @Override
  public double readDouble()
  {
    int ch = read();
    
    switch (ch) {
    case 0xf3:
      return readDoubleData();
    
    case 0xf4:
      return readFloatData();
      
    default:
      throw new UnsupportedOperationException(Long.toHexString(ch));
    }
  }

  @Override
  public String readString()
  {
    int ch = read();
    
    switch (ch) {
    case 0x80: case 0x81: case 0x82: case 0x83: 
    case 0x84: case 0x85: case 0x86: case 0x87:
    case 0x88: case 0x89: case 0x8a: case 0x8b:
    case 0x8c: case 0x8d: case 0x8e: case 0x8f:
      
    case 0x90: case 0x91: case 0x92: case 0x93: 
    case 0x94: case 0x95: case 0x96: case 0x97:
    case 0x98: case 0x99: case 0x9a: case 0x9b:
    case 0x9c: case 0x9d: case 0x9e: case 0x9f:
      return readString(ch - 0x80);
      
    case 0xa0: case 0xa1: case 0xa2: case 0xa3: 
    case 0xa4: case 0xa5: case 0xa6: case 0xa7:
    case 0xa8: case 0xa9: case 0xaa: case 0xab:
    case 0xac: case 0xad: case 0xae: case 0xaf:
      
    case 0xb0: case 0xb1: case 0xb2: case 0xb3: 
    case 0xb4: case 0xb5: case 0xb6: case 0xb7:
    case 0xb8: case 0xb9: case 0xba: case 0xbb:
    case 0xbc: case 0xbd: case 0xbe: case 0xbf:
      return readString((int) readLong(ch - 0xa0, 5));
      
    default:
      throw error(L.l("Unexpected opcode 0x{0} while reading string",
                      Integer.toHexString(ch)));
    }
  }

  @Override
  public byte []readBinary()
  {
    int ch = read();
    
    switch (ch) {
    case 0xc0: case 0xc1: case 0xc2: case 0xc3: 
    case 0xc4: case 0xc5: case 0xc6: case 0xc7:
    {
      byte []data = new byte[ch - 0xc0];
      readBinaryData(data, 0, data.length);
      return data;
    }
    
    case 0xc8: case 0xc9: case 0xca: case 0xcb:
    case 0xcc: case 0xcd: case 0xce: case 0xcf:
    {
      byte []data = new byte[(int) readLong(ch - 0xc8, 3)];
      readBinaryData(data, 0, data.length);
      return data;
    }
      
    default:
      throw error(L.l("Unexpected opcode 0x{0} while reading binary",
                      Integer.toHexString(ch)));
    }
  }

  @Override
  public Object readObject(InH3Amp inAmp)
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
      
    case 0x20: case 0x21: case 0x22: case 0x23: 
    case 0x24: case 0x25: case 0x26: case 0x27:
    case 0x28: case 0x29: case 0x2a: case 0x2b:
    case 0x2c: case 0x2d: case 0x2e: case 0x2f:
      
    case 0x30: case 0x31: case 0x32: case 0x33: 
    case 0x34: case 0x35: case 0x36: case 0x37:
    case 0x38: case 0x39: case 0x3a: case 0x3b:
    case 0x3c: case 0x3d: case 0x3e: case 0x3f:
      
    case 0x40: case 0x41: case 0x42: case 0x43: 
    case 0x44: case 0x45: case 0x46: case 0x47:
    case 0x48: case 0x49: case 0x4a: case 0x4b:
    case 0x4c: case 0x4d: case 0x4e: case 0x4f:
      
    case 0x50: case 0x51: case 0x52: case 0x53: 
    case 0x54: case 0x55: case 0x56: case 0x57:
    case 0x58: case 0x59: case 0x5a: case 0x5b:
    case 0x5c: case 0x5d: case 0x5e: case 0x5f:
      
    case 0x60: case 0x61: case 0x62: case 0x63: 
    case 0x64: case 0x65: case 0x66: case 0x67:
    case 0x68: case 0x69: case 0x6a: case 0x6b:
    case 0x6c: case 0x6d: case 0x6e: case 0x6f:
      
    case 0x70: case 0x71: case 0x72: case 0x73: 
    case 0x74: case 0x75: case 0x76: case 0x77:
    case 0x78: case 0x79: case 0x7a: case 0x7b:
    case 0x7c: case 0x7d: case 0x7e: case 0x7f:
      return readLong(ch);
      
    case 0x80: case 0x81: case 0x82: case 0x83: 
    case 0x84: case 0x85: case 0x86: case 0x87:
    case 0x88: case 0x89: case 0x8a: case 0x8b:
    case 0x8c: case 0x8d: case 0x8e: case 0x8f:
      
    case 0x90: case 0x91: case 0x92: case 0x93: 
    case 0x94: case 0x95: case 0x96: case 0x97:
    case 0x98: case 0x99: case 0x9a: case 0x9b:
    case 0x9c: case 0x9d: case 0x9e: case 0x9f:
      return readString(ch - 0x80);
      
    case 0xa0: case 0xa1: case 0xa2: case 0xa3: 
    case 0xa4: case 0xa5: case 0xa6: case 0xa7:
    case 0xa8: case 0xa9: case 0xaa: case 0xab:
    case 0xac: case 0xad: case 0xae: case 0xaf:
      
    case 0xb0: case 0xb1: case 0xb2: case 0xb3: 
    case 0xb4: case 0xb5: case 0xb6: case 0xb7:
    case 0xb8: case 0xb9: case 0xba: case 0xbb:
    case 0xbc: case 0xbd: case 0xbe: case 0xbf:
      return readString((int) readLong(ch - 0xa0, 5));
      
    case 0xc0: case 0xc1: case 0xc2: case 0xc3: 
    case 0xc4: case 0xc5: case 0xc6: case 0xc7:
    {
      byte []data = new byte[ch - 0xc0];
      readBinaryData(data, 0, data.length);
      return data;
    }
    
    case 0xc8: case 0xc9: case 0xca: case 0xcb:
    case 0xcc: case 0xcd: case 0xce: case 0xcf:
    {
      byte []data = new byte[(int) readLong(ch - 0xc8, 3)];
      readBinaryData(data, 0, data.length);
      return data;
    }
      
    case 0xd0:
      readDefinition(inAmp);
      
      return readObject(inAmp);
      
    case 0xd1: case 0xd2: case 0xd3: 
    case 0xd4: case 0xd5: case 0xd6: case 0xd7:
    case 0xd8: case 0xd9: case 0xda: case 0xdb:
    case 0xdc: case 0xdd: case 0xde: case 0xdf:
    {
      int id = ch - 0xd0;
      
      return inAmp.serializer(id).readObject(this, inAmp);
    }
      
    case 0xe0: case 0xe1: case 0xe2: case 0xe3: 
    case 0xe4: case 0xe5: case 0xe6: case 0xe7:
    case 0xe8: case 0xe9: case 0xea: case 0xeb:
    case 0xec: case 0xed: case 0xee: case 0xef:
    {
      int id = (int) readLong(ch - 0xe0, 4);
      
      return inAmp.serializer(id).readObject(this, inAmp);
    }
    
    case 0xf0:
      return null;
      
    case 0xf1:
      return Boolean.FALSE;
      
    case 0xf2:
      return Boolean.TRUE;
      
    case 0xf3:
      return readDoubleData();
      
    case 0xf4:
      return readFloatData();
      
    default:
      throw error(L.l("Unexpected opcode 0x{0} while reading object",
                      Integer.toHexString(ch)));
    }
  }
  
  private float readFloatData()
  {
    int value = ((read() << 24)
        | (read() << 16)
        | (read() << 8)
        | (read()));
    
    return Float.intBitsToFloat(value);
  }
  
  private double readDoubleData()
  {
    long value = (((long) read() << 56)
        | ((long) read() << 48)
        | ((long) read() << 40)
        | ((long) read() << 32)
        | (read() << 24)
        | (read() << 16)
        | (read() << 8)
        | (read()));
    
    return Double.longBitsToDouble(value);
  }
  
  public boolean wasNull()
  {
    return _offset > 0 && _buffer[_offset - 1] == ConstH3.NULL;
  }

  /**
   * Scan for a query.
   */
  @Override
  public void scan(InH3Amp in, PathH3Amp path, Object[] values)
  {
    int ch = read();
    
    switch (ch) {
    case 0xd0:
      readDefinition(in);
      scan(in, path, values);
      return;
      
    case 0xd1: case 0xd2: case 0xd3: 
    case 0xd4: case 0xd5: case 0xd6: case 0xd7:
    case 0xd8: case 0xd9: case 0xda: case 0xdb:
    case 0xdc: case 0xdd: case 0xde: case 0xdf:
    {
      int id = ch - 0xd0;

      in.serializer(id).scan(this, path, in, values);
      return;
    }
      
    case 0xe0: case 0xe1: case 0xe2: case 0xe3: 
    case 0xe4: case 0xe5: case 0xe6: case 0xe7:
    case 0xe8: case 0xe9: case 0xea: case 0xeb:
    case 0xec: case 0xed: case 0xee: case 0xef:
    {
      int id = (int) readLong(ch - 0xe0, 4);
      
      in.serializer(id).scan(this, path, in, values);
      return;
    }
      
    default:
      throw error(L.l("Unexpected opcode 0x{0} while scanning for {1}",
                      Integer.toHexString(ch), path));
    }
  }

  /**
   * Skip based on queries.
   */
  @Override
  public void skip(InH3Amp in)
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
      
    case 0x20: case 0x21: case 0x22: case 0x23: 
    case 0x24: case 0x25: case 0x26: case 0x27:
    case 0x28: case 0x29: case 0x2a: case 0x2b:
    case 0x2c: case 0x2d: case 0x2e: case 0x2f:
      
    case 0x30: case 0x31: case 0x32: case 0x33: 
    case 0x34: case 0x35: case 0x36: case 0x37:
    case 0x38: case 0x39: case 0x3a: case 0x3b:
    case 0x3c: case 0x3d: case 0x3e: case 0x3f:
      return;
      
    case 0x40: case 0x41: case 0x42: case 0x43: 
    case 0x44: case 0x45: case 0x46: case 0x47:
    case 0x48: case 0x49: case 0x4a: case 0x4b:
    case 0x4c: case 0x4d: case 0x4e: case 0x4f:
      
    case 0x50: case 0x51: case 0x52: case 0x53: 
    case 0x54: case 0x55: case 0x56: case 0x57:
    case 0x58: case 0x59: case 0x5a: case 0x5b:
    case 0x5c: case 0x5d: case 0x5e: case 0x5f:
      
    case 0x60: case 0x61: case 0x62: case 0x63: 
    case 0x64: case 0x65: case 0x66: case 0x67:
    case 0x68: case 0x69: case 0x6a: case 0x6b:
    case 0x6c: case 0x6d: case 0x6e: case 0x6f:
      
    case 0x70: case 0x71: case 0x72: case 0x73: 
    case 0x74: case 0x75: case 0x76: case 0x77:
    case 0x78: case 0x79: case 0x7a: case 0x7b:
    case 0x7c: case 0x7d: case 0x7e: case 0x7f:
      readLong(0,0);
      return;
      
    case 0x80: case 0x81: case 0x82: case 0x83: 
    case 0x84: case 0x85: case 0x86: case 0x87:
    case 0x88: case 0x89: case 0x8a: case 0x8b:
    case 0x8c: case 0x8d: case 0x8e: case 0x8f:
      
    case 0x90: case 0x91: case 0x92: case 0x93: 
    case 0x94: case 0x95: case 0x96: case 0x97:
    case 0x98: case 0x99: case 0x9a: case 0x9b:
    case 0x9c: case 0x9d: case 0x9e: case 0x9f:
      skipString(ch - 0x80);
      return;
      
    case 0xa0: case 0xa1: case 0xa2: case 0xa3: 
    case 0xa4: case 0xa5: case 0xa6: case 0xa7:
    case 0xa8: case 0xa9: case 0xaa: case 0xab:
    case 0xac: case 0xad: case 0xae: case 0xaf:
      
    case 0xb0: case 0xb1: case 0xb2: case 0xb3: 
    case 0xb4: case 0xb5: case 0xb6: case 0xb7:
    case 0xb8: case 0xb9: case 0xba: case 0xbb:
    case 0xbc: case 0xbd: case 0xbe: case 0xbf:
      skipString((int) readLong(ch - 0xa0, 5));
      return;
      
    case 0xc0: case 0xc1: case 0xc2: case 0xc3: 
    case 0xc4: case 0xc5: case 0xc6: case 0xc7:
      skip(ch - 0xc0);
      return;
      
    case 0xc8: case 0xc9: case 0xca: case 0xcb:
    case 0xcc: case 0xcd: case 0xce: case 0xcf:
      skip((int) readLong(ch - 0xc8, 3));
      return;
      
    case 0xd0:
      readDefinition(in);
      skip(in);
      return;
      
    case 0xd1: case 0xd2: case 0xd3: 
    case 0xd4: case 0xd5: case 0xd6: case 0xd7:
    case 0xd8: case 0xd9: case 0xda: case 0xdb:
    case 0xdc: case 0xdd: case 0xde: case 0xdf:
    {
      int id = ch - 0xd0;
      
      in.serializer(id).skip(this, in);
      return;
    }
      
    case 0xe0: case 0xe1: case 0xe2: case 0xe3: 
    case 0xe4: case 0xe5: case 0xe6: case 0xe7:
    case 0xe8: case 0xe9: case 0xea: case 0xeb:
    case 0xec: case 0xed: case 0xee: case 0xef:
    {
      int id = (int) readLong(ch - 0xe0, 4);
      
      in.serializer(id).skip(this, in);
    }
    
    case 0xf0: case 0xf1: case 0xf2:
      return;
      
    case 0xf3:
      skip(8);
      return;
      
    case 0xf4:
      skip(4);
      return;
      
    default:
      throw error(L.l("Unexpected opcode 0x{0} while skipping",
                      Integer.toHexString(ch)));
    }
  }

  /**
   * Define object
   */
  private void readDefinition(InH3Amp inAmp)
  {
    int id = (int) readUnsigned();

    String name = readString();
    
    int type = (int) readUnsigned();
    
    int fields = (int) readUnsigned();
    
    FieldInfoH3 []fieldInfo = new FieldInfoH3[fields];
    
    for (int i = 0; i < fields; i++) {
      fieldInfo[i] = readFieldInfo();
    }
    
    ClassInfoH3 info = new ClassInfoH3(name, fieldInfo);
    
    inAmp.define(id, info);
  }
  
  private FieldInfoH3 readFieldInfo()
  {
    String name = readString();
    
    int type = (int) readUnsigned();
    
    if (type != 1) {
      throw error(L.l("unknown field type {0} for field {1}", 
                      type, name));
    }
    
    return new FieldInfoH3(name);
  }
  
  private String readString(int strlen)
  {
    readString(_charBuffer, strlen);
    
    return new String(_charBuffer, 0, strlen);
  }
  
  private long readLong(long value, int shift)
  {
    while (true) {
      int ch = read();

      if (ch < 0) {
        throw error(L.l("unexpected end of file while reading long"));
      }
      else if (ch < 0x80) {
        value += ((long) ch << shift);
        
        return value;
      }
      else {
        value += ((ch & 0x7fL) << shift);
        shift += 7;
      }
    }
  }
  
  private void readString(char []cBuf, int strlen)
  {
    byte []bBuf = _buffer;
    
    int i = 0;
    while (i < strlen) {
      int offset = _offset;
      int length = _length;
      
      while (i < strlen && offset + 2 < length) {
        int ch = bBuf[offset++] & 0xff;
        
        if (ch < 0x80) {
          cBuf[i++] = (char) ch;
        }
        else if ((ch & 0xe0) == 0xc0) {
          int ch2 = bBuf[offset++] & 0xff;
          
          cBuf[i++] = (char) (((ch & 0x1f) << 6) + (ch2 & 0x3f));
        }
        else if ((ch & 0xf0) == 0xe0) {
          int ch2 = bBuf[offset++] & 0xff;
          int ch3 = bBuf[offset++] & 0xff;
          
          cBuf[i++] = (char) (((ch & 0xf) << 12)
              + ((ch2 & 0x3f) << 6)
              + (ch3 & 0x3f));
        }
        else {
          throw error(L.l("invalid UTF-8 char 0x{0}", Integer.toHexString(ch)));
        }
      }
      
      _offset = offset;
      _length = length;
      
      if (i < strlen) {
        int ch = read();
        
        if ((ch & 0xf80) == 0) {
          cBuf[i++] = (char) ch;
        }
        else if ((ch & 0xfe0) == 0xc0) {
          int ch2 = read();
          
          cBuf[i++] = (char) (((ch & 0x1f) << 6) + (ch2 & 0x3f));
        }
        else if ((ch & 0xff0) == 0xe0) {
          int ch2 = read();
          int ch3 = read();
          
          cBuf[i++] = (char) (((ch & 0xf) << 12)
              + ((ch2 & 0x3f) << 6)
              + (ch3 & 0x3f));
        }
      }
    }
  }
  
  private void skipString(int strlen)
  {
    int i = 0;
    while (i < strlen) {
      byte []bBuf = _buffer;
      int offset = _offset;
      int length = _length;
      
      for (; i < strlen && offset + 2 < length; i++) {
        int ch = bBuf[offset++] & 0xff;
        
        if (ch < 0x80) {
        }
        else if ((ch & 0xe0) == 0xc0) {
          offset++;
        }
        else if ((ch & 0xf0) == 0xe0) {
          offset += 2;
        }
        else {
          throw error(L.l("invalid UTF-8 char 0x{0}", Integer.toHexString(ch)));
        }
      }
      
      _offset = offset;
      _length = length;
      
      if (i < strlen) {
        i++;
        int ch = read();
        
        if ((ch & 0xf80) == 0) {
        }
        else if ((ch & 0xfe0) == 0xc0) {
          read();
        }
        else if ((ch & 0xff0) == 0xe0) {
          read();
          read();
        }
        else {
          throw error(L.l("invalid UTF-8 char 0x{0}", Integer.toHexString(ch)));
        }
      }
    }
  }
  
  private RuntimeException error(String msg)
  {
    return new H3ExceptionIn(msg);
  }

  @Override
  public void close()
  {
    // TODO Auto-generated method stub
    
  }
  
  private void skip(int len)
  {
    while (true) {
      int offset = _offset;
      int length = _length;
    
      int sublen = Math.min(len, length - offset);
    
      _offset = offset + sublen;
      len -= sublen;
      
      if (len <= 0) {
        return;
      }
      else if (! fill()) {
        return;
      }
    }
  }
  
  private void readBinaryData(byte []tBuffer, int tOffset, int tLength)
  {
    while (true) {
      int offset = _offset;
      int length = _length;
    
      int sublen = Math.min(tLength, length - offset);
    
      System.arraycopy(_buffer, offset, tBuffer, tOffset, sublen);
      
      _offset = offset + sublen;
      tLength -= sublen;
      
      if (tLength <= 0) {
        return;
      }
      else if (! fill()) {
        throw error(L.l("Unexpected end of file while reading binary"));
      }
    }
  }
  
  private int read()
  {
    try {
      byte []buffer = _buffer;
      int offset = _offset;
      int length = _length;

      if (offset < length) {
        int value = buffer[offset] & 0xff;
        _offset = offset + 1;
        return value;
      }

      _length = _is.read(buffer, 0, buffer.length);

      if (_length > 0) {
        _offset = 1;
        
        return _buffer[0] & 0xff;
      }
      else {
        return -1;
      }
    } catch (IOException e) {
      throw new H3ExceptionIn(e);
    }
  }
  private boolean fill()
  {
    try {
      _length = _is.read(_buffer, 0, _buffer.length);
      _offset = 0;
    
      return _length > 0;
    } catch (IOException e) {
      throw new H3ExceptionIn(e);
    }
  }
}
