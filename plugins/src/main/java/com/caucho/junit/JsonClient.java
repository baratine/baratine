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
 * @author Alex Rojkov
 */

package com.caucho.junit;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import com.caucho.v5.json.io.JsonReader;

public class JsonClient
{
  private String _url;

  public JsonClient(String url)
  {
    _url = url;
  }

  public Response x()
  {
    HttpURLConnection conn = null;
    try {
      conn = (HttpURLConnection) new URL(_url).openConnection();
      conn.setConnectTimeout(10);
      conn.setReadTimeout(100);
      return new Response(conn);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      if (conn != null)
        conn.disconnect();
    }
  }

  public static class Response
  {
    private final HttpURLConnection _conn;

    public Response(HttpURLConnection conn) throws IOException
    {
      _conn = conn;
      _conn.connect();
    }

    public int getResponseCode()
    {
      try {
        return _conn.getResponseCode();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public String getResponseMessage()
    {
      try {
        return _conn.getResponseMessage();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public long getContentLength()
    {
      return _conn.getContentLengthLong();
    }

    public String getContentType()
    {
      return _conn.getContentType();
    }

    public String getHeaderField(String name)
    {
      return _conn.getHeaderField(name);
    }

    public Map readMap()
    {
      return readObject(Map.class);
    }

    public <T> T readObject(Class<T> type)
    {
      try {
        JsonReader reader
          = new JsonReader(new InputStreamReader(_conn.getInputStream()));

        return (T) reader.readObject(type);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
