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

import io.baratine.inject.Priority;
import io.baratine.web.RequestWeb;
import io.baratine.web.ViewRender;
import io.baratine.web.ViewResolver;

import com.caucho.v5.inject.type.TypeRef;

/**
 * View with associated type meta-data
 */
class ViewRefRender<T> implements ViewRef<T>, ViewResolver<T>
{
  private final ViewRender<T> _view;

  private final Class<T> _type;
  private final int _priority;

  /**
   * Creates the view and analyzes the type
   */
  public ViewRefRender(ViewRender<T> view)
  {
    Objects.requireNonNull(view);

    _view = view;
    _type = typeOf(view);
    _priority = priorityOf(view);
  }

  /**
   * Creates the view and analyzes the type
   */
  public ViewRefRender(ViewRender<T> view, Class<T> type, int priority)
  {
    Objects.requireNonNull(view);

    _view = view;
    _type = type;
    _priority = priority;
  }
  
  private static <T> Class<T> typeOf(ViewRender<T> view)
  {
    TypeRef viewType = TypeRef.of(view.getClass()).to(ViewRender.class);

    TypeRef typeRef = viewType.param(0);

    if (typeRef != null) {
      return (Class) typeRef.rawClass();
    }
    else {
      return (Class) Object.class;
    }
  }
  
  private static int priorityOf(ViewRender<?> view)
  {
    Priority priority = view.getClass().getAnnotation(Priority.class);
    
    if (priority != null) {
      return priority.value();
    }
    else {
      return 0;
    }
  }

  ViewRender <T> view()
  {
    return _view;
  }

  @Override
  public Class<T> type()
  {
    return _type;
  }
  
  @Override
  public int priority()
  {
    return _priority;
  }
  

  @Override
  public ViewResolver<T> resolver()
  {
    return this;
  }

  @Override
  public boolean render(RequestWeb request, T value)
  {
    _view.render(request, value);
    
    return true;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _view + "," + _priority + "]";
  }
}
