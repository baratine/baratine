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

package com.caucho.v5.javac;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

import com.caucho.v5.bytecode.JClass;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.WriteStreamOld;

/**
 * Writing class for generated Java code.
 */
public class JavaWriter extends Writer {
  // Write stream for generating the code
  private WriteStreamOld _os;

  // Indentation depth
  private int _indentDepth;

  // True at the start of a line
  private boolean _startLine = true;

  // The line mapping
  private LineMap _lineMap;
  private boolean _isPreferLast = false;

  // The current output line
  private int _destLine = 1;
  private boolean _lastCr;

  // Generates a unique string.
  private int _uniqueId;

  public JavaWriter(WriteStreamOld os) 
  {
    _os = os;
  }

  /**
   * Returns the underlying stream.
   */
  public WriteStreamOld getWriteStream()
  {
    return _os;
  }

  /**
   * Returns the destination line.
   */
  public int getDestLine()
  {
    return _destLine;
  }

  /**
   * Sets the line map
   */
  public void setLineMap(LineMap lineMap)
  {
    _lineMap = lineMap;
  }

  /**
   * Gets the line map
   */
  public LineMap getLineMap()
  {
    return _lineMap;
  }

  /**
   * Sets the source filename and line.
   * 
   * @param filename
   *          the filename of the source file.
   * @param line
   *          the line of the source file.
   */
  public void setLocation(String filename, int line) throws IOException
  {
    if (_lineMap != null && filename != null && line >= 0) {
      _lineMap.add(filename, line, _destLine, _isPreferLast);
    }
  }

  /**
   * True if later source line numbers should override earlier ones
   */
  public void setPreferLast(boolean isPreferLast)
  {
    _isPreferLast = isPreferLast;
  }

  /**
   * Generates a unique id.
   */
  public int generateId()
  {
    return _uniqueId++;
  }

  /**
   * Prints a Java escaped string
   */
  public void printJavaString(String s) throws IOException
  {
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);

      switch (ch) {
      case '\\':
        _os.print("\\\\");
        break;
      case '\n':
        _os.print("\\n");
        break;
      case '\r':
        _os.print("\\r");
        break;
      case '"':
        _os.print("\\\"");
        break;
      default:
        _os.print(ch);
      }
    }
  }

  /**
   * Prints a Java escaped string
   */
  public void printJavaChar(char ch) throws IOException
  {
    switch (ch) {
    case '\\':
      _os.print("\\\\");
      break;
    case '\n':
      _os.print("\\n");
      break;
    case '\r':
      _os.print("\\r");
      break;
    case '\'':
      _os.print("\\'");
      break;
    default:
      _os.print(ch);
    }
  }

  /**
   * Prints a Java escaped string
   */
  public static String escapeJavaString(String s)
  {
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);

      switch (ch) {
      case '\\':
        sb.append("\\\\");
        break;
      case '\n':
        sb.append("\\n");
        break;
      case '\r':
        sb.append("\\r");
        break;
      case '"':
        sb.append("\\\"");
        break;
      default:
        sb.append(ch);
      }
    }

    return sb.toString();
  }

  /**
   * Pushes an indentation depth.
   */
  public void pushDepth() throws IOException
  {
    _indentDepth += 2;
  }

  /**
   * Pops an indentation depth.
   */
  public void popDepth() throws IOException
  {
    _indentDepth -= 2;
  }

  /**
   * Prints a string
   */
  public void print(String s) throws IOException
  {
    if (_startLine)
      printIndent();

    if (s == null) {
      _lastCr = false;
      _os.print("null");

      return;
    }

    int len = s.length();
    for (int i = 0; i < len; i++) {
      int ch = s.charAt(i);

      if (ch == '\n' && !_lastCr)
        _destLine++;
      else if (ch == '\r')
        _destLine++;

      _lastCr = ch == '\r';

      _os.print((char) ch);
    }
  }

  public void write(char[] buffer, int offset, int length) throws IOException
  {
    print(new String(buffer, offset, length));
  }

  /**
   * Prints a character.
   */
  public void print(char ch) throws IOException
  {
    if (_startLine)
      printIndent();

    if (ch == '\r') {
      _destLine++;
    } else if (ch == '\n' && !_lastCr)
      _destLine++;

    _lastCr = ch == '\r';

    _os.print(ch);
  }

  /**
   * Prints a boolean.
   */
  public void print(boolean b) throws IOException
  {
    if (_startLine)
      printIndent();

    _os.print(b);
    _lastCr = false;
  }

  /**
   * Prints an integer.
   */
  public void print(int i) throws IOException
  {
    if (_startLine)
      printIndent();

    _os.print(i);
    _lastCr = false;
  }

  /**
   * Prints an long
   */
  public void print(long l) throws IOException
  {
    if (_startLine)
      printIndent();

    _os.print(l);
    _lastCr = false;
  }

  /**
   * Prints an object.
   */
  public void print(Object o) throws IOException
  {
    if (_startLine)
      printIndent();

    _os.print(o);
    _lastCr = false;
  }

  /**
   * Prints a string with a new line
   */
  public void println(String s) throws IOException
  {
    print(s);
    println();
  }

  /**
   * Prints a boolean with a new line
   */
  public void println(boolean v) throws IOException
  {
    print(v);
    println();
  }

  /**
   * Prints a character.
   */
  public void println(char ch) throws IOException
  {
    print(ch);
    println();
  }

  /**
   * Prints an integer with a new line
   */
  public void println(int v) throws IOException
  {
    print(v);
    println();
  }

  /**
   * Prints an long with a new line
   */
  public void println(long v) throws IOException
  {
    print(v);
    println();
  }

  /**
   * Prints an object with a new line
   */
  public void println(Object v) throws IOException
  {
    print(v);
    println();
  }

  /**
   * Prints a newline
   */
  public void println() throws IOException
  {
    _os.println();
    if (!_lastCr)
      _destLine++;
    _lastCr = false;
    _startLine = true;
  }

  /**
   * Prints the Java represention of the class
   */
  public void printClass(Class<?> cl) throws IOException
  {
    if (! cl.isArray())
      print(cl.getName().replace('$', '.'));
    else {
      printClass(cl.getComponentType());
      print("[]");
    }
  }

  /**
   * Prints the Java representation of the type
   */
  @SuppressWarnings("unchecked")
  public void printType(Type type)
    throws IOException
  {
    if (type instanceof Class<?>) {
      printTypeClass((Class<?>) type);
    } 
    else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      
      printParameterizedType(parameterizedType);
    }
    else if (type instanceof WildcardType) {
      WildcardType wildcardType = (WildcardType) type;
      
      printWildcardType(wildcardType);
    }
    else if (type instanceof TypeVariable<?>) {
      TypeVariable<? extends GenericDeclaration> typeVariable = (TypeVariable<? extends GenericDeclaration>) type;
      
      printTypeVariable(typeVariable);
    }
    else if (type instanceof GenericArrayType) {
      GenericArrayType genericArrayType = (GenericArrayType) type;

      printType(genericArrayType.getGenericComponentType());
      print("[]");
    }
    else {
      throw new UnsupportedOperationException(type.getClass().getName() + " "
          + String.valueOf(type));
    }
  }

  private void printTypeClass(Class<?> cl)
    throws IOException
  {
    printClass(cl);
    
    if (true)
      return;

    // ioc/1238
    Type []param = cl.getTypeParameters();

    if (param == null || param.length == 0)
      return;

    print("<");
    
    for (int i = 0; i < param.length; i++) {
      if (i != 0)
        print(",");
      
      print("java.lang.Object");
    }
    print(">");
  }

  /**
   * Prints a parameterized type declaration:
   * 
   * <pre>
   * T&lt;X>
   * </pre>
   */
  private void printParameterizedType(ParameterizedType parameterizedType)
    throws IOException
  {
    Type rawType = parameterizedType.getRawType();

    if (rawType instanceof Class<?>)
      printClass((Class<?>) rawType);
    else
      printType(rawType);

    print("<");

    Type[] typeParameters = parameterizedType.getActualTypeArguments();

    for (int i = 0; i < typeParameters.length; i++) {
      if (i != 0) {
        print(", ");
      }

      printType(typeParameters[i]);
    }

    print(">");
  }

  private void printTypeVariable(TypeVariable<?> typeVariable)
    throws IOException
  {
    print(typeVariable.getName());
    
    if (true)
      return;

    Type[] bounds = typeVariable.getBounds();

    if ((bounds != null) && (bounds.length > 0)) {
      print(" extends ");

      for (int i = 0; i < bounds.length; i++) {
        if (i != 0) {
          print(" & ");
        }

        printType(bounds[i]);
      }
    }

    GenericDeclaration genericDeclaration
    = typeVariable.getGenericDeclaration();

    Type[] typeParameters = null;

    typeParameters = genericDeclaration.getTypeParameters();

    if ((typeParameters != null) && (typeParameters.length > 0)) {
      print("<");

      for (int i = 0; i < typeParameters.length; i++) {
        if (i != 0) {
          print(", ");
        }

        printType(typeParameters[i]);
      }

      print(">");
    }
  }

  public void printVarType(TypeVariable<?> typeVariable)
    throws IOException
  {
    print(typeVariable.getName());

    GenericDeclaration genericDeclaration
      = typeVariable.getGenericDeclaration();

    Type[] typeParameters = null;

    typeParameters = genericDeclaration.getTypeParameters();

    if ((typeParameters != null) && (typeParameters.length > 0)) {
      print("<");

      for (int i = 0; i < typeParameters.length; i++) {
        if (i != 0) {
          print(", ");
        }

        printType(typeParameters[i]);
      }

      print(">");
    }

    Type[] bounds = typeVariable.getBounds();

    if (bounds != null) {
      boolean isFirst = true;
      
      for (int i = 0; i < bounds.length; i++) {
        /*if (bounds[i].equals(Object.class))
          continue;
          */
        
        if (isFirst)
          print(" extends ");
        else
          print(" & ");

        isFirst = false;
        
        printType(bounds[i]);
      }
    }
  }

  private void printWildcardType(WildcardType wildcardType)
    throws IOException
  {
    print("?");

    Type[] upperBounds = wildcardType.getUpperBounds();

    if (upperBounds == null || upperBounds.length == 0) {
    }
    else if (upperBounds.length == 1 && upperBounds[0].equals(Object.class)) {
      // skip printing "extends Object"
    }
    else {
      print(" extends ");

      for (int i = 0; i < upperBounds.length; i++) {
        if (i != 0) {
          print(" & ");
        }

        printType(upperBounds[i]);
      }
    }

    Type[] lowerBounds = wildcardType.getLowerBounds();

    if (lowerBounds != null && lowerBounds.length > 0) {
      print(" super ");

      for (int i = 0; i < lowerBounds.length; i++) {
        if (i != 0) {
          print(" & ");
        }

        printType(lowerBounds[i]);
      }
    }
    
  }
  /**
   * Converts a java primitive type to a Java object.
   * 
   * @param value
   *          the java expression to be converted
   * @param javaType
   *          the type of the converted expression.
   */
  public void printJavaTypeToObject(String value, Class<?> javaType)
      throws IOException
  {
    if (Object.class.isAssignableFrom(javaType))
      print(value);
    else if (javaType.equals(boolean.class))
      print("new Boolean(" + value + ")");
    else if (javaType.equals(byte.class))
      print("new Byte(" + value + ")");
    else if (javaType.equals(short.class))
      print("new Short(" + value + ")");
    else if (javaType.equals(int.class))
      print("new Integer(" + value + ")");
    else if (javaType.equals(long.class))
      print("new Long(" + value + ")");
    else if (javaType.equals(char.class))
      print("String.valueOf(" + value + ")");
    else if (javaType.equals(float.class))
      print("new Float(" + value + ")");
    else if (javaType.equals(double.class))
      print("new Double(" + value + ")");
    else
      print(value);
  }

  /**
   * Converts a java primitive type to a Java object.
   * 
   * @param value
   *          the java expression to be converted
   * @param javaType
   *          the type of the converted expression.
   */
  public void printJavaTypeToObject(String value, JClass javaType)
      throws IOException
  {
    if (javaType.getName().equals("boolean"))
      print("new Boolean(" + value + ")");
    else if (javaType.getName().equals("byte"))
      print("new Byte(" + value + ")");
    else if (javaType.getName().equals("short"))
      print("new Short(" + value + ")");
    else if (javaType.getName().equals("int"))
      print("new Integer(" + value + ")");
    else if (javaType.getName().equals("long"))
      print("new Long(" + value + ")");
    else if (javaType.getName().equals("char"))
      print("String.valueOf(" + value + ")");
    else if (javaType.getName().equals("float"))
      print("new Float(" + value + ")");
    else if (javaType.getName().equals("double"))
      print("new Double(" + value + ")");
    else
      print(value);
  }

  /**
   * Prints the indentation at the beginning of a line.
   */
  public void printIndent() throws IOException
  {
    _startLine = false;

    for (int i = 0; i < _indentDepth; i++)
      _os.print(' ');

    _lastCr = false;
  }

  /**
   * Generates the smap file.
   */
  public void generateSmap() throws IOException
  {
    /*
    if (_lineMap != null) {
      PathImpl dstPath = getWriteStream().getPath();
      PathImpl smap = dstPath.getParent().lookup(dstPath.getTail() + ".smap");

      WriteStream out = smap.openWrite();
      try {
        // String srcName = _lineMap.getLastSourceFilename();

        LineMapWriter writer = new LineMapWriter(out);

        if (_lineMap.getSourceType() != null)
          writer.setSourceType(_lineMap.getSourceType());

        writer.write(_lineMap);
      } finally {
        out.close();
      }
    }
    */
  }

  /**
   * Returns the error message with proper line number.
   */
  public String errorMessage(String message)
  {
    /*
     * if (_srcFilename == null) return message; else return _srcFilename + ':'
     * + _srcLine + ": " + message;
     */
    return message;
  }

  public void flush()
  {
  }

  public void close()
  {
  }
}
