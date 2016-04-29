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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;

import io.baratine.web.RequestWeb;
import io.baratine.web.ViewResolver;

/**
 * View with associated type meta-data
 */
class ViewMap extends ClassValue<ViewResolver<?>[]> implements ViewResolver<Object>
{
  private final HashMap<Class<?>,ArrayList<ViewRef<?>>> _viewMap
    = new HashMap<>();
  
  <T> void add(ViewResolver<T> resolver,
               Class<T> type,
               int priority)
  {
    add(new ViewRefResolver<>(resolver, type, priority));
  }
  
  <T> void add(ViewRef<T> viewRef)
  {
    Objects.requireNonNull(viewRef);
    
    Class<T> type = viewRef.type();

    ArrayList<ViewRef<T>> list = (ArrayList) _viewMap.get(type);
    
    if (list == null) {
      list = new ArrayList<>();
      _viewMap.put(type, (ArrayList) list);
    }
    
    list.add(viewRef);
  }

  @Override
  public boolean render(RequestWeb request, Object value)
  {
    Class<?> type = value != null ? value.getClass() : Void.class;
    
    for (ViewResolver resolver : get(type)) {
      if (resolver.render(request, value)) {
        return true;
      }
    }
    
    return false;
  }

  @Override
  protected ViewResolver<?> []computeValue(Class<?> type)
  {
    ArrayList<ViewRef<?>> views = new ArrayList<>();
    
    addViews(views, type);
    
    Collections.sort(views, (x,y)->compareView(x,y,type));

    ViewResolver<?> []resolvers = new ViewResolver[views.size()];
    
    for (int i = 0; i < views.size(); i++) {
      resolvers[i] = views.get(i).resolver();
    }

    return resolvers;
  }
  
  /**
   * sort views.
   * 
   * Higher @Priority is a better match.
   * Closer type match is a better match.
   */
  private int compareView(ViewRef<?> viewA, 
                          ViewRef<?> viewB,
                          Class<?> type)
  {
    int cmp = viewB.priority() - viewA.priority();
    
    if (cmp != 0) {
      return cmp;
    }
    
    cmp = typeDepth(viewA.type(), type) - typeDepth(viewB.type(), type);

    if (cmp != 0) {
      return cmp;
    }
    
    // equivalent views are sorted by name to ensure consistency
    
    String nameA = viewA.resolver().getClass().getName();
    String nameB = viewB.resolver().getClass().getName();
    
    return nameA.compareTo(nameB); 
  }
  
  /**
   * count of how closely the source matches the target.
   */
  private int typeDepth(Class<?> match, Class<?> actual)
  {
    if (actual == null) {
      return Integer.MAX_VALUE / 2;
    }
    
    if (match.equals(Object.class)) {
      return Integer.MAX_VALUE / 4;
    }
    
    if (match.equals(actual)) {
      return 0;
    }
    
    int cost = 1 + typeDepth(match, actual.getSuperclass());
    
    for (Class<?> iface : actual.getInterfaces()) {
      cost = Math.min(cost, 1 + typeDepth(match, iface));
    }
    
    return cost;
  }
  
  private void addViews(ArrayList<ViewRef<?>> views, Class<?> type)
  {
    if (type == null) {
      return;
    }
    
    addViews(views, type.getSuperclass());
    for (Class<?> iface : type.getInterfaces()) {
      addViews(views, iface);
    }
    
    ArrayList<ViewRef<?>> viewsType = _viewMap.get(type);
    
    if (viewsType != null) {
      for (ViewRef<?> view : viewsType) {
        if (! views.contains(view)) {
          views.add(view);
        }
      }
    }
  }
}
