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

import com.caucho.v5.json.io.JsonWriter;
import com.caucho.v5.json.ser.JsonFactory;

import io.baratine.web.RequestWeb;
import io.baratine.web.ViewRender;

/**
 * Default JSON render
 */

public class ViewJsonDefault implements ViewRender<Object>
{
  private JsonFactory _serializer = new JsonFactory();
  private JsonWriter _jOut = _serializer.out();

  @Override
  public boolean render(RequestWeb req, Object value)
  {
    if (value == null
        || value instanceof String
        || value instanceof Character
        || value instanceof Boolean
        || value instanceof Number) {
      return false;
    }

    try (Writer writer = req.writer()) {
      String callback = req.query("callback");

      if (callback != null) {
        req.type("application/javascript");

        req.write(callback);
        req.write("(");
      }
      else {
        req.type("application/json");
      }
      //@TODO add tests
      req.header("Access-Control-Allow-Origin", "*");

      JsonWriter jOut = _jOut;
      jOut.init(writer);
      jOut.write(value);
      jOut.close();
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
      e.printStackTrace();

      req.fail(e);

      return true;
    }
  }
}
