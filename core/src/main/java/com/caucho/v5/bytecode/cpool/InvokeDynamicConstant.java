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

package com.caucho.v5.bytecode.cpool;

import java.io.IOException;
import java.util.logging.Logger;

import com.caucho.v5.bytecode.ByteCodeWriter;
import com.caucho.v5.bytecode.attr.BootstrapMethodAttribute;

/**
 * Represents a field ref in the constant pool.
 */
public class InvokeDynamicConstant extends ConstantPoolEntry {
  private final BootstrapMethodAttribute _attr;
  private int _bootstrapMethodAttrIndex;
  private int _nameAndTypeIndex;

  /**
   * Creates a new invokedynamic ref constant.
   */
  InvokeDynamicConstant(ConstantPool pool,
                        int index,
                        BootstrapMethodAttribute attr,
                        int bootstrapMethodAttrIndex,
                        int nameAndTypeIndex)
  {
    super(pool, index);
    
    _attr = attr;

    _bootstrapMethodAttrIndex = bootstrapMethodAttrIndex;
    _nameAndTypeIndex = nameAndTypeIndex;
  }

  /**
   * Returns the class index.
   */
  public int getMethodIndex()
  {
    return _bootstrapMethodAttrIndex;
  }

  /**
   * Sets the class index.
   */
  public void setBootstrapMethodAttrIndex(int index)
  {
    _bootstrapMethodAttrIndex = index;
  }

  /**
   * Returns the field name
   */
  public String getName()
  {
    return getConstantPool().getNameAndType(_nameAndTypeIndex).getName();
  }

  /**
   * Returns the method type
   */
  public String getType()
  {
    return getConstantPool().getNameAndType(_nameAndTypeIndex).getType();
  }

  /**
   * Writes the contents of the pool entry.
   */
  @Override
  void write(ByteCodeWriter out)
    throws IOException
  {
    out.write(ConstantPool.CP_INVOKEDYNAMIC);
    out.writeShort(_bootstrapMethodAttrIndex);
    out.writeShort(_nameAndTypeIndex);
  }

  /**
   * Sets the method name and type
   */
  public void setNameAndType(String name, String type)
  {
    _nameAndTypeIndex = getConstantPool().addNameAndType(name, type).getIndex();
  }

  /**
   * Exports to the target pool.
   */
  @Override
  public int export(ConstantPool target)
  {
    // return target.addFieldRef(getClassName(), getName(), getType()).getIndex();
    
    return -1;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getMethodIndex() + "." + getName() + "(" + getType() + ")]";
  }
}
