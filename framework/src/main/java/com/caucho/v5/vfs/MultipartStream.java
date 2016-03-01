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

package com.caucho.v5.vfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.ByteArrayBuffer;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.util.L10N;

/*
 * A stream for reading multipart/mime files.
 *
 * <p/>Each call to openRead() will return the next part and return null
 * when complete.
 *
 * <pre><code>
 * MultipartStream multi = new MultipartStream(rawIs, boundary);
 * ReadStream is;
 *
 * while ((is = multi.openRead()) != null) {
 *   // read from is as a normal stream
 * }
 */
public class MultipartStream extends StreamImpl {
  private static final L10N L = new L10N(MultipartStream.class);
  private ByteArrayBuffer _boundary = new ByteArrayBuffer();
  private byte []_boundaryBuffer;
  private int _boundaryLength;

  private ByteArrayBuffer _peekBuffer = new ByteArrayBuffer();
  private byte []_peek;
  private int _peekOffset;
  private int _peekLength;

  private byte []_dummyBuffer = new byte[32];
  
  private ReadStream _is;
  private ReadStream _readStream;
  private boolean _isPartDone;
  private boolean _isDone;
  private boolean _isComplete;
  
  private final LinkedHashMap<String, List<String>> _headers = new LinkedHashMap<>();
  
  private CharBuffer _line = new CharBuffer();
  private long _maxLength = 256 * 1024;

  private String _defaultEncoding;

  public MultipartStream()
    throws IOException
  {
    _boundary = new ByteArrayBuffer();
  }

  public MultipartStream(ReadStream is, String boundary)
    throws IOException
  {
    this();

    init(is, boundary);
  }

  /**
   * Returns the default encoding.
   */
  public String getEncoding()
  {
    return _defaultEncoding;
  }

  /**
   * Sets the default encoding.
   */
  public void setEncoding(String encoding)
  {
    _defaultEncoding = encoding;
  }

  /**
   * Initialize the multipart stream with a given boundary.  The boundary
   * passed to <code>init</code> will have "--" prefixed.
   *
   * @param is the underlying stream
   * @param headerBoundary the multipart/mime boundary.
   */
  public void init(ReadStream is, String headerBoundary)
    throws IOException
  {
    _is = is;
    
    _boundary.clear();
    _boundary.add("--");
    _boundary.add(headerBoundary);

    _boundaryBuffer = _boundary.getBuffer();
    _boundaryLength = _boundary.getLength();

    _peekBuffer.setLength(_boundaryLength + 5);
    _peek = _peekBuffer.getBuffer();
    _peekOffset = 0;
    _peekLength = 0;
    _peek[_peekLength++] = (byte) '\n';

    _isPartDone = false;
    _isDone = false;
    _isComplete = false;
    
    while (read(_dummyBuffer, 0, _dummyBuffer.length) >= 0) {
    }
    
    _isPartDone = true;
  }

  /**
   * Returns true if complete.
   */
  public boolean isComplete()
  {
    return _isComplete;
  }

  /**
   * Opens the next part of the multipart/mime stream for reading.  Returns
   * null when the last section is read.
   */
  public ReadStream openRead()
    throws IOException
  {
    if (_isDone)
      return null;
    else if (_readStream == null)
      _readStream = new ReadStream(this);
    else if (! _isPartDone) {
      int len;
      while ((len = read(_dummyBuffer, 0, _dummyBuffer.length)) >= 0) {
      }

      if (_isDone)
        return null;
    }
    
    _readStream.init(this);

    _isPartDone = false;
    
    if (scanHeaders()) {
      String contentType = (String) getAttribute("content-type");
      
      String charset = getAttributePart(contentType, "charset");
      
      if (charset != null)
        _readStream.setEncoding(charset);
      else if (_defaultEncoding != null)
        _readStream.setEncoding(_defaultEncoding);
      
      return _readStream;
    }
    else {
      _isDone = true;
      _readStream.close();
      return null;
    }
  }

  /**
   * Returns a read attribute from the multipart mime.
   */
  public String getAttribute(String key)
  {
    List<String> values = _headers.get(key.toLowerCase(Locale.ENGLISH));

    if (values != null && values.size() > 0)
      return values.get(0);

    return null;
  }

  /**
   * Returns the headers from the mime.
   */
  public Iterator getAttributeNames()
  {
    return _headers.keySet().iterator();
  }

  public HashMap<String, List<String>> getHeaders()
  {
    return _headers;
  }

  /**
   * Scans the mime headers.  The mime headers are in standard mail/http
   * header format: "key: value".
   */
  private boolean scanHeaders()
    throws IOException
  {
    int ch = read() ;
    long length = 0;

    _headers.clear();
    while (ch > 0 && ch != '\n' && ch != '\r') {
      _line.clear();

      _line.append((char) ch);
      for (ch = read();
           ch >= 0 && ch != '\n' && ch != '\r';
           ch = read()) {
        _line.append((char) ch);
        
        if (_maxLength < length++)
          throw new IOException(L.l("header length {0} exceeded.", _maxLength));
      }

      if (ch == '\r') {
        if ((ch = read()) == '\n')
          ch = read();
      } else if (ch == '\n')
        ch = read();

      int i = 0;
      for (; i < _line.length() && _line.charAt(i) != ':'; i++) {
      }

      String key = null;
      String value = null;
      if (i < _line.length()) {
        key = _line.substring(0, i).trim().toLowerCase(Locale.ENGLISH);
        value = _line.substring(i + 1).trim();

        List<String> values = _headers.get(key);

        if (values == null) {
          values = new ArrayList<>();
        }

        values.add(value);
        
        _headers.put(key, values);
      }
    }
    
    if (ch == '\r') {
      if ((ch = read()) != '\n') {
        _peek[0] = (byte) ch;
        _peekOffset = 0;
        _peekLength = 1;
      }
    }

    return true;
  }
  
  public boolean canRead()
  {
    return true;
  }

  /**
   * Returns the number of available bytes.
   */
  public int getAvailable()
    throws IOException
  {
    if (_isPartDone)
      return 0;
    else if (_peekOffset < _peekLength)
      return _peekLength - _peekOffset;
    else {
      int ch = read();
      if (ch < 0)
        return 0;
      _peekOffset = 0;
      _peekLength = 1;
      _peek[0] = (byte) ch;

      return 1;
    }
  }

  /**
   * Reads from the multipart mime buffer.
   */
  @Override
  public int read(byte []buffer, int offset, int length) throws IOException
  {
    int b = -1;

    if (_isPartDone)
      return -1;

    int i = 0;
    // Need the last peek or would miss the initial '\n'
    while (_peekOffset + 1 < _peekLength && length > 0) {
      buffer[offset + i++] = _peek[_peekOffset++];
      length--;
    }

    while (i < length && (b = read()) >= 0) {
      boolean hasCr = false;

      if (b == '\r') {
        hasCr = true;
        b = read();

        // XXX: Macintosh?
        if (b != '\n') {
          buffer[offset + i++] = (byte) '\r';
          _peek[0] = (byte) b;
          _peekOffset = 0;
          _peekLength = 1;
          continue;
        }
      }
      else if (b != '\n') {
        buffer[offset + i++] = (byte) b;
        continue;
      }

      int j;
      for (j = 0;
           j < _boundaryLength && (b = read()) >= 0 && _boundaryBuffer[j] == b;
           j++) {
      }

      if (j == _boundaryLength) {
        _isPartDone = true;
        if ((b = read()) == '-') {
          if ((b = read()) == '-') {
            _isDone = true;
            _isComplete = true;
          }
        }

        for (; b > 0 && b != '\r' && b != '\n'; b = read()) {
        }
        if (b == '\r' && (b = read()) != '\n') {
          _peek[0] = (byte) b;
          _peekOffset = 0;
          _peekLength = 1;
        }
        
        return i > 0 ? i : -1;
      }

      _peekLength = 0;
      if (hasCr && i + 1 < length) {
        buffer[offset + i++] = (byte) '\r';
        buffer[offset + i++] = (byte) '\n';
      }
      else if (hasCr) {
        buffer[offset + i++] = (byte) '\r';
        _peek[_peekLength++] = (byte) '\n';
      }
      else {
        buffer[offset + i++] = (byte) '\n';
      }

      int k = 0;
      while (k < j && i + 1 < length)
        buffer[offset + i++] = _boundaryBuffer[k++];

      while (k < j)
        _peek[_peekLength++] = _boundaryBuffer[k++];

      _peek[_peekLength++] = (byte) b;
      _peekOffset = 0;
    }

    if (i <= 0) {
      _isPartDone = true;
      if (b < 0)
        _isDone = true;
      return -1;
    }
    else {
      return i;
    }
  }

  /**
   * Read the next byte from the peek or from the underlying stream.
   */
  private int read()
    throws IOException
  {
    if (_peekOffset < _peekLength)
      return _peek[_peekOffset++] & 0xff;
    else
      return _is.read();
  }

  private static String getAttributePart(String attr, String name)
  {
    if (attr == null)
      return null;
    
    int length = attr.length();
    int i = attr.indexOf(name);
    if (i < 0)
      return null;

    for (i += name.length(); i < length && attr.charAt(i) != '='; i++) {
    }
    
    for (i++; i < length && attr.charAt(i) == ' '; i++) {
    }

    CharBuffer value = CharBuffer.allocate();
    if (i < length && attr.charAt(i) == '\'') {
      for (i++; i < length && attr.charAt(i) != '\''; i++)
        value.append(attr.charAt(i));
    }
    else if (i < length && attr.charAt(i) == '"') {
      for (i++; i < length && attr.charAt(i) != '"'; i++)
        value.append(attr.charAt(i));
    }
    else if (i < length) {
      char ch;
      for (; i < length && (ch = attr.charAt(i)) != ' ' && ch != ';'; i++)
        value.append(ch);
    }

    return value.close();
  }
}
