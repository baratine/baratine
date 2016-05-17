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

package com.caucho.v5.inject;

import javax.inject.Named;

/**
 * Utility class for creating @Name annotations, used for generic
 * resources like DataSources.
 */
public class Names extends AnnotationLiteral<Named> implements Named {
  private final String _name;
  
  public Names()
  {
    _name = "";
  }
  
  public Names(String name)
  {
    _name = name;
  }
  
  public static Named of(String name)
  {
    return new Names(name);
  }

  @Override
  public String value()
  { 
    return _name;
  }

  @Override
  public String toString()
  {
    return "@" + Named.class.getSimpleName() + "('" + _name + "')";
  }
}
