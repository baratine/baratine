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

package com.caucho.v5.io.i18n;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.logging.Level;

import com.caucho.v5.io.ByteAppendable;
import com.caucho.v5.io.OutputStreamWithBuffer;

/**
 * Factory for JDK-based encoding writers.
 */
public class JDKWriter extends EncodingWriter {
  private String _javaEncoding;
  
  /**
   * Null-arg constructor for instantiation by com.caucho.v5.vfs.Encoding only.
   */
  public JDKWriter()
  {
  }
  /**
   * Returns the Java encoding for the writer.
   */
  public String getJavaEncoding()
  {
    return _javaEncoding;
  }
  
  /**
   * Sets the Java encoding for the writer.
   */
  public void setJavaEncoding(String encoding)
  {
    _javaEncoding = encoding;
  }
  

  /**
   * Create a JDK-based reader.
   *
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return an EncodingWriter
   */
  public EncodingWriter create(String javaEncoding)
  {
    try {
      return new OutputStreamEncodingWriter(javaEncoding);
    } catch (UnsupportedEncodingException e) {
      log.log(Level.WARNING, e.toString(), e);
      
      return null;
    }
  }

  /**
   * JDKWriter is only a factory.
   */
  @Override
  public void write(ByteAppendable os, char ch)
    throws IOException
  {
    throw new UnsupportedOperationException();
  }

  static class OutputStreamEncodingWriter extends EncodingWriter {
    private Charset _charset;
    private String _encoding;
    private OutputStreamWriter _writer;
    //private OutputStreamWithBuffer _os;

    OutputStreamEncodingWriter(String javaEncoding)
      throws UnsupportedEncodingException
    {
      try {
        _encoding = javaEncoding;

        if (Charset.isSupported(javaEncoding))
          _charset = Charset.forName(javaEncoding);
        else {
          // server/054i
          throw new UnsupportedEncodingException(javaEncoding);
        }
      } catch (java.nio.charset.UnsupportedCharsetException e) {
        throw new UnsupportedEncodingException(e.getMessage());
      }
    }

    /**
     * Writes a char.
     */
    public void write(ByteAppendable sb, char ch)
      throws IOException
    {
      if (_writer == null) {
        OutputStream os;
        
        if (sb instanceof OutputStream) {
          os = (OutputStream)sb;
        } else {
          os = new OutputStreamAdapter(sb);  
        }
        
        if (_charset != null)
          _writer = new OutputStreamWriter(os, _charset);
        else
          _writer = new OutputStreamWriter(os, _encoding);
      }
      
      _writer.write(ch);
      _writer.flush();
    }

    /**
     * Writes a char buffer.
     */
    public int write(OutputStreamWithBuffer os,
                      char []buf, int offset, int length)
      throws IOException
    {
      if (_writer == null) {
        if (_charset != null)
          _writer = new OutputStreamWriter(os, _charset);
        else
          _writer = new OutputStreamWriter(os, _encoding);
      }
      
      _writer.write(buf, offset, length);
      _writer.flush();
      
      return length;
    }

    /**
     * Creates the child.
     */
    public EncodingWriter create(String encoding)
    {
      throw new UnsupportedOperationException();
    }
  }
  
  static class OutputStreamAdapter extends OutputStream {    
    private final ByteAppendable _out;
    
    OutputStreamAdapter(ByteAppendable out) 
    {
      _out = out;
    }
    
    public void write(int b) throws IOException
    {
      _out.write(b);
    }
    
    public void write(byte[] buffer, int offset, int len) 
      throws IOException
    {
      _out.write(buffer, offset, len);
    }
  }
}
