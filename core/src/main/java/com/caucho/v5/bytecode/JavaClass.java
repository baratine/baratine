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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.bytecode.attr.Attribute;
import com.caucho.v5.bytecode.attr.BootstrapMethodAttribute;
import com.caucho.v5.bytecode.attr.CodeWriterAttribute;
import com.caucho.v5.bytecode.attr.OpaqueAttribute;
import com.caucho.v5.bytecode.cpool.ConstantPool;

/**
 * Represents a java class.
 */
public class JavaClass extends JClass {
  static private final Logger log
    = Logger.getLogger(JavaClass.class.getName());

  public static final int MAGIC = 0xcafebabe;

  public static final int ACC_PUBLIC    = 0x0001;
  public static final int ACC_PRIVATE   = 0x0002;
  public static final int ACC_PROTECTED = 0x0004;
  public static final int ACC_STATIC    = 0x0008;
  public static final int ACC_FINAL     = 0x0010;
  public static final int ACC_SUPER     = 0x0020;

  private JavaClassLoader _loader;

  private URL _url;

  private int _major;
  private int _minor;

  private ConstantPool _constantPool = new ConstantPool();
  
  private int _accessFlags;

  private String _thisClass;
  private String _superClass;

  private ArrayList<String> _interfaces = new ArrayList<String>();
  
  private ArrayList<JavaField> _fields = new ArrayList<JavaField>();
  
  private ArrayList<JavaMethod> _methods = new ArrayList<JavaMethod>();
  
  private ArrayList<Attribute> _attributes = new ArrayList<Attribute>();
  
  private JavaAnnotation []_annotations;

  private boolean _isWrite;

  public JavaClass()
  {
    this(new JavaClassLoader());
  }

  public JavaClass(JavaClassLoader loader)
  {
    if (loader == null)
      throw new NullPointerException();
    
    _loader = loader;
  }

  /**
   * Returns the loader.
   */
  public JavaClassLoader getClassLoader()
  {
    return _loader;
  }

  public void setWrite(boolean isWrite)
  {
    _isWrite = isWrite;
  }

  /**
   * Sets the URL.
   */
  public void setURL(URL url)
  {
    _url = url;
  }

  /**
   * Sets the major identifier of the class file.
   */
  public void setMajor(int major)
  {
    _major = major;
  }

  /**
   * Gets the major identifier of the class file.
   */
  public int getMajor()
  {
    return _major;
  }

  /**
   * Sets the minor identifier of the class file.
   */
  public void setMinor(int minor)
  {
    _minor = minor;
  }

  /**
   * Gets the minor identifier of the class file.
   */
  public int getMinor()
  {
    return _minor;
  }

  /**
   * Returns the class's constant pool.
   */
  public ConstantPool getConstantPool()
  {
    return _constantPool;
  }

  /**
   * Sets the access flags.
   */
  public void setAccessFlags(int flags)
  {
    _accessFlags = flags;
  }

  /**
   * Gets the access flags.
   */
  public int getAccessFlags()
  {
    lazyLoad();
    
    return _accessFlags;
  }

  /**
   * Sets this class.
   */
  public void setThisClass(String className)
  {
    _thisClass = className;

    if (_isWrite)
      getConstantPool().addClass(className);
  }

  /**
   * Gets this class name.
   */
  public String getThisClass()
  {
    return _thisClass;
  }

  /**
   * Sets the super class.
   */
  public void setSuperClass(String className)
  {
    _superClass = className;

    getConstantPool().addClass(className);
  }

  /**
   * Gets the super class name.
   */
  public String getSuperClassName()
  {
    lazyLoad();
    
    return _superClass;
  }

  /**
   * Gets the super class name.
   */
  public JClass getSuperClass()
  {
    lazyLoad();
    
    if (_superClass == null)
      return null;
    else
      return getClassLoader().forName(_superClass.replace('/', '.'));
  }

  /**
   * Returns true for a final class.
   */
  public boolean isFinal()
  {
    return Modifier.isFinal(getAccessFlags());
  }

  /**
   * Returns true for an abstract class.
   */
  public boolean isAbstract()
  {
    return Modifier.isAbstract(getAccessFlags());
  }

  /**
   * Returns true for a public class.
   */
  public boolean isPublic()
  {
    return Modifier.isPublic(getAccessFlags());
  }

  /**
   * Returns true for a primitive class.
   */
  public boolean isPrimitive()
  {
    return false;
  }

  public void addInterface(Class<?> cl)
  {
    addInterface(cl.getName().replace('.', '/'));
  }

  /**
   * Adds an interface.
   */
  public void addInterface(String className)
  {
    _interfaces.add(className);

    if (_isWrite)
      getConstantPool().addClass(className);
  }

  /**
   * Adds an interface.
   */
  public ArrayList<String> getInterfaceNames()
  {
    return _interfaces;
  }

  /**
   * Gets the interfaces.
   */
  public JClass []getInterfaces()
  {
    lazyLoad();
    
    JClass []interfaces = new JClass[_interfaces.size()];

    for (int i = 0; i < _interfaces.size(); i++) {
      String name = _interfaces.get(i);
      name = name.replace('/', '.');
      
      interfaces[i] = getClassLoader().forName(name);
    }
    
    return interfaces;
  }

  /**
   * Adds a field
   */
  public void addField(JavaField field)
  {
    _fields.add(field);
  }

  public JavaField createField(String name, Class<?> type)
  {
    return createField(name, CodeWriterAttribute.createDescriptor(type));
  }
  
  public JavaField createField(String name, String descriptor)
  {
    if (! _isWrite)
      throw new IllegalStateException("create field requires write");

    JavaField jField = new JavaField();
    jField.setWrite(true);
    jField.setJavaClass(this);

    jField.setName(name);
    jField.setDescriptor(descriptor);

    _fields.add(jField);

    return jField;
  }

  /**
   * Returns the fields.
   */
  public ArrayList<JavaField> getFieldList()
  {
    lazyLoad();
    
    return _fields;
  }

  /**
   * Returns a fields.
   */
  public JavaField getField(String name)
  {
    ArrayList<JavaField> fieldList = getFieldList();
    
    for (int i = 0; i < fieldList.size(); i++) {
      JavaField field = fieldList.get(i);

      if (field.getName().equals(name))
        return field;
    }

    return null;
  }

  /**
   * Adds a method
   */
  public void addMethod(JavaMethod method)
  {
    _methods.add(method);
  }

  public JavaMethod createMethod(String name, Class<?> retType, Class<?> ...args)
  {
    return createMethod(name, CodeWriterAttribute.createDescriptor(retType, args));
  }

  public JavaMethod createMethod(String name, String descriptor)
  {
    if (! _isWrite)
      throw new IllegalStateException("create method requires write");

    JavaMethod jMethod = new JavaMethod();
    jMethod.setWrite(true);
    jMethod.setJavaClass(this);

    jMethod.setName(name);
    jMethod.setDescriptor(descriptor);

    _methods.add(jMethod);

    return jMethod;
  }

  /**
   * Returns the methods.
   */
  public ArrayList<JavaMethod> getMethodList()
  {
    lazyLoad();
    
    return _methods;
  }

  /**
   * Returns true for an array.
   */
  public boolean isArray()
  {
    return false;
  }

  /**
   * Returns true for an interface.
   */
  public boolean isInterface()
  {
    lazyLoad();
    
    return Modifier.isInterface(_accessFlags);
  }

  /**
   * Returns a method.
   */
  public JavaMethod getMethod(String name)
  {
    ArrayList<JavaMethod> methodList = getMethodList();
    
    for (int i = 0; i < methodList.size(); i++) {
      JavaMethod method = methodList.get(i);

      if (method.getName().equals(name))
        return method;
    }

    return null;
  }

  /**
   * Finds a method.
   */
  public JavaMethod findMethod(String name, String descriptor)
  {
    ArrayList<JavaMethod> methodList = getMethodList();
    
    for (int i = 0; i < methodList.size(); i++) {
      JavaMethod method = methodList.get(i);

      if (method.getName().equals(name) &&
          method.getDescriptor().equals(descriptor))
        return method;
    }

    return null;
  }

  /**
   * Adds an attribute
   */
  public void addAttribute(Attribute attr)
  {
    _attributes.add(attr);

    attr.addConstants(this);
  }

  /**
   * Returns the methods.
   */
  public ArrayList<Attribute> getAttributeList()
  {
    lazyLoad();
    
    return _attributes;
  }

  /**
   * Returns the attribute.
   */
  public Attribute getAttribute(String name)
  {
    ArrayList<Attribute> attributeList = getAttributeList();
    
    for (int i = attributeList.size() - 1; i >= 0; i--) {
      Attribute attr = attributeList.get(i);

      if (attr.getName().equals(name))
        return attr;
    }

    return null;
  }

  /**
   * @return
   */
  public BootstrapMethodAttribute getBootstrapMethods()
  {
    Attribute attr = getAttribute(BootstrapMethodAttribute.NAME);
    
    if (attr == null) {
      getConstantPool().addUTF8("BootstrapMethods");
      addAttribute(new BootstrapMethodAttribute());
      
      attr = getAttribute("BootstrapMethods");
    }
    
    return (BootstrapMethodAttribute) attr;
  }

  //
  // JClass methods.
  //

  /**
   * Returns the class-equivalent name.
   */
  public String getName()
  {
    return getThisClass().replace('/', '.');
  }
  
  /**
   * Returns true if the class is assignable from the argument.
   */
  public boolean isAssignableFrom(JClass cl)
  {
    if (getName().equals(cl.getName()))
      return true;

    JClass []ifc = cl.getInterfaces();

    for (int i = 0; i < ifc.length; i++) {
      if (isAssignableFrom(ifc[i]))
        return true;
    }

    if (cl.getSuperClass() != null)
      return isAssignableFrom(cl.getSuperClass());
    else
      return false;
  }
  
  /**
   * Returns true if the class is assignable from the argument.
   */
  public boolean isAssignableFrom(Class cl)
  {
    if (getName().equals(cl.getName()))
      return true;

    Class []ifc = cl.getInterfaces();

    for (int i = 0; i < ifc.length; i++) {
      if (isAssignableFrom(ifc[i]))
        return true;
    }

    if (cl.getSuperclass() != null)
      return isAssignableFrom(cl.getSuperclass());
    else
      return false;
  }
  
  /**
   * Returns true if the class is assignable from the argument.
   */
  public boolean isAssignableTo(Class cl)
  {
    if (getName().equals(cl.getName()))
      return true;

    JClass []ifc = getInterfaces();

    for (int i = 0; i < ifc.length; i++) {
      if (ifc[i].isAssignableTo(cl))
        return true;
    }

    if (getSuperClass() != null)
      return getSuperClass().isAssignableTo(cl);
    else
      return false;
  }
  
  /**
   * Returns the array of declared methods.
   */
  public JMethod []getDeclaredMethods()
  {
    ArrayList<JavaMethod> methodList = getMethodList();
    
    JMethod[] methods = new JMethod[methodList.size()];

    methodList.toArray(methods);

    return methods;
  }
  
  /**
   * Returns the array of declared methods.
   */
  public JMethod []getConstructors()
  {
    ArrayList<JavaMethod> ctorList = new ArrayList<JavaMethod>();
    
    for (JavaMethod method : getMethodList()) {
      if (method.getName().equals("<init>"))
        ctorList.add(method);
    }
    
    JMethod[] methods = new JMethod[ctorList.size()];

    ctorList.toArray(methods);

    return methods;
  }
  
  /**
   * Returns the matching method
   */
  public JMethod getMethod(String name, JClass []paramTypes)
  {
    loop:
    for (JMethod method : getMethods()) {
      if (! method.getName().equals(name))
        continue;

      JClass []mParamTypes = method.getParameterTypes();
      if (mParamTypes.length != paramTypes.length)
        continue;

      for (int i = 0; i < paramTypes.length; i++) {
        if (! paramTypes[i].getName().equals(mParamTypes[i].getName()))
          continue loop;
      }


      return method;
    }

    return null;
  }
  
  /**
   * Returns the matching method
   */
  public JMethod []getMethods()
  {
    ArrayList<JMethod> methodList = new ArrayList<JMethod>();

    getMethods(methodList);

    JMethod []methods = new JMethod[methodList.size()];
    methodList.toArray(methods);

    return methods;
  }
  
  /**
   * Returns the matching method
   */
  private void getMethods(ArrayList<JMethod> methodList)
  {
    for (JMethod method : getDeclaredMethods()) {
      if (! methodList.contains(method))
        methodList.add(method);
    }

    if (getSuperClass() != null) {
      for (JMethod method : getSuperClass().getMethods()) {
        if (! methodList.contains(method))
          methodList.add(method);
      }
    }
  }
  
  /**
   * Returns the array of declared fields.
   */
  public JField []getDeclaredFields()
  {
    ArrayList<JavaField> fieldList = getFieldList();
    
    JField[] fields = new JField[fieldList.size()];

    fieldList.toArray(fields);

    return fields;
  }
  
  /**
   * Returns the array of fields.
   */
  public JField []getFields()
  {
    ArrayList<JField> fieldList = new ArrayList<JField>();

    getFields(fieldList);

    JField []fields = new JField[fieldList.size()];
    fieldList.toArray(fields);

    return fields;
  }
  
  /**
   * Returns all the fields
   */
  private void getFields(ArrayList<JField> fieldList)
  {
    for (JField field : getDeclaredFields()) {
      if (! fieldList.contains(field))
        fieldList.add(field);
    }

    if (getSuperClass() != null) {
      for (JField field : getSuperClass().getFields()) {
        if (! fieldList.contains(field))
          fieldList.add(field);
      }
    }
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

          ConstantPool cp = getConstantPool();

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
   * Returns the annotation.
   */
  public JAnnotation getAnnotation(String className)
  {
    JAnnotation []annList = getDeclaredAnnotations();

    for (int i = 0; i < annList.length; i++) {
      if (annList[i].getType().equals(className))
        return annList[i];
    }
    
    return null;
  }

  /**
   * Lazily load the class.
   */
  private void lazyLoad()
  {
    if (_major > 0)
      return;

    try {
      if (_url == null)
        throw new IllegalStateException();
      
      try (InputStream is = _url.openStream()) {
      //ReadStream rs = VfsOld.openRead(is);
        _major = 1;

        ByteCodeParser parser = new ByteCodeParser();
        parser.setClassLoader(_loader);
        parser.setJavaClass(this);

        parser.parse(is);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Writes the class to the output.
   */
  public void write(OutputStream os)
    throws IOException
  {
    ByteCodeWriter out = new ByteCodeWriter(os, this);

    out.writeInt(MAGIC);
    out.writeShort(_minor);
    out.writeShort(_major);

    _constantPool.write(out);

    out.writeShort(_accessFlags);
    out.writeClass(_thisClass);
    out.writeClass(_superClass);

    out.writeShort(_interfaces.size());
    for (int i = 0; i < _interfaces.size(); i++) {
      String className = _interfaces.get(i);

      out.writeClass(className);
    }

    out.writeShort(_fields.size());
    for (int i = 0; i < _fields.size(); i++) {
      JavaField field = _fields.get(i);

      field.write(out);
    }

    out.writeShort(_methods.size());
    for (int i = 0; i < _methods.size(); i++) {
      JavaMethod method = _methods.get(i);

      method.write(out);
    }

    out.writeShort(_attributes.size());
    for (int i = 0; i < _attributes.size(); i++) {
      Attribute attr = _attributes.get(i);

      attr.write(out);
    }
  }

  public String toString()
  {
    return "JavaClass[" + _thisClass + "]";
  }
}
