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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.web.webapp.RouteBuilderAmp;

import io.baratine.config.Config;
import io.baratine.inject.InjectionPoint;
import io.baratine.service.Services;
import io.baratine.web.HttpStatus;
import io.baratine.web.Oauth;
import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWeb;

public class OauthFilter implements ServiceWeb
{
  private static final Logger LOG = Logger.getLogger(OauthFilter.class.toString());

  private OauthConfig _config;

  private RunnableService _runnableService;

  public OauthFilter(Oauth oauth,
                     InjectionPoint<?> ip,
                     RouteBuilderAmp builder)
  {
    Config config = builder.webBuilder().injector().instance(Config.class);

    init(config);
  }

  protected void init(Config systemConfig)
  {
    _config = new OauthConfig();

    _config.clientId("e1b0014930028455edea");
    _config.clientSecret("c67fa0c49a19ebfaf578036257131adf166a488a");

    _config.codeUri("https://github.com/login/oauth/authorize");
    _config.tokenUri("https://github.com/login/oauth/access_token");


    //systemConfig.inject(_config);

    _config.validate();

    if (LOG.isLoggable(Level.FINE)) {
      LOG.fine("oauth authentication added, codeUri=" + _config.codeUri() + ", tokenUri=" + _config.tokenUri());
    }

    _runnableService = Services.current().service(RunnableService.class);
  }

  @Override
  public void service(RequestWeb request) throws Exception
  {
    OauthSession session = request.session(OauthSession.class);

    if (! session.isGranted()) {
    }
    else if (session.isExpired()) {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine("oauth token has expired, sessionId=" + session.id());
      }
    }
    else {
      request.ok();

      return;
    }

    String code = request.query("code");

    StringBuilder sb = new StringBuilder();

    sb.append(request.protocol());
    sb.append("://");
    sb.append(request.host());

    sb.append(request.uri());

    String myUrl = sb.toString();

    if (request.query() != null) {
      myUrl += "?" + request.query();
    }

    if (code != null) {
      handleCodeResponse(request, session, myUrl);
    }
    else {
      handleCodeRequest(request, session, myUrl);
    }
  }

  private void handleCodeRequest(RequestWeb request, OauthSession session, String myUrl)
  {
    if (LOG.isLoggable(Level.FINE)) {
      LOG.fine("new oauth code request for " + myUrl + " to " + _config.codeUri() + ", sessionId=" + session.id());
    }

    if (_config.redirectUri() != null) {
      myUrl = _config.redirectUri();
    }

    String redirectUri = OauthUtil.buildCodeRequestUri(_config.codeUri(), _config.clientId(), myUrl, "abc");

    request.redirect(redirectUri);
  }

  private void handleCodeResponse(RequestWeb request, OauthSession session, String myUrl)
  {
    if (LOG.isLoggable(Level.FINE)) {
      LOG.fine("received oauth code from " + _config.codeUri() + ", sessionId=" + session.id());
    }

    String code = request.query("code");

    _runnableService.run(() -> {
      try {
        if (LOG.isLoggable(Level.FINE)) {
          LOG.fine("sending oauth token request to " + _config.tokenUri() + ", sessionId=" + session.id());
        }

        String query = ""
            + "client_id" + "=" + _config.clientId()
            + "&" + "client_secret" + "=" + _config.clientSecret()
            + "&" + "code" + "=" + code;

        URL url = new URL(_config.tokenUri());

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        OutputStream os = conn.getOutputStream();
        os.write(query.getBytes("UTF-8"));
        os.flush();
        os.close();

        int responseCode = conn.getResponseCode();
        String responseMessage = conn.getResponseMessage();

        String token = null;

        if (LOG.isLoggable(Level.FINE)) {
          LOG.fine("received oauth token response with HTTP code " + responseCode + ", sessionId=" + session.id());
        }

        if (responseCode == 200) {
          StringBuilder sb = new StringBuilder();

          InputStream is = conn.getInputStream();
          int ch;

          while ((ch = is.read()) >= 0) {
            sb.append((char) ch);
          }

          int p = sb.indexOf(OauthUtil.ACCESS_TOKEN + '=');

          if (p >= 0) {
            p = p + (OauthUtil.ACCESS_TOKEN + '=').length();

            int q = sb.indexOf("&", p);
            if (q < 0) {
              q = sb.length();
            }

            token = sb.substring(p, q);
          }
        }

        handleTokenResponse(request, session, responseCode, token);
      }
      catch (Exception e) {
        e.printStackTrace();
      }

    }, (r, e) -> {
      if (e != null) {
        request.fail(e);
      }
    });
  }

  private void handleTokenResponse(RequestWeb request, OauthSession session, int responseCode, String token)
  {
    if (responseCode != 200) {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine("oauth token response error for " + session.id() + " to " + _config.codeUri() + ", response error code: " + responseCode);
      }

      request.write("oauth token response error code: " + responseCode);
      request.halt(HttpStatus.UNAUTHORIZED);
    }
    else if (token == null) {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine("oauth token response access_token not set, sessionId=" + session.id());
      }

      request.write("oauth token response " + OauthUtil.ACCESS_TOKEN + " not set");
      request.halt(HttpStatus.UNAUTHORIZED);
    }
    else {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine("oauth token response is valid, access granted, sessionId=" + session.id());
      }

      String expiresInSecondsStr = request.query(OauthUtil.EXPIRES_IN);
      int expiresInSeconds = 3600;

      try {
        if (expiresInSecondsStr != null) {
          expiresInSeconds = Integer.valueOf(expiresInSecondsStr);
        }
      }
      catch (NumberFormatException e) {
        if (LOG.isLoggable(Level.FINER)) {
          LOG.finer("oauth token " + OauthUtil.EXPIRES_IN + " is invalid: " + expiresInSecondsStr + ", sessionId=" + session.id());
        }
      }

      session.token(token, expiresInSeconds);

      request.ok();
    }
  }
}
