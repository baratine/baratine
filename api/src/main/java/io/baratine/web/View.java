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

package io.baratine.web;

import java.util.Map;

public interface View
{
  String view();
  
  Map<String,Object> map();
  
  Object get(String key);
  
  <X> X get(Class<X> type);
  
  Object get();
  
  static ViewBuilder newView(String name)
  {
    return new ViewImpl(name);
  }
  
  public interface ViewBuilder extends View
  {
    ViewBuilder add(String key, Object value);
    
    <X> ViewBuilder add(X value);
    
    ViewBuilder set(Object value);
  }
}
