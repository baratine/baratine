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

package com.caucho.v5.bytecode.attr;

import java.io.*;
import java.lang.reflect.Method;
import java.util.HashMap;

import com.caucho.v5.bytecode.CodeVisitor;
import com.caucho.v5.bytecode.JClass;
import com.caucho.v5.bytecode.JavaClass;
import com.caucho.v5.bytecode.cpool.ClassConstant;
import com.caucho.v5.bytecode.cpool.ConstantPool;
import com.caucho.v5.bytecode.cpool.ConstantPoolEntry;
import com.caucho.v5.bytecode.cpool.FieldRefConstant;
import com.caucho.v5.bytecode.cpool.InterfaceMethodRefConstant;
import com.caucho.v5.bytecode.cpool.InvokeDynamicConstant;
import com.caucho.v5.bytecode.cpool.LongConstant;
import com.caucho.v5.bytecode.cpool.MethodRefConstant;
import com.caucho.v5.bytecode.cpool.StringConstant;
import com.caucho.v5.bytecode.cpool.Utf8Constant;

/**
 * Code generator attribute.
 */
public class CodeWriterAttribute extends CodeAttribute {
  private int _stack;
  private ByteArrayOutputStream _bos;

  private static HashMap<Class<?>,String> _prim
    = new HashMap<Class<?>,String>();

  public CodeWriterAttribute(JavaClass jClass)
  {
    setJavaClass(jClass);

    addUTF8("Code");

    _bos = new ByteArrayOutputStream();
  }

  public void cast(Class<?> cl)
  {
    cast(cl.getName().replace('.', '/'));
  }

  public void cast(String className)
  {
    int index = addClass(className);

    write(CodeVisitor.CHECKCAST);
    write(index >> 8);
    write(index);
  }

  public void getField(String className, String fieldName, String sig)
  {
    int index = addFieldRef(className, fieldName, sig);

    write(CodeVisitor.GETFIELD);
    write(index >> 8);
    write(index);
  }

  public void getField(String className, String fieldName, Class<?> type)
  {
    getField(className, fieldName, createDescriptor(type));
  }

  public void putField(String className, String fieldName, String sig)
  {
    int index = addFieldRef(className, fieldName, sig);

    write(CodeVisitor.PUTFIELD);
    write(index >> 8);
    write(index);
  }

  public void putField(String className, String fieldName, Class<?> type)
  {
    putField(className, fieldName, createDescriptor(type));
  }

  public void getStatic(String className, String fieldName, String sig)
  {
    int index = addFieldRef(className, fieldName, sig);

    write(CodeVisitor.GETSTATIC);
    write(index >> 8);
    write(index);
  }

  public void putStatic(String className, String fieldName, String sig)
  {
    int index = addFieldRef(className, fieldName, sig);

    write(CodeVisitor.PUTSTATIC);
    write(index >> 8);
    write(index);
  }

  public void getArrayObject()
  {
    write(CodeVisitor.AALOAD);
  }

  public void setArrayObject()
  {
    write(CodeVisitor.AASTORE);
  }

  public void pushObjectVar(int index)
  {
    _stack++;

    if (index <= 3) {
      write(CodeVisitor.ALOAD_0 + index);
    }
    else {
      write(CodeVisitor.ALOAD);
      write(index);
    }
  }

  public void pushIntVar(int index)
  {
    _stack++;

    if (index <= 3) {
      write(CodeVisitor.ILOAD_0 + index);
    }
    else {
      write(CodeVisitor.ILOAD);
      write(index);
    }
  }

  public void pushLongVar(int index)
  {
    _stack += 2;

    if (index <= 3) {
      write(CodeVisitor.LLOAD_0 + index);
    }
    else {
      write(CodeVisitor.LLOAD);
      write(index);
    }
  }

  public void pushFloatVar(int index)
  {
    _stack += 1;

    if (index <= 3) {
      write(CodeVisitor.FLOAD_0 + index);
    }
    else {
      write(CodeVisitor.FLOAD);
      write(index);
    }
  }

  public void pushDoubleVar(int index)
  {
    _stack += 2;

    if (index <= 3) {
      write(CodeVisitor.DLOAD_0 + index);
    }
    else {
      write(CodeVisitor.DLOAD);
      write(index);
    }
  }

  public void pushNull()
  {
    _stack += 1;

    write(CodeVisitor.ACONST_NULL);
  }

  public void pushInt(int value)
  {
    _stack += 1;

    write(CodeVisitor.SIPUSH);
    write(value >> 8);
    write(value);
  }

  public void pushConstant(String value)
  {
    int index = addString(value);

    ldc(index);
  }

  public void pushConstantClass(Class<?> cl)
  {
    pushConstantClass(cl.getName());
  }

  public void pushConstantClass(String className)
  {
    int index = addClass(className.replace('.', '/'));

    ldc(index);
  }

  public void pushConstant(long value)
  {
    int index = addLong(value);

    _stack += 2;

    write(CodeVisitor.LDC2_W);
    write(index >> 8);
    write(index);
  }

  public void ldc(int index)
  {
    _stack += 1;

    write(CodeVisitor.LDC_W);
    write(index >> 8);
    write(index);
  }

  public void invoke(Class<?> declaringClass,
                     String methodName,
                     Class<?> retType,
                     Class<?> ...param)
  {
    int argStack = getLength(param) + 1;
    int retStack = getLength(retType);

    invoke(declaringClass.getName().replace('.', '/'),
           methodName,
           createDescriptor(retType, param),
           argStack,
           retStack);
  }

  public void invoke(String className,
                     String methodName,
                     String signature,
                     int argStack,
                     int returnStack)
  {
    _stack += returnStack - argStack;

    int index = addMethodRef(className, methodName, signature);

    write(CodeVisitor.INVOKEVIRTUAL);
    write(index >> 8);
    write(index);
  }

  public void invokeInterface(Class<?> declaringClass,
                            String methodName,
                            Class<?> retType,
                            Class<?> ...param)
  {
    int argStack = getLength(param) + 1;
    int retStack = getLength(retType);

    invokeInterface(declaringClass.getName().replace('.', '/'),
                    methodName,
                    createDescriptor(retType, param),
                    argStack,
                    retStack);
  }

  public void invokeInterface(String className,
                              String methodName,
                              String signature,
                              int argStack,
                              int returnStack)
  {
    _stack += returnStack - argStack;

    int index = addInterfaceMethodRef(className, methodName, signature);

    write(CodeVisitor.INVOKEINTERFACE);
    write(index >> 8);
    write(index);
    write(argStack);
    write(0);
  }

  public void invokedynamic(String methodName,
                            String methodSignature,
                            int argStack,
                            int returnStack,
                            String bootClassName,
                            String bootMethodName,
                            String bootSignature,
                            ConstantPoolEntry ...bootArgs)
  {
    _stack += returnStack - argStack;

    int index = addInvokeDynamicRef(methodName, methodSignature,
                                    bootClassName, bootMethodName, bootSignature,
                                    bootArgs);

    write(CodeVisitor.INVOKEDYNAMIC);
    write(index >> 8);
    write(index);
    write(0);
    write(0);
  }

  public void newInstance(Class<?> type)
  {
    newInstance(type.getName().replace('.', '/'));
  }

  public void newInstance(String className)
  {
    _stack += 1;

    int index = addClass(className);

    write(CodeVisitor.NEW);
    write(index >> 8);
    write(index);
  }

  public void newObjectArray(Class<?> cl)
  {
    newObjectArray(cl.getName().replace('.', '/'));
  }

  public void newObjectArray(String className)
  {
    _stack += 1;

    int index = addClass(className);

    write(CodeVisitor.ANEWARRAY);
    write(index >> 8);
    write(index);
  }

  public void dup()
  {
    _stack += 1;

    write(CodeVisitor.DUP);
  }

  public void i2b()
  {
    write(CodeVisitor.I2B);
  }

  public void i2s()
  {
    write(CodeVisitor.I2S);
  }

  public void i2c()
  {
    write(CodeVisitor.I2C);
  }

  public void invokespecial(Class<?> declaringClass,
                            String methodName,
                            Class<?> retType,
                            Class<?> ...param)
  {
    invokespecial(declaringClass.getName().replace('.', '/'),
                  methodName,
                  retType,
                  param);
  }

  public void invokespecial(String declaringClass,
                             String methodName,
                             Class<?> retType,
                             Class<?> ...param)
  {
    int argStack = getLength(param) + 1;
    int retStack = getLength(retType);

    invokespecial(declaringClass,
                  methodName,
                  createDescriptor(retType, param),
                  argStack,
                  retStack);
  }

  public void invokespecial(String className,
                            String methodName,
                            String signature,
                            int argStack,
                            int returnStack)
  {
    _stack += returnStack - argStack;

    int index = addMethodRef(className, methodName, signature);

    write(CodeVisitor.INVOKESPECIAL);
    write(index >> 8);
    write(index);
  }

  public void invokestatic(Class<?> declaringClass,
                            String methodName,
                            Class<?> retType,
                            Class<?> ...param)
  {
    invokestatic(declaringClass.getName().replace('.', '/'),
                  methodName,
                  retType,
                  param);
  }

  public void invokestatic(String declaringClass,
                             String methodName,
                             Class<?> retType,
                             Class<?> ...param)
  {
    int argStack = getLength(param) + 1;
    int retStack = getLength(retType);

    invokestatic(declaringClass,
                  methodName,
                  createDescriptor(retType, param),
                  argStack,
                  retStack);
  }

  public void invokestatic(String className,
                           String methodName,
                           String signature,
                           int argStack,
                           int returnStack)
  {
    _stack += returnStack - argStack;

    int index = addMethodRef(className, methodName, signature);

    write(CodeVisitor.INVOKESTATIC);
    write(index >> 8);
    write(index);
  }

  public void addThrow()
  {
    write(CodeVisitor.ATHROW);
  }

  public void addReturn()
  {
    write(CodeVisitor.RETURN);
  }

  public void addIntReturn()
  {
    write(CodeVisitor.IRETURN);
  }

  public void addLongReturn()
  {
    write(CodeVisitor.LRETURN);
  }

  public void addFloatReturn()
  {
    write(CodeVisitor.FRETURN);
  }

  public void addDoubleReturn()
  {
    write(CodeVisitor.DRETURN);
  }

  public void addObjectReturn()
  {
    write(CodeVisitor.ARETURN);
  }

  public int addFieldRef(String className, String fieldName, String sig)
  {
    FieldRefConstant ref
      = getConstantPool().addFieldRef(className, fieldName, sig);

    return ref.getIndex();
  }

  public int addMethodRef(String className, String methodName, String sig)
  {
    MethodRefConstant ref
      = getConstantPool().addMethodRef(className, methodName, sig);

    return ref.getIndex();
  }

  public int addInterfaceMethodRef(String className, String methodName, String sig)
  {
    InterfaceMethodRefConstant ref
      = getConstantPool().addInterfaceRef(className, methodName, sig);

    return ref.getIndex();
  }

  public int addInvokeDynamicRef(String methodName,
                                 String methodType,
                                 String bootClass,
                                 String bootMethod,
                                 String bootType,
                                 ConstantPoolEntry []entries)
  {
    JavaClass jClass = getJavaClass();

    BootstrapMethodAttribute attr = jClass.getBootstrapMethods();

    InvokeDynamicConstant ref
      = getConstantPool().addInvokeDynamicRef(attr,
                                              methodName, methodType,
                                              bootClass, bootMethod, bootType,
                                              entries);

    return ref.getIndex();
  }

  public int addUTF8(String code)
  {
    Utf8Constant value = getConstantPool().addUTF8(code);

    return value.getIndex();
  }

  public int addString(String code)
  {
    StringConstant value = getConstantPool().addString(code);

    return value.getIndex();
  }

  public int addLong(long data)
  {
    LongConstant value = getConstantPool().addLong(data);

    return value.getIndex();
  }

  public int addClass(String className)
  {
    ClassConstant value = getConstantPool().addClass(className);

    return value.getIndex();
  }

  public ConstantPool getConstantPool()
  {
    return getJavaClass().getConstantPool();
  }

  private void write(int v)
  {
    _bos.write(v);
  }

  public void close()
  {
    if (_bos != null) {
      setCode(_bos.toByteArray());
      _bos = null;
    }
  }

  private int getLength(Class<?> []types)
  {
    if (types == null) {
      return 0;
    }

    int length = 0;

    for (int i = 0; i < types.length; i++) {
      length += getLength(types[i]);
    }

    return length;
  }

  private int getLength(Class<?> type)
  {
    if (void.class.equals(type)) {
      return 0;
    }
    else if (long.class.equals(type)
             || double.class.equals(type)) {
      return 2;
    }
    else {
      return 1;
    }
  }

  public String createDescriptor(Method method)
  {
    return createDescriptor(method.getReturnType(), method.getParameterTypes());
  }

  public static String createDescriptor(Class<?> retType, Class<?> ...paramTypes)
  {
    StringBuilder sb = new StringBuilder();

    sb.append("(");

    if (paramTypes != null) {
      for (Class<?> param : paramTypes) {
        sb.append(createDescriptor(param));
      }
    }

    sb.append(")");
    sb.append(createDescriptor(retType));

    return sb.toString();
  }

  public static String createDescriptor(Class<?> cl)
  {
    if (cl.isArray())
      return "[" + createDescriptor(cl.getComponentType());

    String primValue = _prim.get(cl);

    if (primValue != null)
      return primValue;

    return "L" + cl.getName().replace('.', '/') + ";";
  }

  static {
    _prim.put(boolean.class, "Z");
    _prim.put(byte.class, "B");
    _prim.put(char.class, "C");
    _prim.put(short.class, "S");
    _prim.put(int.class, "I");
    _prim.put(long.class, "J");
    _prim.put(float.class, "F");
    _prim.put(double.class, "D");
    _prim.put(void.class, "V");
  }

}
