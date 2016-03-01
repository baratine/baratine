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

package com.caucho.v5.ramp.hamp;

import java.security.MessageDigest;
import java.security.Principal;

import com.caucho.v5.http.security.BasicPrincipal;
import com.caucho.v5.http.security.DigestBuilder;
import com.caucho.v5.util.Base64Util;

/**
 * Manages links on the client
 */

public class ClientAuthManager {
  public String sign(String algorithm,
                     String uid, 
                     String nonce, 
                     String password)
  {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
    
      if (uid != null)
        md.update(uid.getBytes("UTF-8"));
      
      md.update(nonce.getBytes("UTF-8"));
      
      if (password != null) {
        String signed = password;
        
        if (algorithm != null && ! "".equals(uid)) {
          Principal user = new BasicPrincipal(uid);
        
          char []digest = DigestBuilder.getDigest(user,
                                                  algorithm,
                                                  password.toCharArray(),
                                                  algorithm.toCharArray());
          
          if (digest != null)
            signed = new String(digest);
        }

        md.update(signed.getBytes("UTF-8"));
      }
      
      byte []digest = md.digest();
      
      return Base64Util.encode(digest);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
