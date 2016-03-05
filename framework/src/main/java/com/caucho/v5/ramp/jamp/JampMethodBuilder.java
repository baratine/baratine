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

import io.baratine.service.MethodRef;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.MethodRefAmp;
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

    Type []paramTypes = method.getParameterTypes();
    Annotation [][]paramAnns = method.getParameterAnnotations();

    Type varArgType = null;
    Annotation []varArgAnns = null;

    if (method.isVarArgs() && paramTypes != null) {
      int lenNew = paramTypes.length - 1;

      varArgType = getVarArgType(paramTypes[lenNew]);
      Type []paramTypesNew = new Type[lenNew];

      for (int i = 0; i < lenNew; i++) {
        paramTypesNew[i] = varArgType;
      }

      paramTypes = paramTypesNew;

      if (paramAnns != null) {
        varArgAnns = paramAnns[lenNew];

        Annotation [][]paramAnnsNew = new Annotation[lenNew][];
        System.arraycopy(paramAnns, 0, paramAnnsNew, 0, lenNew);

        paramAnns = paramAnnsNew;
      }
    }

    String defaultValue = null; // XXX:

    if (paramTypes != null) {
      _params = new JampArg[paramTypes.length];

      if (paramAnns == null) {
        for (int i = 0; i < paramTypes.length; i++) {
          JampMarshal marshal = JampMarshal.create(paramTypes[i]);

          _params[i] = new JampArgQuery(marshal,
                                        defaultValue,
                                        "p" + i);
        }
      }
      else {
        for (int i = 0; i < paramTypes.length; i++) {
          // Annotation []anns = paramAnns[i];

          Type type = paramTypes[i];

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
