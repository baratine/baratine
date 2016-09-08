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

/**
 * Interface {@code View} along with {@code ViewResolver} provide support for
 * custom rendering of content.
 * <p>
 * View is a named map, that should be used as a container for data to be rendered.
 * <p>
 * Name of the view allows ViewResolver to choose appropriate rendering strategy,
 * e.g. a template file.
 * <p>
 * E.g.
 * <blockquote><pre>
 *   &#64;Get
 *   public void indexHtml(RequestWeb request) {
 *     View view = View.newView("index.velocity");
 *     view.put("greeting", "Hello");
 *     //
 *     request.ok(view);
 *   }
 *   ...
 *   public class VelocityViewResolver implements ViewResolver&lt;View&gt;{
 *     public boolean render(RequestWeb request, View view) {
 *       //call to velocity to render the request
 *       return true;
 *     }
 *   }
 * </pre></blockquote>
 * <p>
 *
 * @see ViewResolver
 */
public interface View
{
  /**
   * View name
   *
   * @return name of the view, e.g. associated template to use for rendering.
   */
  String name();

  /**
   * Map of Objects for rendering
   *
   * @return data to render
   */
  Map<String,Object> map();

  /**
   * Data Object from a map
   *
   * @param key key of Object in the map
   * @return
   */
  Object get(String key);

  /**
   * Data Object form a map with key calculated as {@code type.getSimpleName()}
   *
   * @param type key
   * @param <X>  type of the resulting object
   * @return value matching key
   */
  <X> X get(Class<X> type);

  /**
   * Returns a single value set with a {@code ViewBuilder.set(Object)} method.
   *
   * @return single value
   * @see ViewBuilder#set(Object)
   */
  Object get();

  /**
   * Creates a named instance of a ViewBuilder
   *
   * @param name name of a View
   * @return named instance of a ViewBuilder
   */
  static ViewBuilder newView(String name)
  {
    return new ViewImpl(name);
  }

  /**
   * Interface ViewBuilder helps build a view by providing methods that set
   * view's values.
   */
  interface ViewBuilder extends View
  {
    /**
     * Adds value to the View
     *
     * @param key   value key
     * @param value value
     * @return this instance of the ViewBuilder for call chaining
     */
    ViewBuilder add(String key, Object value);

    /**
     * Adds value using key calculated as value.getClass().getSimpleName()
     *
     * @param value value to add to the view
     * @param <X>   type of the value
     * @return this instance of the ViewBuilder for call chaining
     */
    <X> ViewBuilder add(X value);

    /**
     * Sets single value as view's main data object.
     *
     * @param value main data object
     * @return this instance of the ViewBuilder for call chaining
     */
    ViewBuilder set(Object value);
  }
}
