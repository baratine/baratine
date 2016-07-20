/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.http.protocol;

import java.io.Serializable;

import com.caucho.v5.web.CookieWeb;

/**
 * Encapsulates HTTP cookies.
 *
 * <p/>Cookies are use to keep track of users for sessions and to
 * recognize users back for personalization.
 *
 * <p/>When deleting cookies or changing the time, it's important to
 * set the same domain and path as originally passed.  Browsers distinguish
 * cookies with different domain and path.
 *
 * <code><pre>
 * Cookie myCookie = new Cookie("mycookie", "myvalue");
 * myCookie.setPath("/path");
 * myCookie.setDomain("mydom.com");
 * // will live for a month
 * myCookie.setMaxAge(60 * 24 * 3600);
 * response.addCookie(myCookie);
 * </pre></code>
 *
 * <p/>To delete the above cookie, you'll need to do something like the
 * following:
 *
 * <code><pre>
 * Cookie myCookie = new Cookie("mycookie", "myvalue");
 * myCookie.setPath("/path");
 * myCookie.setDomain("mydom.com");
 * // kill the cookies
 * myCookie.setMaxAge(0);
 * response.addCookie(myCookie);
 * </pre></code>
 */
public class WebCookie implements Cloneable, Serializable, CookieWeb {
  private String name;
  private String value;
  private String comment;
  private String domain;
  private int maxAge = -1;
  private String path;
  private boolean secure;
  private int version = 0;
  private boolean isHttpOnly;

  /**
   * Create a new cookie with the specified name and value.
   *
   * @param name name of the cookie
   * @param value value of the cookie
   */
  public WebCookie(String name, String value)
  {
    int length = name.length();

    for (int i = length - 1; i >= 0; i--) {
      char ch = name.charAt(i);

      if (ch >= 0x7f || ! validChar[ch])
        throw new IllegalArgumentException("illegal cookie name: " + name);

      else if (ch == '$' && i == 0)
        throw new IllegalArgumentException("cookie can't start with '$'");
    }

    this.name = name;
    this.value = value;
  }

  /**
   * Sets the cookie comment
   *
   * @param comment comment string
   */
  public void setComment(String comment)
  {
    this.comment = comment;
  }

  /**
   * Gets the cookie comment
   */
  public String getComment()
  {
    return this.comment;
  }

  /**
   * Sets the cookie domain.  A site which uses multiple machines, e.g.
   * with DNS round robin, but a single site will want to set the domain.
   *
   * <code><pre>
   * cookie.setDomain("yahoo.com");
   * </pre></code>
   *
   * @param domain DNS domain name
   */
  public void setDomain(String domain)
  {
    this.domain = domain;
  }

  /**
   * Returns the cookie's domain
   */
  public String domain()
  {
    return this.domain;
  }

  /**
   * True for HttpOnly request
   *
   * @since Servlet 3.0
   */
  public boolean httpOnly()
  {
    return this.isHttpOnly;
  }

  /**
   * True for HttpOnly request
   *
   * @since Servlet 3.0
   */
  public void setHttpOnly(boolean isHttpOnly)
  {
    this.isHttpOnly = isHttpOnly;
  }

  /**
   * Sets the max age of a cookie.  Setting <code>maxAge</code> to zero
   * deletes the cookie.  Setting it to something large makes the cookie
   * persistent.  If <code>maxAge</code> is not set, the cookie will only
   * last for the session.
   *
   * @param maxAge lifetime of the cookie in seconds.
   */
  public void setMaxAge(int maxAge)
  {
    this.maxAge = maxAge;
  }

  /**
   * Returns the max age of the cookie in seconds.
   */
  @Override
  public long maxAge()
  {
    return this.maxAge * 1000;
  }

  /**
   * Sets the URL path of a cookie.  Normally, cookies will just use
   * the root path.
   */
  public void setPath(String path)
  {
    this.path = path;
  }

  /**
   * Gets the URL path of a cookie.
   */
  public String path()
  {
    return this.path;
  }

  /**
   * Tells the browser that this cookie should only be passed over a
   * secure connection like SSL.
   */
  public void setSecure(boolean secure)
  {
    this.secure = secure;
  }

  /**
   * Returns true if the cookie must be over a secure connection.
   */
  public boolean secure()
  {
    return this.secure;
  }

  /**
   * Returns the cookie's name.
   */
  public String name()
  {
    return this.name;
  }

  /**
   * Sets the cookie's value.  Normally this will be a random unique
   * string to lookup the cookie in a database.
   */
  public void setValue(String value)
  {
    this.value = value;
  }

  /**
   * Returns the cookie's value.
   */
  public String value()
  {
    return this.value;
  }

  /**
   * Returns cookie protocol version.
   */
  public int getVersion()
  {
    return this.version;
  }

  /**
   * Sets cookie protocol version, defaulting to 0.
   */
  public void setVersion(int version)
  {
    this.version = version;
  }

  /**
   * Returns a clone of the cookie
   */
  public Object clone()
  {
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e.getMessage ());
    }
  }
  
  /**
   * Converts the cookie to a string for debugging.
   */
  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("Cookie[" + this.name + "=" + this.value);
    if (this.path != null)
      sb.append(",path=" + this.path);
    if (this.domain != null)
      sb.append(",domain=" + this.domain);
    if (this.maxAge > 0)
      sb.append(",max-age=" + this.maxAge);
    if (this.secure)
      sb.append(",secure");
    if (this.isHttpOnly)
      sb.append(",httpOnly");
    sb.append("]");
    return sb.toString();
  }

  private static boolean []validChar;

  static {
    validChar = new boolean[128];
    for (int i = 33; i < 127; i++)
      validChar[i] = true;
    validChar[','] = false;
    validChar[';'] = false;
    /*
     * Disabled to match jakarta
     *
    validChar['('] = false;
    validChar[')'] = false;
    validChar['<'] = false;
    validChar['>'] = false;
    validChar['@'] = false;
    validChar[':'] = false;
    validChar['\\'] = false;
    validChar['"'] = false;
    validChar['/'] = false;
    validChar['['] = false;
    validChar[']'] = false;
    validChar['?'] = false;
    validChar['='] = false;
    validChar['{'] = false;
    validChar['}'] = false;
    */
  }
}
