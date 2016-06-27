/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.v5.oauth;

import io.baratine.service.Service;
import io.baratine.vault.Id;

@Service("session:")
public class OauthSession
{
  @Id
  private String _id;

  private String _token;

  private long _grantTime;
  private long _expiresInSeconds;

  public String id()
  {
    return _id;
  }

  public boolean isExpired()
  {
    return _grantTime + _expiresInSeconds < System.currentTimeMillis() * 1000;
  }

  public boolean isGranted()
  {
    return _token != null;
  }

  public void token(String token, int expiresInSeconds)
  {
    _token = token;

    _grantTime = System.currentTimeMillis();
    _expiresInSeconds = expiresInSeconds;
  }
}
