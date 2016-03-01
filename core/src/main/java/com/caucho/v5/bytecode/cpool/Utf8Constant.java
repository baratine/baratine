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

/**
 * Represents a utf-8n constant.
 */
public class Utf8Constant extends ConstantPoolEntry {
  static private final Logger log = Logger.getLogger(Utf8Constant.class.getName());

  private String _value;

  /**
   * Creates a new integer constant.
   */
  public Utf8Constant(ConstantPool pool, int index, String value)
  {
    super(pool, index);

    _value = value;
  }

  /**
   * Returns the value;
   */
  public String getValue()
  {
    return _value;
  }

  /**
   * Sets the value;
   */
  public void setValue(String value)
  {
    _value = value;
  }

  /**
   * Writes the contents of the pool entry.
   */
  void write(ByteCodeWriter out)
    throws IOException
  {
    out.write(ConstantPool.CP_UTF8);
    out.writeUTF8(_value);
  }

  /**
   * Exports to the target pool.
   */
  public int export(ConstantPool target)
  {
    return target.addUTF8(_value).getIndex();
  }

  public String toString()
  {
    return "Utf8Constant[" + _value + "]";
  }
}
