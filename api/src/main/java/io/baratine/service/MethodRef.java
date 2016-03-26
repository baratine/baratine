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

package io.baratine.service;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Low level interface to Baratine service methods.
 */
public interface MethodRef
{
  /**
   * Name of the method.
   */
  String getName();
  
  /**
   * Annotations on the method
   */
  //Annotation []getAnnotations();
  
  /**
   * The return type of this method.
   */
  //Type getReturnType();
  
  /** 
   * Types of the method arguments.
   */
  //Type []getParameterTypes();
  
  /**
   * Annotations for each method parameter.
   */
  //Annotation [][]getParameterAnnotations();

  /**
   * True if the final argument is variable-length.
   */
  //boolean isVarArgs();
  
  /**
   * The owning service.
   */
  ServiceRef serviceRef();
  
  /**
   * Call a method without a result value.
   * 
   * @param args arguments for this method invocation
   */
  void send(Object ...args);

  /**
   * Invoke a method with a result.
   * 
   * @param result Callback that handles success and failure cases for the query operation.
   * @param args arguments for this method invocation
   */
  <T> void query(Result<T> result,
                 Object ...args);
}
