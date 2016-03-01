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

package com.caucho.v5.http.security;

import java.io.Serializable;


/**
 * Digest-based credentials
 *
 * The Digest authentication uses the following algorithm
 * to calculate the digest.  The digest is then compared to
 * the client digest.
 *
 * <code><pre>
 * A1 = MD5(username + ':' + realm + ':' + password)
 * digest = MD5(A1 + ':' + nonce)
 * </pre></code>
 */
public class DigestCredentials implements Credentials, Serializable
{
  private String _userName;
  private String _nonce;
  private String _realm;
  
  private String _digest;
  
  public DigestCredentials()
  {
  }
  
  public DigestCredentials(String userName,
                           String nonce,
                           String digest)
  {
    _userName = userName;
    _nonce = nonce;
    _digest = digest;
  }

  public String getUserName()
  {
    return _userName;
  }

  public void setUserName(String userName)
  {
    _userName = userName;
  }

  public String getNonce()
  {
    return _nonce;
  }

  public void setNonce(String nonce)
  {
    _nonce = nonce;
  }

  public String getRealm()
  {
    return _realm;
  }

  public void setRealm(String realm)
  {
    _realm = realm;
  }

  public String getDigest()
  {
    return _digest;
  }

  public void setDigest(String digest)
  {
    _digest = digest;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _userName + "]";
  }
}

