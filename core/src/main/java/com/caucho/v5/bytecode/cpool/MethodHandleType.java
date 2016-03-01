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

/**
 * Represents a class in the constant pool.
 */
public enum MethodHandleType 
{
  GET_FIELD {
    public int getCode() { return 1; }
  },
  GET_STATIC {
    public int getCode() { return 2; }
  },
  INVOKE_VIRTUAL {
    public int getCode() { return 5; }
  },
  INVOKE_STATIC {
    public int getCode() { return 6; }
  },
  INVOKE_SPECIAL {
    public int getCode() { return 7; }
  },
  NEW_INVOKE_SPECIAL {
    public int getCode() { return 8; }
  },
  INVOKE_INTERFACE {
    public int getCode() { return 9; }
  }
  ;
  
  public int getCode() { throw new UnsupportedOperationException(toString()); }
}
