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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.vfs.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import com.caucho.v5.io.i18n.EncodingReader;

/**
 * Implements an encoding reader to convert the stupid
 * windows "smart" quotes into ISO-8859-1 (Latin-1) characters.
 *
 * <p>The windows "smart" quotes actually do map into
 * unicode characters.  If that's what you want, use
 * the window-1521 encoding instead.  windows-hack converts
 * to the closest latin-1 equivalent.
 *
 * <p>The three exceptions are the elipses '...', the
 * trademark, and the per-mille characters.  Those are translated into
 * their unicode equivalents because there isn't a useful
 * latin-1 equivalent.
 */
public class WindowsHackReader extends EncodingReader {
  private InputStream is;

  /**
   * Null-arg constructor for instantiation by com.caucho.v5.vfs.Encoding only.
   */
  public WindowsHackReader()
  {
  }

  /**
   * Create a windows-hack reader based on the readStream.
   */
  private WindowsHackReader(InputStream is)
  {
    this.is = is;
  }

  /**
   * Create a windows-hack reader based on the readStream.
   *
   * @param is the input stream providing the bytes.
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return the windows-hack reader.
   */
  public Reader create(InputStream is, String javaEncoding)
  {
    return new WindowsHackReader(is);
  }

  /**
   * Reads into a character buffer using the correct encoding.
   */
  public int read()
    throws IOException
  {
    int ch1 = is.read();

    switch (ch1) {
    case 130: // unicode 8218
      return ',';
        
    case 131: // unicode 402
      return 'f';
        
    case 132: // unicode 8222
      return '"';
        
    case 133: // unicode 8230 "..."
      return 8230;
        
    case 134: // unicode 8224 (dagger)
      return '+';
        
    case 135: // unicode 8225 (double dagger)
      return '+';
        
    case 136: // unicode 710
      return '^';
        
    case 137: // unicode 8240 (per-mille 0/00)
      return 8240;
        
    case 138: // unicode 352
      return 'S';
        
    case 139: // unicode 8249
      return '<';
        
    case 140: // unicode 338 (OE)
      return 'O';
        
    case 145: // unicode 8216
    case 146: // unicode 8217
      return '\'';
        
    case 147: // unicode 8220
    case 148: // unicode 8221
      return '"';
        
    case 149: // unicode 8226 (bullet)
      return '*';
        
    case 150: // unicode 8211
    case 151: // unicode 8212
      return '-';
        
    case 152: // unicode 732
      return '~';
        
    case 153: // unicode 8482 (trademark)
      return 8482;
        
    case 154: // unicode 353
      return 's';
        
    case 155: // unicode 8250
      return '>';
        
    case 156: // unicode 339 (oe)
      return 'o';
        
    case 376: // unicode 376 (Y with umlaut)
      return 'Y';
        
    default:
      return ch1;
    }
  }

  /**
   * Reads into a character buffer using the correct encoding.
   *
   * @param cbuf character buffer receiving the data.
   * @param off starting offset into the buffer.
   * @param len number of characters to read.
   *
   * @return the number of characters read or -1 on end of file.
   */
  public int read(char []cbuf, int off, int len)
    throws IOException
  {
    int i = 0;

    for (i = 0; i < len; i++) {
      int ch = is.read();

      if (ch < 0)
        return i == 0 ? -1 : i;

      switch (ch) {
      case -1:
        return i == 0 ? -1 : i;
        
      case 130: // unicode 8218
        cbuf[off + i] = ',';
        break;
        
      case 131: // unicode 402
        cbuf[off + i] = 'f';
        break;
        
      case 132: // unicode 8222
        cbuf[off + i] = '"';
        break;
        
      case 133: // unicode 8230 "..."
        cbuf[off + i] = (char) 8230;
        break;
        
      case 134: // unicode 8224 (dagger)
        cbuf[off + i] = '+';
        break;
        
      case 135: // unicode 8225 (double dagger)
        cbuf[off + i] = '+';
        break;
        
      case 136: // unicode 710
        cbuf[off + i] = '^';
        break;
        
      case 137: // unicode 8240 (per-mille 0/00)
        cbuf[off + i] = (char) 8240;
        break;
        
      case 138: // unicode 352
        cbuf[off + i] = 'S';
        break;
        
      case 139: // unicode 8249
        cbuf[off + i] = '<';
        break;
        
      case 140: // unicode 338 (OE)
        cbuf[off + i] = 'O';
        break;
        
      case 145: // unicode 8216
      case 146: // unicode 8217
        cbuf[off + i] = '\'';
        break;
        
      case 147: // unicode 8220
      case 148: // unicode 8221
        cbuf[off + i] = (char) '"';
        break;
        
      case 149: // unicode 8226 (bullet)
        cbuf[off + i] = (char) '*';
        break;
        
      case 150: // unicode 8211
      case 151: // unicode 8212
        cbuf[off + i] = (char) '-';
        break;
        
      case 152: // unicode 732
        cbuf[off + i] = (char) '~';
        break;
        
      case 153: // unicode 8482 (trademark)
        cbuf[off + i] = (char) 8482;
        break;
        
      case 154: // unicode 353
        cbuf[off + i] = 's';
        break;
        
      case 155: // unicode 8250
        cbuf[off + i] = '>';
        break;
        
      case 156: // unicode 339 (oe)
        cbuf[off + i] = 'o';
        break;
        
      case 376: // unicode 376 (Y with umlaut)
        cbuf[off + i] = 'Y';
        break;
        
      default:
        cbuf[off + i] = (char) ch;
      }
    }

    return i;
  }
}
