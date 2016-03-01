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

package com.caucho.v5.javac;

import com.caucho.v5.io.i18n.ByteToChar;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.util.CharCursor;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.StringCharCursor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads javac error messages and parses them into a usable format.
 */
class JavacErrorParser extends ErrorParser {
  private static final L10N L = new L10N(JavacErrorParser.class);
  private static final Logger log
    = Logger.getLogger(JavacErrorParser.class.getName());
  
  private CharBuffer _token = new CharBuffer();
  private CharBuffer _buf = new CharBuffer();
  private ByteToChar _lineBuf = ByteToChar.create();

  private AbstractJavaCompiler _compiler;
  private String _file;
  private String _encoding;

  public JavacErrorParser()
    throws UnsupportedEncodingException
  {
    this(null, null, null);
  }

  public JavacErrorParser(AbstractJavaCompiler compiler,
                          String file,
                          String encoding)
    throws UnsupportedEncodingException
  {
    if (encoding == null)
      encoding = System.getProperty("file.encoding");

    _compiler = compiler;
    _file = file;
    _encoding = encoding;
  
    _lineBuf.setEncoding(encoding);
  }

  String parseErrors(InputStream is, LineMap lineMap)
    throws IOException
  {
    CharBuffer result = new CharBuffer();

    int ch = is.read();
    for (; ch >= 0; ch = is.read()) {
      try {
        _lineBuf.clear();

        for (; ch >= 0 && ch != '\n'; ch = is.read()) {
          try {
            _lineBuf.addByte((byte) ch);
          } catch (Exception e) {
            log.log(Level.FINER, e.toString(), e);

            log.warning(L.l("{0}: javac error parsing encoding error detected while parsing Javac output.  The javac error encoding appears to be different from the Server's configured encoding '{1}'\n  {2}",
                            _file,
                            _encoding,
                            e.toString()));
          }
        }
      
        _lineBuf.addByte('\n');

        String lineString = _lineBuf.getConvertedString();

        StringCharCursor cursor = new StringCharCursor(lineString);

        String line = parseLine(cursor, lineMap);
        if (line == null)
          result.append(lineString);
        else
          result.append(line);
      } catch (Exception e) {
        log.log(Level.WARNING, e.toString(), e);

        result.append(e).append("\n");
      }
    }

    return result.toString();
  }

  /**
   * Scans errors.
   *
   * <p>Javac errors look like "filename:line: message"
   */
  String parseLine(CharCursor is, LineMap lineMap)
    throws IOException
  {
    int ch = is.read();

    _buf.clear();

    String filename = null;
    int line = 0;

    // Take 3: match /.*:\d+:/
    _token.clear();

  line:
    for (; ch != is.DONE; ch = is.read()) {
      while (ch == ':') {
        line = 0;
        for (ch = is.read(); ch >= '0' && ch <= '9'; ch = is.read())
          line = 10 * line + ch - '0';
        if (ch == ':' && line > 0) {
          filename = _token.toString();
          break line;
        }
        else {
          _token.append(':');
          if (line > 0)
            _token.append(line);
        }
      }

      if (ch != is.DONE)
        _token.append((char) ch);
    }

    if (filename == null)
      return null;

    int column = 0;

    // skip added junk like jikes extra "emacs" style columns
    for (; ch != is.DONE && ch != ' '; ch = is.read()) {
    }
    
    for (; ch == ' '; ch = is.read()) {
    }

    // now gather the message
    _buf.clear();
    for (; ch != is.DONE; ch = is.read())
      _buf.append((char) ch);

    String message = _buf.toString();

    if (lineMap != null)
      return lineMap.convertError(filename, line, 0, message);
    else
      return filename + ":" + line + ": " + message;
  }
}
