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
import java.util.logging.Logger;

import com.caucho.v5.bytecode.ByteCodeParser;
import com.caucho.v5.bytecode.ByteCodeWriter;
import com.caucho.v5.bytecode.JavaClass;
import com.caucho.v5.bytecode.cpool.ConstantPool;

/**
 * Represents a generic attribute
 */
public class SignatureAttribute extends Attribute {
  private String _signature;

  public SignatureAttribute()
  {
    super("Signature");
  }

  SignatureAttribute(String signature)
  {
    super("Signature");

    _signature = signature;
  }

  /**
   * Returns the signature.
   */
  public String getSignature()
  {
    return _signature;
  }

  /**
   * Reads the signature.
   */
  public void read(ByteCodeParser in)
    throws IOException
  {
    int length = in.readInt();

    if (length != 2)
      throw new IOException("expected length of 2 at " + length);
    
    int code = in.readShort();
    _signature = in.getUTF8(code);
  }

  /**
   * Writes the field to the output.
   */
  public void write(ByteCodeWriter out)
    throws IOException
  {
    out.writeUTF8Const(getName());
    out.writeInt(2);
    out.writeUTF8Const(_signature);
  }

  /**
   * Clones the attribute
   */
  public Attribute export(JavaClass source, JavaClass target)
  {
    ConstantPool cp = target.getConstantPool();

    cp.addUTF8(getName());
    cp.addUTF8(_signature);
    
    return new SignatureAttribute(_signature);
  }

  public String toString()
  {
    return "SignatureAttribute[" + _signature + "]";
  }
}
