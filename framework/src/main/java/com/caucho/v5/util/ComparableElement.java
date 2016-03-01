/**
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
 * @author Fred Zappert (fred@caucho.com)
 */

package com.caucho.v5.util;

/**
 * Defines an interface to that an object can provide a comparison of itself to others of its type,
 * with the comparison returned as an object.
 *
 */
public interface ComparableElement<E> extends Comparable<E>

{
  /**
   * Returns a result that provides a {@link Comparison} of this element
   * with that of the element parameter.
   *
   * @param element to be compared with.
   * @return the Comparison
   */
  public Comparison<E> compareWith(E element);


  /**
   * The recommended implementation of this method is:
   *   <code>public int compareTo(E element)
   *         {
   *           return comparison(element).value();
   *         }
   *   </code>
   */
  @Override
  public int compareTo(E element);

  /**
   * A comparison of the typed element.
   *
   * The interface can be readily implemented by declaring an enum enumerating the comparisons, each
   * with the value.  This permits a switch statement to used the comparison method.  A well designed set of
   * comparisons can provide more effective use of tree-backed maps and sets.
   */
  public interface Comparison<E>
 
  {
    /**
     * The value assigned to this comparison.
     */
    public int value();
  }
}
