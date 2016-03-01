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
import java.util.logging.Logger;

import com.caucho.v5.bytecode.ByteCodeWriter;

/**
 * Represents a method ref in the constant pool.
 */
public class MethodHandleConstant extends ConstantPoolEntry {
  private final MethodHandleType _type;
  private final ConstantPoolEntry _entry;

  /**
   * Creates a new field ref constant.
   */
  public MethodHandleConstant(ConstantPool pool, int index,
                              MethodHandleType type,
                              ConstantPoolEntry entry)
  {
    super(pool, index);

    _type = type;
    _entry = entry;
  }

  /**
   * Returns the class index.
   */
  public MethodHandleType getType()
  {
    return _type;
  }
  
  public ConstantPoolEntry getConstantEntry()
  {
    return _entry;
  }

  /**
   * Writes the contents of the pool entry.
   */
  @Override
  void write(ByteCodeWriter out)
    throws IOException
  {
    out.write(ConstantPool.CP_METHODHANDLE);
    out.write(_type.getCode());
    out.writeShort(_entry.getIndex());
  }

  /**
   * Exports to the target pool.
   */
  @Override
  public int export(ConstantPool target)
  {
    int entryIndex = _entry.export(target);
    
    return target.addMethodHandle(_type, target.getEntry(entryIndex)).getIndex();
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + getType() + "," + getConstantEntry() + "]";
  }
}
