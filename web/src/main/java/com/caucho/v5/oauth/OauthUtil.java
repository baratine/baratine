package com.caucho.v5.oauth;

public class OauthUtil
{
  public static String CLIENT_ID = "client_id";
  public static String REDIRECT_URI = "redirect_uri";
  public static String CODE = "code";

  public static String RESPONSE_TYPE = "response_type";
  public static String RESPONSE_TYPE_CODE = "code";

  public static String GRANT_TYPE = "grant_type";
  public static String GRANT_TYPE_TOKEN = "authorization_code";

  public static String RETURN_URL = "oauth_return_url";

  public static final String ACCESS_TOKEN = "access_token";
  public static final String EXPIRES_IN = "expires_in";

  public static String buildCodeRequestUri(String baseUrl,
                                           String clientId,
                                           String redirectUri,
                                           String stateId)
  {
    StringBuilder sb = new StringBuilder();

    sb.append(baseUrl);

    sb.append('?');
    sb.append(RESPONSE_TYPE);
    sb.append('=');
    sb.append(RESPONSE_TYPE_CODE);

    sb.append('&');
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
    sb.append(GRANT_TYPE);
    sb.append('=');
    sb.append(GRANT_TYPE_TOKEN);

    sb.append('&');
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
