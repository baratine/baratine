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

package com.caucho.v5.bytecode.attr;

import java.io.IOException;
import java.util.ArrayList;

import com.caucho.v5.bytecode.ByteCodeParser;
import com.caucho.v5.bytecode.ByteCodeWriter;
import com.caucho.v5.bytecode.JavaClass;
import com.caucho.v5.bytecode.cpool.ConstantPool;
import com.caucho.v5.bytecode.cpool.ConstantPoolEntry;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.TempOutputStream;

/**
 * Represents a generic attribute
 */
public class BootstrapMethodAttribute extends Attribute {
  public static final String NAME = "BootstrapMethods";
  
  private ArrayList<BootstrapMethod> _methods = new ArrayList<BootstrapMethod>();

  public BootstrapMethodAttribute()
  {
    super(NAME);
  }

  public int addMethod(int methodRef,
                       ConstantPoolEntry []entries)
  {
    BootstrapMethod method = findMethod(methodRef, entries);
    
    if (method == null) {
      int index = _methods.size();
      
      method = new BootstrapMethod(index, methodRef, entries);
      
      _methods.add(method);
    }
    
    return method.getIndex();
  }

  public BootstrapMethod findMethod(int methodRef,
                                    ConstantPoolEntry []entries)
  {
    for (BootstrapMethod method : _methods) {
      if (method.getMethodRef() != methodRef) {
        continue;
      }
      
      if (isMatch(method.getArguments(), entries)) {
        return method;
      }
    }
    
    return null;
  }
  
  private boolean isMatch(ConstantPoolEntry []a, ConstantPoolEntry []b)
  {
    int aLength = a != null ? a.length : 0;
    int bLength = b != null ? b.length : 0;
    
    if (aLength != bLength) {
      return false;
    }
    
    for (int i = 0; i < a.length; i++) {
      if (! a[i].equals(b[i])) {
        return false;
      }
    }
    
    return true;
  }
  
  
  /**
   * Returns the exceptions.
   */
  public ArrayList<BootstrapMethod> getMethodList()
  {
    return _methods;
  }

  /**
   * Writes the field to the output.
   */
  public void read(ByteCodeParser in)
    throws IOException
  {
    int length = in.readInt();
    
    int exnCount = in.readShort();

    for (int i = 0; i < exnCount; i++) {
      int index = in.readShort();

      if (index == 0) {
        _methods.add(null);
      }
      
      // _methods.add(in.getConstantPool().getClass(index).getName());
    }
  }

  /**
   * Writes the field to the output.
   */
  @Override
  public void write(ByteCodeWriter out)
    throws IOException
  {
    out.writeUTF8Const(getName());

    TempOutputStream ts = new TempOutputStream();
    //ts.openWrite();
    //WriteStream ws = new WriteStream(ts);
    ByteCodeWriter o2 = new ByteCodeWriter(ts, out.getJavaClass());

    o2.writeShort(_methods.size());
    for (int i = 0; i < _methods.size(); i++) {
      BootstrapMethod method = _methods.get(i);
      
      o2.writeShort(method.getMethodRef());
      o2.writeShort(method.getArgumentSize());
      
      for (ConstantPoolEntry entry : method.getArguments()) {
        o2.writeShort(entry.getIndex());
      }
    }
    
    ts.close();
    
    out.writeInt(ts.getLength());
    
    TempBuffer ptr = ts.getHead();

    for (; ptr != null; ptr = ptr.getNext()) {
      out.write(ptr.buffer(), 0, ptr.length());
    }

    ts.destroy();
  }

  /**
   * Clones the attribute
   */
  @Override
  public Attribute export(JavaClass cl, JavaClass target)
  {
    ConstantPool cp = target.getConstantPool();

    cp.addUTF8(getName());
    
    BootstrapMethodAttribute attr = new BootstrapMethodAttribute();

    /*
    for (int i = 0; i < _exceptions.size(); i++) {
      String exn = _exceptions.get(i);

      cp.addClass(exn);

      attr.addException(exn);
    }
    */

    return attr;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getName() + "]";
  }
  
  public static class BootstrapMethod {
    private final int _index;
    
    private final int _methodRef;
    
    private final ConstantPoolEntry []_arguments;
    
    BootstrapMethod(int index, int methodRef, ConstantPoolEntry []args)
    {
      _index = index;
      
      _methodRef = methodRef;
      _arguments = args;
    }
    
    public int getIndex()
    {
      return _index;
    }
    
    /**
     * CONSTANT_MethodHandle_info
     */
    public int getMethodRef()
    {
      return _methodRef;
    }

    /**
     * @return
     */
    public int getArgumentSize()
    {
      return _arguments.length;
    }
    
    public ConstantPoolEntry []getArguments()
    {
      return _arguments;
    }
  }
}
