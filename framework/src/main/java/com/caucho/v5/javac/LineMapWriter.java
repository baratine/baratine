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

import com.caucho.v5.util.IntMap;
import com.caucho.v5.vfs.WriteStreamOld;

import java.io.IOException;
import java.util.Iterator;

/**
 * LineMapWriter writes the *.map files.
 */
public class LineMapWriter {
  private WriteStreamOld _os;
  private String _sourceType = "JSP";

  /**
   * Creates the writer.
   */
  public LineMapWriter(WriteStreamOld os)
  {
    _os = os;
  }

  /**
   * Sets the source-type, e.g. JSP
   */
  public void setSourceType(String type)
  {
    _sourceType = type;
  }

  /**
   * Writes the line map
   */
  public void write(LineMap lineMap)
    throws IOException
  {
    _os.println("SMAP");
    _os.println(lineMap.getDestFilename());
    _os.println(_sourceType);
    _os.println("*S " + _sourceType);

    IntMap fileMap = new IntMap();

    _os.println("*F");
    Iterator<LineMap.Line> iter = lineMap.iterator();
    while (iter.hasNext()) {
      LineMap.Line line = iter.next();

      String filename = line.getSourceFilename();
      int index = fileMap.get(filename);
      if (index < 0) {
        index = fileMap.size() + 1;
        fileMap.put(filename, index);

        if (filename.indexOf('/') >= 0) {
          int p = filename.lastIndexOf('/');

          _os.println("+ " + index + " " + filename.substring(p + 1));
          // XXX: _os.println(filename);

          if (filename.startsWith("/"))
            _os.println(filename.substring(1));
          else
            _os.println(filename);
        }
        else
          _os.println(index + " " + filename);
      }
    }
    
    _os.println("*L");
    int size = lineMap.size();
    int lastIndex = 0;
    for (int i = 0; i < size; i++) {
      LineMap.Line line = lineMap.get(i);

      String filename = line.getSourceFilename();
      int index = fileMap.get(filename);

      String fileMarker = "";

      _os.print(line.getSourceLine());
      _os.print("#" + index);

      if (line.getRepeatCount() > 1)
        _os.print("," + line.getRepeatCount());

      _os.print(":");
      _os.print(line.getDestinationLine());
      if (line.getDestinationIncrement() > 1)
        _os.print("," + line.getDestinationIncrement());
      _os.println();
    }
    
    _os.println("*E");
  }
}
