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

package com.caucho.v5.util;

import java.util.logging.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Represents an internet network mask.
 */
public class InetNetwork {
  private static final Logger log
    = Logger.getLogger(InetNetwork.class.getName());
  
  private InetAddress _inetAddress;
  private byte []_address = new byte[8];
  
  private int _subnetBits;
  
  private int _subnetByte;
  private int _subnetMask;

  /**
   * Create a internet mask.
   *
   * @param inetAddress the main address
   * @param maskIndex the number of bits to match.
   */
  public InetNetwork(InetAddress inetAddress, int subnetBits)
  {
    _inetAddress = inetAddress;
    _address = inetAddress.getAddress();
    
    if (subnetBits < 0)
      subnetBits = 8 * _address.length;
    
    _subnetBits = subnetBits;
    
    _subnetByte = subnetBits / 8;
    _subnetMask = ~((1 << (8 - subnetBits % 8)) - 1) & 0xff;
  }

  public static InetNetwork valueOf(String network)
    throws UnknownHostException
  {
    return create(network);
  }
  
  public static InetNetwork create(String network)
    throws UnknownHostException
  {
    if (network == null)
      return null;
    
    int subnetBits = -1;
    
    int p = network.indexOf('/');
    
    if (p > 0) {
      String subnet = network.substring(p + 1);
      subnetBits = Integer.parseInt(subnet);
      network = network.substring(0, p);
    }

    InetAddress inetAddress = InetAddress.getByName(network);
    
    return new InetNetwork(inetAddress, subnetBits);
  }

  /**
   * Returns true if the address is in this network.
   */
  public boolean isMatch(InetAddress inetAddress)
  {
    byte []bytes = inetAddress.getAddress();

    if (bytes.length != _address.length)
      return false;

    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == _address[i]) {
      }
      else if (_subnetByte < i) {
        return true;
      }
      else if (i == _subnetByte) {
        return (bytes[i] & _subnetMask) == (_address[i] & _subnetMask);
      }
      else {
        return false;
      }
    }

    return true;
  }

  /**
   * Returns true if the address is in this network.
   */
  public boolean isMatch(String address)
  {
    try {
      return isMatch(InetAddress.getByName(address));
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);

      return false;
    }
  }

  /**
   * Return a readable string.
   */
  @Override
  public String toString()
  {
    return _inetAddress + "/" + _subnetBits;
  }
}
