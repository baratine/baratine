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
import java.util.ArrayList;

import com.caucho.v5.bytecode.attr.Attribute;
import com.caucho.v5.bytecode.attr.BootstrapMethodAttribute;
import com.caucho.v5.bytecode.attr.CodeAttribute;
import com.caucho.v5.bytecode.attr.ExceptionsAttribute;
import com.caucho.v5.bytecode.attr.OpaqueAttribute;
import com.caucho.v5.bytecode.attr.SignatureAttribute;
import com.caucho.v5.bytecode.cpool.ClassConstant;
import com.caucho.v5.bytecode.cpool.ConstantPool;
import com.caucho.v5.bytecode.cpool.ConstantPoolEntry;
import com.caucho.v5.bytecode.cpool.DoubleConstant;
import com.caucho.v5.bytecode.cpool.FieldRefConstant;
import com.caucho.v5.bytecode.cpool.FloatConstant;
import com.caucho.v5.bytecode.cpool.IntegerConstant;
import com.caucho.v5.bytecode.cpool.InterfaceMethodRefConstant;
import com.caucho.v5.bytecode.cpool.LongConstant;
import com.caucho.v5.bytecode.cpool.MethodRefConstant;
import com.caucho.v5.bytecode.cpool.NameAndTypeConstant;
import com.caucho.v5.bytecode.cpool.StringConstant;
import com.caucho.v5.bytecode.cpool.Utf8Constant;
import com.caucho.v5.util.L10N;

/**
 * Interface to the bytecode parser.
 */
public class ByteCodeParser {
  private static final L10N L = new L10N(ByteCode.class);

  static final int CP_CLASS = 7;
  static final int CP_FIELD_REF = 9;
  static final int CP_METHOD_REF = 10;
  static final int CP_INTERFACE_METHOD_REF = 11;
  static final int CP_STRING = 8;
  static final int CP_INTEGER = 3;
  static final int CP_FLOAT = 4;
  static final int CP_LONG = 5;
  static final int CP_DOUBLE = 6;
  static final int CP_NAME_AND_TYPE = 12;
  static final int CP_UTF8 = 1;
  
  static final int CP_METHOD_HANDLE = 15;
  static final int CP_METHOD_TYPE = 16;
  static final int CP_INVOKE_DYNAMIC = 18;

  private JavaClassLoader _loader;
  private InputStream _is;
  private JavaClass _class;
  private ConstantPool _cp;

  /**
   * Sets the JClassLoader
   */
  public void setClassLoader(JavaClassLoader loader)
  {
    _loader = loader;
  }

  /**
   * Sets the class.
   */
  public void setJavaClass(JavaClass javaClass)
  {
    _class = javaClass;
  }
  
  /**
   * Parses the .class file.
   */
  public JavaClass parse(InputStream is)
    throws IOException
  {
    _is = is;

    if (_loader == null)
      _loader = new JavaClassLoader();
    
    if (_class == null)
      _class = new JavaClass(_loader);

    _cp = _class.getConstantPool();
    
    parseClass();

    return _class;
  }

  /**
   * Returns the constant pool.
   */
  public ConstantPool getConstantPool()
  {
    return _cp;
  }

  /**
   * Returns a UTF8 String from the constant pool.
   */
  public String getUTF8(int index)
  {
    return getConstantPool().getUtf8AsString(index);
  }

  /**
   * Parses the ClassFile construct
   */
  private void parseClass()
    throws IOException
  {
    int magic = readInt();

    if (magic != JavaClass.MAGIC)
      throw error(L.l("bad magic number in class file"));

    int minor = readShort();
    int major = readShort();

    _class.setMajor(major);
    _class.setMinor(minor);

    parseConstantPool();

    int accessFlags = readShort();
    _class.setAccessFlags(accessFlags);

    int thisClassIndex = readShort();
    _class.setThisClass(_cp.getClass(thisClassIndex).getName());
    
    int superClassIndex = readShort();
    if (superClassIndex > 0)
      _class.setSuperClass(_cp.getClass(superClassIndex).getName());

    int interfaceCount = readShort();
    for (int i = 0; i < interfaceCount; i++) {
      int classIndex = readShort();

      _class.addInterface(_cp.getClass(classIndex).getName());
    }

    int fieldCount = readShort();
    for (int i = 0; i < fieldCount; i++) {
      parseField();
    }

    int methodCount = readShort();
    for (int i = 0; i < methodCount; i++)
      parseMethod();

    int attrCount = readShort();
    for (int i = 0; i < attrCount; i++) {
      Attribute attr = parseAttribute();

      _class.addAttribute(attr);
    }
  }

  /**
   * Parses the constant pool.
   */
  public void parseConstantPool()
    throws IOException
  {
    int count = readShort();

    for (int i = 1; i < count; i++) {
      ConstantPoolEntry entry = parseConstantPoolEntry(i);

      _cp.addConstant(entry);
      
      if (entry instanceof DoubleConstant ||
          entry instanceof LongConstant) {
        i++;
        _cp.addConstant(null);
      }
    }
  }

  /**
   * Parses a constant pool entry.
   */
  private ConstantPoolEntry parseConstantPoolEntry(int index)
    throws IOException
  {
    int tag = read();

    switch (tag) {
    case CP_CLASS:
      return parseClassConstant(index);
      
    case CP_FIELD_REF:
      return parseFieldRefConstant(index);
      
    case CP_METHOD_REF:
      return parseMethodRefConstant(index);
      
    case CP_INTERFACE_METHOD_REF:
      return parseInterfaceMethodRefConstant(index);
      
    case CP_STRING:
      return parseStringConstant(index);
      
    case CP_INTEGER:
      return parseIntegerConstant(index);
      
    case CP_FLOAT:
      return parseFloatConstant(index);
      
    case CP_LONG:
      return parseLongConstant(index);
      
    case CP_DOUBLE:
      return parseDoubleConstant(index);
      
    case CP_NAME_AND_TYPE:
      return parseNameAndTypeConstant(index);
      
    case CP_UTF8:
      return parseUtf8Constant(index);

    default:
      throw error(L.l("'{0}' is an unknown constant pool type.",
                      tag));
    }
  }

  /**
   * Parses a class constant pool entry.
   */
  private ClassConstant parseClassConstant(int index)
    throws IOException
  {
    int nameIndex = readShort();

    return new ClassConstant(_class.getConstantPool(), index, nameIndex);
  }

  /**
   * Parses a field ref constant pool entry.
   */
  private FieldRefConstant parseFieldRefConstant(int index)
    throws IOException
  {
    int classIndex = readShort();
    int nameAndTypeIndex = readShort();

    return new FieldRefConstant(_class.getConstantPool(), index,
                                classIndex, nameAndTypeIndex);
  }

  /**
   * Parses a method ref constant pool entry.
   */
  private MethodRefConstant parseMethodRefConstant(int index)
    throws IOException
  {
    int classIndex = readShort();
    int nameAndTypeIndex = readShort();

    return new MethodRefConstant(_class.getConstantPool(), index,
                                 classIndex, nameAndTypeIndex);
  }

  /**
   * Parses an interface method ref constant pool entry.
   */
  private InterfaceMethodRefConstant parseInterfaceMethodRefConstant(int index)
    throws IOException
  {
    int classIndex = readShort();
    int nameAndTypeIndex = readShort();

    return new InterfaceMethodRefConstant(_class.getConstantPool(), index,
                                          classIndex, nameAndTypeIndex);
  }

  /**
   * Parses a string constant pool entry.
   */
  private StringConstant parseStringConstant(int index)
    throws IOException
  {
    int stringIndex = readShort();

    return new StringConstant(_class.getConstantPool(), index, stringIndex);
  }

  /**
   * Parses an integer constant pool entry.
   */
  private IntegerConstant parseIntegerConstant(int index)
    throws IOException
  {
    int value = readInt();

    return new IntegerConstant(_class.getConstantPool(), index, value);
  }

  /**
   * Parses a float constant pool entry.
   */
  private FloatConstant parseFloatConstant(int index)
    throws IOException
  {
    int bits = readInt();

    float value = Float.intBitsToFloat(bits);

    return new FloatConstant(_class.getConstantPool(), index, value);
  }

  /**
   * Parses a long constant pool entry.
   */
  private LongConstant parseLongConstant(int index)
    throws IOException
  {
    long value = readLong();

    return new LongConstant(_class.getConstantPool(), index, value);
  }

  /**
   * Parses a double constant pool entry.
   */
  private DoubleConstant parseDoubleConstant(int index)
    throws IOException
  {
    long bits = readLong();

    double value = Double.longBitsToDouble(bits);

    return new DoubleConstant(_class.getConstantPool(), index, value);
  }

  /**
   * Parses a name and type pool entry.
   */
  private NameAndTypeConstant parseNameAndTypeConstant(int index)
    throws IOException
  {
    int nameIndex = readShort();
    int descriptorIndex = readShort();

    return new NameAndTypeConstant(_class.getConstantPool(), index,
                                   nameIndex, descriptorIndex);
  }

  /**
   * Parses a utf-8 constant pool entry.
   */
  private Utf8Constant parseUtf8Constant(int index)
    throws IOException
  {
    int length = readShort();
    
    StringBuilder cb = new StringBuilder();

    for (int i = 0; i < length; i++) {
      int ch = read();

      if (ch < 0x80) {
        cb.append((char) ch);
      }
      else if ((ch & 0xe0) == 0xc0) {
        int ch2 = read();
        i++;

        cb.append((char) (((ch & 0x1f) << 6)+
                          (ch2 & 0x3f)));
      }
      else {
        int ch2 = read();
        int ch3 = read();
        i += 2;
      
        cb.append((char) (((ch & 0xf) << 12)+
                          ((ch2 & 0x3f) << 6) +
                          ((ch3 & 0x3f))));
      }
    }

    return new Utf8Constant(_class.getConstantPool(), index, cb.toString());
  }

  /**
   * Parses a field entry.
   */
  private void parseField()
    throws IOException
  {
    int accessFlags = readShort();
    int nameIndex = readShort();
    int descriptorIndex = readShort();

    JavaField field = new JavaField();
    field.setJavaClass(_class);
    field.setName(_cp.getUtf8(nameIndex).getValue());
    field.setDescriptor(_cp.getUtf8(descriptorIndex).getValue());
    field.setAccessFlags(accessFlags);

    int attributesCount = readShort();

    for (int i = 0; i < attributesCount; i++) {
      Attribute attr = parseAttribute();

      field.addAttribute(attr);
    }

    _class.addField(field);
  }

  /**
   * Parses a method entry.
   */
  private void parseMethod()
    throws IOException
  {
    int accessFlags = readShort();
    int nameIndex = readShort();
    int descriptorIndex = readShort();

    JavaMethod method = new JavaMethod(_loader);
    method.setJavaClass(_class);
    method.setName(_cp.getUtf8(nameIndex).getValue());
    method.setDescriptor(_cp.getUtf8(descriptorIndex).getValue());
    method.setAccessFlags(accessFlags);

    int attributesCount = readShort();

    for (int i = 0; i < attributesCount; i++) {
      Attribute attr = parseAttribute();

      method.addAttribute(attr);
      
      if (attr instanceof ExceptionsAttribute) {
        ExceptionsAttribute exn = (ExceptionsAttribute) attr;

        ArrayList<String> exnNames = exn.getExceptionList();

        if (exnNames.size() > 0) {
          JClass []exnClasses = new JClass[exnNames.size()];

          for (int j = 0; j < exnNames.size(); j++) {
            String exnName = exnNames.get(j).replace('/', '.');

            exnClasses[j] = _loader.forName(exnName);
          }

          method.setExceptionTypes(exnClasses);
        }
      }
    }

    _class.addMethod(method);
  }

  /**
   * Parses an attribute.
   */
  public Attribute parseAttribute()
    throws IOException
  {
    int nameIndex = readShort();

    String name = _cp.getUtf8(nameIndex).getValue();

    if (name.equals("Code")) {
      CodeAttribute code = new CodeAttribute(name);
      code.read(this);
      return code;
    }
    else if (name.equals("Exceptions")) {
      ExceptionsAttribute code = new ExceptionsAttribute(name);
      code.read(this);
      return code;
    }
    else if (name.equals("Signature")) {
      SignatureAttribute attr = new SignatureAttribute();
      attr.read(this);
      return attr;
    }
    else if (name.equals("BootstrapMethods")) {
      BootstrapMethodAttribute attr = new BootstrapMethodAttribute();
      attr.read(this);
      return attr;
    }
    
    OpaqueAttribute attr = new OpaqueAttribute(name);
    
    int length = readInt();

    byte []bytes = new byte[length];
    
    read(bytes, 0, bytes.length);

    attr.setValue(bytes);

    return attr;
  }

  /**
   * Parses a 64-bit int.
   */
  long readLong()
    throws IOException
  {
    return (((long) _is.read() << 56) |
            ((long) _is.read() << 48) |
            ((long) _is.read() << 40) |
            ((long) _is.read() << 32) |
            ((long) _is.read() << 24) |
            ((long) _is.read() << 16) |
            ((long) _is.read() << 8) |
            ((long) _is.read()));
  }

  /**
   * Parses a 32-bit int.
   */
  public int readInt()
    throws IOException
  {
    return ((_is.read() << 24) |
            (_is.read() << 16) |
            (_is.read() << 8) |
            (_is.read()));
  }

  /**
   * Parses a 16-bit int.
   */
  public int readShort()
    throws IOException
  {
    int c1 = _is.read();
    int c2 = _is.read();

    return ((c1 << 8) | c2);
  }

  /**
   * Parses a byte
   */
  int read()
    throws IOException
  {
    return _is.read();
  }

  /**
   * Reads a chunk
   */
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    int readLength = 0;
    
    while (length > 0) {
      int sublen = _is.read(buffer, offset, length);

      if (sublen < 0)
        return readLength == 0 ? -1 : readLength;

      offset += sublen;
      length -= sublen;
      readLength += sublen;
    }

    return readLength;
  }

  /**
   * Returns an error message.
   */
  private IOException error(String message)
  {
    return new IOException(message);
  }
}
