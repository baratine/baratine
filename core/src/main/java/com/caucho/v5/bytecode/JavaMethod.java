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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.bytecode.attr.Attribute;
import com.caucho.v5.bytecode.attr.CodeAttribute;
import com.caucho.v5.bytecode.attr.CodeWriterAttribute;
import com.caucho.v5.bytecode.attr.OpaqueAttribute;
import com.caucho.v5.bytecode.attr.SignatureAttribute;
import com.caucho.v5.bytecode.cpool.ConstantPool;

/**
 * Represents a java field.
 */
public class JavaMethod extends JMethod {
  static private final Logger log = Logger.getLogger(JavaMethod.class.getName());

  private static final JClass []NULL_CLASS = new JClass[0];

  private JavaClassLoader _loader;

  private JavaClass _jClass;

  private int _accessFlags;
  private String _name;
  private String _descriptor;
  private JClass []_exceptions = NULL_CLASS;
  private int _line = -1;

  private ArrayList<Attribute> _attributes = new ArrayList<Attribute>();

  private JavaAnnotation []_annotations;

  private boolean _isWrite;

  public JavaMethod(JavaClassLoader loader)
  {
    _loader = loader;
  }

  public JavaMethod()
  {
  }

  /**
   * Sets the JavaClass.
   */
  public void setJavaClass(JavaClass jClass)
  {
    _jClass = jClass;
  }

  public void setWrite(boolean isWrite)
  {
    _isWrite = isWrite;
  }

  /**
   * Sets the name.
   */
  public void setName(String name)
  {
    _name = name;

    if (_jClass != null)
      _jClass.getConstantPool().addUTF8(name);
  }

  /**
   * Gets the name.
   */
  public String getName()
  {
    return _name;
  }

  /**
   * Returns the line number.
   */
  public int getLine()
  {
    if (_line >= 0)
      return _line;

    Attribute attr = getAttribute("LineNumberTable");

    if (attr == null) {
      _line = 0;
      return _line;
    }

    _line = 0;
    return _line;
  }

  /**
   * Returns the class loader.
   */
  public JavaClassLoader getClassLoader()
  {
    return _loader;
  }

  /**
   * Sets the access flags
   */
  public void setAccessFlags(int flags)
  {
    _accessFlags = flags;
  }

  /**
   * Gets the access flags
   */
  public int getAccessFlags()
  {
    return _accessFlags;
  }

  /**
   * Returns true for a final method
   */
  public boolean isFinal()
  {
    return Modifier.isFinal(getAccessFlags());
  }

  /**
   * Returns true for a public method
   */
  public boolean isPublic()
  {
    return Modifier.isPublic(getAccessFlags());
  }

  /**
   * Returns true for a protected method
   */
  public boolean isProtected()
  {
    return Modifier.isProtected(getAccessFlags());
  }

  /**
   * Returns true for a private method
   */
  public boolean isPrivate()
  {
    return Modifier.isPrivate(getAccessFlags());
  }

  /**
   * Returns true for an abstract method
   */
  public boolean isAbstract()
  {
    return Modifier.isAbstract(getAccessFlags());
  }

  /**
   * Returns true for a static method
   */
  public boolean isStatic()
  {
    return Modifier.isStatic(getAccessFlags());
  }

  /**
   * Sets the descriptor.
   */
  public void setDescriptor(String descriptor)
  {
    _descriptor = descriptor;

    if (_jClass != null)
      _jClass.getConstantPool().addUTF8(descriptor);
  }

  /**
   * Gets the descriptor.
   */
  public String getDescriptor()
  {
    return _descriptor;
  }

  /**
   * Returns the declaring class.
   */
  public JClass getDeclaringClass()
  {
    return _jClass;
  }

  /**
   * Returns the return types.
   */
  public JClass getReturnType()
  {
    String descriptor = getDescriptor();

    int i = descriptor.lastIndexOf(')');

    return getClassLoader().descriptorToClass(descriptor, i + 1);
  }

  /**
   * Returns the return type.
   */
  public JType getGenericReturnType()
  {
    SignatureAttribute sigAttr = (SignatureAttribute) getAttribute("Signature");

    if (sigAttr != null) {
      String sig = sigAttr.getSignature();

      int t = sig.lastIndexOf(')');

      return _loader.parseParameterizedType(sig.substring(t + 1));
    }

    return getReturnType();
  }

  /**
   * Returns the parameter types.
   */
  public JClass []getParameterTypes()
  {
    String descriptor = getDescriptor();

    ArrayList<JClass> typeList = new ArrayList<JClass>();

    int i = 0;
    while ((i = nextDescriptor(descriptor, i)) >= 0) {
      typeList.add(getClassLoader().descriptorToClass(descriptor, i));
    }

    JClass []types = new JClass[typeList.size()];

    typeList.toArray(types);

    return types;
  }

  private int nextDescriptor(String name, int i)
  {
    switch (name.charAt(i)) {
    case ')':
      return -1;

    case '(':
    case 'V':
    case 'Z':
    case 'C':
    case 'B':
    case 'S':
    case 'I':
    case 'J':
    case 'F':
    case 'D':
      i += 1;
      break;

    case '[':
      return nextDescriptor(name, i + 1);

    case 'L':
      {
  int tail = name.indexOf( ';', i);

  if (tail < 0)
    throw new IllegalStateException();

  i = tail + 1;
      }
      break;

    default:
      throw new UnsupportedOperationException(name.substring(i));
    }

    if (name.length() <= i)
      return -1;
    else if (name.charAt(i) == ')')
      return -1;
    else
      return i;
  }

  /**
   * Sets the exception types
   */
  public void setExceptionTypes(JClass []exceptions)
  {
    _exceptions = exceptions;
  }

  /**
   * Returns the exception types.
   */
  public JClass []getExceptionTypes()
  {
    return _exceptions;
  }

  /**
   * Adds an attribute.
   */
  public void addAttribute(Attribute attr)
  {
    _attributes.add(attr);
  }

  public CodeWriterAttribute createCodeWriter()
  {
    CodeWriterAttribute code = new CodeWriterAttribute(_jClass);

    _attributes.add(code);

    return code;
  }

  /**
   * Removes an attribute.
   */
  public Attribute removeAttribute(String name)
  {
    for (int i = _attributes.size() - 1; i >= 0; i--) {
      Attribute attr = _attributes.get(i);

      if (attr.getName().equals(name)) {
        _attributes.remove(i);
        return attr;
      }
    }

    return null;
  }

  /**
   * Returns the attribute.
   */
  public ArrayList<Attribute> getAttributes()
  {
    return _attributes;
  }

  /**
   * Returns the attribute.
   */
  public Attribute getAttribute(String name)
  {
    for (int i = _attributes.size() - 1; i >= 0; i--) {
      Attribute attr = _attributes.get(i);

      if (attr.getName().equals(name))
        return attr;
    }

    return null;
  }

  /**
   * Returns the declared annotations.
   */
  public JAnnotation []getDeclaredAnnotations()
  {
    if (_annotations == null) {
      Attribute attr = getAttribute("RuntimeVisibleAnnotations");

      if (attr instanceof OpaqueAttribute) {
        byte []buffer = ((OpaqueAttribute) attr).getValue();

        try {
          ByteArrayInputStream is = new ByteArrayInputStream(buffer);

          ConstantPool cp = _jClass.getConstantPool();

          _annotations = JavaAnnotation.parseAnnotations(is, cp,
                                                         getClassLoader());
        } catch (IOException e) {
          log.log(Level.FINER, e.toString(), e);
        }
      }

      if (_annotations == null) {
        _annotations = new JavaAnnotation[0];
      }
    }

    return _annotations;
  }

  /**
   * Returns the code attribute.
   */
  public CodeAttribute getCode()
  {
    for (int i = 0; i < _attributes.size(); i++) {
      Attribute attr = _attributes.get(i);

      if (attr instanceof CodeAttribute)
        return (CodeAttribute) attr;
    }

    return null;
  }

  /**
   * Create the code attribute.
   */
  public CodeAttribute createCode()
  {
    CodeAttribute code = new CodeAttribute();
    
    for (int i = 0; i < _attributes.size(); i++) {
      Attribute attr = _attributes.get(i);

      if (attr instanceof CodeAttribute)
        return (CodeAttribute) attr;
    }

    return null;
  }


  /**
   * Writes the field to the output.
   */
  public void write(ByteCodeWriter out)
    throws IOException
  {
    out.writeShort(_accessFlags);
    out.writeUTF8Const(_name);
    out.writeUTF8Const(_descriptor);

    out.writeShort(_attributes.size());
    for (int i = 0; i < _attributes.size(); i++) {
      Attribute attr = _attributes.get(i);

      attr.write(out);
    }
  }

  /**
   * exports the method.
   */
  public JavaMethod export(JavaClass source, JavaClass target)
  {
    JavaMethod method = new JavaMethod(_loader);
    method.setName(_name);
    method.setDescriptor(_descriptor);
    method.setAccessFlags(_accessFlags);

    target.getConstantPool().addUTF8(_name);
    target.getConstantPool().addUTF8(_descriptor);

    for (int i = 0; i < _attributes.size(); i++) {
      Attribute attr = _attributes.get(i);

      method.addAttribute(attr.export(source, target));
    }

    return method;
  }

  /**
   * Concatenates the method.
   */
  public void concatenate(JavaMethod tail)
  {
    CodeAttribute codeAttr = getCode();
    CodeAttribute tailCodeAttr = tail.getCode();

    byte []code = codeAttr.getCode();
    byte []tailCode = tailCodeAttr.getCode();

    int codeLength = code.length;

    if ((code[codeLength - 1] & 0xff) == CodeVisitor.RETURN)
      codeLength = codeLength - 1;

    byte []newCode = new byte[codeLength + tailCode.length];
    System.arraycopy(code, 0, newCode, 0, codeLength);
    System.arraycopy(tailCode, 0, newCode, codeLength, tailCode.length);

    codeAttr.setCode(newCode);

    if (codeAttr.getMaxStack() < tailCodeAttr.getMaxStack())
      codeAttr.setMaxStack(tailCodeAttr.getMaxStack());

    if (codeAttr.getMaxLocals() < tailCodeAttr.getMaxLocals())
      codeAttr.setMaxLocals(tailCodeAttr.getMaxLocals());

    ArrayList<CodeAttribute.ExceptionItem> exns = tailCodeAttr.getExceptions();
    for (int i = 0; i < exns.size();  i++) {
      CodeAttribute.ExceptionItem exn = exns.get(i);

      CodeAttribute.ExceptionItem newExn = new CodeAttribute.ExceptionItem();

      newExn.setType(exn.getType());
      newExn.setStart(exn.getStart() + codeLength);
      newExn.setEnd(exn.getEnd() + codeLength);
      newExn.setHandler(exn.getHandler() + codeLength);
    }
  }

  public String toString()
  {
    return "JavaMethod[" + _name + "]";
  }
}
