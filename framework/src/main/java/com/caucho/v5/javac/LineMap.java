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

package com.caucho.v5.javac;

import com.caucho.v5.util.CharBuffer;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * LineMap maps generated code lines back to the source lines.
 *
 * <p>The LineMap tells the user the line in the JSP or XSL
 * file that is broken, as opposed to the generated Java line.
 */
public class LineMap implements Serializable {
  private String _dstFilename;
  private String _srcFilename;

  private String _srcType = "JSP";
  
  private String _lastSrcFilename;
  
  private ArrayList<Line> _lines = new ArrayList<Line>();

  /**
   * Null-arg constructor for serialization.
   */
  public LineMap()
  {
  }

  public LineMap(String dstFilename, String srcFilename)
  {
    int tail = dstFilename.lastIndexOf('/');
    if (tail < 0)
      _dstFilename = dstFilename;
    else
      dstFilename = dstFilename.substring(tail + 1);
    
    _srcFilename = srcFilename;
    _lastSrcFilename = _srcFilename;
  }

  public LineMap(String dstFilename)
  {
    int tail = dstFilename.lastIndexOf('/');
    if (tail < 0)
      _dstFilename = dstFilename;
    else
      _dstFilename = dstFilename.substring(tail + 1); 
  }

  public void setSourceType(String type)
  {
    _srcType = type;
  }

  public String getSourceType()
  {
    return _srcType;
  }

  public String getDestFilename()
  {
    return _dstFilename;
  }

  public String getLastSourceFilename()
  {
    return _lastSrcFilename;
  }

  /**
   * Adds a new line map entry.
   *
   * <p>LineMap assumes that dstLine increases monotonically.
   *
   * @param srcFilename the source filename, e.g. the included filename
   * @param srcLine the source line, e.g. the line in the included file
   * @param dstLine the line of the generated file.
   *
   * @return true if a new entry is needed
   */
  public boolean add(String srcFilename, int srcLine, int dstLine)
  {
    return add(srcFilename, srcLine, dstLine, false);
  }
  
  public boolean add(String srcFilename,
                     int srcLine,
                     int dstLine,
                     boolean isPreferLast)
  {
    _lastSrcFilename = srcFilename;

    if (_lines.size() > 0) {
      Line line = _lines.get(_lines.size() - 1);

      if (line.add(srcFilename, srcLine, dstLine, isPreferLast)) {
        if (_lines.size() > 1) {
          Line prevLine = _lines.get(_lines.size() - 2);

          if (prevLine.merge(line)) {
            _lines.remove(_lines.size() - 1);
          }
        }

        return true;
      }

      if (line.getLastDestinationLine() + 1 < dstLine) {
        _lines.add(new Line(line.getLastSourceLine(),
                            line.getSourceFilename(),
                            1,
                            line.getLastDestinationLine() + 1,
                            dstLine - line.getLastDestinationLine()));
      }
    }
    
    _lines.add(new Line(srcFilename, srcLine, dstLine));

    return true;
  }

  /**
   * Adds a line from the smap
   */
  public void addLine(int startLine, String sourceFile, int repeatCount,
                      int outputLine, int outputIncrement)
  {
    _lines.add(new Line(startLine, sourceFile, repeatCount,
                        outputLine, outputIncrement));
  }
  
  public void add(int srcLine, int dstLine)
  {
    add(_lastSrcFilename, srcLine, dstLine);
  }

  public Iterator<Line> iterator()
  {
    return _lines.iterator();
  }

  public int size()
  {
    return _lines.size();
  }

  public Line get(int i)
  {
    return _lines.get(i);
  }

  public Line getLine(int line)
  {
    for (int i = 0; i < _lines.size(); i++) {
      Line map = _lines.get(i);

      if (map._dstLine <= line && line <= map.getLastDestinationLine()) {
        return map;
      }
    }

    return null;
  }

  /**
   * Converts an error in the generated file to a CompileError based on
   * the source.
   */
  public String convertError(String filename, int line,
                             int column, String message)
  {
    String srcFilename = null;
    int destLine = 0;
    int srcLine = 0;
    
    for (int i = 0; i < _lines.size(); i++) {
      Line map = _lines.get(i);

      if (filename != null && ! filename.endsWith(_dstFilename)) {
      }
      else if (map._dstLine <= line && line <= map.getLastDestinationLine()) {
        srcFilename = map._srcFilename;
        srcLine = map.getSourceLine(line);
      }
    }

    if (srcFilename != null)
      return srcFilename + ":" + srcLine + ": " + message;
    else
      return filename + ":" + line + ": " + message;
  }

  public String convertLine(String filename, int line)
  {
    Line bestLine = getLine(line);

    if (bestLine != null)
      return bestLine.getSourceFilename() + ":" + bestLine.getSourceLine(line);
    else
      return filename + ":" + line;
  }

  /**
   * Filter a stack trace, replacing names.
   */
  public void printStackTrace(Throwable e, OutputStream os)
  {
    CharArrayWriter writer = new CharArrayWriter();
    PrintWriter pw = new PrintWriter(writer);
    
    e.printStackTrace(pw);

    pw.close();
    char []array = writer.toCharArray();

    CharBuffer cb = filter(array);

    if (os != null) {
      byte []b = cb.toString().getBytes();

      try {
        os.write(b, 0, b.length);
      } catch (IOException e1) {
      }
    } else
      System.out.println(cb);
  }

  /**
   * Filter a stack trace, replacing names.
   */
  public void printStackTrace(Throwable e, PrintWriter os)
  {
    CharArrayWriter writer = new CharArrayWriter();
    PrintWriter pw = new PrintWriter(writer);
    
    e.printStackTrace(pw);

    pw.close();
    char []array = writer.toCharArray();

    CharBuffer cb = filter(array);

    if (os != null)
      os.print(cb.toString());
    else
      System.out.println(cb);
  }

  /**
   * Parses a Java stack trace, converting files and line numbers when
   * possible.
   */
  private CharBuffer filter(char []array)
  {
    CharBuffer buf = new CharBuffer();
    CharBuffer fun = new CharBuffer();
    CharBuffer file = new CharBuffer();

    int i = 0;
    while (i < array.length) {
      fun.clear();
      file.clear();
      int start = i;
      int end;
      for (end = i; end < array.length && array[end] != '\n'; end++) {
      }

      for (; i < end && Character.isWhitespace(array[i]); i++) {
        fun.append(array[i]);
      }

      // skip 'at'
      for (; i < end && ! Character.isWhitespace(array[i]); i++) {
        fun.append(array[i]);
      }

      if (! fun.endsWith("at")) {
        for (i = start; i < end; i++) {
          buf.append(array[i]);
        }
        i = end + 1;

        buf.append('\n');

        continue;
      }

      for (; i < end && Character.isWhitespace(array[i]); i++) {
      }

      fun.clear();
      for (; i < end && ! Character.isWhitespace(array[i]) &&
             array[i] != '('; i++) {
        fun.append(array[i]);
      }

      if (i < end && array[i] == '(')
        i++;

      for (; i < end && ! Character.isWhitespace(array[i]) &&
             array[i] != ':' && array[i] != ')'; i++) {
        file.append(array[i]);
      }
      
      int line = -1;
      if (i < end && array[i] == ':') {
        line = 0;
        for (i++; i < end && array[i] >= '0' && array[i] <= '9'; i++) {
          line = 10 * line + array[i] - '0';
        }
      }

      for (; i < end && ! Character.isWhitespace(array[i]) &&
             array[i] != ':' && array[i] != ')'; i++) {
        file.append(array[i]);
      }

      buf.append("\tat ");
      buf.append(fun);
      buf.append("(");
      String dstFile = file.toString();

      if (dstFile.equals(_dstFilename)) {
        convertError(buf, line);
      }
      else {
        buf.append(file);
        if (line > 0) {
          buf.append(":");
          buf.append(line);
        }
      }
      buf.append(array, i, end - i);
      buf.append('\n');
      i = end + 1;
    }

    return buf;
  }

  /**
   * Maps a destination line to an error location.
   *
   * @param buf CharBuffer to write the error location
   * @param line generated source line to convert.
   */
  private void convertError(CharBuffer buf, int line)
  {
    String srcFilename = null;
    int destLine = 0;
    int srcLine = 0;
    int srcTailLine = Integer.MAX_VALUE;
    
    for (int i = 0; i < _lines.size(); i++) {
      Line map = (Line) _lines.get(i);

      if (map._dstLine <= line && line <= map.getLastDestinationLine()) {
        srcFilename = map._srcFilename;
        destLine = map._dstLine;
        srcLine = map.getSourceLine(line);
        break;
      }
    }

    if (srcFilename != null) {
    }
    else if (_lines.size() > 0)
      srcFilename = ((Line) _lines.get(0))._srcFilename;
    else
      srcFilename = "";

    buf.append(srcFilename);
    if (line >= 0) {
      buf.append(":");
      buf.append(srcLine + (line - destLine));
    }
  }

  public static class Line implements Serializable {
    String _srcFilename;
    int _srcLine;
    
    int _dstLine;
    int _dstIncrement = 1;
    
    int _repeat = 1;

    /**
     * Constructor for serialization.
     */
    public Line()
    {
    }

    Line(String srcFilename, int srcLine, int dstLine)
    {
      _srcFilename = srcFilename;
      _srcLine = srcLine;
      _dstLine = dstLine;
    }

    Line(int srcLine, String srcFilename, int repeat,
         int dstLine, int dstIncrement)
    {
      _srcFilename = srcFilename;
      _srcLine = srcLine;
      _dstLine = dstLine;
      _repeat = repeat;
      _dstIncrement = dstIncrement;
    }

    /**
     * Tries to add a new location.
     */
    boolean add(String srcFilename, int srcLine, int dstLine,
                boolean isPreferLast)
    {
      if (_srcFilename != null
          && (! _srcFilename.equals(srcFilename) || srcFilename == null))
        return false;

      if (dstLine <= _dstLine) {
        // php/180u
        if (! isPreferLast)
          return true;
        else if (_dstIncrement == 1 && _repeat == 1) {
          _srcLine = srcLine;
          return true;
        }
        else if (_repeat > 1) {
          _repeat--;
          return false;
        }
        else if (_dstIncrement > 1) {
          _dstIncrement--;
          return false;
        }
        else
          return true;
      }

      if (srcLine == _srcLine) {
        _dstIncrement = dstLine - _dstLine + 1;

        return true;
      }
      else if (dstLine - _dstLine == (srcLine - _srcLine) * _dstIncrement) {
        _repeat = srcLine - _srcLine + 1;

        return true;
      }
      else if (srcLine == _srcLine + 1 && _repeat == 1) {
        _dstIncrement = dstLine - _dstLine;

        return false;
      }
      /*
      else if (_repeat == 1 && _dstIncrement <= 1) {
        _dstIncrement = dstLine - 1 - _dstLine;
        if (_dstIncrement < 0)
          _dstIncrement = 1;

        return true;
      }
      */

      return false;
    }

    /**
     * Tries to merge two lines
     */
    boolean merge(Line next)
    {
      if (_srcFilename != null && ! _srcFilename.equals(next._srcFilename))
        return false;

      else if (_dstIncrement != next._dstIncrement)
        return false;
      else if (getLastDestinationLine() + 1 != next._dstLine)
        return false;
      else if (getLastSourceLine() + 1 != next._srcLine)
        return false;
      else {
        _repeat += next._repeat;

        return true;
      }
    }

    public String getSourceFilename()
    {
      return _srcFilename;
    }

    public int getSourceLine()
    {
      return _srcLine;
    }

    /**
     * Returns the source line.
     */
    public int getSourceLine(int dstLine)
    {
      return _srcLine + (dstLine - _dstLine) / _dstIncrement;
    }

    public int getRepeatCount()
    {
      return _repeat;
    }

    public int getDestinationLine()
    {
      return _dstLine;
    }

    public int getLastSourceLine()
    {
      return _srcLine + _repeat - 1;
    }

    public int getLastDestinationLine()
    {
      return _dstLine + _dstIncrement * _repeat - 1;
    }

    public int getDestinationIncrement()
    {
      return _dstIncrement;
    }

    public String toString()
    {
      return "Line[src:" + _srcFilename + ":" + _srcLine + ",dst:" + _dstLine + "]";
    }
  }
}
