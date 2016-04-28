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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.io.SocketSystem.SocketBarBuilder;
import com.caucho.v5.io.VfsStream;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.json.io.JsonWriter;
import com.caucho.v5.util.L10N;

public class HttpClient implements AutoCloseable
{
  private static final Logger log
    = Logger.getLogger(HttpClient.class.getName());

  private static final L10N L = new L10N(HttpClient.class);

  private SocketSystem _system;

  private String _addressRemote;
  private int _port;
  private InetSocketAddress _inetRemote;

  private String _addressLocal;
  private int _portLocal;
  private InetSocketAddress _inetLocal;

  private WriteStream _out;

  private SocketBar _socket;

  private boolean _isSsl;

  private String[] _sslProtocols;

  public HttpClient(int port)
  {
    _system = SocketSystem.current();

    Objects.requireNonNull(_system);

    port(port);
    ipRemote("127.0.0.1");

    portLocal(10010);
    ipLocal("127.0.0.1");

    _socket = _system.createSocket();

    _out = new WriteStream();
    _out.reuseBuffer(true);
  }

  public void port(int port)
  {
    if (port > 0) {
      _port = port;
    }

    fillAddressRemote();
  }

  public void portLocal(int port)
  {
    _portLocal = port;

    fillAddressLocal();
  }

  public void ipRemote(String ip)
  {
    _addressRemote = ip;

    fillAddressRemote();
  }

  public void ipLocal(String ip)
  {
    _addressLocal = ip;

    fillAddressLocal();
  }

  private void fillAddressRemote()
  {
    try {
      if (_port > 0 && _addressRemote != null) {
        _inetRemote = new InetSocketAddress(_addressRemote, _port);
      }
      else {
        _inetRemote = null;
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void fillAddressLocal()
  {
    try {
      if (_portLocal > 0 && _addressLocal != null) {
        _inetLocal = new InetSocketAddress(_addressLocal, _portLocal);
      }
      else {
        _inetLocal = null;
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void setSecure(boolean isSecure)
  {
    //_conn.setSecure(isSecure);
  }

  public void ssl(boolean isSsl)
  {
    _isSsl = isSsl;
  }

  public void sslProtocol(String... protocols)
  {
    _sslProtocols = protocols;
  }

  public void timeout(int timeout)
    throws IOException
  {
    _socket.setSoTimeout(timeout);
  }

  public Response request(String input, byte[] data) throws IOException
  {
    ByteArrayOutputStream os = new ByteArrayOutputStream();

    _out.init(new VfsStream(os));

    request(input, data, _out);

    _out.close();

/*
    FileOutputStream f = new FileOutputStream("/tmp/junk.dat");
    f.write(os.toByteArray());

    f.close();
*/

    return new Response(new ByteArrayInputStream(os.toByteArray()));
  }

  public void requestToNull(String input) throws Exception
  {
    _out.init(new VfsStream());

    request(input, null, _out);

    _out.close();
  }

  public String requestEnc(String input) throws Exception
  {
    ByteArrayOutputStream os = new ByteArrayOutputStream();

    _out.init(new VfsStream(os));

    request(input, null, _out);

    _out.close();

    byte[] values = os.toByteArray();

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < values.length; i++) {
      int ch = values[i] & 0xff;

      if (ch == '\n' || ch == '\r') {
        sb.append((char) ch);
      }
      else if (ch < 0x20 || ch >= 0x7f) {
        sb.append("\\x").append(Integer.toHexString((ch >> 4) & 0xf))
          .append(Integer.toHexString(ch & 0xf));
      }
      else {
        sb.append((char) ch);
      }
    }

    return sb.toString();
  }

  public void request(String input, byte[] data, WriteStream out)
    throws IOException
  {
    SocketBarBuilder socketBuilder = _system.connect();

    socketBuilder.socket(_socket);

    socketBuilder.address(_inetRemote);

    if (_inetLocal != null) {
      socketBuilder.addressLocal(_inetLocal);
    }
    //socketBuilder.port(_port);
    //socketBuilder.portLocal(_portLocal);

    if (_isSsl) {
      socketBuilder.ssl(true);

      if (_sslProtocols != null) {
        socketBuilder.sslProtocols(_sslProtocols);
      }
    }

    try (SocketBar socket = socketBuilder.get()) {
      socket.setSoTimeout(2000);

      try (ReadStream sIn = socket.getInputStream()) {
        try (WriteStream sOut = socket.getOutputStream()) {
          sOut.print(input);

          if (data != null)
            sOut.write(data);
        }

        out.writeStream(sIn);
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);

      out.println("\n" + e.toString());
    }
  }

  public void postMultipart(String url, Map<String,String> map)
    throws Exception
  {
    String boundaryStr = "-----boundary0";

    StringBuilder sb = new StringBuilder();

    map.forEach((k, v) -> {
      sb.append(boundaryStr + "\r");

      sb.append("Content-Disposition: form-data; name=\"" + k + "\"\r");
      sb.append("\r");
      sb.append(v);
      sb.append("\r");
    });

    String request = "POST "
                     + url
                     + " HTTP/1.0\r"
                     + "Content-Type: multipart/form-data; boundary="
                     + boundaryStr
                     + "\r"
                     + "Content-Length: "
                     + sb.length()
                     + "\r"
                     + "\r"
                     + sb;

    request(request, null);
  }

  public Request post(String url)
  {
    return new Request(this).method("POST").url(url);
  }

  public Request get(String url)
  {
    return new Request(this).method("GET").url(url);
  }

  public void close()
  {
  }

  public void handleExit(Object o)
  {
    close();
  }

  public static class Request
  {
    private HttpClient _tcp;
    private String _method;
    private String _host = "localhost";
    private String _url;
    private byte[] _body;
    private String _type;

    private LinkedHashMap<String,String> _headers
      = new LinkedHashMap<>();

    private LinkedHashMap<String,String> _cookies
      = new LinkedHashMap<>();

    Request(HttpClient tcp)
    {
      _tcp = tcp;
    }

    public Request method(String method)
    {
      Objects.requireNonNull(method);

      _method = method;

      return this;
    }

    public Request url(String url)
    {
      Objects.requireNonNull(url);

      _url = url;

      return this;
    }

    public Request type(String type)
    {
      Objects.requireNonNull(type);

      _type = type;

      return this;
    }

    public Request body(String body)
    {
      Objects.requireNonNull(body);

      _body = body.getBytes();

      return this;
    }

    public void body(Object bean)
    {
      Objects.requireNonNull(bean);

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      try (
        OutputStreamWriter writer = new OutputStreamWriter(buffer);
        JsonWriter jsonWriter = new JsonWriter(writer)) {
        jsonWriter.write(bean);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      _body = buffer.toByteArray();

      _type = "application/json";
    }

    public Request header(String key, String value)
    {
      Objects.requireNonNull(key);
      Objects.requireNonNull(value);

      _headers.put(key, value);

      return this;
    }

    public Request cookie(String key, String value)
    {
      Objects.requireNonNull(key);
      Objects.requireNonNull(value);

      _cookies.put(key, value);

      return this;
    }

    public Request session(String value)
    {
      Objects.requireNonNull(value);

      _cookies.put("JSESSIONID", value);

      return this;
    }

    public Response go() throws IOException
    {
      StringBuilder sb = new StringBuilder();

      sb.append(_method);
      sb.append(" ").append(_url);
      sb.append(" HTTP/1.1\r\n");

      sb.append("Host: " + _host + "\r\n");

      if (_type != null) {
        sb.append("Content-Type: " + _type + "\r\n");
      }

      for (Map.Entry<String,String> entry : _headers.entrySet()) {
        sb.append(entry.getKey() + ": " + entry.getValue() + "\r\n");
      }

      if (_body != null) {
        sb.append("Content-Length: " + _body.length + "\r\n");
      }

      if (_cookies.size() > 0) {
        sb.append("Cookie:");

        for (Map.Entry<String,String> entry : _cookies.entrySet()) {
          sb.append(" " + entry.getKey() + "=" + entry.getValue());
        }

        sb.append("\r\n");
      }

      sb.append("\r\n");

      return _tcp.request(sb.toString(), _body);
    }
  }

  public static class Response
  {
    private InputStream _in;
    private int _status;

    private Map<String,String> _headers;

    public Response(InputStream in)
    {
      _in = in;
    }

    public int status()
    {
      if (_headers == null)
        parseHead();

      return _status;
    }

    public Map<String,String> headers()
    {
      if (_headers == null)
        parseHead();

      return _headers;
    }

    private void parseHead()
    {
      try {
        parseStatusLine();

        parseHeaders();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private void parseHeaders() throws IOException
    {
      if (_headers != null)
        throw new IllegalStateException();

      _headers = new HashMap<>();

      int i;

      while ((i = _in.read()) != '\r' && i > -1) {
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        StringBuilder current = key;

        boolean isKey = true;
        for (; i != '\n' && i > -1; i = _in.read()) {
          switch (i) {
          case ':': {
            if (isKey) {
              isKey = false;
              current = value;
              if ((i = _in.read()) != ' ')
                current.append((char) i);
            }
            break;
          }
          case '\r':
            break;
          default: {
            current.append((char) i);
          }
          }
        }

        _headers.put(key.toString(), value.toString());
      }
      if ((i = _in.read()) != '\n')
        throw new IllegalStateException(L.l(
          "expected 0x0a but encountered {0}",
          String.format("0x%02x", i)));
    }

    private void parseStatusLine() throws IOException
    {
      int i;
      //skip protocol
      for (i = _in.read(); i != ' '; i = _in.read()) ;

      int status = 0;
      for (i = _in.read(); i != ' '; i = _in.read()) {
        status = status * 10 + (i - '0');
      }

      //skip message
      for (i = _in.read(); i != '\n'; i = _in.read()) ;

      _status = status;
    }

    private InputStream getInputStream()
    {
      return _in;
    }

    public Map readMap() throws IOException
    {
      return readObject(Map.class);
    }

    public <T> T readObject(Class<T> type) throws IOException
    {
      if (_headers == null)
        parseHeaders();

      JsonReader reader
        = new JsonReader(new InputStreamReader(getInputStream()));

      return (T) reader.readObject(type);
    }

    public String body() throws IOException
    {
      if (_headers == null)
        parseHeaders();

      StringBuilder builder = new StringBuilder();

      for (int i = _in.read(); i > -1; i = _in.read()) {
        builder.append((char) i);
      }

      return builder.toString();
    }
  }

  public static void main(String[] args) throws IOException
  {
    FileInputStream in = new FileInputStream("/tmp/junk.dat");

    Response r = new Response(in);

    System.out.println("HttpClient2.main " + r.status());

    System.out.println("HttpClient2.main " + r.headers());

    System.out.println("HttpClient2.main " + r.body());

    int i = '\n';

    System.out.println("HttpClient.main " + String.format("0x%02x", i));
  }
}
