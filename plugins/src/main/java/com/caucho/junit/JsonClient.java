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
