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

package com.caucho.v5.bytecode.cpool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import com.caucho.v5.bytecode.ByteCodeWriter;
import com.caucho.v5.bytecode.attr.BootstrapMethodAttribute;

/**
 * Represents a constant pool entry.
 */
public class ConstantPool {
  public static final int CP_CLASS = 7;
  public static final int CP_FIELD_REF = 9;
  public static final int CP_METHOD_REF = 10;
  public static final int CP_INTERFACE_METHOD_REF = 11;
  public static final int CP_STRING = 8;
  public static final int CP_INTEGER = 3;
  public static final int CP_FLOAT = 4;
  public static final int CP_LONG = 5;
  public static final int CP_DOUBLE = 6;
  public static final int CP_NAME_AND_TYPE = 12;
  public static final int CP_UTF8 = 1;

  public static final int CP_METHODHANDLE = 15;
  public static final int CP_METHODTYPE = 16;
  public static final int CP_INVOKEDYNAMIC = 18;

  private ArrayList<ConstantPoolEntry> _entries;
  private HashMap<String,Utf8Constant> _utf8Map
    = new HashMap<String,Utf8Constant>();

  public ConstantPool()
  {
    _entries = new ArrayList<ConstantPoolEntry>();
    _entries.add(null);
  }

  /**
   * Returns an entry
   */
  public ConstantPoolEntry getEntry(int index)
  {
    return _entries.get(index);
  }

  /**
   * Returns all the entries.
   */
  public ArrayList<ConstantPoolEntry> getEntries()
  {
    return _entries;
  }

  /**
   * Returns a class constant
   */
  public ClassConstant getClass(int index)
  {
    return (ClassConstant) _entries.get(index);
  }

  /**
   * Returns a field ref entry
   */
  public FieldRefConstant getFieldRef(int index)
  {
    return (FieldRefConstant) _entries.get(index);
  }

  /**
   * Returns a method ref entry
   */
  public MethodRefConstant getMethodRef(int index)
  {
    return (MethodRefConstant) _entries.get(index);
  }

  /**
   * Returns an interface method ref entry
   */
  public InterfaceMethodRefConstant getInterfaceMethodRef(int index)
  {
    return (InterfaceMethodRefConstant) _entries.get(index);
  }

  /**
   * Returns a string constant
   */
  public StringConstant getString(int index)
  {
    return (StringConstant) _entries.get(index);
  }

  /**
   * Returns an integer constant
   */
  public IntegerConstant getInteger(int index)
  {
    return (IntegerConstant) _entries.get(index);
  }

  /**
   * Returns a long constant
   */
  public LongConstant getLong(int index)
  {
    return (LongConstant) _entries.get(index);
  }

  /**
   * Returns a float constant
   */
  public FloatConstant getFloat(int index)
  {
    return (FloatConstant) _entries.get(index);
  }

  /**
   * Returns a double constant
   */
  public DoubleConstant getDouble(int index)
  {
    return (DoubleConstant) _entries.get(index);
  }

  /**
   * Returns a name-and-type constant
   */
  public NameAndTypeConstant getNameAndType(int index)
  {
    return (NameAndTypeConstant) _entries.get(index);
  }

  /**
   * Returns a utf-8 constant
   */
  public Utf8Constant getUtf8(int index)
  {
    return (Utf8Constant) _entries.get(index);
  }

  /**
   * Returns a utf-8 constant as a string
   */
  public String getUtf8AsString(int index)
  {
    Utf8Constant utf8 = (Utf8Constant) _entries.get(index);

    if (utf8 == null)
      return null;
    else
      return utf8.getValue();
  }

  /**
   * Adds a new constant.
   */
  public void addConstant(ConstantPoolEntry entry)
  {
    if (entry instanceof Utf8Constant) {
      Utf8Constant utf8 = (Utf8Constant) entry;

      _utf8Map.put(utf8.getValue(), utf8);
    }

    _entries.add(entry);
  }

  /**
   * Gets a UTF-8 constant.
   */
  public Utf8Constant getUTF8(String value)
  {
    return _utf8Map.get(value);
  }

  /**
   * Adds a UTF-8 constant.
   */
  public Utf8Constant addUTF8(String value)
  {
    Utf8Constant entry = getUTF8(value);

    if (entry != null)
      return entry;

    entry = new Utf8Constant(this, _entries.size(), value);

    addConstant(entry);

    return entry;
  }

  /**
   * Gets a string constant.
   */
  public StringConstant getString(String name)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof StringConstant))
        continue;

      StringConstant stringEntry = (StringConstant) entry;

      if (stringEntry.getString().equals(name))
        return stringEntry;
    }

    return null;
  }

  /**
   * Adds a string constant.
   */
  public StringConstant addString(String name)
  {
    StringConstant entry = getString(name);

    if (entry != null) {
      return entry;
    }

    Utf8Constant utf8 = addUTF8(name);

    entry = new StringConstant(this, _entries.size(), utf8.getIndex());

    addConstant(entry);

    return entry;
  }

  /**
   * Gets a integer constant.
   */
  public IntegerConstant getIntegerByValue(int value)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof IntegerConstant))
        continue;

      IntegerConstant integerEntry = (IntegerConstant) entry;

      if (integerEntry.getValue() == value)
        return integerEntry;
    }

    return null;
  }

  /**
   * Adds a integer constant.
   */
  public IntegerConstant addInteger(int value)
  {
    IntegerConstant entry = getIntegerByValue(value);

    if (entry != null)
      return entry;

    entry = new IntegerConstant(this, _entries.size(), value);

    addConstant(entry);

    return entry;
  }

  /**
   * Gets a long constant.
   */
  public LongConstant getLongByValue(long value)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof LongConstant))
        continue;

      LongConstant longEntry = (LongConstant) entry;

      if (longEntry.getValue() == value)
        return longEntry;
    }

    return null;
  }

  /**
   * Adds a long constant.
   */
  public LongConstant addLong(long value)
  {
    LongConstant entry = getLongByValue(value);

    if (entry != null)
      return entry;

    entry = new LongConstant(this, _entries.size(), value);

    addConstant(entry);
    addConstant(null);

    return entry;
  }

  /**
   * Gets a float constant.
   */
  public FloatConstant getFloatByValue(float value)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof FloatConstant))
        continue;

      FloatConstant floatEntry = (FloatConstant) entry;

      if (floatEntry.getValue() == value)
        return floatEntry;
    }

    return null;
  }

  /**
   * Adds a float constant.
   */
  public FloatConstant addFloat(float value)
  {
    FloatConstant entry = getFloatByValue(value);

    if (entry != null)
      return entry;

    entry = new FloatConstant(this, _entries.size(), value);

    addConstant(entry);

    return entry;
  }

  /**
   * Gets a double constant.
   */
  public DoubleConstant getDoubleByValue(double value)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof DoubleConstant))
        continue;

      DoubleConstant doubleEntry = (DoubleConstant) entry;

      if (doubleEntry.getValue() == value)
        return doubleEntry;
    }

    return null;
  }

  /**
   * Adds a double constant.
   */
  public DoubleConstant addDouble(double value)
  {
    DoubleConstant entry = getDoubleByValue(value);

    if (entry != null)
      return entry;

    entry = new DoubleConstant(this, _entries.size(), value);

    addConstant(entry);
    addConstant(null);

    return entry;
  }

  /**
   * Gets a class constant.
   */
  public ClassConstant getClass(String name)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof ClassConstant))
        continue;

      ClassConstant classEntry = (ClassConstant) entry;

      if (classEntry.getName().equals(name))
        return classEntry;
    }

    return null;
  }

  /**
   * Adds a class constant.
   */
  public ClassConstant addClass(String name)
  {
    ClassConstant entry = getClass(name);

    if (entry != null)
      return entry;

    Utf8Constant utf8 = addUTF8(name);

    entry = new ClassConstant(this, _entries.size(), utf8.getIndex());

    addConstant(entry);

    return entry;
  }

  /**
   * Gets a name-and-type constant.
   */
  public NameAndTypeConstant getNameAndType(String name, String type)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof NameAndTypeConstant))
        continue;

      NameAndTypeConstant methodEntry = (NameAndTypeConstant) entry;

      if (methodEntry.getName().equals(name) &&
          methodEntry.getType().equals(type))
        return methodEntry;
    }

    return null;
  }

  /**
   * Adds a name-and-type constant.
   */
  public NameAndTypeConstant addNameAndType(String name, String type)
  {
    NameAndTypeConstant entry = getNameAndType(name, type);

    if (entry != null)
      return entry;

    Utf8Constant nameEntry = addUTF8(name);
    Utf8Constant typeEntry = addUTF8(type);

    entry = new NameAndTypeConstant(this, _entries.size(),
                                    nameEntry.getIndex(),
                                    typeEntry.getIndex());

    addConstant(entry);

    return entry;
  }

  /**
   * Gets a field ref constant.
   */
  public FieldRefConstant getFieldRef(String className,
                                      String name,
                                      String type)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof FieldRefConstant))
        continue;

      FieldRefConstant fieldEntry = (FieldRefConstant) entry;

      if (fieldEntry.getClassName().equals(className) &&
          fieldEntry.getName().equals(name) &&
          fieldEntry.getType().equals(type))
        return fieldEntry;
    }

    return null;
  }

  /**
   * Gets a field ref constant.
   */
  public FieldRefConstant getFieldRef(String name)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof FieldRefConstant))
        continue;

      FieldRefConstant fieldEntry = (FieldRefConstant) entry;

      if (fieldEntry.getName().equals(name))
        return fieldEntry;
    }

    return null;
  }

  /**
   * Adds a field ref constant.
   */
  public FieldRefConstant addFieldRef(String className,
                                      String name,
                                      String type)
  {
    FieldRefConstant entry = getFieldRef(className, name, type);

    if (entry != null)
      return entry;

    ClassConstant classEntry = addClass(className);
    NameAndTypeConstant typeEntry = addNameAndType(name, type);

    entry = new FieldRefConstant(this, _entries.size(),
                                 classEntry.getIndex(),
                                 typeEntry.getIndex());

    addConstant(entry);

    return entry;
  }

  /**
   * Gets a method ref constant.
   */
  public MethodRefConstant getMethodRef(String className,
                                        String name,
                                        String type)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof MethodRefConstant))
        continue;

      MethodRefConstant methodEntry = (MethodRefConstant) entry;

      if (methodEntry.getClassName().equals(className) &&
          methodEntry.getName().equals(name) &&
          methodEntry.getType().equals(type))
        return methodEntry;
    }

    return null;
  }

  /**
   * Adds a method ref constant.
   */
  public MethodRefConstant addMethodRef(String className,
                                        String name,
                                        String type)
  {
    MethodRefConstant entry = getMethodRef(className, name, type);

    if (entry != null)
      return entry;

    ClassConstant classEntry = addClass(className);
    NameAndTypeConstant typeEntry = addNameAndType(name, type);

    entry = new MethodRefConstant(this, _entries.size(),
                                  classEntry.getIndex(),
                                  typeEntry.getIndex());

    addConstant(entry);

    return entry;
  }

  /**
   * Gets a method ref constant.
   */
  public MethodHandleConstant getMethodHandle(MethodHandleType mhType,
                                              ConstantPoolEntry cpEntry)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof MethodHandleConstant)) {
        continue;
      }

      MethodHandleConstant methodHandle = (MethodHandleConstant) entry;

      if (methodHandle.getType().equals(mhType)
          && methodHandle.getConstantEntry().equals(cpEntry)) {
        return methodHandle;
      }
    }

    return null;
  }

  /**
   * Adds a method ref constant.
   */
  public MethodHandleConstant addMethodHandle(MethodHandleType mhType,
                                              ConstantPoolEntry cpEntry)
  {
    MethodHandleConstant entry = getMethodHandle(mhType, cpEntry);

    if (entry != null) {
      return entry;
    }

    entry = new MethodHandleConstant(this, _entries.size(), mhType, cpEntry);

    addConstant(entry);

    return entry;
  }

  /**
   * Gets an interface constant.
   */
  public InterfaceMethodRefConstant getInterfaceRef(String className,
                                                    String name,
                                                    String type)
  {
    for (int i = 0; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (! (entry instanceof InterfaceMethodRefConstant))
        continue;

      InterfaceMethodRefConstant methodEntry;
      methodEntry = (InterfaceMethodRefConstant) entry;

      if (methodEntry.getClassName().equals(className) &&
          methodEntry.getName().equals(name) &&
          methodEntry.getType().equals(type))
        return methodEntry;
    }

    return null;
  }

  /**
   * Adds an interface ref constant.
   */
  public InterfaceMethodRefConstant addInterfaceRef(String className,
                                                    String name,
                                                    String type)
  {
    InterfaceMethodRefConstant entry = getInterfaceRef(className, name, type);

    if (entry != null)
      return entry;

    ClassConstant classEntry = addClass(className);
    NameAndTypeConstant typeEntry = addNameAndType(name, type);

    entry = new InterfaceMethodRefConstant(this, _entries.size(),
                                           classEntry.getIndex(),
                                           typeEntry.getIndex());

    addConstant(entry);

    return entry;
  }

  /**
   * Adds an invokedynamic constant.
   */
  public InvokeDynamicConstant addInvokeDynamicRef(BootstrapMethodAttribute attr,
                                                   String methodName,
                                                   String methodType,
                                                   String bootClass,
                                                   String bootMethod,
                                                   String bootType,
                                                   ConstantPoolEntry []cpEntries)
  {
    NameAndTypeConstant methodEntry = addNameAndType(methodName, methodType);

    MethodRefConstant bootMethodRef
        = addMethodRef(bootClass, bootMethod, bootType);

    MethodHandleConstant bootMethodHandle
      = addMethodHandle(MethodHandleType.INVOKE_STATIC,
                        bootMethodRef);

    int bootIndex = attr.addMethod(bootMethodHandle.getIndex(), cpEntries);

    InvokeDynamicConstant invokedynamicRef
      = new InvokeDynamicConstant(this,
                                  _entries.size(),
                                  attr,
                                  bootIndex,
                                  methodEntry.getIndex());

    addConstant(invokedynamicRef);

    return invokedynamicRef;
  }

  /**
   * Writes the contents of the pool.
   */
  public void write(ByteCodeWriter out)
    throws IOException
  {
    out.writeShort(_entries.size());

    for (int i = 1; i < _entries.size(); i++) {
      ConstantPoolEntry entry = _entries.get(i);

      if (entry != null)
        entry.write(out);
    }
  }
}
