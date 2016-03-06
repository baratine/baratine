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

package com.caucho.v5.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A fast bufferered output stream supporting both character
 * and byte data.  The underlying stream sources are provided by StreamImpl
 * classes, so all streams have the same API regardless of the underlying
 * implementation.
 *
 * <p>OutputStream and Writers are combined.  The <code>write</code> routines
 * write bytes and the <code>print</code> routines write characters.
 *
 * <p>Most applications will use the Path routines to create their own streams.
 * Specialized applications, like servers, need the capability of recycling
 * streams.
 */
public class WriteBuffer extends OutputStreamWithBuffer
  //implements SendfileOutputStream
{
  private static final byte []LF_BYTES = new byte[] {'\n'};
  private static final byte []CR_BYTES = new byte[] {'\r'};
  private static final byte []CRLF_BYTES = new byte[] {'\r', '\n'};
  
  private static final byte []LATIN1;

  private static String _sysNewline;
  private static byte []_sysNewlineBytes;

  private static final int CHARS_LENGTH;

  static {
    _sysNewline = "\n"; // PathImpl.getNewlineString();
    _sysNewlineBytes = _sysNewline.getBytes();
  }

  private TempBuffer _tempWrite;
  private byte []_writeBuffer;
  private int _writeLength;

  private boolean _isFlushRequired;

  private StreamImpl _source;
  private long _position;

  private final char []_chars = new char[CHARS_LENGTH];
  private byte []_bytes;

  // private EncodingWriter _writeEncoding;
  //private String _writeEncodingName;

  private boolean _implicitFlush = false;
  private boolean _isFlushOnNewline;
  private boolean _isDisableFlush;
  private boolean _isReuseBuffer;

  //private StreamPrintWriter _printWriter;

  private String _newline = "\n";
  private byte []_newlineBytes = LF_BYTES;

  /**
   * Creates an uninitialized stream. Use <code>init</code> to initialize.
   */
  public WriteBuffer()
  {
  }

  /**
   * Creates a stream and initializes with a specified source.
   *
   * @param source Underlying source for the stream.
   */
  public WriteBuffer(StreamImpl source)
  {
    init(source);
  }

  /**
   * Creates a stream and initializes with a specified source.
   *
   * @param source Underlying source for the stream.
   */
  public WriteBuffer(OutputStream os)
  {
    init(new VfsStream(os));
  }

  /**
   * Initializes the stream with a given source.
   *
   * @param source Underlying source for the stream.
   */
  public void init(StreamImpl source)
  {
    if (_source != null && _source != source) {
      try {
        close();
      } catch (IOException e) {
      }
    }

    if (source == null) {
      throw new IllegalArgumentException();
    }

    if (_tempWrite == null) {
      _tempWrite = TempBuffer.allocate();
      _writeBuffer = _tempWrite.buffer();
    }

    _source = source;

    _position = 0;
    _writeLength = 0;
    _isFlushRequired = false;

    _isFlushOnNewline = source.getFlushOnNewline();

    // Possibly, this should be dependent on the source.  For example,
    // a http: stream should behave the same on Mac as on unix.
    // For now, CauchoSystem.getNewlineString() returns "\n".
    _newline = "\n";
    _newlineBytes = LF_BYTES;

    //_writeEncoding = null;
    //_writeEncodingName = "ISO-8859-1";
  }

  public void setSysNewline()
  {
    _newline = _sysNewline;
    _newlineBytes = _sysNewlineBytes;
  }

  /**
   * Returns the underlying source for the stream.
   *
   * @return the source
   */
  public StreamImpl getSource()
  {
    return _source;
  }

  /**
   * Pushes a filter on the top of the stream stack.
   *
   * @param filter the filter to be added.
   */
  /*
  public void pushFilter(StreamFilter filter)
  {
    filter.init(_source);
    _source = filter;
  }
  */

  /**
   * Returns true if the buffer allows writes.
   *
   * <p>LogStreams, used for debugging, use this feature to
   * test if they should write with very little overhead.
   *
   * <code><pre>
   * if (dbg.canWrite())
   *   dbg.log("some debug value " + expensiveDebugCalculation(foo));
   * </pre></code>
   */
  public boolean canWrite()
  {
    return _source != null && _source.canWrite();
  }

  /**
   * Clears the write buffer
   */
  public void clearWrite()
  {
    if (_source != null) {
      _source.clearWrite();
    }
  }

  public void reuseBuffer(boolean reuse)
  {
    _isReuseBuffer = reuse;
  }

  /**
   * Returns the write buffer.
   */
  @Override
  public byte []getBuffer()
  {
    return _writeBuffer;
  }

  /**
   * Returns the write offset.
   */
  @Override
  public int getBufferOffset()
  {
    return _writeLength;
  }

  /**
   * Sets the write offset.
   */
  @Override
  public void setBufferOffset(int offset)
  {
    _writeLength = offset;
  }

  /**
   * Returns the write size.
   */
  public int getBufferSize()
  {
    return _writeBuffer.length;
  }

  /**
   * Returns the bytes remaining in the buffer.
   */
  public int getRemaining()
  {
    if (_source == null) {
      return 0;
    }
    else {
      return _writeBuffer.length - _writeLength;
    }
  }

  public void setImplicitFlush(boolean implicitFlush)
  {
    _implicitFlush = implicitFlush;
  }

  /**
   * Writes a byte.
   */
  @Override
  public void write(int ch) throws IOException
  {
    int len = _writeLength;
    byte []writeBuffer = _writeBuffer;

    if (writeBuffer.length <= len) {
      if (_source == null) {
        return;
      }

      _writeLength = 0;
      _source.write(writeBuffer, 0, len, false);
      _position += len;
      _isFlushRequired = true;
      len = 0;
    }

    writeBuffer[len] = (byte) ch;

    _writeLength = len + 1;

    if (_implicitFlush)
      flush();
  }

  /**
   * Writes a byte array
   */
  @Override
  public void write(byte []buf, int offset, int length) throws IOException
  {
    final byte []buffer = _writeBuffer;
    final int bufferLength = buffer.length;
    StreamImpl source = _source;
    
    while (true) {
      final int writeLength = _writeLength;
      
      // write through
      if (writeLength == 0 && bufferLength <= length) {
        if (source != null) {
          source.write(buf, offset, length, false);
          _position += length;
          return;
        }
      }

      final int sublen = Math.min(length, bufferLength - writeLength);
      
      System.arraycopy(buf, offset, buffer, writeLength, sublen);
      
      if (length == sublen) {
        _writeLength = writeLength + length;
        
        if (_implicitFlush) {
          flush();
        }
        
        return;
      }
      
      if (source == null) {
        return;
      }
      
      source.write(buffer, 0, bufferLength, false);
      _position += bufferLength;
      _writeLength = 0; 

      length -= sublen;
      offset += sublen;
      
      if (bufferLength <= length) {
        source.write(buf, offset, length, false);
        _position += length;
        return;
      }
    }
  }

  /**
   * Writes a byte buffer
   */
  public void write(ByteBuffer buf, int length) throws IOException
  {
    final byte []buffer = _writeBuffer;
    final int bufferLength = buffer.length;
    StreamImpl source = _source;
    
    while (length > 0) {
      final int writeLength = _writeLength;

      final int sublen = Math.min(length, bufferLength - writeLength);

      buf.get(buffer, writeLength, sublen);
      
      if (length == sublen) {
        _writeLength = writeLength + length;
        
        if (_implicitFlush) {
          flush();
        }
        
        return;
      }
      
      if (source == null) {
        return;
      }
      
      source.write(buffer, 0, bufferLength, false);
      _position += bufferLength;
      _writeLength = 0; 

      length -= sublen;
    }
  }

  /**
   * Writes a byte array
   */
  public void writeThrough(byte []buf, int offset, int length) throws IOException
  {
    if (_writeLength > 0) {
      flush();
    }
    
    StreamImpl source = _source;
    if (source == null) {
      return;
    }
      
    source.write(buf, offset, length, false);
    _position += length;
  }

  /**
   * Flushes and writes the buffer
   */
  public byte []nextBuffer(int offset) throws IOException
  {
    _writeLength = 0;

    if (_source != null) {
      _source.write(_writeBuffer, 0, offset, false);
      _position += offset;
      _isFlushRequired = true;
    }
    else {
      _position += offset;
    }


    if (_implicitFlush) {
      flush();
    }

    return _writeBuffer;
  }

  /**
   * Writes a byte array.
   */
  @Override
  public void write(byte []buf) throws IOException
  {
    write(buf, 0, buf.length);
  }

  /**
   * Flushes the buffer to the source.
   */
  @Override
  public void flush()
    throws IOException
  {
    if (_isDisableFlush || _source == null) {
      return;
    }

    int len = _writeLength;
    if (len > 0) {
      _writeLength = 0;

      _source.write(_writeBuffer, 0, len, false);
      _position += len;
      
      _isFlushRequired = true;
    }

    if (_source != null && _isFlushRequired) {
      _isFlushRequired = false;
      _source.flush();
    }
  }

  /**
   * Flushes the buffer to the disk
   */
  public void flushToDisk() throws IOException
  {
    StreamImpl source = _source;

    if (_isDisableFlush || source == null) {
      return;
    }

    flush();

    source.flushToDisk();
  }

  /**
   * Flushes the buffer to the source.
   */
  public final void flushBuffer()
    throws IOException
  {
    final StreamImpl source = _source;

    if (_isDisableFlush || _source == null)
      return;

    final int len = _writeLength;
    if (len > 0) {
      _writeLength = 0;
      source.write(_writeBuffer, 0, len, false);
      _position += len;
      _isFlushRequired = true;
      source.flushBuffer();
    }
  }

  /**
   * Seeks based on the start
   */
  public void seekStart(long pos)
    throws IOException
  {
    flushBuffer();

    StreamImpl source = _source;

    if (source != null) {
      source.seekStart(pos);
      _position = pos;
    }
  }

  /**
   * Seeks based on the end
   */
  public void seekEnd(long offset)
    throws IOException
  {
    flushBuffer();

    StreamImpl source = _source;

    if (source != null)
      source.seekEnd(offset);

    // XXX : Don't know where end position is

    _position = offset;
  }

  /*
   * Writer methods
   */

  /**
   * Sets the character encoding for writing to this stream.
   * Encoding can be a Java encoding or a mime-encoding.
   */
  /*
  public void setEncoding(String encoding)
    throws UnsupportedEncodingException
  {
    if (_source instanceof ReaderWriterStream)
      encoding = "UTF-8";

    String mimeName = Encoding.getMimeName(encoding);

    if (mimeName != null && mimeName.equals(_writeEncodingName))
      return;

    if (_source != null)
      _source.setWriteEncoding(encoding);

    _writeEncoding = Encoding.getWriteEncoding(encoding);
    _writeEncodingName = mimeName;
  }

  public void setLocale(Locale locale)
    throws UnsupportedEncodingException
  {
    if (_writeEncoding == null && locale != null)
      setEncoding(Encoding.getMimeName(locale));
  }
  */

  /**
   * Returns the mime-encoding used for writing.
   */
  /*
  public String getEncoding()
  {
    if (_source instanceof ReaderWriterStream)
      return ((ReaderWriterStream) _source).getEncoding();
    else
      return _writeEncodingName;
  }

  public String getJavaEncoding()
  {
    return Encoding.getJavaName(getEncoding());
  }
  */

  /**
   * Some streams, like log streams, should be flushed on every println
   * call.  Embedded newlines, i.e. '\n' in strings, do not trigger a
   * flush.
   *
   * @param flushOnNewline set to true if println flushes.
   */
  public void setFlushOnNewline(boolean flushOnNewline)
  {
    this._isFlushOnNewline = flushOnNewline;
  }

  /**
   * Returns the current string used for println newlines
   */
  public String getNewlineString()
  {
    return _newline;
  }

  /**
   * Sets the string to use for println newlines
   */
  public void setNewlineString(String newline)
  {
    if (newline != null) {
      if (this._newline == newline || newline.equals(this._newline)) {
      }
      else if (newline == "\n" || newline.equals("\n")) {
        this._newlineBytes = LF_BYTES;
      }
      else if (newline == "\r\n" || newline.equals("\r\n")) {
        this._newlineBytes = CRLF_BYTES;
      }
      else if (newline == "\r" || newline.equals("\r")) {
        this._newlineBytes = CR_BYTES;
      }
      else {
        this._newlineBytes = newline.getBytes();
      }
      this._newline = newline;
    }
  }

  /**
   * Prints the character buffer to the stream.
   *
   * @param buffer character buffer to write
   * @param offset offset into the buffer to start writes
   * @param length number of characters to write
   */
  public final void print(char []buffer, int offset, int length)
    throws IOException
  {
    if (_source == null)
      return;

    /*
    if (_writeEncoding != null) {
      _isDisableFlush = true;
      _writeEncoding.write(this, buffer, offset, length);
      _isDisableFlush = false;
      return;
    }
    */

    printLatin1(buffer, offset, length);
  }

  /**
   * Prints the character buffer to the stream encoded as latin1.
   *
   * @param buffer character buffer to write
   * @param offset offset into the buffer to start writes
   * @param length number of characters to write
   */
  public final void printLatin1(char []buffer, int offset, int length)
    throws IOException
  {
    StreamImpl source = _source;
    
    if (source == null) {
      return;
    }

    byte []writeBuffer = _writeBuffer;
    int writeLength = _writeLength;

    while (true) {
      int sublen = Math.min(length, writeBuffer.length - writeLength);

      for (int i = sublen - 1; i >= 0; i--) {
        char ch = buffer[offset + i];

        // writeBuffer[writeLength + i] = (byte) ch;
        writeBuffer[writeLength + i] = LATIN1[ch];
        /*
        if (ch < 0x100) {
          writeBuffer[writeLength + i] = (byte) ch;
        }
        else {
          writeBuffer[writeLength + i] = 0x3f;
        }
        */
      }
      
      if (length == sublen) {
        _writeLength = writeLength + sublen;
        return;
      }
      
      writeLength += sublen;
      offset += sublen;
      length -= sublen;

      source.write(writeBuffer, 0, writeLength, false);
      _position += writeLength;
      _isFlushRequired = true;
      writeLength = 0;
    }
  }

  public final void printUtf8(String value, int offset, int length)
    throws IOException
  {
    for (int i = 0; i < length; i++) {
      int ch = value.charAt(offset + i);

      if (ch < 0x80) {
        write(ch);
      }
      else if (ch < 0x800) {
        write(0xc0 | (ch >> 6));
        write(0x80 | (ch & 0x3f));
      }
      else {
        write(0xe0 | (ch >> 12));
        write(0x80 | ((ch >> 6) & 0x3f));
        write(0x80 | (ch & 0x3f));
      }
    }
  }

  /**
   * Prints the character buffer to the stream.
   *
   * @param ch char
   */
  public final void print(char ch)
    throws IOException
  {
    /*
    if (_writeEncoding != null) {
      _isDisableFlush = true;
      _writeEncoding.write(this, ch);
      _isDisableFlush = false;
      return;
    }
    */

    write((byte) ch);
  }

  /**
   * Prints the character buffer to the stream.
   *
   * @param buffer character buffer to write
   */
  public final void print(char []buffer)
    throws IOException
  {
    print(buffer, 0, buffer.length);
  }

  /**
   * Prints the character buffer to the stream.
   *
   * @param segment character buffer to write
   */
  /*
  public final void print(CharSegment segment)
    throws IOException
  {
    print(segment.getBuffer(), segment.getOffset(), segment.getLength());
  }
  */

  /**
   * Prints a string.
   */
  public final void print(String string)
    throws IOException
  {
    printLatin1(string);
    /*
    if (string == null)
      string = "null";

    int length = string.length();
    int offset = 0;

    char []chars = _chars;

    while (length > 0) {
      int sublen = Math.min(length, CHARS_LENGTH);

      string.getChars(offset, offset + sublen, chars, 0);

      print(chars, 0, sublen);

      length -= sublen;
      offset += sublen;
    }
    */
  }

  /**
   * Prints a string.
   */
  public final void printLatin1(String string)
    throws IOException
  {
    if (string == null)
      string = "null";

    int length = string.length();
    int offset = 0;

    char []chars = _chars;
    /*
    if (chars == null) {
      _chars = new char[CHARS_LENGTH];
      chars = _chars;
    }
    */

    while (length > 0) {
      int sublen = Math.min(length, CHARS_LENGTH);

      string.getChars(offset, offset + sublen, chars, 0);

      printLatin1(chars, 0, sublen);

      length -= sublen;
      offset += sublen;
    }
  }

  /**
   * Prints a string.
   */
  public final void XprintLatin1NoLf(String string)
      throws IOException
  {
    if (string == null)
      string = "null";

    int length = string.length();
    int offset = 0;

    char []chars = _chars;

    while (length > 0) {
      int sublen = Math.min(length, chars.length);

      string.getChars(offset, offset + sublen, chars, 0);

      for (int i = sublen - 1; i >= 0; i--) {
        char value = chars[i];

        // server/1kr8
        if (value == '\r' || value == '\n') {
          sublen = i;
          length = sublen;
        }
      }

      printLatin1(chars, 0, sublen);
      
      length -= sublen;
      offset += sublen;
    }
  }
  
  public final void printLatin1NoLf(String string)
      throws IOException
  {
    if (string == null)
      string = "null";

    int length = string.length();
    int offset = 0;

    char []chars = _chars;

    byte []writeBuffer = _writeBuffer;
    int writeLength = _writeLength;
    
    while (length > 0) {
      int sublen = Math.min(length, chars.length);
      sublen = Math.min(sublen, writeBuffer.length - writeLength);

      string.getChars(offset, offset + sublen, chars, 0);

      for (int i = sublen - 1; i >= 0; i--) {
        char ch = chars[i];

        if (0x20 <= ch && ch < 0x80) {
          writeBuffer[writeLength + i] = (byte) ch;
        }
        else if (ch == '\r' || ch == '\n') {
          sublen = i;
          length = sublen;
        }
        else {
          writeBuffer[writeLength + i] = LATIN1[i];
        }
      }
      
      if (length == sublen) {
        _writeLength = writeLength + sublen;
        return;
      }

      length -= sublen;
      offset += sublen;
      writeLength += sublen;
      
      _source.write(writeBuffer, 0, writeLength, false);
      _position += writeLength;
      _isFlushRequired = true;
      writeLength = 0;
    }
  }

  /**
   * Prints a substring.
   *
   * @param string the string to print
   * @param offset starting offset into the string
   * @param length length of the substring to print.
   */
  public final void print(String string, int offset, int length)
    throws IOException
  {
    if (string == null)
      string = "null";

    int charsLength = CHARS_LENGTH;
    char []chars = _chars;
    /*
    if (chars == null) {
      _chars = new char[charsLength];
      chars = _chars;
    }
    */

    while (length > 0) {
      int sublen = Math.min(length, charsLength);

      string.getChars(offset, offset + sublen, chars, 0);

      print(chars, 0, sublen);

      length -= sublen;
      offset += sublen;
    }
  }

  /**
   * Prints a boolean.
   */
  public final void print(boolean b) throws IOException
  {
    print(b ? "true" : "false");
  }

  /**
   * Prints an integer.
   */
  public final void print(int i) throws IOException
  {
    if (i == 0x80000000) {
      print("-2147483648");
      return;
    }

    if (i < 0) {
      write('-');
      i = -i;
    } else if (i < 9) {
      write('0' + i);
      return;
    }

    int length = 0;
    int exp = 10;

    if (i >= 1000000000)
      length = 9;
    else {
      for (; i >= exp; length++) {
        exp = 10 * exp;
      }
    }

    byte []buffer = _writeBuffer;
    int writeLength = _writeLength;

    if (writeLength + length < buffer.length) {
      writeLength += length;
      _writeLength = writeLength + 1;

      for (int j = 0; j <= length; j++) {
        buffer[writeLength - j] = (byte) (i % 10 + '0');
        i = i / 10;
      }
      return;
    }

    if (_bytes == null) {
      _bytes = new byte[32];
    }

    int j = 31;

    while (i > 0) {
      _bytes[--j] = (byte) ((i % 10) + '0');
      i /= 10;
    }

    write(_bytes, j, 31 - j);
  }

  /**
   * Prints a long.
   */
  public final void print(long i) throws IOException
  {
    if (i == 0x8000000000000000L) {
      print("-9223372036854775808");
      return;
    }

    if (_bytes == null) {
      _bytes = new byte[32];
    }

    if (i < 0) {
      write('-');
      i = -i;
    } else if (i == 0) {
      write('0');
      return;
    }

    int j = 31;

    while (i > 0) {
      _bytes[--j] = (byte) ((i % 10) + '0');
      i /= 10;
    }

    write(_bytes, j, 31 - j);
  }

  /**
   * Prints a float.
   */
  public final void print(float f) throws IOException
  {
    print(String.valueOf(f));
  }

  /**
   * Prints an double
   */
  public final void print(double d) throws IOException
  {
    print(String.valueOf(d));
  }

  /**
   * Prints a newline
   */
  public final void println() throws IOException
  {
    write(_newlineBytes, 0, _newlineBytes.length);
    
    if (_isFlushOnNewline) {
      flush();
    }
  }

  /**
   * Prints a character buffer followed by a newline.
   */
  public final void println(char []buf, int offset, int length)
    throws IOException
  {
    print(buf, offset, length);
    write(_newlineBytes, 0, _newlineBytes.length);
    
    if (_isFlushOnNewline) {
      flush();
    }
  }

  /**
   * Prints a string buffer followed by a newline.
   */
  public final void println(String string) throws IOException
  {
    print(string);
    write(_newlineBytes, 0, _newlineBytes.length);
    if (_isFlushOnNewline)
      flush();
  }

  /**
   * Prints a boolean followed by a newline.
   */
  public final void println(boolean b) throws IOException
  {
    println(b ? "true" : "false");
  }

  /**
   * Prints a char followed by a newline.
   */
  public final void println(char ch) throws IOException
  {
    write(ch);
    write(_newlineBytes, 0, _newlineBytes.length);
    
    if (_isFlushOnNewline) {
      flush();
    }
  }

  /**
   * Prints an integer followed by a newline.
   */
  public final void println(int i) throws IOException
  {
    print(i);
    write(_newlineBytes, 0, _newlineBytes.length);
    
    if (_isFlushOnNewline) {
      flush();
    }
  }

  /**
   * Prints a long followed by a newline.
   */
  public final void println(long l) throws IOException
  {
    print(l);
    write(_newlineBytes, 0, _newlineBytes.length);
    
    if (_isFlushOnNewline) {
      flush();
    }
  }

  /**
   * Prints a float followed by a newline.
   */
  public final void println(float f) throws IOException
  {
    println(String.valueOf(f));
  }

  /**
   * Prints a double followed by a newline.
   */
  public final void println(double d) throws IOException
  {
    println(String.valueOf(d));
  }

  /**
   * Prints an object, converted to a string, followed by a newline.
   */
  public final void println(Object o) throws IOException
  {
    if (o == null)
      println("null");
    else
      println(o.toString());
  }

  /**
   * Logs a line to the stream.  log is essentially println, but
   * it doesn't throw an exception and it always flushes the output.
   */
  public final void log(String string)
  {
    try {
      synchronized (this) {
        println(string);
        flush();
      }
    } catch (Exception e) {
    }
  }

  /**
   * Writes the contents of a JDK stream.  Essentially this will copy
   * <code>source</code> to the current stream.
   *
   * @param source InputStream to read.
   */
  public long writeStream(InputStream source) throws IOException
  {
    if (source == null)
      return 0;

    int len;
    int length = _writeBuffer.length;
    long outputLength = 0;

    if (length <= _writeLength) {
      int tmplen = _writeLength;
      _writeLength = 0;
      _source.write(_writeBuffer, 0, tmplen, false);
      _position += tmplen;
      _isFlushRequired = true;
      outputLength += tmplen;
    }

    while ((len = source.read(_writeBuffer,
                              _writeLength,
                              length - _writeLength)) >= 0) {
      _writeLength += len;
      outputLength += len;

      if (length <= _writeLength) {
        int tmplen = _writeLength;
        _writeLength = 0;
        _source.write(_writeBuffer, 0, tmplen, false);
        _position += tmplen;
        _isFlushRequired = true;
      }
    }

    if (_isFlushOnNewline || _implicitFlush)
      flush();

    return outputLength;
  }

  /**
   * Writes the contents of a JDK reader.  Essentially this will copy
   * <code>source</code> to the current stream.
   *
   * @param source InputStream to read.
   */
  public void writeStream(Reader reader) throws IOException
  {
    if (reader == null)
      return;

    char []chars = _chars;
    /*
    if (chars == null) {
      _chars = new char[CHARS_LENGTH];
      chars = _chars;
    }
    */

    int len;
    while ((len = reader.read(chars, 0, CHARS_LENGTH)) > 0) {
      print(chars, 0, len);
    }
  }

  /**
   * Writes the contents of a JDK stream.  Essentially this will copy
   * <code>source</code> to the current stream.
   *
   * @param source InputStream to read.
   */
  public void writeStream(InputStream source, int totalLength)
    throws IOException
  {
    if (source == null)
      return;

    int length = _writeBuffer.length;

    if (length <= _writeLength) {
      int tmplen = _writeLength;
      _writeLength = 0;
      _source.write(_writeBuffer, 0, tmplen, false);
      _position += tmplen;
      _isFlushRequired = true;
    }

    while (totalLength > 0) {
      int sublen = Math.min(totalLength, length - _writeLength);

      sublen = source.read(_writeBuffer, _writeLength, sublen);
      if (sublen < 0)
        break;

      _writeLength += sublen;
      totalLength -= sublen;

      if (length <= _writeLength) {
        int tmplen = _writeLength;
        _writeLength = 0;
        _source.write(_writeBuffer, 0, tmplen, false);
        _position += tmplen;
        _isFlushRequired = true;
      }
    }

    if (_isFlushOnNewline || _implicitFlush) {
      flush();
    }
  }


  /**
   * Writes the contents of a JDK stream.  Essentially this will copy
   * <code>source</code> to the current stream.
   *
   * @param source InputStream to read.
   */
  public void writeStream(StreamImpl source) throws IOException
  {
    if (source == null)
      return;

    int len;
    int length = _writeBuffer.length;

    if (length <= _writeLength) {
      int sublen = _writeLength;
      _writeLength = 0;
      _source.write(_writeBuffer, 0, sublen, false);
      _position += sublen;
      _isFlushRequired = true;
    }

    while ((len = source.read(_writeBuffer,
                              _writeLength,
                              length - _writeLength)) >= 0) {
      _writeLength += len;
      if (length <= _writeLength) {
        int tmplen = _writeLength;
        _writeLength = 0;
        _source.write(_writeBuffer, 0, tmplen, false);
        _position += tmplen;
        _isFlushRequired = true;
      }
    }

    if (_isFlushOnNewline || _implicitFlush) {
      flush();
    }
  }

  /**
   * Copies a file to the stream.
   *
   * @param path Path of the file to copy.
   */
  /*
  public void writeFile(PathImpl path) throws IOException
  {
    StreamImpl is = path.openReadImpl();

    try {
      if (is != null) {
        writeStream(is);
      }
    } finally {
      if (is != null)
        is.close();
    }
  }
  */

  /**
   * Returns true if the stream is closed.
   */
  @Override
  public final boolean isClosed()
  {
    return _source == null || _source.isClosed() || _writeBuffer == null;
  }

  /**
   * Close the stream, first flushing the write buffer.
   */
  @Override
  public final void close() throws IOException
  {
    StreamImpl s;
    
    synchronized (this) {
      s = _source;
      
      _source = null;
    }
    
    if (s == null) {
      return;
    }

    try {
      int len = _writeLength;
      if (len > 0) {
        _writeLength = 0;

        if (s != null) {
          s.write(_writeBuffer, 0, len, true);
        }
      }
    } finally {
      _source = null;

      /*
      if (_writeEncoding != null) {
        _writeEncoding = null;
      }
      */

      if (! _isReuseBuffer) {
        TempBuffer tempWrite = _tempWrite;
        _tempWrite = null;
        _writeBuffer = null;
        
        if (tempWrite != null) {
          tempWrite.freeSelf();
        }
      }

      if (s != null) {
        s.closeWrite();
      }
    }
    
    /*
    if (s != null) {
      PathImpl path = s.getPath();
      
      if (path != null) {
        path.clearStatusCache();
      }
    }
    */
  }

  /**
   * Frees the buffer
   */
  public final void free()
  {
    _source = null;

    TempBuffer tempWrite = _tempWrite;

    _tempWrite = null;
    _writeBuffer = null;

    if (tempWrite != null) {
      TempBuffer.free(tempWrite);
    }
  }


  /**
   * Returns a named attribute.  For example, an HTTP stream
   * may use this to return header values.
   */
  /*
  public Object getAttribute(String name)
    throws IOException
  {
    return _source.getAttribute(name);
  }
  */

  /**
   * Sets a named attribute.  For example, an HTTP stream
   * may use this to set header values.
   */
  /*
  public void setAttribute(String name, Object value)
    throws IOException
  {
    _source.setAttribute(name, value);
  }
  */

  /**
   * Removes a named attribute.
   */
  /*
  public void removeAttribute(String name)
    throws IOException
  {
    _source.removeAttribute(name);
  }
  */

  /**
   * Lists all named attributes.
   */
  /*
  public Iterator<String> getAttributeNames()
    throws IOException
  {
    return _source.getAttributeNames();
  }
  */

  /**
   * Returns the Path which opened this stream.
   */
  /*
  public PathImpl getPath()
  {
    if (_source != null)
      return _source.getPath();
    else
      return null;
  }
  */

  /**
   * Returns the user path which opened this stream.
   *
   * <p>Parsing routines typically use this for error reporting.
   */
  /*
  public String getUserPath()
  {
    return _source.getPath().getUserPath();
  }
  */

  /**
   * Sets a path name associated with the stream.
   */
  /*
  public void setPath(PathImpl path)
  {
    _source.setPath(path);
  }
  */

  /**
   * For testing, sets the system newlines.
   */
  public static void setSystemNewline(String newline)
  {
    _sysNewline = newline;
    _sysNewlineBytes = _sysNewline.getBytes();
  }

  /*
  public boolean lock(boolean shared, boolean block)
  {
    if (! (_source instanceof LockableStream))
      return true;

    LockableStream ls = (LockableStream) _source;
    return ls.lock(shared, block);
  }

  public boolean unlock()
  {
    if (! (_source instanceof LockableStream))
      return true;

    LockableStream ls = (LockableStream) _source;
    return ls.unlock();
  }
  */

  /**
   * Returns the write position.
   */
  public long getPosition()
  {
    return _position + _writeLength;
  }

  /**
   * Clears the position for statistics cases like a socket stream.
   */
  public void clearPosition()
  {
    _position = - _writeLength;
  }

  /**
   * Sets the current write position.
   */

  public boolean setPosition(long pos)
    throws IOException
  {
    if (pos < 0) {
      // Return error on seek to negative stream position

      return false;
    } else {
      // Seek backwards/forwards in the stream

      seekStart(pos);

      if (_source != null)
        return true;
      else
        return false;
    }
  }

  //@Override
  public boolean isMmapEnabled()
  {
    return _source.isMmapEnabled();
  }

  //@Override
  public boolean isSendfileEnabled()
  {
    return _source.isSendfileEnabled();
  }

  /*
  @Override
  public void writeMmap(long mmapAddress, long mmapOffset, int mmapLength)
    throws IOException
  {
    if (_writeLength > 0) {
      int writeLength = _writeLength;
      _writeLength = 0;
      _position += writeLength;

      _source.write(_writeBuffer, 0, writeLength, false);
    }

    _source.writeMmap(mmapAddress, mmapOffset, mmapLength);

    _position += mmapLength;
  }
  */

  //@Override
  public void writeMmap(long mmapAddress,
                        long []mmapBlocks,
                        long mmapOffset,
                        long mmapLength)
    throws IOException
  {
    if (_writeLength > 0) {
      int writeLength = _writeLength;
      _writeLength = 0;

      _source.write(_writeBuffer, 0, writeLength, false);
      _position += writeLength;
    }

    _source.writeMmap(mmapAddress, mmapBlocks, mmapOffset, mmapLength);
    _position += mmapLength;
    _isFlushRequired = true;

    _position += mmapLength;
  }

  //@Override
  public void writeSendfile(byte []fileName, int nameLength,
                            long fileLength)
    throws IOException
  {
    int writeLength = _writeLength;

    if (writeLength > 0) {
      _writeLength = 0;
      _position += writeLength;

      // _source.write(_writeBuffer, 0, writeLength, false);
    }

    _source.writeSendfile(_writeBuffer, 0, writeLength,
                          fileName, nameLength, fileLength);
    _position += fileLength;

    _isFlushRequired = true;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _source + "]";
  }
  
  static {
    int charsLength;
    
    if (TempBuffer.isSmallmem()) {
      charsLength = 128;
    }
    else {
      charsLength = 256;
    }
    
    CHARS_LENGTH = charsLength;
    
    LATIN1 = new byte[65536];
    Arrays.fill(LATIN1, (byte) '?');
    
    for (int i = 0; i < 0x100; i++) {
      LATIN1[i] = (byte) i;
    }
  }
}
