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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.web.view;

import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import io.baratine.inject.Priority;
import io.baratine.web.RequestWeb;
import io.baratine.web.ViewResolver;

import com.caucho.v5.json.JsonEngine;
import com.caucho.v5.json.JsonWriter;
import com.caucho.v5.json.io.JsonWriterImpl;
import com.caucho.v5.json.ser.JsonFactory;

/**
 * Default JSON render
 */
@Priority(-100)
public class ViewJsonDefault implements ViewResolver<Object>
{
  private static Logger LOG = Logger.getLogger(ViewJsonDefault.class.getName());

  private JsonFactory _serializer = new JsonFactory();
  private JsonWriterImpl _jOut = _serializer.out();

  @Inject
  private JsonEngine _jsonEngine;

  public ViewJsonDefault()
  {
  }

  @Override
  public boolean render(RequestWeb req, Object value)
  {
    /*
    if (value == null
        || value instanceof String
        || value instanceof Character
        || value instanceof Boolean
        || value instanceof Number) {
      return false;
    }
    */
    if (value == null) {
      return false;
    }

    try {
      Writer writer = req.writer();

      String callback = req.query("callback");

      if (callback != null) {
        req.type("application/javascript");

        req.write(callback);
        req.write("(");
      }
      else {
        req.type("application/json");
      }

      // req.header("Access-Control-Allow-Origin", "*");

      JsonWriter jsonWriter = _jsonEngine.newWriter();

      jsonWriter.init(writer);
      jsonWriter.write(value);
      jsonWriter.flush();

      /*
      JsonWriter jOut = _jOut;
      jOut.init(writer);
      jOut.write(value);
      jOut.close();
      */

      /*
      try (JsonWriter jOut = _serializer.out(writer)) {
        jOut.write(value);
      }
      */

      if (callback != null) {
        req.write(");");
      }

      req.ok();

      return true;
    } catch (Exception e) {
      LOG.log(Level.FINER, e.getMessage(), e);

      req.fail(e);

      return true;
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
