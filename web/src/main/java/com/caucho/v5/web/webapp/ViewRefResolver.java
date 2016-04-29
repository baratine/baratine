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

package com.caucho.v5.web.webapp;

import java.util.Objects;

import io.baratine.web.ViewResolver;

/**
 * View with associated type meta-data
 */
class ViewRefResolver<T> implements ViewRef<T>
{
  private final ViewResolver<? super T> _resolver;
  private final Class<T> _type;
  private final int _priority;

  /**
   * Creates the view and analyzes the type
   */
  public ViewRefResolver(ViewResolver<? super T> resolver,
                         Class<T> type,
                         int priority)
  {
    Objects.requireNonNull(resolver);
    Objects.requireNonNull(type);

    _resolver = resolver;
    _type = type;
    _priority = priority;
  }

  @Override
  public Class<T> type()
  {
    return _type;
  }

  @Override
  public ViewResolver<? super T> resolver()
  {
    return _resolver;
  }
  
  @Override
  public int priority()
  {
    return _priority;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _resolver + "," + _priority + "]";
  }
}
