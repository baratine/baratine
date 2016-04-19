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

package com.caucho.v5.http.protocol2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Header compression/decompression common methods and structures.
 */
class HeaderCommon
{
  private static final HashMap<String,TableEntry> _tableKeyStatic;
  private static final HashMap<TableEntry,TableEntry> _tableEntryStatic;
  private static final TableEntry []_staticEntryArray;

  private static final HuffmanCode []_huffmanEncoding;
  private static final char [][]_huffmanDecodeTable;

  private static HuffmanProgram _huffmanDecodeProgram;

  protected Map<String,TableEntry> getTableKeyStatic()
  {
    return _tableKeyStatic;
  }

  protected Map<TableEntry,TableEntry> tableEntryStatic()
  {
    return _tableEntryStatic;
  }

  protected TableEntry []getEntryArrayStatic()
  {
    return _staticEntryArray;
  }

  protected static HuffmanCode []getHuffmanTable()
  {
    return _huffmanEncoding;
  }

  protected int read()
    throws IOException
  {
    return -1;
  }

  protected static int huffmanEncode(byte []buffer, int head, String value,
                                     HuffmanCode []table)
  {
    int strlen = value.length();

    long data = 0;
    int bits = 0;

    int offset = head;

    for (int i = 0; i < strlen; i++) {
      int ch = value.charAt(i);

      HuffmanCode entry = table[ch];

      int length = entry.getLength();
      int code = entry.getCode();

      data = (data << length) | code;
      bits += length;

      while (bits >= 8) {
        buffer[++offset] = (byte) (data >> (bits - 8));
        bits -= 8;
      }
    }

    if (bits > 0) {
      buffer[++offset] = (byte) ((data << (8 - bits))
                                | (0xff >> bits));
    }

    int len = offset - head;

    buffer[head] = (byte) (0x80 + len);

    return offset + 1;
  }

  private static void addTableStatic(ArrayList<TableEntry> list,
                                     String key,
                                     String value)
  {
    int id = list.size();

    TableEntryStatic entry = new TableEntryStatic(id, key, value);

    _tableKeyStatic.put(key, entry);
    _tableEntryStatic.put(entry, entry);

    list.add(entry);
  }

  protected int huffmanDecode(int length, char []chars)
    throws IOException
  {
    long chunk = 0;
    int bits = 0;
    int offset = 0;
    char [][]charTable = _huffmanDecodeTable;
    HuffmanProgram topProgram = _huffmanDecodeProgram;
    int tailBits = 0;

    while (true) {
      while (bits <= 56) {
        if (length > 0) {
          int d = read();
          
          // System.out.println("  HC: 0x" + Integer.toHexString(d));

          chunk = (chunk << 8) | d;
          length--;

          bits += 8;
        }
        else if (bits <= 0) {
          return offset;
        } 
        else {
          //int rest = (64 - bits) & ~0x7;
          int rest = 8;

          //chunk = (chunk << rest) | ((1L << rest) - 1);
          chunk = (chunk << rest) | 0xff;
          bits += rest;
          tailBits += rest;
          
          break;
        }
      }

      int top = (int) ((chunk >> (bits - 8)) & 0xff);
      int index;

      if (true) {
        //System.out.println("  TOP: 0x" + Integer.toHexString(top));
        HuffmanProgram ptr = topProgram.getChild(top);

        int bitOff = 16;

        while (ptr != null && ! ptr.isFinal()) {
          int t1 = (int) ((chunk >> (bits - bitOff)) & 0xff);

          ptr = ptr.getChild(t1);
          bitOff += 8;
        }

        if (ptr == null || bits - tailBits < ptr.getLength()) {
          return offset;
        }

        chars[offset++] = (char) ptr.getChar();
        bits -= ptr.getLength();
        continue;
      }

      switch (top) {
      case 0x00: case 0x01: case 0x02: case 0x03:
      case 0x04: case 0x05: case 0x06: case 0x07:
      case 0x08: case 0x09: case 0x0a: case 0x0b:
      case 0x0c: case 0x0d: case 0x0e: case 0x0f:

      case 0x10: case 0x11: case 0x12: case 0x13:
      case 0x14: case 0x15: case 0x16: case 0x17:
      case 0x18: case 0x19: case 0x1a: case 0x1b:
      case 0x1c: case 0x1d: case 0x1e: case 0x1f:
        // [4] '/', 'e'
        index = (int) (top >> 4);
        bits -= 4;

        chars[offset++] = charTable[4][index];
        break;

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

      case 0x80: case 0x81: case 0x82: case 0x83:
      case 0x84: case 0x85: case 0x86: case 0x87:
      case 0x88: case 0x89: case 0x8a: case 0x8b:
      case 0x8c: case 0x8d: case 0x8e: case 0x8f:
        // [5] '.', '0', '1', '2',
        index = (int) ((top - 0x20) >> 3);
        bits -= 5;

        chars[offset++] = charTable[5][index];
        break;

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

      case 0xc0: case 0xc1: case 0xc2: case 0xc3:
      case 0xc4: case 0xc5: case 0xc6: case 0xc7:
      case 0xc8: case 0xc9: case 0xca: case 0xcb:
      case 0xcc: case 0xcd: case 0xce: case 0xcf:

      case 0xd0: case 0xd1: case 0xd2: case 0xd3:
      case 0xd4: case 0xd5: case 0xd6: case 0xd7:
      case 0xd8: case 0xd9: case 0xda: case 0xdb:
        // [6] '%' ... 'w'
        index = (int) ((top - 0x90) >> 2);
        bits -= 6;

        chars[offset++] = charTable[6][index];
        break;

      case 0xdc: case 0xdd: case 0xde: case 0xdf:

      case 0xe0: case 0xe1: case 0xe2: case 0xe3:
      case 0xe4: case 0xe5: case 0xe6: case 0xe7:
        // [7]
        index = (int) ((top - 0xdc) >> 1);
        bits -= 7;

        chars[offset++] = charTable[7][index];
        break;

      case 0xe8: case 0xe9: case 0xea: case 0xeb:
      case 0xec: case 0xed: case 0xee: case 0xef:

      case 0xf0: case 0xf1: case 0xf2: case 0xf3:
      case 0xf4: case 0xf5:
        // [8]
        index = (int) (top - 0xe8);
        bits -= 8;

        chars[offset++] = charTable[8][index];
        break;

      case 0xf6: case 0xf7: case 0xf8: case 0xf9:
      case 0xfa: case 0xfb: case 0xfc: case 0xfd:
        // [9]

      case 0xfe:
        // [9-10]

      case 0xff:
        // [10 - 27]

      {
        HuffmanProgram ptr = topProgram.getChild(top);

        int bitOff = 16;

        while (ptr != null && ! ptr.isFinal()) {
          int t1 = (int) ((chunk >> (bits - bitOff)) & 0xff);

          ptr = ptr.getChild(t1);
          bitOff += 8;
        }

        if (ptr == null || bits - tailBits < ptr.getLength()) {
          return offset;
        }

        chars[offset++] = (char) ptr.getChar();
        bits -= ptr.getLength();
        break;
      }
      }
    }
  }

  static class TableEntry {
    private long _sequence;
    private String _key;
    private String _value;

    private int _hashCode;
    
    //private long _reference;
    
    // used for static
    private TableEntry _next;

    public TableEntry()
    {
    }

    public TableEntry(TableEntry next)
    {
      update(0, next.key(), next.getValue());
      
      _next = next;
    }

    public TableEntry(long sequence, String key, String value)
    {
      update(sequence, key, value);
    }

    public final long sequence()
    {
      return _sequence;
    }

    public final void sequence(long sequence)
    {
      _sequence = sequence;
    }

    /*
    public final void setReference(long reference)
    {
      _reference = reference;
    }

    public final long reference()
    {
      return _reference;
    }
    */

    public boolean isStatic()
    {
      return false;
    }
    
    public TableEntry getNext()
    {
      return _next;
    }

    public void update(long sequence, String key, String value)
    {
      _sequence = sequence;
      _key = key;
      _value = value;

      _hashCode = key.hashCode() * 65521 + value.hashCode();
    }

    public final String key()
    {
      return _key;
    }

    public final String getValue()
    {
      return _value;
    }

    public int getSize()
    {
      return 32 + key().length() + getValue().length();
    }

    @Override
    public final int hashCode()
    {
      return _hashCode;
    }

    @Override
    public final boolean equals(Object o)
    {
      TableEntry entry = (TableEntry) o;

      return _key.equals(entry._key) && _value.equals(entry._value);
    }

    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
              + "[" + _sequence + "," + _key + "," + _value + "]");
    }
  }

  private static class TableEntryStatic extends TableEntry
  {
    public TableEntryStatic(long sequence, String key, String value)
    {
      super(sequence, key, value);
    }

    @Override
    public boolean isStatic()
    {
      return true;
    }
  }

  abstract private static class HuffmanProgram {
    private final int _ch;
    private final int _code;
    private final int _length;

    HuffmanProgram(int ch, int code, int length)
    {
      _ch = ch;
      _code = code;
      _length = length;
    }

    public boolean isFinal()
    {
      return false;
    }

    final int getChar()
    {
      return _ch;
    }

    final int getCode()
    {
      return _code;
    }

    final int getLength()
    {
      return _length;
    }

    HuffmanProgram getChild(int i)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }

    HuffmanProgram addPartial(int code)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }

    void addFinal(int code, HuffmanCode entry)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }
  }

  private static final class HuffmanPartial extends HuffmanProgram {
    private HuffmanProgram []_children = new HuffmanProgram[256];

    HuffmanPartial()
    {
      super(0, 0, 1);
    }

    @Override
    HuffmanProgram getChild(int i)
    {
      return _children[i];
    }

    @Override
    HuffmanProgram addPartial(int code)
    {
      code = code & 0xff;

      HuffmanProgram child = _children[code];

      if (child == null) {
        child = new HuffmanPartial();
        _children[code] = child;
      }
      else if (child.isFinal()) {
        throw new IllegalStateException("0x" + Integer.toHexString(code) + " " + String.valueOf(child));
      }

      return child;
    }

    @Override
    void addFinal(int code, HuffmanCode entry)
    {
      code = code & 0xff;

      HuffmanProgram child = _children[code];

      if (child == null) {
        _children[code] = entry;
      }
      else {
        throw new IllegalStateException("0x" + Integer.toHexString(code) + " " + String.valueOf(child));
      }
    }
  }

  static final class HuffmanCode extends HuffmanProgram {
    HuffmanCode(int ch, int code, int length)
    {
      super(ch, code, length);
    }

    @Override
    public final boolean isFinal()
    {
      return true;
    }

    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
          + "[0x" + Integer.toHexString(getChar()) + "(" + (int) getChar() + ")"
          + ",0x" + Integer.toHexString(getCode())
          + "," + getLength() + "]");
    }
  }

  static class HuffmanBuilder {
    private ArrayList<ArrayList<Character>> _decoder = new ArrayList<>();
    private HuffmanPartial _top = new HuffmanPartial();
    private HuffmanCode []_encode = new HuffmanCode[256];

    void add(int ch, int code, int length)
    {
      try {
        HuffmanCode entry = new HuffmanCode(ch, code, length);
        _encode[ch] = entry;

        while (_decoder.size() <= length) {
          _decoder.add(new ArrayList<Character>());
        }

        ArrayList<Character> charList = _decoder.get(length);
        charList.add((char) ch);

        HuffmanProgram ptr = _top;
        int codePtr = code;

        while (length > 8) {
          int op = codePtr >> (length - 8);

          ptr = ptr.addPartial(op);
          length -= 8;
        }

        codePtr = (codePtr << (8 - length)) & 0xff;

        int sublen = (1 << (8 - length));

        for (int i = 0; i < sublen; i++) {
          ptr.addFinal(codePtr + i, entry);
        }
      } catch (Exception e) {
        System.out.println("BAD: " + (char) ch + " " + ch + " " + e);
        try { Thread.sleep(5000); } catch (Exception e1) {}
        throw new IllegalStateException("ch=" + ch + " (0x" + Integer.toHexString(ch) + ")"
                                        + ",code=0x" + Integer.toHexString(code)
                                        + ",len=" + length + ": " + e.getMessage(),
                                        e);
      }
    }

    public HuffmanCode[] getEncoder()
    {
      return _encode;
    }

    public HuffmanProgram getDecodeProgram()
    {
      return _top;
    }

    public char[][] getDecodeTable()
    {
      char [][] table = new char[_decoder.size()][];

      for (int i = 0; i < _decoder.size(); i++) {
        ArrayList<Character> charList = _decoder.get(i);

        table[i] = new char[charList.size()];

        for (int j = 0; j < charList.size(); j++) {
          table[i][j] = charList.get(j);
        }
      }

      return table;
    }
  }

  static {
    _tableKeyStatic = new HashMap<>();
    _tableEntryStatic = new HashMap<>();

    ArrayList<TableEntry> list = new ArrayList<>();

    // 00
    list.add(null);
    addTableStatic(list, ":authority", "");
    addTableStatic(list, ":method", "GET");
    addTableStatic(list, ":method", "POST");
    addTableStatic(list, ":path", "/");
    
    addTableStatic(list, ":path", "/index.html");
    addTableStatic(list, ":scheme", "http");
    addTableStatic(list, ":scheme", "https");
    addTableStatic(list, ":status", "200");
    addTableStatic(list, ":status", "204");
    
    // 10
    addTableStatic(list, ":status", "206");
    addTableStatic(list, ":status", "304");
    addTableStatic(list, ":status", "400");
    addTableStatic(list, ":status", "404");
    addTableStatic(list, ":status", "500");
    
    addTableStatic(list, "accept-charset", "");
    addTableStatic(list, "accept-encoding", "gzip, deflate");
    addTableStatic(list, "accept-language", "");
    addTableStatic(list, "accept-ranges", "");
    addTableStatic(list, "accept", "");
    
    // 20
    addTableStatic(list, "access-control-allow-origin", "");
    addTableStatic(list, "age", "");
    addTableStatic(list, "allow", "");
    addTableStatic(list, "authorization", "");
    addTableStatic(list, "cache-control", "");
    
    addTableStatic(list, "content-disposition", "");
    addTableStatic(list, "content-encoding", "");
    addTableStatic(list, "content-language", "");
    addTableStatic(list, "content-length", "");
    addTableStatic(list, "content-location", "");
    
    // 30
    addTableStatic(list, "content-range", "");
    addTableStatic(list, "content-type", "");
    addTableStatic(list, "cookie", "");
    addTableStatic(list, "date", "");
    addTableStatic(list, "etag", "");

    addTableStatic(list, "expect", "");
    addTableStatic(list, "expires", "");
    addTableStatic(list, "from", "");
    addTableStatic(list, "host", "");
    addTableStatic(list, "if-match", "");

    // 40
    addTableStatic(list, "if-modified-since", "");
    addTableStatic(list, "if-none-match", "");
    addTableStatic(list, "if-range", "");
    addTableStatic(list, "if-unmodified-since", "");
    addTableStatic(list, "last-modified", "");
    
    addTableStatic(list, "link", "");
    addTableStatic(list, "location", "");
    addTableStatic(list, "max-forwards", "");
    addTableStatic(list, "proxy-authenticate", "");
    addTableStatic(list, "proxy-authorization", "");

    // 50
    addTableStatic(list, "range", "");
    addTableStatic(list, "referer", "");
    addTableStatic(list, "refresh", "");
    addTableStatic(list, "retry-after", "");
    addTableStatic(list, "server", "");
    
    addTableStatic(list, "set-cookie", "");
    addTableStatic(list, "strict-transport-security", "");
    addTableStatic(list, "transfer-encoding", "");
    addTableStatic(list, "user-agent", "");
    addTableStatic(list, "vary", "");
    
    // 60
    addTableStatic(list, "via", "");
    addTableStatic(list, "www-authenticate", "");

    _staticEntryArray = new TableEntry[list.size()];
    list.toArray(_staticEntryArray);
    
    if (_staticEntryArray.length != 62) {
      System.out.println("INVALID STATIC:" + _staticEntryArray);
    }

    HuffmanBuilder builder = new HuffmanBuilder();

    for (int i = 0; i <= 31; i++) {
      builder.add(i, 0x3ffffba + i, 26);
    }

    builder.add(0x20, 0x0014, 6);  // ' '
    builder.add(0x21, 0x03f8, 10); // '!'
    builder.add(0x22, 0x03f9, 10); // '"'
    builder.add(0x23, 0x0ffa, 12); // '#'
    builder.add(0x24, 0x1ff9, 13); // '$'
    builder.add(0x25, 0x0015, 6); // '%'
    builder.add(0x26, 0x00f8, 8); // '&'
    builder.add(0x27, 0x07fa, 11); // '''
    builder.add(0x28, 0x03fa, 10); // '('
    builder.add(0x29, 0x03fb, 10); // ')'
    builder.add(0x2a, 0x00f9, 8);  // '*'
    builder.add(0x2b, 0x07fb, 11); // '+'
    builder.add(0x2c, 0x00fa, 8); // ','
    builder.add(0x2d, 0x0016, 6); // '-'
    builder.add(0x2e, 0x0017, 6); // '.'
    builder.add(0x2f, 0x0018, 6); // '/'

    builder.add(0x30, 0x0000, 5); // '0'
    builder.add(0x31, 0x0001, 5); // '1'
    builder.add(0x32, 0x0002, 5); // '2'
    builder.add(0x33, 0x0019, 6); // '3'
    builder.add(0x34, 0x001a, 6); // '4'
    builder.add(0x35, 0x001b, 6); // '5'
    builder.add(0x36, 0x001c, 6); // '6'
    builder.add(0x37, 0x001d, 6); // '7'
    builder.add(0x38, 0x001e, 6); // '8'
    builder.add(0x39, 0x001f, 6); // '9'
    builder.add(0x3a, 0x005c, 7); // ':'
    builder.add(0x3b, 0x00fb, 8); // ';'
    builder.add(0x3c, 0x7ffc, 15); // '<'
    builder.add(0x3d, 0x0020, 6); // '='
    builder.add(0x3e, 0x0ffb, 12); // '>'
    builder.add(0x3f, 0x03fc, 10); // '?'

    builder.add(0x40, 0x1ffa, 13); // '@'
    builder.add(0x41, 0x0021, 6); // 'A'
    builder.add(0x42, 0x005d, 7); // 'B'
    builder.add(0x43, 0x005e, 7); // 'C'
    builder.add(0x44, 0x005f, 7); // 'D'
    builder.add(0x45, 0x0060, 7); // 'E'
    builder.add(0x46, 0x0061, 7); // 'F'
    builder.add(0x47, 0x0062, 7); // 'G'
    builder.add(0x48, 0x0063, 7); // 'H'
    builder.add(0x49, 0x0064, 7); // 'I'
    builder.add(0x4a, 0x0065, 7); // 'J'
    builder.add(0x4b, 0x0066, 7); // 'K'
    builder.add(0x4c, 0x0067, 7); // 'L'
    builder.add(0x4d, 0x0068, 7); // 'M'
    builder.add(0x4e, 0x0069, 7); // 'N'
    builder.add(0x4f, 0x006a, 7); // 'O'

    builder.add(0x50, 0x006b, 7); // 'P'
    builder.add(0x51, 0x006c, 7); // 'Q'
    builder.add(0x52, 0x006d, 7); // 'R'
    builder.add(0x53, 0x006e, 7); // 'S'
    builder.add(0x54, 0x006f, 7); // 'T'
    builder.add(0x55, 0x0070, 7); // 'U'
    builder.add(0x56, 0x0071, 7); // 'V'
    builder.add(0x57, 0x0072, 7); // 'W'
    builder.add(0x58, 0x00fc, 8); // 'X'
    builder.add(0x59, 0x0073, 7); // 'Y'
    builder.add(0x5a, 0x00fd, 8); // 'Z'
    builder.add(0x5b, 0x1ffb, 13); // '['
    builder.add(0x5c, 0x7fff0, 19); // '\'
    builder.add(0x5d, 0x1ffc, 13); // ']'
    builder.add(0x5e, 0x3ffc, 14); // '^'
    builder.add(0x5f, 0x0022, 6); // '_'

    builder.add(0x60, 0x7ffd, 15); // '`'
    builder.add(0x61, 0x0003, 5); // 'a'
    builder.add(0x62, 0x0023, 6); // 'b'
    builder.add(0x63, 0x0004, 5); // 'c'
    builder.add(0x64, 0x0024, 6); // 'd'
    builder.add(0x65, 0x0005, 5); // 'e'
    builder.add(0x66, 0x0025, 6); // 'f'
    builder.add(0x67, 0x0026, 6); // 'g'
    builder.add(0x68, 0x0027, 6); // 'h'
    builder.add(0x69, 0x0006, 5); // 'i'
    builder.add(0x6a, 0x0074, 7); // 'j'
    builder.add(0x6b, 0x0075, 7); // 'k'
    builder.add(0x6c, 0x0028, 6); // 'l'
    builder.add(0x6d, 0x0029, 6); // 'm'
    builder.add(0x6e, 0x002a, 6); // 'n'
    builder.add(0x6f, 0x0007, 5); // 'o'

    builder.add(0x70, 0x002b, 6); // 'p'
    builder.add(0x71, 0x0076, 7); // 'q'
    builder.add(0x72, 0x002c, 6); // 'r'
    builder.add(0x73, 0x0008, 5); // 's'
    builder.add(0x74, 0x0009, 5); // 't'
    builder.add(0x75, 0x002d, 6); // 'u'
    builder.add(0x76, 0x0077, 7); // 'v'
    builder.add(0x77, 0x0078, 7); // 'w'
    builder.add(0x78, 0x0079, 7); // 'x'
    builder.add(0x79, 0x007a, 7); // 'y'
    builder.add(0x7a, 0x007b, 7); // 'z'
    builder.add(0x7b, 0x7ffe, 15); // '{'
    builder.add(0x7c, 0x07fc, 11); // '|'
    builder.add(0x7d, 0x3ffd, 14); // '}'
    builder.add(0x7e, 0x1ffd, 13); // '~'

    for (int i = 127; i <= 163; i++) {
      builder.add(i, 0x3ffffdb + i - 127, 26);
    }

    for (int i = 164; i < 256; i++) {
      builder.add(i, 0x1ffff80 + i - 164, 25);
    }

    _huffmanEncoding = builder.getEncoder();
    _huffmanDecodeTable = builder.getDecodeTable();
    _huffmanDecodeProgram = builder.getDecodeProgram();
  }
}
