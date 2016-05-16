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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.caucho.v5.io.MultipartStream;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.Utf8Util;
import io.baratine.web.Form;
import io.baratine.web.Part;

/**
 * Reads a body
 */
public class BodyResolverBase implements BodyResolver
{
  private static final L10N L = new L10N(BodyResolverBase.class);

  public static final String FORM_TYPE = "application/x-www-form-urlencoded";

  @Override
  public <T> T body(RequestWebSpi request, Class<T> type)
  {
    if (InputStream.class.equals(type)) {
      InputStream is = request.inputStream(); // new TempInputStream(_bodyHead);

      return (T) is;
    }
    else if (String.class.equals(type)) {
      InputStream is = request.inputStream();

      try {
        return (T) Utf8Util.readString(is);
      } catch (IOException e) {
        throw new BodyException(e);
      }
    }
    else if (Reader.class.equals(type)) {
      InputStream is = request.inputStream();

      try {
        return (T) new InputStreamReader(is, "utf-8");
      } catch (IOException e) {
        throw new BodyException(e);
      }
    }
    else if (Form.class.equals(type)) {
      String contentType = request.header("content-type");

      //TODO: parse and use the encoding of the content type e.g. application/x-www-form-urlencoded; UTF-8
      if (contentType == null || !contentType.startsWith(FORM_TYPE)) {
        throw new IllegalStateException(L.l("Form expects {0}", FORM_TYPE));
      }

      return (T) parseForm(request);
    }
    else if (Part.class.equals(type) || Part[].class.equals(type)) {
      return (T) parseMultipart(request);
    }
    /*
    else if (header("content-type").startsWith("application/json")) {
      TempInputStream is = new TempInputStream(_bodyHead);
      _bodyHead = _bodyTail = null;

      try {
        Reader reader = new InputStreamReader(is, "utf-8");

        JsonReader isJson = new JsonReader(reader);
        return (X) isJson.readObject(type);
      } catch (IOException e) {
        throw new BodyException(e);
      }
    }
    */

    return bodyDefault(request, type);
  }

  public <T> T bodyDefault(RequestWebSpi request, Class<T> type)
  {
    String contentType = request.header("content-type");

    //TODO: parse and use the encoding of the content type e.g. application/x-www-form-urlencoded; UTF-8
    if (contentType.startsWith(FORM_TYPE)) {
      Form form = parseForm(request);

      return (T) formToBody(form, type);
    }

    throw new IllegalStateException(L.l("Unknown body type: " + type));
  }

  private <T> T formToBody(Form form, Class<T> type)
  {
    try {
      if (Map.class.isAssignableFrom(type)) {
        return formToMap(form, type);
      }
      if (type.isAssignableFrom(form.getClass())) {
        return (T) form;
      }

      T bean = (T) type.newInstance();

      for (Field field : type.getDeclaredFields()) {
        String name = field.getName();

        String value = form.first(name);

        if (value == null && name.startsWith("_")) {
          value = form.first(name.substring(1));
        }

        if (value == null) {
          continue;
        }

        // XXX: introspection and conversion

        field.setAccessible(true);

        setFieldValue(bean, field, value);
      }

      return bean;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new BodyException(e);
    }
  }

  private <T> T formToMap(Form form, Class<T> type)
    throws InstantiationException, IllegalAccessException
  {
    Map<String,String> map;

    if (Modifier.isAbstract(type.getModifiers())) {
      map = new HashMap<>();
    }
    else {
      map = (Map) type.newInstance();
    }

    for (Map.Entry<String,List<String>> entry : form.entrySet()) {
      map.put(entry.getKey(), entry.getValue().get(0));
    }

    return (T) map;
  }

  private <T> T formToParam(Form form, Class<T> type, String param)
  {
    Object result = null;

    String str = form.first(param);

    if (type == String.class) {
      result = str;
    }
    else if (type == boolean.class) {
      result = transform(str, false, Boolean::parseBoolean);
    }
    else if (type == Boolean.class) {
      result = transform(str, null, v -> Boolean.parseBoolean(v));
    }
    else if (type == byte.class) {
      result = transform(str, (byte) 0, v -> Byte.parseByte(v));
    }
    else if (type == Byte.class) {
      result = transform(str, null, v -> Byte.parseByte(v));
    }
    else if (type == short.class) {
      result = transform(str, (short) 0, v -> Short.parseShort(v));
    }
    else if (type == Short.class) {
      result = transform(str, null, v -> Short.parseShort(v));
    }
    else if (type == char.class) {
      result = transform(str,
                         (char) 0,
                         v -> str.length() > 0 ? str.charAt(0) : (char) 0);
    }
    else if (type == Character.class) {
      result = transform(str,
                         null,
                         v -> str.length() > 0 ? str.charAt(0) : null);
    }
    else if (type == int.class) {
      result = transform(str, 0, v -> Integer.parseInt(v));
    }
    else if (type == Integer.class) {
      result = transform(str, null, v -> Integer.parseInt(v));
    }
    else if (type == long.class) {
      result = transform(str, 0L, v -> Long.parseLong(v));
    }
    else if (type == Long.class) {
      result = transform(str, null, v -> Long.parseLong(v));
    }
    else if (type == float.class) {
      result = transform(str, (float) 0, v -> Float.parseFloat(v));
    }
    else if (type == Float.class) {
      result = transform(str, null, v -> Float.parseFloat(v));
    }
    else if (type == double.class) {
      result = transform(str, 0.0, v -> Double.parseDouble(v));
    }
    else if (type == Double.class) {
      result = transform(str, null, v -> Double.parseDouble(v));
    }

    return (T) result;
  }

  public static <T> Object transform(String str,
                                     Object defaultV,
                                     Function<String,T> fun)
  {
    return str != null ? fun.apply(str) : defaultV;

    /*
    if (str != null) {
      try {
        return fun.apply(str);
      }
      catch (Exception e) {
      }
    }

    return defaultV;
    */
  }

  private void setFieldValue(Object bean, Field field, String rawValue)
    throws IllegalAccessException
  {
    Class fieldType = field.getType();
    Object value = rawValue;

    if (fieldType == String.class) {

    }
    else if (boolean.class == fieldType || Boolean.class == fieldType) {
      value = Boolean.parseBoolean(rawValue);
    }
    else if (byte.class == fieldType || Byte.class == fieldType) {
      value = Byte.parseByte(rawValue);
    }
    else if (char.class == fieldType || Character.class == fieldType) {
      value = rawValue.charAt(0);
    }
    else if (short.class == fieldType || Short.class == fieldType) {
      value = Short.parseShort(rawValue);
    }
    else if (int.class == fieldType || Integer.class == fieldType) {
      value = Integer.parseInt(rawValue);
    }
    else if (long.class == fieldType || Long.class == fieldType) {
      value = Long.parseLong(rawValue);
    }
    else if (float.class == fieldType || Float.class == fieldType) {
      value = Float.parseFloat(rawValue);
    }
    else if (double.class == fieldType || Double.class == fieldType) {
      value = Double.parseDouble(rawValue);
    }
    else {
      throw new IllegalStateException(
        L.l("can't parse value to type {0}", field.getType()));
    }

    field.set(bean, value);
  }

  private Form parseForm(RequestWebSpi request)
  {
    /*
    Form form = request.getForm();

    if (form != null)
      return form;
      */

    InputStream is = request.inputStream();

    try {
      FormImpl form = new FormImpl();

      FormBaratine.parseQueryString((FormImpl) form, is, "utf-8");

      //((RequestBaratineImpl)request).setForm((FormImpl) form);

      return form;
    } catch (Exception e) {
      throw new BodyException(e);
    }
  }

  private Part[] parseMultipart(RequestWebSpi request)
  {
    try {
      List<Part> parts = new ArrayList<>();

      InputStream in = request.inputStream();

      String boundary
        = MultipartStream.parseBoundary(request.header("Content-Type"));

      MultipartStream parser = new MultipartStream(new ReadStream(in),
                                                   boundary);

      ReadStream stream;
      while ((stream = parser.openRead()) != null) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WriteStream writer = new WriteStream(out);
        writer.writeStream(stream);
        writer.close();

        final MultipartStream.Attribute[] attributes
          = parser.parseAttribute("Content-Disposition");

        PartImpl part = new PartImpl();

        fillAttributes(part, attributes);

        // FIXME: 2016-05-16 should be temp file
        part.setData(out.toByteArray());
        part.setHeaders(parser.getHeaders());

        parts.add(part);
      }

      return parts.toArray(new Part[parts.size()]);
    } catch (Throwable e) {
      // FIXME what's proper exception handling?
      throw new RuntimeException(e);
    }
  }

  private void fillAttributes(PartImpl part,
                              MultipartStream.Attribute[] attributes)
  {
    for (MultipartStream.Attribute attribute : attributes) {
      String name = attribute.getName();
      if ("name".equals(name))
        part.setName(attribute.getValue());
      else if ("filename".equals(name))
        part.setFileName(attribute.getValue());
    }
  }
}
