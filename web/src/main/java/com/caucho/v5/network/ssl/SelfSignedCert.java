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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.network.ssl;

import java.io.IOException;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import com.caucho.v5.util.L10N;

/**
 * self-signed certificate for testing
 */
public class SelfSignedCert implements Serializable
{
  private static final L10N L = new L10N(SelfSignedCert.class);
  private static final Logger log
    = Logger.getLogger(SelfSignedCert.class.getName());

  private X509Certificate _cert;
  private PrivateKey _key;
  private transient KeyManagerFactory _kmf;
  
  private SelfSignedCert(X509Certificate cert, PrivateKey key)
    throws Exception
  {
    _cert = cert;
    _key = key;
    
    _kmf = getKeyManagerFactory();
  }

  public static SelfSignedCert create(String name,
                                      String []cipherSuites)
  {
    try {
      //String keyAlgName = "DSA";
      //String sigAlgName = "SHA1WithDSA";
      
      String keyAlgName = null;
      String sigAlgName = null;

      if (cipherSuites != null) {
        for (String s : cipherSuites) {
          if (s.indexOf("RSA") >= 0 && s.indexOf("SHA") >= 0) {
            keyAlgName = "RSA";
            sigAlgName = "SHA1WithRSA";
          }
          else if (sigAlgName == null
                   && s.indexOf("DSS") >= 0 && s.indexOf("SHA") >= 0) {
            keyAlgName = "DSA";
            sigAlgName = "SHA1WithDSA";
          }
          else if (sigAlgName == null
                   && s.indexOf("RSA") >= 0 && s.indexOf("MD5") >= 0) {
            keyAlgName = "RSA";
            sigAlgName = "MD5WithRSA";
          }
        }
      }

      if (sigAlgName == null) {
        keyAlgName = "DSA";
        sigAlgName = "SHA1WithDSA";
      }
      
      String providerName = null;
      int keysize = 1024;
      int days = 365;

      CertAndKeyGen keypair;
      keypair = new CertAndKeyGen(keyAlgName, sigAlgName, providerName);

      keypair.generate(keysize);

      PrivateKey privKey = keypair.getPrivateKey();
      X500Name x500name = new X500Name("CN=" + name);
      
      X509Certificate cert
        = keypair.getSelfCertificate(x500name, days * 24 * 3600);

      return new SelfSignedCert(cert, privKey);
      //return null;
      /*
    } catch (ClassNotFoundException e) {
      throw ConfigException.create(L.l("SelfSigned certificates require Sun JDK\n  {0}",
                                       e),
                                   e);
                                   */
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);

      e.printStackTrace();

      return null;
    }
  }

  public PrivateKey getPrivateKey()
  {
    return _key;
  }

  public X509Certificate []getCertificateChain()
  {
    return new X509Certificate[] { _cert };
  }

  public KeyManager []getKeyManagers()
  {
    try {
      return getKeyManagerFactory().getKeyManagers();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  private KeyManagerFactory getKeyManagerFactory()
    throws NoSuchAlgorithmException, IOException, GeneralSecurityException
  {
    if (_kmf == null) {
      KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");

      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

      ks.load(null, "password".toCharArray());

        
      ks.setKeyEntry("anonymous", getPrivateKey(),
                     "key-password".toCharArray(), getCertificateChain());
      
      kmf.init(ks, "key-password".toCharArray());

      _kmf = kmf;
    }
      
    return _kmf;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _cert.getSubjectX500Principal() + "]";
  }
}

