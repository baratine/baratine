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

package com.caucho.v5.web.builder;

import com.caucho.v5.amp.service.ValidatorService;
import com.caucho.v5.util.L10N;
import io.baratine.inject.Injector.IncludeInject;
import io.baratine.service.Service;
import io.baratine.vault.Vault;
import io.baratine.web.Delete;
import io.baratine.web.Get;
import io.baratine.web.IncludeWeb;
import io.baratine.web.Options;
import io.baratine.web.Post;
import io.baratine.web.Put;
import io.baratine.web.Route;
import io.baratine.web.ServiceWebSocket;
import io.baratine.web.Trace;

import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Validation of the configuration
 */
class WebServerValidator
{
  private static final L10N L = new L10N(WebServerValidator.class);

  private static final ArrayList<Class<?>> _includeActiveInterfaces
    = new ArrayList<>();

  private static final HashSet<Class<?>> _includeClassAnnotations
    = new HashSet<>();

  private static final HashSet<Class<?>> _includeMethodAnnotations
    = new HashSet<>();

  private final ValidatorService _validatorService = new ValidatorService();

  /*
  private static final HashSet<Class<?>> _includeMethodMetaAnnotations
    = new HashSet<>();
    */

  /**
   * {@code Web.service(Class)} validation
   */
  public <T> void serviceClass(Class<T> serviceClass)
  {
    Objects.requireNonNull(serviceClass);

    _validatorService.serviceClass(serviceClass);

    if (Vault.class.isAssignableFrom(serviceClass)) {
    }
    else if (serviceClass.isInterface()) {
      throw new IllegalArgumentException(L.l("service class '{0}' is invalid because it's an interface",
                                             serviceClass.getName()));
    }

    if (serviceClass.isMemberClass()
        && ! Modifier.isStatic(serviceClass.getModifiers())) {
      throw new IllegalArgumentException(L.l("service class '{0}' is invalid because it's a non-static inner class",
                                             serviceClass.getName()));
    }

    if (serviceClass.isPrimitive()) {
      throw new IllegalArgumentException(L.l("service class '{0}' is invalid because it's a primitive class",
                                             serviceClass.getName()));
    }

    if (serviceClass.isArray()) {
      throw new IllegalArgumentException(L.l("service class '{0}' is invalid because it's an array",
                                             serviceClass.getName()));
    }

    if (Class.class.equals(serviceClass)) {
      throw new IllegalArgumentException(L.l("service class '{0}' is invalid",
                                             serviceClass.getName()));
    }
  }

  /**
   * {@code Web.include(Class)} class validation
   */
  public <T> void includeClass(Class<T> includeClass)
  {
    Objects.requireNonNull(includeClass);

    validateIncludeClass(includeClass);

    if (includeClass.isAnnotationPresent(Service.class)) {
      serviceClass(includeClass);
    }

    if (! includeIsActive(includeClass)) {
      throw new IllegalArgumentException(L.l("include class '{0}' is invalid because it does not define any beans, services, or paths.",
                                             includeClass.getName()));
    }
  }

  private <T> void validateIncludeClass(Class<T> includeClass)
  {
    if (Vault.class.isAssignableFrom(includeClass)) {
    }
    else if (includeClass.isInterface()) {
      throw new IllegalArgumentException(L.l("include class '{0}' is invalid because it's an interface",
                                             includeClass.getName()));
    }

    if (includeClass.isMemberClass()
        && ! Modifier.isStatic(includeClass.getModifiers())) {
      throw new IllegalArgumentException(L.l("include class '{0}' is invalid because it's a non-static inner class",
                                             includeClass.getName()));
    }

    if (includeClass.isPrimitive()) {
      throw new IllegalArgumentException(L.l("include class '{0}' is invalid because it's a primitive class",
                                             includeClass.getName()));
    }

    if (includeClass.isArray()) {
      throw new IllegalArgumentException(L.l("include class '{0}' is invalid because it's an array",
                                             includeClass.getName()));
    }

    if (Class.class.equals(includeClass)) {
      throw new IllegalArgumentException(L.l("include class '{0}' is invalid",
                                             includeClass.getName()));
    }
  }

  private <T> boolean includeIsActive(Class<T> includeClass)
  {
    for (Class<?> activeIface : _includeActiveInterfaces) {
      if (activeIface.isAssignableFrom(includeClass)) {
        return true;
      }
    }

    if (isActiveAnnotation(includeClass, _includeClassAnnotations)) {
      return true;
    }

    for (Method method : includeClass.getMethods()) {
      if (isActiveAnnotation(method, _includeMethodAnnotations)) {
        return true;
      }
    }

    return false;
  }

  private boolean isActiveAnnotation(Class annotated,
                                     Set<Class<?>> activeAnnTypes)
  {
    Class<?> t = annotated;
    Set<AnnotatedElement> checked = new HashSet<>();

    boolean isActive = false;
    do {
      isActive = isActiveAnnotationRec(t, activeAnnTypes, checked);

      isActive |= isActiveAnnotation(t.getInterfaces(),
                                     activeAnnTypes,
                                     checked);

    } while (! isActive
             && ((t = t.getSuperclass()) != null
             && ! Object.class.equals(t)));

    return isActive;
  }

  private boolean isActiveAnnotation(Class<?>[] interfaces,
                                     Set<Class<?>> activeAnnTypes,
                                     Set<AnnotatedElement> checked)
  {
    boolean isActive = false;

    for (int i = 0; !isActive && i < interfaces.length; i++) {
      Class<?> face = interfaces[i];

      if (checked.contains(face)) {
        continue;
      }

      isActive = isActiveAnnotationRec(face, activeAnnTypes, checked);
    }

    return isActive;
  }

  private boolean isActiveAnnotation(AnnotatedElement annotated,
                                     Set<Class<?>> activeAnnTypes)
  {
    return isActiveAnnotationRec(annotated, activeAnnTypes, new HashSet<>());
  }

  private boolean isActiveAnnotationRec(AnnotatedElement annotated,
                                         Set<Class<?>> activeAnnTypes,
                                         Set<AnnotatedElement> checkedTypes)
  {
    if (annotated == null || checkedTypes.contains(annotated)) {
      return false;
    }

    checkedTypes.add(annotated);

    for (Annotation ann : annotated.getDeclaredAnnotations()) {
      if (activeAnnTypes.contains(ann.annotationType())) {
        return true;
      }

      if (ann.annotationType() != annotated
          && isActiveAnnotationRec(ann.annotationType(),
                                   activeAnnTypes,
                                   checkedTypes)) {
        return true;
      }
    }

    return false;
  }

  static {
    _includeActiveInterfaces.add(IncludeInject.class);
    //_includeActiveInterfaces.add(IncludeService.class);
    _includeActiveInterfaces.add(IncludeWeb.class);

    _includeActiveInterfaces.add(ServiceWebSocket.class);

    _includeClassAnnotations.add(Service.class);

    _includeMethodAnnotations.add(Service.class);

    _includeMethodAnnotations.add(Delete.class);
    _includeMethodAnnotations.add(Get.class);
    _includeMethodAnnotations.add(Options.class);
    _includeMethodAnnotations.add(Post.class);
    _includeMethodAnnotations.add(Put.class);
    _includeMethodAnnotations.add(Route.class);
    _includeMethodAnnotations.add(Trace.class);

    // meta annotations
    _includeMethodAnnotations.add(Qualifier.class);

  }
}
