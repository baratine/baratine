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
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.StreamImpl;

/**
 * Handles a stream which outputs to a writer.
 */
public class WriterStreamImpl extends StreamImpl {
  private static Logger log
    = Logger.getLogger(WriterStreamImpl.class.getName());
  
  private Writer _writer;
  private ByteToCharWriter _byteToChar = new ByteToCharWriter();
  private boolean _isClosed;

  /**
   * Sets the writer.
   */
  public void setWriter(Writer writer)
  {
    _writer = writer;
    _byteToChar.setWriter(writer);
    _isClosed = false;

    try {
      _byteToChar.setEncoding(null);
    } catch (UnsupportedEncodingException e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  /**
   * Returns true if this is a writable stream.
   */
  public boolean canWrite()
  {
    return true;
  }

  /**
   * Sets the write encoding.
   */
  public void setWriteEncoding(String encoding)
  {
    try {
      _byteToChar.setEncoding(encoding);
    } catch (UnsupportedEncodingException e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  /**
   * Writes a buffer to the underlying stream.
   *
   * @param buffer the byte array to write.
   * @param offset the offset into the byte array.
   * @param length the number of bytes to write.
   * @param isEnd true when the write is flushing a close.
   */
  public void write(byte []buffer, int offset, int length, boolean isEnd)
    throws IOException
  {
    if (_isClosed)
      return;
    
    for (int i = 0; i < length; i++)
      _byteToChar.addByte(buffer[offset + i]);

    _byteToChar.flush();
  }

  /**
   * Flushes the write output.
   */
  public void flush() throws IOException
  {
  }

  /**
   * Closes the output.
   */
  public void close()
  {
    _isClosed = true;
  }
}
