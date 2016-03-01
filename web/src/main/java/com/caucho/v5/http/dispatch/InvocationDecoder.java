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

package com.caucho.v5.http.dispatch;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.http.protocol.BadRequestException;
import com.caucho.v5.io.i18n.ByteToChar;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.util.FreeList;
import com.caucho.v5.util.HomeUtil;
import com.caucho.v5.util.L10N;

/**
 * Decodes invocation URI.
 */
public class InvocationDecoder<I extends Invocation>
{
  private static final Logger log
    = Logger.getLogger(InvocationDecoder.class.getName());
  private static final L10N L = new L10N(InvocationDecoder.class);

  private static final FreeList<ByteToChar> _freeConverters
    = new FreeList<ByteToChar>(256);
  
  // The character encoding
  private String _encoding = "UTF-8";

  private int _maxURILength = 1024;

  /**
   * Creates the invocation decoder.
   */
  public InvocationDecoder()
  {
    // XXX:
    _encoding = "UTF-8"; // CharacterEncoding.getLocalEncoding();
    
    if (_encoding == null) {
      _encoding = "UTF-8";
    }
  }

  /**
   * Returns the character encoding.
   */
  public String getEncoding()
  {
    return _encoding;
  }

  /**
   * Sets the character encoding.
   */
  public void setEncoding(String encoding)
  {
    _encoding = encoding;
  }

  public int getMaxURILength()
  {
    return _maxURILength;
  }

  public void setMaxURILength(int maxURILength)
  {
    _maxURILength = maxURILength;
  }

  /**
   * Splits out the query string and unescape the value.
   */
  public void splitQueryAndUnescape(I invocation,
                                    byte []rawURIBytes,
                                    int uriLength)
    throws IOException
  {
    for (int i = 0; i < uriLength; i++) {
      if (rawURIBytes[i] == '?') {
        i++;

        // XXX: should be the host encoding?
        String queryString = byteToChar(rawURIBytes, i, uriLength - i,
                                        "ISO-8859-1");
        invocation.setQueryString(queryString);

        uriLength = i - 1;
        break;
      }
    }

    String rawURIString = byteToChar(rawURIBytes, 0, uriLength, "ISO-8859-1");
    invocation.setRawURI(rawURIString);
    
    String decodedURI = normalizeUriEscape(rawURIBytes, 0, uriLength, _encoding);

    decodedURI = decodeURI(rawURIString, decodedURI, invocation);

    String uri = normalizeUri(decodedURI);

    invocation.setURI(uri);
  }
  
  protected String decodeURI(String rawURI, String decodedURI, I invocation)
  {
    return decodedURI;
  }

  /**
   * Splits out the query string, and normalizes the URI, assuming nothing
   * needs unescaping.
   */
  public void splitQuery(I invocation, String rawURI)
    throws IOException
  {
    int p = rawURI.indexOf('?');
    if (p > 0) {
      invocation.setQueryString(rawURI.substring(p + 1));
      
      rawURI = rawURI.substring(0, p);
    }

    invocation.setRawURI(rawURI);
    
    String uri = normalizeUri(rawURI);

    invocation.setURI(uri);
  }

  /**
   * Just normalize the URI.
   */
  public void normalizeURI(I invocation, String rawURI)
    throws IOException
  {
    invocation.setRawURI(rawURI);
    
    String uri = normalizeUri(rawURI);

    invocation.setURI(uri);
  }

  private String byteToChar(byte []buffer, int offset, int length,
                            String encoding)
  {
    ByteToChar converter = allocateConverter();
    // XXX: make this configurable

    if (encoding == null)
      encoding = "utf-8";

    try {
      converter.setEncoding(encoding);
    } catch (UnsupportedEncodingException e) {
      log.log(Level.FINE, e.toString(), e);
    }

    String result;
    
    try {
      for (; length > 0; length--)
        converter.addByte(buffer[offset++]);
      
      result = converter.getConvertedString();
      
      freeConverter(converter);
    } catch (IOException e) {
      result = "unknown";
    }
    
    return result;
  }

  /**
   * Normalize a uri to remove '///', '/./', 'foo/..', etc.
   *
   * @param uri the raw uri to be normalized
   * @return a normalized URI
   */
  public String normalizeUri(String uri)
    throws IOException
  {
    return normalizeUri(uri, HomeUtil.isWindows());
  }

  /**
   * Normalize a uri to remove '///', '/./', 'foo/..', etc.
   *
   * @param uri the raw uri to be normalized
   * @return a normalized URI
   */
  public String normalizeUri(String uri, boolean isWindows)
    throws IOException
  {
    CharBuffer cb = new CharBuffer();

    int len = uri.length();

    if (_maxURILength < len)
      throw new BadRequestException(L.l("The request contains an illegal URL because it is too long."));

    char ch;
    if (len == 0 || (ch = uri.charAt(0)) != '/' && ch != '\\')
      cb.append('/');

    for (int i = 0; i < len; i++) {
      ch = uri.charAt(i);

      if (ch == '/' || ch == '\\') {
      dots:
        while (i + 1 < len) {
          ch = uri.charAt(i + 1);

          if (ch == '/' || ch == '\\')
            i++;
          else if (ch != '.')
            break dots;
          else if (len <= i + 2
                   || (ch = uri.charAt(i + 2)) == '/' || ch == '\\') {
            i += 2;
          }
          else if (ch != '.')
            break dots;
          else if (len <= i + 3
                   || (ch = uri.charAt(i + 3)) == '/' || ch == '\\') {
            int j;

            for (j = cb.length() - 1; j >= 0; j--) {
              if ((ch = cb.charAt(j)) == '/' || ch == '\\')
                break;
            }
            if (j > 0)
              cb.length(j);
            else
              cb.length(0);
            i += 3;
          } else {
            throw new BadRequestException(L.l("The request contains an illegal URL."));
          }
        }

        while (isWindows && cb.length() > 0
               && ((ch = cb.lastChar()) == '.' || ch == ' ')) {
          cb.length(cb.length() - 1);

          if (cb.length() > 0
              && (ch = cb.lastChar()) == '/' || ch == '\\') {
            cb.length(cb.length() - 1);
            // server/003n
            continue;
          }
        }

        cb.append('/');
      }
      else if (ch == 0)
        throw new BadRequestException(L.l("The request contains an illegal URL."));
      else
        cb.append(ch);
    }

    while (isWindows && cb.length() > 0
           && ((ch = cb.lastChar()) == '.' || ch == ' ')) {
      cb.length(cb.length() - 1);
    }

    return cb.toString();
  }

  /**
   * Converts the escaped URI to a string.
   *
   * @param rawUri the escaped URI
   * @param i index into the URI
   * @param len the length of the uri
   * @param encoding the character encoding to handle %xx
   *
   * @return the converted URI
   */
  private static String normalizeUriEscape(byte []rawUri, int i, int len,
                                           String encoding)
    throws IOException
  {
    ByteToChar converter = allocateConverter();
    // XXX: make this configurable

    if (encoding == null) {
      encoding = "utf-8";
    }

    try {
      converter.setEncoding(encoding);
    } catch (UnsupportedEncodingException e) {
      log.log(Level.FINE, e.toString(), e);
    }

    try {
      while (i < len) {
        int ch = rawUri[i++] & 0xff;

        if (ch == '%')
          i = scanUriEscape(converter, rawUri, i, len);
        else
          converter.addByte(ch);
      }

      String result = converter.getConvertedString();
      
      freeConverter(converter);
      
      return result;
    } catch (Exception e) {
      throw new BadRequestException(L.l("The URL contains escaped bytes unsupported by the {0} encoding.", encoding));
    }
  }

  /**
   * Scans the next character from URI, adding it to the converter.
   *
   * @param converter the byte-to-character converter
   * @param rawUri the raw URI
   * @param i index into the URI
   * @param len the raw URI length
   *
   * @return next index into the URI
   */
  private static int scanUriEscape(ByteToChar converter,
                                   byte []rawUri, int i, int len)
    throws IOException
  {
    int ch1 = i < len ? (rawUri[i++] & 0xff) : -1;

    if (ch1 == 'u') {
      ch1 = i < len ? (rawUri[i++] & 0xff) : -1;
      int ch2 = i < len ? (rawUri[i++] & 0xff) : -1;
      int ch3 = i < len ? (rawUri[i++] & 0xff) : -1;
      int ch4 = i < len ? (rawUri[i++] & 0xff) : -1;

      converter.addChar((char) ((toHex(ch1) << 12) +
                                (toHex(ch2) << 8) + 
                                (toHex(ch3) << 4) + 
                                (toHex(ch4))));
    }
    else {
      int ch2 = i < len ? (rawUri[i++] & 0xff) : -1;

      int b = (toHex(ch1) << 4) + toHex(ch2);;

      converter.addByte(b);
    }

    return i;
  }

  /**
   * Convert a character to hex
   */
  private static int toHex(int ch)
  {
    if (ch >= '0' && ch <= '9')
      return ch - '0';
    else if (ch >= 'a' && ch <= 'f')
      return ch - 'a' + 10;
    else if (ch >= 'A' && ch <= 'F')
      return ch - 'A' + 10;
    else
      return -1;
  }
  
  private static ByteToChar allocateConverter()
  {
    ByteToChar converter = _freeConverters.allocate();
    
    if (converter == null)
      converter = ByteToChar.create();
    else
      converter.clear();
    
    return converter;
  }
  
  private static void freeConverter(ByteToChar converter)
  {
    _freeConverters.free(converter);
  }
}
