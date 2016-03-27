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

package com.caucho.v5.cloud.security;

import java.security.MessageDigest;

import com.caucho.v5.config.Admin;
import com.caucho.v5.http.security.AuthenticatorRole;
import com.caucho.v5.http.security.BasicPrincipal;
import com.caucho.v5.http.security.DigestBuilder;
import com.caucho.v5.http.security.DigestCredentials;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.ramp.hamp.SignedCredentials;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.Base64Util;

import io.baratine.inject.Key;

public class SecuritySystem extends SubSystemBase
{
  public static final int START_PRIORITY = START_PRIORITY_ENV_SYSTEM + 1;
  
  private String _signatureSecret;
  private AuthenticatorRole _authenticator;
  
  public SecuritySystem()
  {
  }
  
  public static SecuritySystem createAndAddSystem()
  {
    SystemManager system = preCreate(SecuritySystem.class);
    
    SecuritySystem service = new SecuritySystem();
    system.addSystem(SecuritySystem.class, service);
    
    return service;
  }
  
  public static SecuritySystem getCurrent()
  {
    return SystemManager.getCurrentSystem(SecuritySystem.class);
  }
  
  public void setSignatureSecret(String secret)
  {
    if ("".equals(secret)) {
      secret = null;
    }

    _signatureSecret = secret;
  }
  
  public boolean isSystemAuthKey()
  {
    return _signatureSecret != null;
  }
  
  public void setAuthenticator(AuthenticatorRole auth)
  {
    _authenticator = auth;
  }
  
  public AuthenticatorRole getAuthenticator()
  {
    return _authenticator;
  }
  
  public String getAlgorithm(String uid)
  {
    if (_authenticator != null)
      return _authenticator.getAlgorithm(new BasicPrincipal(uid));
    else
      return "plain";
  }
  
  public Object credentials(String algorithm,
                            String user,
                            String password,
                            String nonce)
  {
    Object credentials;
    
    if (user == null || "".equals(user)) {
      if (password == null || "".equals(password)) {
        password = _signatureSecret;
      }
      
      credentials = signedCredentials(algorithm, user, password, nonce);
    }
    else {
      credentials = createCredentials(algorithm, user, password, nonce);
    }
    
    return credentials;
  }
  
  public SignedCredentials signedCredentials(String algorithm,
                                             String user,
                                             String password,
                                             String nonce)
  {
    String signature = sign(algorithm, user, password, nonce);
    
    return new SignedCredentials(user, nonce, signature);
  }
  
  public String signSystem(String uid, String nonce)
  {
    try {
      String password = null;
      
      password = _signatureSecret;
      
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      
      if (uid != null)
        digest.update(uid.getBytes("UTF-8"));
      
      digest.update(nonce.getBytes("UTF-8"));

      if (password != null) {
        digest.update(password.getBytes("UTF-8"));
      }
      
      String signature = Base64Util.encode(digest.digest());
      
      return signature;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public String sign(String algorithm,
                     String uid, String password, 
                     String nonce)
  {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      
      if (uid != null) {
        digest.update(uid.getBytes("UTF-8"));
      }
      
      digest.update(nonce.getBytes("UTF-8"));
      
      if (password != null) {
        char []pwDigest = DigestBuilder.getDigest(new BasicPrincipal(uid),
                                                  algorithm,
                                                  password.toCharArray(),
                                                  algorithm.toCharArray());
          
        if (pwDigest != null) {
          password = new String(pwDigest);
        }
        
        digest.update(password.getBytes("UTF-8"));
      }

      return Base64Util.encode(digest.digest());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public byte [] sign(byte []data)
  {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      
      digest.update(data);

      if (_signatureSecret != null)
        digest.update(_signatureSecret.getBytes("UTF-8"));
      
      return digest.digest();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public DigestCredentials createCredentials(String algorithm,
                                             String user,
                                             String password,
                                             String nonce)
  {
    String digest = sign(algorithm, user, password, nonce);
    
    DigestCredentials cred = new DigestCredentials(user, nonce, digest);
    cred.setRealm("resin");
    
    return cred;
  }
  
  public byte []createDigest(String user, 
                             String password, 
                             String nonce)
  {
    try {
      String realm = "resin";
      
      MessageDigest md = MessageDigest.getInstance("MD5");
      
      if (user != null)
        md.update(user.getBytes("UTF-8"));
      
      md.update((byte) ':');
      md.update(realm.getBytes("UTF-8"));
      md.update((byte) ':');
      
      if (password != null)
        md.update(password.getBytes("UTF-8"));
      
      byte []digest = md.digest();
      
      md.reset();
      
      updateHex(md, digest);
      md.update((byte) ':');
      md.update(nonce.getBytes("UTF-8"));
      
      return md.digest();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
  
  private void updateHex(MessageDigest md, byte []digest)
  {
    for (int i = 0; i < digest.length; i++) {
      updateHex(md, digest[i] >> 4);
      updateHex(md, digest[i]);
    }
  }
  
  private void updateHex(MessageDigest md, int digit)
  {
    digit = digit & 0xf;
    
    if (digit < 10)
      md.update((byte) (digit + '0'));
    else
      md.update((byte) (digit - 10 + 'a'));
  }
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
  
  @Override
  public void start()
  {
    if (_authenticator == null) {
      _authenticator = findAuthenticator();
    }
  }
  
  private AuthenticatorRole findAuthenticator()
  {
    InjectorAmp injectManager = InjectorAmp.current();

    AuthenticatorRole auth;
    
    auth = injectManager.instance(Key.of(AuthenticatorRole.class, Admin.class));
    
    if (auth == null) {
      auth = injectManager.instance(AuthenticatorRole.class);
    }
    
    /*
    if (auth == null) {
      auth = cdiManager.getReference(AuthenticatorRole.class, AnyLiteral.ANY);
    }
    */

    return auth;
  }
}
