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

public class OauthConfig
{
  private String _prefix;

  private String _clientId;
  private String _clientSecret;

  private String _codeUri;
  private String _tokenUri;

  private String _redirectUri;
  private String _errorUri;

  public OauthConfig()
  {
    this("oauth");
  }

  public OauthConfig(String prefix)
  {
    _prefix = prefix;
  }

  public String clientId()
  {
    return _clientId;
  }

  public void clientId(String id)
  {
    _clientId = id;
  }

  public String clientSecret()
  {
    return _clientSecret;
  }

  public void clientSecret(String secret)
  {
    _clientSecret = secret;
  }

  public String codeUri()
  {
    return _codeUri;
  }

  public void codeUri(String uri)
  {
    _codeUri = uri;
  }

  public String tokenUri()
  {
    return _tokenUri;
  }

  public void tokenUri(String uri)
  {
    _tokenUri = uri;
  }

  public String redirectUri()
  {
    return _redirectUri;
  }

  public void redirectUri(String uri)
  {
    _redirectUri = uri;
  }

  public String errorUri()
  {
    return _errorUri;
  }

  public void errorUri(String uri)
  {
    _errorUri = uri;
  }

  public void validate()
  {
    if (clientId() == null) {
      throw new RuntimeException(_prefix + ".clientId is not set");
    }

    if (clientSecret() == null) {
      throw new RuntimeException(_prefix + ".clientSecret is not set");
    }

    if (codeUri() == null) {
      throw new RuntimeException(_prefix + ".codeUri is not set");
    }

    if (tokenUri() == null) {
      throw new RuntimeException(_prefix + ".tokenUri is not set");
    }
  }
}
