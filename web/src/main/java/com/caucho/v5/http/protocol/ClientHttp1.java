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

package com.caucho.v5.http.protocol;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.caucho.v5.http.protocol2.InputStreamClient;
import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.network.ssl.OpenSSLClientFactory;
import com.caucho.v5.util.L10N;

/**
 * InputStreamHttp reads a single HTTP frame.
 */
public class ClientHttp1 implements AutoCloseable
{
  private static final L10N L = new L10N(ClientHttp1.class);
  
  private Path _logPathIn;
  private Path _logPathOut;
  
  private SocketBar _socket;
  private ReadBuffer _is;
  private WriteBuffer _os;
  private long _socketTimeout;
  private int _window;
  
  private final AtomicBoolean _isCloseRead = new AtomicBoolean();

  private String _url;
  
  public ClientHttp1(String url)
    throws IOException
  {
    this();
    
    connect(url);
  }
  
  public ClientHttp1()
  {
  }
  
  public void setLogOut(Path logOutPath)
  {
    _logPathOut = logOutPath;
  }
  
  public void setLogIn(Path logInPath)
  {
    _logPathIn = logInPath;
  }
  
  public void setSocketTimeout(long ms)
    throws IOException
  {
    _socketTimeout = ms;
    
    if (_socket != null) {
      _socket.setSoTimeout((int) ms);
    }
  }
  
  public void setWindow(int size)
  {
    _window = size;
  }
  
  public void connect(String url)
    throws IOException
  {
    try {
      Objects.requireNonNull(url);
      
      URI uri = new URI(url);
    
      String host = uri.getHost();
      int port = uri.getPort();
      
      _url = url;
      
      SocketSystem system = SocketSystem.current();
    
      SocketBar socket;
      
      switch (uri.getScheme()) {
      case "http":
        socket = system.connect(host, port);
        break;
        
      case "https":
        OpenSSLClientFactory factory = new OpenSSLClientFactory();
        factory.setOfferedProtocols("h2-12", "http/1.1", "http/1.0");
        factory.setServerName(uri.getAuthority());
        
        socket = factory.connect(host, port);
        break;
        
      default:
        throw new IllegalArgumentException(L.l("'{0}' is an unknown scheme for http",
                                               uri.getScheme()));
      }
    
      connect(socket);
    } catch (RuntimeException | IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }
  
  public void connect(SocketBar socket)
    throws IOException
  {
    _socket = socket;
    
    if (_socketTimeout > 0) {
      socket.setSoTimeout((int) _socketTimeout);
    }
    
    socket.setTcpNoDelay(true);
    
    StreamImpl sockStream = _socket.getStream();
    
    StreamImpl inStream;
    StreamImpl outStream;
    
    /*
    if (_logPathIn != null) {
      StreamImpl logIn = _logPathIn.openWriteImpl();
      
      inStream = new StreamImplTee(sockStream, logIn);
    }
    else {
      inStream = sockStream;
    }
    */
    inStream = sockStream;

    /*
    if (_logPathOut != null) {
      StreamImpl logOut = _logPathOut.openWriteImpl();
      
      outStream = new StreamImplTee(sockStream, logOut);
    }
    else {
      outStream = sockStream;
    }
    */
    outStream = sockStream;
    
    ReadBuffer is = new ReadBuffer(inStream);
    WriteBuffer os = new WriteBuffer(outStream);
    
    init(is, os);
  }
  
  public void init(ReadBuffer is, WriteBuffer os)
    throws IOException
  {
    _is = is;
    _os = os;
  }
  
  public InputStreamClient get(String path)
    throws IOException
  {
    if (_socket == null) {
      throw new IllegalStateException(L.l("No connection available"));
    }

    ClientStream1 stream = open(path);
    
    stream.method("GET");
    
    return stream.getInputStream();
  }
  
  public ClientStream1 open(String path)
  {
    if (_socket == null) {
      throw new IllegalStateException(L.l("No connection available"));
    }
    
    return new ClientStream1(this, path);
  }

  public String getProtocol()
  {
    return "http/1.1";
  }

  WriteBuffer getOut()
  {
    return _os;
  }

  ReadBuffer getIn()
  {
    return _is;
  }
  
  private void waitForReadClose()
  {
    synchronized (_isCloseRead) {
      if (! _isCloseRead.get()) {
        try {
          _isCloseRead.wait(60000);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _url + "]";
  }
  
  @Override
  public void close()
    throws IOException
  {
    //if (_inHttp.onPeerClose() > 0) {
    //waitForReadClose();
    //}
    // _inHttp.close();
    
    _os.close();
    _is.close();
    
    _socket.close();
  }
  
  public class StreamBuilder {
    private ClientHttp1 _client;
    private String _path;
    private HashMap<String,String> _headers;
    
    StreamBuilder(ClientHttp1 client, String path)
    {
      Objects.requireNonNull(path);
      
      _path = path;
      _client = client;
      
    }
    
    public void setHeader(String key, String value)
    {
      if (_headers == null) {
        _headers = new HashMap<>();
      }
      
      _headers.put(key, value);
    }

    /*
    public InputStreamClient request()
    {
      ServiceFuture<InputStreamClient> future = new ServiceFuture<>();
      
      InRequestClient request = new InRequestClient(_client, future);
      
      FlagsHttp flags = FlagsHttp.END_STREAM;
      
      MessageRequestClientHttp2 msg
        = new MessageRequestClientHttp2("GET", "localhost", _path, _headers, request, flags);
      
      _outHttp.offer(msg);
      
      return future.get(10, TimeUnit.SECONDS);
    }
    */
  }

  public boolean isActive()
  {
    return true;
  }
}
