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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.inject.Inject;

import com.caucho.v5.json.JsonEngine;
import com.caucho.v5.json.JsonReader;
import com.caucho.v5.json.io.JsonReaderImpl;
import com.caucho.v5.util.L10N;

/**
 * Reads a body
 */
public class BodyResolverFramework extends BodyResolverBase
{
  private static final L10N L = new L10N(BodyResolverFramework.class);

  @Inject
  private JsonEngine _jsonEngine;

  @Override
  public <T> T bodyDefault(RequestWebSpi request, Class<T> type)
  {
    String contentType = request.header("content-type");

    if (contentType == null) {
      return super.bodyDefault(request, type);
    }

    int p = contentType.indexOf(';');

    if (p >= 0) {
      contentType = contentType.substring(0, p).trim();
    }

    if (contentType.equals("application/json")) {
      InputStream is = request.inputStream();

      try {
        Reader reader = new InputStreamReader(is, "utf-8");

        JsonReader jsonReader = _jsonEngine.newReader();
        jsonReader.init(reader);

        return jsonReader.readObject(type);

      } catch (IOException e) {
        throw new BodyException(e);
      }
    }

    return super.bodyDefault(request, type);
  }
}
