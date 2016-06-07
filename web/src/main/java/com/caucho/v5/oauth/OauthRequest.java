package com.caucho.v5.oauth;

public class OauthRequest
{
  public static String CLIENT_ID = "client_id";
  public static String REDIRECT_URI = "redirect_uri";
  public static String CODE = "code";

  public static String STATE_CODE = "__state_code";
  public static String STATE_TOKEN = "__state_token";

  public static String buildCodeRequestUri(String baseUrl,
                                           String clientId,
                                           String redirectUri,
                                           String stateId)
  {
    StringBuilder sb = new StringBuilder();

    sb.append(baseUrl);

    sb.append('?');
    sb.append(CLIENT_ID);
    sb.append('=');
    sb.append(clientId);

    sb.append('&');
    sb.append(REDIRECT_URI);
    sb.append('=');
    sb.append(redirectUri);

    return sb.toString();
  }

  public static String buildTokenRequestUrl(String baseUrl,
                                            String clientId,
                                            String redirectUri,
                                            String stateId,
                                            String code)
  {
    StringBuilder sb = new StringBuilder();

    sb.append(baseUrl);

    sb.append('?');
    sb.append(CLIENT_ID);
    sb.append('=');
    sb.append(clientId);

    sb.append('&');
    sb.append(REDIRECT_URI);
    sb.append('=');
    sb.append(redirectUri);

    sb.append('&');
    sb.append(CODE);
    sb.append('=');
    sb.append(code);

    return sb.toString();
  }
}
