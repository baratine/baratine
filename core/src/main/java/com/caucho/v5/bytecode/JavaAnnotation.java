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
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.bytecode.cpool.ConstantPool;

/**
 * Represents a java annotation.
 */
public class JavaAnnotation extends JAnnotation {
  static private final Logger log =
    Logger.getLogger(JavaAnnotation.class.getName());

  private static Method _enumValueOf;

  private JavaClassLoader _loader;
  private HashMap<String,Object> _valueMap = new HashMap<String,Object>(8);
  private String _type;

  /**
   * Sets the class loader.
   */
  public void setClassLoader(JavaClassLoader loader)
  {
    _loader = loader;
  }

  /**
   * Gets the class loader.
   */
  public JavaClassLoader getClassLoader()
  {
    return _loader;
  }

  /**
   * Sets the type.
   */
  public void setType(String type)
  {
    _type = type;
  }

  /**
   * Gets the type.
   */
  public String getType()
  {
    return _type;
  }

  /**
   * Returns the value map.
   */
  public HashMap<String,Object> getValueMap()
  {
    return _valueMap;
  }

  /**
   * Sets a value.
   */
  public Object putValue(String key, Object value)
  {
    return _valueMap.put(key, value);
  }

  /**
   * Parses the annotation from an annotation block.
   */
  static JavaAnnotation []parseAnnotations(InputStream is,
                                           ConstantPool cp,
                                           JavaClassLoader loader)
    throws IOException
  {
    int n = readShort(is);

    JavaAnnotation []annArray = new JavaAnnotation[n];

    for (int i = 0; i < n; i++) {
      annArray[i] = parseAnnotation(is, cp, loader);
    }

    return annArray;
  }

  private static JavaAnnotation parseAnnotation(InputStream is,
                                                ConstantPool cp,
                                                JavaClassLoader loader)
    throws IOException
  {
    JavaAnnotation ann = new JavaAnnotation();
    ann.setClassLoader(loader);
      
    int type = readShort(is);

    String typeName = cp.getUtf8(type).getValue();

    if (typeName.endsWith(";"))
      typeName = typeName.substring(1, typeName.length() - 1).replace('/', '.');
    
    ann.setType(typeName);

    try {
      Class aClass = Class.forName(typeName, false, Thread.currentThread().getContextClassLoader());

      for (Method method : aClass.getDeclaredMethods()) {
        Object value = method.getDefaultValue();

        if (value instanceof Class) {
          String className = ((Class) value).getName();

          ann.putValue(method.getName(), loader.forName(className));
        }
        else if (value != null)
          ann.putValue(method.getName(), value);
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }

    int nPairs = readShort(is);
    for (int j = 0; j < nPairs; j++) {
      int nameIndex = readShort(is);

      String name = cp.getUtf8(nameIndex).getValue();

      Object value = parseElementValue(is, cp, loader);

      ann.putValue(name, value);
    }

    return ann;
  }

  private static Object parseElementValue(InputStream is,
                                          ConstantPool cp,
                                          JavaClassLoader loader)
    throws IOException
  {
    int tag = is.read();

    switch (tag) {
    case 'Z':
      {
        int i = readShort(is);

        return cp.getInteger(i).getValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
      }
      
    case 'B':
      {
        int i = readShort(is);

        return new Byte((byte) cp.getInteger(i).getValue());
      }
      
    case 'S':
      {
        int i = readShort(is);

        return new Short((short) cp.getInteger(i).getValue());
      }
      
    case 'I':
      {
        int i = readShort(is);

        return new Integer(cp.getInteger(i).getValue());
      }
      
    case 'J':
      {
        int i = readShort(is);

        return new Long(cp.getLong(i).getValue());
      }
      
    case 'F':
      {
        int i = readShort(is);

        return new Float(cp.getFloat(i).getValue());
      }
      
    case 'D':
      {
        int i = readShort(is);

        return new Double(cp.getDouble(i).getValue());
      }
      
    case 'C':
      {
        int i = readShort(is);

        return new Character((char) cp.getInteger(i).getValue());
      }
      
    case 's':
      int i = readShort(is);
      return cp.getUtf8(i).getValue();
    case 'e':
      {
        int type = readShort(is);
        int value = readShort(is);
        String enumClassName = cp.getUtf8(type).getValue();
        enumClassName = enumClassName.substring(1, enumClassName.length() - 1);
        enumClassName = enumClassName.replace('/', '.');

        try {
          Class enumClass = Class.forName(enumClassName, false, Thread.currentThread().getContextClassLoader());
          String enumName = cp.getUtf8(value).getValue();

          return _enumValueOf.invoke(null, enumClass, enumName);

        } catch (Exception e) {
          log.log(Level.FINE, e.toString(), e);

          return null;
        }
      }
    case 'c':
      // class
      {
        String className = cp.getUtf8(readShort(is)).getValue();

        return loader.descriptorToClass(className, 0);
      }
    case '@':
      return parseAnnotation(is, cp, loader);
    case '[':
      {
        int n = readShort(is);

        Object []array = new Object[n];
        for (int j = 0; j < n; j++) {
          array[j] = parseElementValue(is, cp, loader);
        }

        return array;
      }
    default:
      throw new IllegalStateException();
    }
  }

  static int readShort(InputStream is)
    throws IOException
  {
    return (((is.read() & 0xff) << 8) +
            (is.read() & 0xff));
  }

  static int readInt(InputStream is)
    throws IOException
  {
    return (((is.read() & 0xff) << 24) +
            ((is.read() & 0xff) << 16) +
            ((is.read() & 0xff) << 8) +
            ((is.read() & 0xff)));
  }

  public String toString()
  {
    return "JavaAnnotation[" + _type + "]";
  }

  static {
    try {
      Class cl = Class.forName("java.lang.Enum");
      _enumValueOf = cl.getMethod("valueOf",
                                  new Class[] { Class.class, String.class });
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }
}
