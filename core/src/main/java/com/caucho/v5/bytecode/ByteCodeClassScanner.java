/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.bytecode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.util.L10N;
import com.caucho.v5.util.ModulePrivate;

/**
 * Scans for matching classes.
 */
@ModulePrivate
public class ByteCodeClassScanner {
  private static final Logger log
    = Logger.getLogger(ByteCodeClassScanner.class.getName());
  private static final L10N L = new L10N(ByteCodeClassScanner.class);
  
  private static final char []RUNTIME_VISIBLE_ANNOTATIONS
    = "RuntimeVisibleAnnotations".toCharArray();
  
  private static final boolean []IS_JAVA_IDENTIFIER;

  private String _className;

  private InputStream _is;

  private ByteCodeClassMatcher _matcher;
  
  private char []_charBuffer;
  private int _charBufferOffset;
  
  private int _cpCount;
  private int []_cpData;
  private int []_cpLengths;
  private int []_classData;
  
  public ByteCodeClassScanner()
  {
    _charBuffer = new char[16384];
  }

  public void init(String className,
                   InputStream is,
                   ByteCodeClassMatcher matcher)
  {
    _className = className;

    _is = is;

    _matcher = matcher;
    
    _charBufferOffset = 0;
  }

  public boolean scan()
  {
    try {
      InputStream is = _is;
      
      int magic = readInt(is);

      if (magic != JavaClass.MAGIC) {
        throw error(L.l("bad magic number in class file"));
      }

      is.skip(2); // major
      is.skip(2); // minor

      parseConstantPool(is);

      int modifiers = readShort(is);
      int thisClassIndex = readShort(is);
      
      int cpIndex = _classData[thisClassIndex];
      
      if (cpIndex > 0) {
        String className = new String(_charBuffer, 
                                      _cpData[cpIndex], 
                                      _cpLengths[cpIndex]);
        
        if (! _matcher.scanClass(className, modifiers)) {
          return false;
        }
      }
      
      int superClassIndex = readShort(is);

      if (superClassIndex > 0) {
        cpIndex = _classData[superClassIndex];
        
        if (cpIndex > 0) {
          _matcher.addSuperClass(_charBuffer, 
                                 _cpData[cpIndex], 
                                 _cpLengths[cpIndex]);
        }
      }

      int interfaceCount = readShort(is);
      for (int i = 0; i < interfaceCount; i++) {
        int classIndex = readShort(is);
        
        cpIndex = _classData[classIndex];
        if (cpIndex > 0) {
          _matcher.addInterface(_charBuffer, 
                                _cpData[cpIndex], 
                                _cpLengths[cpIndex]);
        }
      }

      int fieldCount = readShort(is);
      for (int i = 0; i < fieldCount; i++) {
        scanField(is);
      }

      int methodCount = readShort(is);
      for (int i = 0; i < methodCount; i++) {
        scanMethod(is);
      }

      int attrCount = readShort(is);

      for (int i = 0; i < attrCount; i++) {
        scanClassAttribute(is);
      }
      
      char []charBuffer = _charBuffer;
      
      for (int i = 0; i < _cpCount; i++) {
        int cpLength = _cpLengths[i];
        
        if (cpLength > 0) {
          int cpOffset = _cpData[i];
          
          if (charBuffer[cpOffset] == 'L'
              && charBuffer[cpOffset + cpLength - 1] == ';') {
            _matcher.addPoolString(charBuffer, 
                                   cpOffset + 1,
                                   cpLength - 2);
          }
        }
      }
      
      return _matcher.finishScan();
    } catch (Exception e) {
      log.log(Level.WARNING,
              "failed scanning class " + _className + "\n" + e.toString(),
              e);

      return false;
    }
  }

  /**
   * Parses the constant pool.
   */
  public boolean parseConstantPool(InputStream is)
    throws IOException
  {
    int count = readShort(is);
    
    _cpCount = count;
    
    if (_cpData == null || _cpData.length <= count) {
      _cpData = new int[count];
      _cpLengths = new int[count];
      _classData = new int[count];
    }
    
    Arrays.fill(_cpData, 0);
    Arrays.fill(_cpLengths, 0);
    Arrays.fill(_classData, 0);

    int i = 1;
    while (i < count) {
      int index = i;
      int code = is.read();

      if (code == ByteCodeParser.CP_LONG || code == ByteCodeParser.CP_DOUBLE)
        i += 2;
      else
        i += 1;
      
      switch (code) {
      case ByteCodeParser.CP_CLASS:
        int utf8Index = readShort(is);
        
        // index of the UTF-8 string
        _classData[index] = utf8Index;
        break;
      
      case ByteCodeParser.CP_FIELD_REF:
        // int classIndex = readShort();
        // int nameAndTypeIndex = readShort();

        is.skip(4);
        break;
      
      case ByteCodeParser.CP_METHOD_REF:
        // int classIndex = readShort();
        // int nameAndTypeIndex = readShort();

        is.skip(4);
        break;
      
      case ByteCodeParser.CP_INTERFACE_METHOD_REF:
        // int classIndex = readShort();
        // int nameAndTypeIndex = readShort();

        is.skip(4);
        break;

      case ByteCodeParser.CP_STRING:
        // int stringIndex = readShort();

        is.skip(2);
        break;
      
      case ByteCodeParser.CP_INTEGER:
        is.skip(4);
        break;
      
      case ByteCodeParser.CP_FLOAT:
        is.skip(4);
        break;
      
      case ByteCodeParser.CP_LONG:
        is.skip(8);
        break;
      
      case ByteCodeParser.CP_DOUBLE:
        is.skip(8);
        break;
      
      case ByteCodeParser.CP_NAME_AND_TYPE:
        // int nameIndex = readShort();
        // int descriptorIndex = readShort();

        is.skip(4);
        break;
      
      case ByteCodeParser.CP_UTF8:
        {
          int length = readShort(is);

          _cpData[index] = _charBufferOffset;
          _cpLengths[index] = parseUtf8(is, length);

          break;
        }
        
      case ByteCodeParser.CP_METHOD_HANDLE:
        is.skip(3);
        break;
        
      case ByteCodeParser.CP_METHOD_TYPE:
        is.skip(2);
        break;
        
      case ByteCodeParser.CP_INVOKE_DYNAMIC:
        is.skip(4);
        break;
        

      default:
        throw error(L.l("'{0}' is an unknown constant pool type.", code));
      }
    }

    return false;
  }
  
  /**
   * Parses the UTF.
   */
  private int parseUtf8(InputStream is, int length)
    throws IOException
  {
    if (length <= 0)
      return 0;
    
    if (length > 256) {
      is.skip(length);
      return 0;
    }
    
    int offset = _charBufferOffset;
    
    if (_charBuffer.length <= offset + length) {
      char []buffer = new char[2 * _charBuffer.length];
      System.arraycopy(_charBuffer, 0, buffer, 0, _charBuffer.length);
      _charBuffer = buffer;
    }
    
    char []buffer = _charBuffer;
    boolean []isJavaIdentifier = IS_JAVA_IDENTIFIER;
    boolean isIdentifier = true;
    
    while (length > 0) {
      int d1 = is.read();
      
      char ch;

      if (d1 == '/') {
        ch = '.';
        
        length--;
      }
      else if (d1 < 0x80) {
        ch = (char) d1;
        
        length--;
      }
      else if (d1 < 0xe0) {
        int d2 = is.read() & 0x3f;

        ch = (char) (((d1 & 0x1f) << 6) + (d2));
        
        length -= 2;
      }
      else if (d1 < 0xf0) {
        int d2 = is.read() & 0x3f;
        int d3 = is.read() & 0x3f;

        ch = (char) (((d1 & 0xf) << 12) + (d2 << 6) + d3);
        
        length -= 3;
      }
      else
        throw new IllegalStateException();
      
      if (isIdentifier && isJavaIdentifier[ch]) {
        buffer[offset++] = ch;
      }
      else {
        isIdentifier = false;
      }
    }
    
    if (! isIdentifier)
      return 0;

    int charLength = offset - _charBufferOffset;
    
    _charBufferOffset = offset;
    
    return charLength;
  }

  /**
   * Parses a field entry.
   */
  private void scanField(InputStream is)
    throws IOException
  {
    // int accessFlags = readShort();
    // int nameIndex = readShort();
    // int descriptorIndex = readShort();
    
    is.skip(6);

    int attributesCount = readShort(is);

    for (int i = 0; i < attributesCount; i++) {
      scanAttributeForAnnotation(is);
    }
  }

  /**
   * Parses a method entry.
   */
  private void scanMethod(InputStream is)
    throws IOException
  {
    /*
    int accessFlags = readShort();
    int nameIndex = readShort();
    int descriptorIndex = readShort();
    */
    
    is.skip(6);

    int attributesCount = readShort(is);
    
    for (int i = 0; i < attributesCount; i++) {
      scanAttributeForAnnotation(is);
    }
  }

  /**
   * Parses an attribute.
   */
  private void scanClassAttribute(InputStream is)
    throws IOException
  {
    int nameIndex = readShort(is);

    // String name = _cp.getUtf8(nameIndex).getValue();
      
    int length = readInt(is);

    if (! isNameAnnotation(nameIndex)) {
      is.skip(length);
      return;
    }
      
    int count = readShort(is);
      
    for (int i = 0; i < count; i++) {
      int annTypeIndex = scanAnnotation(is);
      
      if (annTypeIndex > 0) {
        _matcher.addClassAnnotation(_charBuffer, 
                                    _cpData[annTypeIndex] + 1, 
                                    _cpLengths[annTypeIndex] - 2);
      }
    }
  }

  /**
   * Parses an attribute for an annotation.
   */
  private void scanAttributeForAnnotation(InputStream is)
    throws IOException
  {
    int nameIndex = readShort(is);

    // String name = _cp.getUtf8(nameIndex).getValue();
      
    int length = readInt(is);

    if (! isNameAnnotation(nameIndex)) {
      is.skip(length);
      return;
    }
      
    int count = readShort(is);
      
    for (int i = 0; i < count; i++) {
      int annTypeIndex = scanAnnotation(is);
      
      if (annTypeIndex > 0 && _cpLengths[annTypeIndex] > 2) {
        _matcher.addClassAnnotation(_charBuffer, 
                                    _cpData[annTypeIndex] + 1, 
                                    _cpLengths[annTypeIndex] - 2);
      }
    }
  }
  
  private int scanAnnotation(InputStream is)
    throws IOException
  {
    int typeIndex = readShort(is);
    
    int valueCount = readShort(is);
    for (int j = 0; j < valueCount; j++) {
      is.skip(2); // int eltIndex = readShort(is);
        
      skipElementValue(is);
    }
    
    return typeIndex;
  }
  
  private void skipElementValue(InputStream is)
    throws IOException
  {
    int code = is.read();

    switch (code) {
    case 'B': case 'C': case 'D': case 'F': case 'I': case 'J':
    case 'S': case 'Z': case 's':
      is.skip(2);
      return;
    case 'e':
      is.skip(4);
      return;
    case 'c':
      is.skip(2);
      return;
    case '@':
      scanAnnotation(is);
      return;
    case '[':
      int len = readShort(is);
      for (int i = 0; i < len; i++) {
        skipElementValue(is);
      }
      return;
    default:
      throw new IllegalStateException("unknown code: " + (char) code);
    }
  }
  
  private boolean isNameAnnotation(int nameIndex)
  {
    if (nameIndex <= 0)
      return false;
    
    int offset = _cpData[nameIndex];
    int length = _cpLengths[nameIndex];
    
    char []charBuffer = _charBuffer;
    
    if (length != RUNTIME_VISIBLE_ANNOTATIONS.length)
      return false;
    
    for (int i = 0; i < length; i++) {
      if (charBuffer[i + offset] != RUNTIME_VISIBLE_ANNOTATIONS[i])
        return false;
    }
    
    return true;
  }

  /**
   * Parses a 32-bit int.
   */
  private int readInt(InputStream is)
    throws IOException
  {
    return ((is.read() << 24)
            | (is.read() << 16)
            | (is.read() << 8)
            | (is.read()));
  }

  /**
   * Parses a 16-bit int.
   */
  private int readShort(InputStream is)
    throws IOException
  {
    int c1 = is.read();
    int c2 = is.read();

    return ((c1 << 8) | c2);
  }

  /**
   * Returns an error message.
   */
  private IllegalStateException error(String message)
  {
    return new IllegalStateException(_className + ": " + message);
  }
  
  static {
    IS_JAVA_IDENTIFIER = new boolean[65536];
    
    for (int i = 0; i < 65536; i++) {
      if (Character.isJavaIdentifierPart(i) || i == '.' || i == ';' || i == '-')
        IS_JAVA_IDENTIFIER[i] = true;
    }
  }
}
