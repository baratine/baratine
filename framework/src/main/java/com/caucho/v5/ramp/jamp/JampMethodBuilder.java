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

package com.caucho.v5.ramp.jamp;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.json.ser.JsonFactory;
import com.caucho.v5.util.L10N;

/**
 * Method introspected using jaxrs
 */
class JampMethodBuilder
{
  private static final L10N L = new L10N(JampMethodBuilder.class);
  private static final Logger log
    = Logger.getLogger(JampMethodBuilder.class.getName());

  private MethodRefAmp _methodRef;
  private JampArg []_params;
  private JampMarshal _varArgsMarshal;

  private Type _genericType;
  private Annotation []_methodAnnotations;
  private JsonFactory _factory;

  JampMethodBuilder(MethodRefAmp method)
  {
    _methodRef = method;

    _genericType = method.getReturnType();
    _methodAnnotations = method.getAnnotations();

    Annotation []methodAnns = method.getAnnotations();

    ParameterAmp []parameters = method.parameters();

    Type varArgType = null;
    Annotation []varArgAnns = null;

    if (method.isVarArgs() && parameters != null) {
      int lenNew = parameters.length - 1;

      varArgType = getVarArgType(parameters[lenNew].type());
      ParameterAmp []paramTypesNew = new ParameterAmp[lenNew];

      for (int i = 0; i < lenNew; i++) {
        paramTypesNew[i] = ParameterAmp.of(varArgType);
      }

      parameters = paramTypesNew;
    }

    String defaultValue = null; // XXX:

    if (parameters != null) {
      _params = new JampArg[parameters.length];

      for (int i = 0; i < parameters.length; i++) {
        Type type = parameters[i].type();

        JampMarshal marshal = JampMarshal.create(type);

        _params[i] = new JampArgQuery(marshal,
                                      defaultValue,
                                      "p" + i);
      }

      if (varArgType != null) {
        JampMarshal marshal = JampMarshal.create(varArgType);

        _varArgsMarshal = marshal;
      }
    }
  }

  private Class<?> getVarArgType(Type type)
  {
    if (type instanceof Class) {
      return ((Class) type).getComponentType();
    }
    else if (type instanceof ParameterizedType) {
      return String.class;
    }
    else {
      return String.class;
    }
  }

  MethodRefAmp getMethod()
  {
    return _methodRef;
  }

  Type getGenericReturnType()
  {
    return _genericType;
  }

  Annotation[] getAnnotations()
  {
    return _methodAnnotations;
  }

  JampArg[] getParams()
  {
    return _params;
  }

  JampMarshal getVarArgMarshal()
  {
    return _varArgsMarshal;
  }

  public JampMethodRest build()
  {
    Annotation []methodAnns = _methodRef.getAnnotations();

    return new JampMethodStandard(this);
  }

  public void setJsonFactory(JsonFactory factory)
  {
    Objects.requireNonNull(factory);

    _factory = factory;
  }

  public JsonFactory getJsonFactory()
  {
    if (_factory != null) {
      return _factory;
    }
    else {
      return new JsonFactory();
    }
  }

  private boolean isJaxrsMethod(Annotation []anns)
  {
    return false;
  }

  private <T> T getAnnotation(Annotation []anns, Class<T> annType)
  {
    if (anns == null) {
      return null;
    }

    for (Annotation ann : anns) {
      if (annType.equals(ann.annotationType())) {
        return (T) ann;
      }
    }

    return null;
  }
}
