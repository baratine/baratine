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

import java.io.IOException;
import java.util.ArrayList;

import com.caucho.v5.bytecode.Analyzer;
import com.caucho.v5.bytecode.ByteCodeParser;
import com.caucho.v5.bytecode.ByteCodeWriter;
import com.caucho.v5.bytecode.CodeEnhancer;
import com.caucho.v5.bytecode.CodeVisitor;
import com.caucho.v5.bytecode.JavaClass;
import com.caucho.v5.bytecode.cpool.ClassConstant;
import com.caucho.v5.bytecode.cpool.ConstantPool;
import com.caucho.v5.bytecode.cpool.ConstantPoolEntry;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.TempOutputStream;

/**
 * Represents a generic attribute
 */
public class CodeAttribute extends Attribute {
  private JavaClass _jClass;
  private int _maxStack;
  private int _maxLocals;
  private byte []_code;
  private ArrayList<ExceptionItem> _exceptions =
    new ArrayList<ExceptionItem>();
  
  private ArrayList<Attribute> _attributes = new ArrayList<Attribute>();

  public CodeAttribute()
  {
    super("Code");
  }

  public CodeAttribute(String name)
  {
    super(name);
  }

  public void setJavaClass(JavaClass jClass)
  {
    _jClass = jClass;
  }

  public JavaClass getJavaClass()
  {
    return _jClass;
  }

  /**
   * Returns the max locals.
   */
  public int getMaxLocals()
  {
    return _maxLocals;
  }

  /**
   * Sets the max locals.
   */
  public void setMaxLocals(int max)
  {
    _maxLocals = max;
  }

  /**
   * Returns the max stack.
   */
  public int getMaxStack()
  {
    return _maxStack;
  }

  /**
   * Sets the max stack.
   */
  public void setMaxStack(int max)
  {
    _maxStack = max;
  }

  /**
   * Sets the code value.
   */
  public void setCode(byte []code)
  {
    _code = code;
  }

  /**
   * Gets the code value.
   */
  public byte []getCode()
  {
    return _code;
  }

  /**
   * Adds an attribute.
   */
  public void addAttribute(Attribute attr)
  {
    _attributes.add(attr);
  }

  /**
   * Returns the exceptions.
   */
  public ArrayList<Attribute> getAttributes()
  {
    return _attributes;
  }

  /**
   * Returns the exceptions.
   */
  public void setAttributes(ArrayList<Attribute> attributes)
  {
    if (_attributes != attributes) {
      _attributes.clear();
      _attributes.addAll(attributes);
    }
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
   * Returns the exceptions.
   */
  public ArrayList<ExceptionItem> getExceptions()
  {
    return _exceptions;
  }

  /**
   * Returns the exceptions.
   */
  public void addException(ClassConstant type, int start, int end, int handler)
  {
    _exceptions.add(new ExceptionItem(type.getIndex(), start, end, handler));
  }

  /**
   * Writes the field to the output.
   */
  public void read(ByteCodeParser in)
    throws IOException
  {
    int length = in.readInt();
    
    _maxStack = in.readShort();
    _maxLocals = in.readShort();

    int codeLength = in.readInt();

    _code = new byte[codeLength];
    in.read(_code, 0, codeLength);

    int exnCount = in.readShort();

    for (int i = 0; i < exnCount; i++) {
      ExceptionItem exn = new ExceptionItem();

      exn.setStart(in.readShort() & 0xffff);
      exn.setEnd(in.readShort() & 0xffff);
      exn.setHandler(in.readShort() & 0xffff);
      exn.setType(in.readShort() & 0xffff);

      _exceptions.add(exn);
    }

    int attrCount = in.readShort();

    for (int i = 0; i < attrCount; i++) {
      Attribute attr = in.parseAttribute();

      _attributes.add(attr);
    }
  }

  /**
   * Writes the field to the output.
   */
  public void write(ByteCodeWriter out)
    throws IOException
  {
    out.writeUTF8Const(getName());

    TempOutputStream ts = new TempOutputStream();
    //ts.openWrite();
    //WriteStream ws = new WriteStream(ts);
    ByteCodeWriter o2 = new ByteCodeWriter(ts, out.getJavaClass());
    
    o2.writeShort(_maxStack);
    o2.writeShort(_maxLocals);
    o2.writeInt(_code.length);
    o2.write(_code, 0, _code.length);

    o2.writeShort(_exceptions.size());
    for (int i = 0; i < _exceptions.size(); i++) {
      ExceptionItem exn = _exceptions.get(i);

      o2.writeShort(exn.getStart());
      o2.writeShort(exn.getEnd());
      o2.writeShort(exn.getHandler());
      o2.writeShort(exn.getType());
    }

    o2.writeShort(_attributes.size());
    for (int i = 0; i < _attributes.size(); i++) {
      Attribute attr = _attributes.get(i);

      attr.write(o2);
    }
    
    //ws.close();
    
    out.writeInt(ts.getLength());
    TempBuffer ptr = ts.getHead();

    for (; ptr != null; ptr = ptr.next())
      out.write(ptr.buffer(), 0, ptr.length());

    ts.destroy();
  }

  /**
   * Clones the attribute
   */
  public Attribute export(JavaClass source, JavaClass target)
  {
    ConstantPool cp = target.getConstantPool();

    cp.addUTF8(getName());
    
    CodeAttribute attr = new CodeAttribute(getName());

    attr._maxStack = _maxStack;
    attr._maxLocals = _maxLocals;

    byte []code = new byte[_code.length];
    System.arraycopy(_code, 0, code, 0, _code.length);
    attr._code = code;

    for (int i = 0; i < _exceptions.size(); i++) {
      ExceptionItem exn = _exceptions.get(i);

      int type = exn.getType();
      
      if (type != 0)
        type = cp.addClass(source.getConstantPool().getClass(type).getName()).getIndex();

      ExceptionItem newExn = new ExceptionItem(type,
                                               exn.getStart(),
                                               exn.getEnd(),
                                               exn.getHandler());
    
      attr._exceptions.add(newExn);
    }

    for (int i = 0; i < _attributes.size(); i++) {
      Attribute codeAttr = _attributes.get(i);

      attr.addAttribute(codeAttr.export(source, target));
    }

    try {
      attr.exportCode(source, target);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return attr;
  }

  /**
   * Exports code.
   */
  public void exportCode(JavaClass source, JavaClass target)
    throws Exception
  {
    ExportAnalyzer analyzer = new ExportAnalyzer(source, target);

    CodeEnhancer visitor = new CodeEnhancer(source, this);

    visitor.analyze(analyzer, false);

    visitor.update();
  }

  public String toString()
  {
    return "CodeAttribute[" + getName() + "]";
  }

  public static class ExceptionItem {
    private int _type;
    
    private int _start;
    private int _end;
    private int _handler;

    public ExceptionItem()
    {
    }

    public ExceptionItem(int type, int start, int end, int handler)
    {
      _type = type;
      _start = start;
      _end = end;
      _handler = handler;
    }

    /**
     * Sets the exception type.
     */
    public void setType(int type)
    {
      _type = type;
    }

    /**
     * Returns the exception type.
     */
    public int getType()
    {
      return _type;
    }

    /**
     * Sets the start PC
     */
    public void setStart(int pc)
    {
      _start = pc;
    }

    /**
     * Gets the start PC
     */
    public int getStart()
    {
      return _start;
    }

    /**
     * Sets the end PC
     */
    public void setEnd(int pc)
    {
      _end = pc;
    }

    /**
     * Gets the end PC
     */
    public int getEnd()
    {
      return _end;
    }

    /**
     * Sets the handler PC
     */
    public void setHandler(int pc)
    {
      _handler = pc;
    }

    /**
     * Gets the handler PC
     */
    public int getHandler()
    {
      return _handler;
    }
  }

  public static class ExportAnalyzer extends Analyzer {
    private JavaClass _source;
    private JavaClass _target;
    
    public ExportAnalyzer(JavaClass source, JavaClass target)
    {
      _source = source;
      _target = target;
    }

    @Override
    public void analyze(CodeVisitor visitor)
      throws Exception
    {
      int op = visitor.getOpcode();
      int index;
      ConstantPool sourcePool = _source.getConstantPool();
      ConstantPool targetPool = _target.getConstantPool();
      ConstantPoolEntry entry;

      switch (op) {
      case CodeVisitor.ANEWARRAY:
      case CodeVisitor.CHECKCAST:
      case CodeVisitor.GETFIELD:
      case CodeVisitor.GETSTATIC:
      case CodeVisitor.INSTANCEOF:
      case CodeVisitor.INVOKEINTERFACE:
      case CodeVisitor.INVOKESPECIAL:
      case CodeVisitor.INVOKESTATIC:
      case CodeVisitor.INVOKEVIRTUAL:
      case CodeVisitor.INVOKEDYNAMIC:
      case CodeVisitor.LDC_W:
      case CodeVisitor.LDC2_W:
      case CodeVisitor.MULTIANEWARRAY:
      case CodeVisitor.NEW:
      case CodeVisitor.PUTFIELD:
      case CodeVisitor.PUTSTATIC:
        index = visitor.getShortArg(1);

        entry = sourcePool.getEntry(index);
        int targetIndex = entry.export(targetPool);

        visitor.setShortArg(1, targetIndex);
        break;

      case CodeVisitor.LDC:
        index = visitor.getByteArg(1);
        entry = sourcePool.getEntry(index);
        index = entry.export(targetPool);
        if (index <= 0xff)
          visitor.setByteArg(1, index);
        else {
          CodeEnhancer enhancer = (CodeEnhancer) visitor;
          enhancer.setByteArg(0, CodeVisitor.LDC_W);
          enhancer.addByte(enhancer.getOffset() + 2, 0);
          enhancer.setShortArg(1, index);
        }
        break;

      default:
        break;
      }
    }
  }
}
