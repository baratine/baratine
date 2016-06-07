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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import io.baratine.config.Config;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.web.FilterBefore;
import io.baratine.web.Get;
import io.baratine.web.HttpStatus;
import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWeb;
import io.baratine.web.Web;

public class OauthBeforeFilter implements ServiceWeb
{
  private static String COOKIE = "__oauth_baratine";

  @Inject
  private Config _config;

  @Inject @Service
  private RunnableService _runnableService;

  private String _clientId;
  private String _clientSecret;

  private String _codeUri;
  private String _tokenUri;
  private String _redirectUri;

  private HashMap<String,State> _cookieMap = new HashMap<>();

  @PostConstruct
  public void init()
  {
    _clientId = "XXX";
    _clientSecret = "YYY";

    _codeUri = "https://github.com/login/oauth/authorize";
    _tokenUri = "https://github.com/login/oauth/access_token";
    _redirectUri = "http://localhost:8080/test";
  }

  @Override
  public void service(RequestWeb request) throws Exception
  {
    if (_clientId == null) {
      init();
    }

    String cookie = request.cookie(COOKIE);
    State state = null;

    if (cookie != null) {
      state = _cookieMap.get(cookie);
    }

    if (state == null) {
      if (cookie == null) {
        cookie = UUID.randomUUID().toString();
        request.cookie(COOKIE, cookie);
      }

      state = new State();

      _cookieMap.put(cookie, state);
    }

    System.err.println("OauthBeforeFilter.service0: " + _config + " . " + _runnableService);

    switch (state.state()) {
      case INIT:
        handleCodeRequest(request, state);
        break;
      case CODE:
        handleCodeResponse(request, state);
        break;
      case GRANTED:
        request.ok();

        break;
      default:
        throw new RuntimeException();
    }
  }

  private void handleCodeRequest(RequestWeb request, State state)
  {
    String url = request.host() + request.uri();

    if (request.query() != null) {
      url += "?" + request.query();
    }

    state.toCode(url);

    String redirectUri = OauthRequest.buildCodeRequestUri(_codeUri, _clientId, _redirectUri, "abc");

    request.redirect(redirectUri);
  }

  private void handleCodeResponse(RequestWeb request, State state)
  {
    String code = request.query("code");

    if (code == null) {
      state.toInit();

      request.write("oauth code response not set");
      request.halt(HttpStatus.UNAUTHORIZED);

      return;
    }

    state.toToken(code);

    _runnableService.run(() -> {
      try {
        StringBuilder sb = new StringBuilder();

        sb.append(_tokenUri);
        sb.append("?");

        sb.append("client_id");
        sb.append("=");
        sb.append(_clientId);

        sb.append("&");
        sb.append("client_secret");
        sb.append("=");
        sb.append(_clientSecret);

        sb.append("&");
        sb.append("code");
        sb.append("=");
        sb.append(code);

        URL url = new URL(sb.toString());

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");

        int responseCode = conn.getResponseCode();
        String responseMessage = conn.getResponseMessage();

        String token = null;

        System.err.println("OauthBeforeFilter.handleCodeResponse2: " + responseCode + " . " + responseMessage);

        if (responseCode == 200) {
          sb.setLength(0);

          InputStream is = conn.getInputStream();
          int ch;

          while ((ch = is.read()) >= 0) {
            sb.append((char) ch);
          }

          int p = sb.indexOf("access_token=");

          if (p >= 0) {
            p = p + "access_token=".length();

            int q = sb.indexOf("&", p);
            if (q < 0) {
              q = sb.length();
            }

            token = sb.substring(p, q);
          }
        }

        handleTokenResponse(request, state, responseCode, token);
      }
      catch (Exception e) {
        e.printStackTrace();
      }


    }, Result.ignore());
  }

  private void handleTokenResponse(RequestWeb request, State state, int responseCode, String token)
  {
    if (responseCode != 200) {
      request.write("oauth token response error code: " + responseCode);
      request.halt(HttpStatus.UNAUTHORIZED);
    }
    else if (token == null) {
      state.toInit();

      request.write("oauth token response not set");
      request.halt(HttpStatus.UNAUTHORIZED);
    }
    else {
      state.toGranted(token);

      request.ok();
    }
  }

  public static void main(String[] args)
    throws Exception
  {
    Web.include(MyWeb.class);
    Web.include(RunnableService.class);

    Web.start();
  }

  public static class MyWeb {
    @Get
    @FilterBefore(OauthBeforeFilter.class)
    public void test(RequestWeb result)
    {
      System.err.println("MyWeb.test0");

      result.ok("test0");
    }

    @Get
    public void callback(RequestWeb result)
    {
      System.err.println("MyWeb.callback0");

      result.ok("callback0");
    }
  }

}
