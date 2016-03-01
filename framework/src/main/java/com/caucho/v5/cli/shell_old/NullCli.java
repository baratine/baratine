/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.cli.shell_old;

/**
 * Null value type for the command-line
 */
public class NullCli extends ValueCli
{
  public static final NullCli NULL = new NullCli();
  
  private NullCli()
  {
  }
  
  public final TypeCli type()
  {
    return TypeCli.NULL;
  }
  
  @Override
  public Object javaValue()
  {
    return null;
  }
  
  @Override
  public String stringValue()
  {
    return null;
  }

  @Override
  public int compareTo(Object valueObj)
  {
    if (! (valueObj instanceof ValueCli)) {
      return -1;
    }
    
    ValueCli value = (ValueCli) valueObj;
    
    int cmp = type().ordinal() - value.type().ordinal();
    
    if (cmp != 0) {
      return Integer.signum(cmp);
    }
    
    return 0;
  }
}
