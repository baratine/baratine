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

package com.caucho.v5.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.TimedCache;

/**
 * Abstract network system.
 */
abstract public class SocketSystem
{
  private static final L10N L = new L10N(SocketSystem.class);
  private static final Logger log 
    = Logger.getLogger(SocketSystem.class.getName());
  
  private static final EnvironmentLocal<SocketSystem> _localSystem
    = new EnvironmentLocal<SocketSystem>();

  private static final SocketSystem _tcpSystem;
  
  // static address cache for QA
  private static final TimedCache<String,ArrayList<InetAddress>> _staticAddressCache;
  
  private TimedCache<String,ArrayList<InetAddress>> _addressCache;
    
  protected SocketSystem()
  {
    _addressCache = createAddressCache();
    
    Objects.requireNonNull(_addressCache);
  }
  
  protected TimedCache<String,ArrayList<InetAddress>> createAddressCache()
  {
    long timeout = CurrentTime.isTest() ? Integer.MAX_VALUE : 120000;
    
    if (CurrentTime.isTest()) {
      return _staticAddressCache;
    }
    else {
      return new TimedCache<>(128, timeout);
    }
  }
    
  public static void localSystem(SocketSystem value)
  {
    _localSystem.set(value);
  }

  public static SocketSystem current()
  {
    SocketSystem system = _localSystem.get();

    if (system == null) {
      system = _tcpSystem;
    }

    return system;
  }

  public static SocketSystem createSubSystem(String name)
  {
    SocketSystem system = current();

    SocketSystem subSystem = system.createSubSystemImpl(name);

    return subSystem;
  }

  protected SocketSystem createSubSystemImpl(String name)
  {
    return this;
  }
  
  public boolean isJni()
  {
    return false;
  }

  /**
   * Returns the InetAddresses for the local machine.
   */
  public ArrayList<InetAddress> getLocalAddresses()
  {
    synchronized (_addressCache) {
      ArrayList<InetAddress> localAddresses = _addressCache.get("addresses");
      
      if (localAddresses == null) {
        localAddresses = new ArrayList<InetAddress>();
        
        try {
          for (NetworkInterfaceBase iface : getNetworkInterfaces()) {
            for (InetAddress addr : iface.getInetAddresses()) {
              localAddresses.add(addr);
            }
          }
          
          Collections.sort(localAddresses, new LocalIpCompare());
          
          _addressCache.put("addresses", localAddresses);
        } catch (Exception e) {
          log.log(Level.WARNING, e.toString(), e);
        } finally {
          _addressCache.put("addresses", localAddresses);
        }
      }
      
      return new ArrayList<>(localAddresses);
    }
  }

  /**
   * Returns the primary local host IP address.
   */
  public String getHostAddress()
  {
    for (InetAddress address : getLocalAddresses()) {
      return address.getHostAddress();
    }
    
    return "127.0.0.1";
  }
  
  /**
   * Returns a unique identifying byte array for the server, generally
   * the mac address.
   */
  public byte[] getHardwareAddress()
  {
    if (CurrentTime.isTest() || System.getProperty("test.mac") != null) {
      return new byte[] { 10, 0, 0, 0, 0, 10 };
    }
    
    for (NetworkInterfaceBase nic : getNetworkInterfaces()) {
      if (! nic.isLoopback()) {
        return nic.getHardwareAddress();
      }
    }
    
    try {
      InetAddress localHost = InetAddress.getLocalHost();
      
      return localHost.getAddress();
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    return new byte[0];
  }

  public ServerSocketBar openServerSocket(String address, int port)
    throws IOException
  {
    InetAddress inetAddr = InetAddress.getByName(address);

    return openServerSocket(inetAddr, port, 100, true);
  }

  public ServerSocketBar openServerSocket(int port)
    throws IOException
  {
    return openServerSocket(null, port, 100, true);
  }

  public abstract ServerSocketBar openServerSocket(InetAddress address,
                                                 int port,
                                                 int backlog,
                                                 boolean isJni)
    throws IOException;

  public ServerSocketBar openUnixServerSocket(Path unixPath)
    throws IOException
  {
    throw new ConfigException(L.l("unix sockets are not supported"));
  }

  public SocketBar connect(String address, int port)
    throws IOException
  {
    InetAddress inetAddr = InetAddress.getByName(address);

    return connect(inetAddr, port, -1, false);
  }

  public final SocketBar connect(InetAddress address,
                               int port,
                               long timeout)
    throws IOException
  {
    return connect(address, port, timeout, false);
  }

  public final SocketBar connect(InetAddress address,
                               int port,
                               long timeout,
                               boolean isSSL)
    throws IOException
  {
    return connect(null, new InetSocketAddress(address, port), null, timeout, isSSL);
  }

  public SocketBar connect(InetSocketAddress address,
                         long timeout)
    throws IOException
  {
    return connect(null, address, null, timeout, false);
  }

  public SocketBar connect(InetSocketAddress address,
                         long timeout,
                         boolean isSSL)
    throws IOException
  {
    return connect(null, address, null, timeout, isSSL);
  }

  public abstract SocketBar connect(SocketBar socket,
                                    InetSocketAddress addressRemote,
                                    InetSocketAddress addressLocal,
                                    long timeout,
                                    boolean isSSL)
    throws IOException;
  
  public SocketBarBuilder connect()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public final SocketBar connectUnix(Path path)
    throws IOException
  {
    return connectUnix(null, path);
  }

  public SocketBar connectUnix(SocketBar socket, Path path)
    throws IOException
  {
    throw new SocketException(L.l("unix sockets are not supported"));
  }

  public abstract SocketBar createSocket();


  protected ArrayList<NetworkInterfaceBase> getNetworkInterfaces()
  {
    ArrayList<NetworkInterfaceBase> interfaceList = new ArrayList<>();
      
    try {
      Enumeration<NetworkInterface> ifaceEnum
        = NetworkInterface.getNetworkInterfaces();
    
      while (ifaceEnum.hasMoreElements()) {
        NetworkInterface iface = ifaceEnum.nextElement();

        interfaceList.add(new NetworkInterfaceTcp(iface));
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
      
    return interfaceList;
  }
  
  static class LocalIpCompare implements Comparator<InetAddress>
  {
    @Override
    public int compare(InetAddress a, InetAddress b)
    {
      byte []bytesA = a.getAddress();
      byte []bytesB = b.getAddress();
      
      if (bytesA[0] == bytesB[0]) {
      }
      else if (bytesA[0] == 0) {
        return 1;
      }
      else if (bytesB[0] == 0) {
        return -1;
      }
      else if (bytesA[0] == 127) {
        return 1;
      }
      else if (bytesB[0] == 127) {
        return -1;
      }
      
      if (bytesA.length != bytesB.length) {
        return bytesA.length - bytesB.length;
      }
      
      for (int i = 0; i < bytesA.length; i++) {
        if (bytesA[i] != bytesB[i]) {
          return bytesA[i] - bytesB[i];
        }
      }

      return 0;
    }
    
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }

  public interface SocketBarBuilder
  {
    SocketBarBuilder socket(SocketBar socket);
    //SocketBarBuilder address(String address);
    SocketBarBuilder address(InetSocketAddress address);
    //SocketBarBuilder port(int port);
    SocketBarBuilder addressLocal(InetSocketAddress address);
    SocketBarBuilder timeoutConnect(long timeout);
    SocketBarBuilder ssl(boolean isSsl);
    SocketBarBuilder sslProtocols(String ...protocol);
    
    SocketBar get() throws IOException;
  }
    
  static {
    _staticAddressCache = new TimedCache<>(128, Integer.MAX_VALUE);
    _tcpSystem = SocketSystemTcp.create();
  }
}

