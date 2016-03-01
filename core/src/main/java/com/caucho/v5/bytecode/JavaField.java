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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.bytecode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.bytecode.attr.Attribute;
import com.caucho.v5.bytecode.attr.OpaqueAttribute;
import com.caucho.v5.bytecode.attr.SignatureAttribute;
import com.caucho.v5.bytecode.cpool.ConstantPool;

/**
 * Represents a java field.
 */
public class JavaField extends JField {
  static private final Logger log
    = Logger.getLogger(JavaField.class.getName());

  private JavaClass _jClass;
  private int _accessFlags;
  private String _name;
  private String _descriptor;

  private boolean _isWrite;
  
  private ArrayList<Attribute> _attributes = new ArrayList<Attribute>();

  private JavaAnnotation []_annotations;

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
   * Returns the declaring class.
   */
  public JClass getDeclaringClass()
  {
    return _jClass;
  }

  /**
   * Returns the class loader.
   */
  public JavaClassLoader getClassLoader()
  {
    return _jClass.getClassLoader();
  }

  /**
   * Sets the name.
   */
  public void setName(String name)
  {
    _name = name;

    if (_isWrite)
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
   * Sets the descriptor.
   */
  public void setDescriptor(String descriptor)
  {
    _descriptor = descriptor;

    if (_isWrite)
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
   * Gets the typename.
   */
  public JClass getType()
  {
    return getClassLoader().descriptorToClass(getDescriptor(), 0);
  }

  /**
   * Returns true for a static field.
   */
  public boolean isStatic()
  {
    return Modifier.isStatic(getAccessFlags());
  }

  /**
   * Returns true for a private field.
   */
  public boolean isPrivate()
  {
    return Modifier.isPrivate(getAccessFlags());
  }

  /**
   * Returns true for a transient field.
   */
  public boolean isTransient()
  {
    return Modifier.isTransient(getAccessFlags());
  }

  /**
   * Gets the typename.
   */
  public JType getGenericType()
  {
    SignatureAttribute sigAttr = (SignatureAttribute) getAttribute("Signature");

    if (sigAttr != null) {
      return getClassLoader().parseParameterizedType(sigAttr.getSignature());
    }

    return getType();
  }

  /**
   * Adds an attribute.
   */
  public void addAttribute(Attribute attr)
  {
    _attributes.add(attr);
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
   * exports the field
   */
  public JavaField export(JavaClass cl, JavaClass target)
  {
    JavaField field = new JavaField();
    field.setName(_name);
    field.setDescriptor(_descriptor);
    field.setAccessFlags(_accessFlags);

    target.getConstantPool().addUTF8(_name);
    target.getConstantPool().addUTF8(_descriptor);

    for (int i = 0; i < _attributes.size(); i++) {
      Attribute attr = _attributes.get(i);

      field.addAttribute(attr.export(cl, target));
    }

    return field;
  }

  public boolean equals(Object o)
  {
    if (o == null || ! JavaField.class.equals(o.getClass()))
      return false;

    JavaField field = (JavaField) o;

    return _name.equals(field._name);
  }

  public String toString()
  {
    return "JavaField[" + _name + "]";
  }
}
