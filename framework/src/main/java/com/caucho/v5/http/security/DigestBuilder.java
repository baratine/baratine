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

import java.security.MessageDigest;
import java.security.Principal;
import java.util.HashMap;
import java.util.Locale;
import java.util.logging.Logger;

import com.caucho.v5.util.Base64Util;

/**
 * All applications should extend AbstractAuthenticator to implement
 * their custom authenticators.  While this isn't absolutely required,
 * it protects implementations from API changes.
 *
 * <p>The AbstractAuthenticator provides a single-signon cache.  Users
 * logged into one web-app will share the same principal.
 */
abstract public class DigestBuilder
{
  private static final Logger log
    = Logger.getLogger(DigestBuilder.class.getName());
  
  private static final HashMap<String,DigestBuilder> _digestBuilderMap
    = new HashMap<String,DigestBuilder>();

  abstract public char []buildDigest(String code,
                                     Principal user, 
                                     char []password,
                                     char []systemDigest)
    throws Exception;
  
  public static char []getDigest(Principal user,
                                 String algorithm,
                                 char []testPassword, 
                                 char []systemDigest)
  {
    String code = getCode(systemDigest, algorithm);
    
    DigestBuilder builder = getBuilder(code);
    
    if (builder == null) {
      return null;
    }

    char []digest;
    
    if (builder != null) {
      try {
        digest = builder.buildDigest(code, user, testPassword, systemDigest);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    else {
      log.warning("password " + code + " is an unsupportedtype for "
                  + " " + user);
      
      digest = testPassword;
    }
    
    return digest;
  }
  
  public static String getAlgorithm(char []systemDigest)
  {
    String code = getCode(systemDigest);
    
    if (code == null)
      return null;
    
    DigestBuilder builder = getBuilder(code);
    
    if (builder != null)
      return builder.buildAlgorithm(code, systemDigest);
    else
      return null;
  }
  
  protected String buildAlgorithm(String code, char []systemDigest)
  {
    return code;
  }
  
  private static DigestBuilder getBuilder(String code)
  {
    if (code != null)
      return _digestBuilderMap.get(code.toLowerCase(Locale.ENGLISH));
    else
      return null;
  }
  
  private static String getCode(char []systemDigest,
                                String algorithm)
  {
    String code = getCode(systemDigest);
    
    if (code != null)
      return code;
    else if (algorithm != null)
      return getCode(algorithm.toCharArray());
    else
      return null;
  }
  
  private static String getCode(char []systemDigest)
  {
    int len = systemDigest.length;
    
    if (len == 0 || systemDigest[0] != '{') {
      return null;
    }
    
    StringBuilder sb = new StringBuilder();
    int i = 1;
    
    sb.append("{");    
    for (; i < len && systemDigest[i] != '}'; i++) {
      sb.append(systemDigest[i]);
    }
    sb.append("}");
    
    return sb.toString();
  }
  
  static class NoneDigestBuilder extends DigestBuilder {
    @Override
    public char []buildDigest(String code,
                              Principal user, 
                              char []password,
                              char []systemDigest)
    {
      char []digest = new char[code.length() + password.length];
      code.getChars(0, code.length(), digest, 0);
      
      System.arraycopy(password, 0, digest, code.length(), password.length);
      
      return digest;
    }
  }
  
  static class ShaDigestBuilder extends DigestBuilder {
    @Override
    public char []buildDigest(String code, 
                              Principal user, 
                              char []password,
                              char []systemDigest)
      throws Exception
    {
      StringBuilder sb = new StringBuilder();
      sb.append(code);
      
      MessageDigest md = MessageDigest.getInstance("sha1");
      
      for (int i = 0; i < password.length; i++) {
        md.update((byte) password[i]);
      }
      
      byte []mdDigest = md.digest();

      Base64Util.encode(sb, mdDigest, 0, mdDigest.length);
      char []digest = new char[sb.length()];
      sb.getChars(0, sb.length(), digest, 0);
      
      return digest;
    }
  }
  
  static class SaltShaDigestBuilder extends DigestBuilder {
    public String buildAlgorithm(String code, char []systemDigest)
    {
      byte []decodedBytes = decodeBytes(code, systemDigest);
      int saltOffset = 20;
      
      for (int i = 0; i < saltOffset; i++) {
        decodedBytes[i] = 0;
      }
      
      return code + Base64Util.encode(decodedBytes);
    }
    
    @Override
    public char []buildDigest(String code, 
                              Principal user, 
                              char []password,
                              char []systemDigest)
      throws Exception
    {
      StringBuilder sb = new StringBuilder();
      sb.append(code);

      byte []decodedBytes = decodeBytes(code, systemDigest);
      
      byte []mdDigest = new byte[decodedBytes.length];
      
      MessageDigest md = MessageDigest.getInstance("sha1");
      
      for (int i = 0; i < password.length; i++) {
        md.update((byte) password[i]);
      }
      
      // salt
      
      int baseLength = 20;
      for (int i = baseLength; i < decodedBytes.length; i++) {
        md.update(decodedBytes[i]);
        mdDigest[i] = decodedBytes[i];
      }
      
      md.digest(mdDigest, 0, baseLength);

      Base64Util.encode(sb, mdDigest, 0, mdDigest.length);
      
      char []digest = new char[sb.length()];
      sb.getChars(0, sb.length(), digest, 0);
      
      return digest;
    }
    
    private byte []decodeBytes(String code, char []systemDigest)
    {
      int offset = code.length();
      String systemBytes = new String(systemDigest, offset, 
                                      systemDigest.length - offset);
      
      byte []decodedBytes = Base64Util.decodeToByteArray(systemBytes);
      
      return decodedBytes;
    }
  }
  
  static class Md5DigestBuilder extends DigestBuilder {
    @Override
    public char []buildDigest(String code, 
                              Principal user, 
                              char []password,
                              char []systemDigest)
      throws Exception
    {
      StringBuilder sb = new StringBuilder();
      sb.append(code);
      
      MessageDigest md = MessageDigest.getInstance("md5");
      
      for (int i = 0; i < password.length; i++) {
        md.update((byte) password[i]);
      }
      
      byte []mdDigest = md.digest();

      Base64Util.encode(sb, mdDigest, 0, mdDigest.length);
      char []digest = new char[sb.length()];
      sb.getChars(0, sb.length(), digest, 0);
      
      return digest;
    }
  }
  
  static class ResinMd5Base64DigestBuilder extends DigestBuilder {
    @Override
    public char []buildDigest(String code, 
                              Principal user, 
                              char []password,
                              char []systemDigest)
      throws Exception
    {
      StringBuilder sb = new StringBuilder();
      sb.append(code);
      
      MessageDigest md = MessageDigest.getInstance("md5");
      
      String uid = user.getName();
      
      for (int i = 0; i < uid.length(); i++) {
        md.update((byte) uid.charAt(i));
      }

      String realm = "resin";
      
      md.update((byte) ':');
      
      for (int i = 0; i < realm.length(); i++) {
        md.update((byte) realm.charAt(i));
      }
      
      
      md.update((byte) ':');
      for (int i = 0; i < password.length; i++) {
        md.update((byte) password[i]);
      }
      
      byte []mdDigest = md.digest();

      Base64Util.encode(sb, mdDigest, 0, mdDigest.length);

      char []digest = new char[sb.length()];
      sb.getChars(0, sb.length(), digest, 0);
      
      return digest;
    }
  }
  
  static {
    _digestBuilderMap.put("{plain}", new NoneDigestBuilder());
    _digestBuilderMap.put("{sha}", new ShaDigestBuilder());
    _digestBuilderMap.put("{ssha}", new SaltShaDigestBuilder());
    _digestBuilderMap.put("{md5}", new Md5DigestBuilder());
    _digestBuilderMap.put("{md5-base64}", new ResinMd5Base64DigestBuilder());
  }
}
