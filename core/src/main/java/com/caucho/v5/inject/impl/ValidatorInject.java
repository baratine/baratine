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

package com.caucho.v5.inject.impl;

import java.lang.reflect.Modifier;
import java.util.Objects;

import com.caucho.v5.util.L10N;

/**
 * Validation of the configuration
 */
public class ValidatorInject
{
  private static final L10N L = new L10N(ValidatorInject.class);
  
  /**
   * {@code InjectBuilder.bean(Class)} validation
   */
  public <T> void beanClass(Class<T> beanClass)
  {
    validateBeanClass(beanClass);
    
    if (Modifier.isAbstract(beanClass.getModifiers())
        && ! abstractMethods(beanClass)) {
      throw error(L.l("abstract bean class '{0}' is invalid because the abstract methods can't be generated.",
                      beanClass.getSimpleName()));
    }
  }
  
  private boolean abstractMethods(Class<?> beanClass)
  {
    return false;
  }
  
  private <T> void validateBeanClass(Class<T> serviceClass)
  {
    Objects.requireNonNull(serviceClass);
    
    if (serviceClass.isInterface()) {
      throw new IllegalArgumentException(L.l("bean class '{0}' is invalid because it's an interface",
                                             serviceClass.getSimpleName()));
    }
    
    if (serviceClass.isMemberClass()
        && ! Modifier.isStatic(serviceClass.getModifiers())) {
      throw new IllegalArgumentException(L.l("bean class '{0}' is invalid because it's a non-static inner class",
                                             serviceClass.getSimpleName()));
    }
    
    if (serviceClass.isPrimitive()) {
      throw new IllegalArgumentException(L.l("bean class '{0}' is invalid because it's a primitive class",
                                             serviceClass.getSimpleName()));
    }
    
    if (serviceClass.isArray()) {
      throw new IllegalArgumentException(L.l("bean class '{0}' is invalid because it's an array",
                                             serviceClass.getSimpleName()));
    }
    
    if (Class.class.equals(serviceClass)) {
      throw new IllegalArgumentException(L.l("bean class '{0}' is invalid",
                                             serviceClass.getSimpleName()));
    }
  }
  
  private RuntimeException error(String msg, Object ...args)
  {
    throw new IllegalArgumentException(L.l(msg, args));
  }
}
