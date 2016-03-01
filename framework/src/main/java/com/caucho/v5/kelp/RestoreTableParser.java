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

package com.caucho.v5.kelp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import com.caucho.v5.h3.H3;
import com.caucho.v5.h3.InH3;
import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.util.L10N;

/**
 * Restores a kelp table from tha parsed archive.
 */
public class RestoreTableParser
{
  private static final L10N L = new L10N(RestoreTableParser.class);
  
  private static final String ARCHIVE_VERSION = ArchiveTableKelp.ARCHIVE_VERSION;
  
  private static final String HEADER = ArchiveTableKelp.HEADER;
  private static final String DATA = ArchiveTableKelp.DATA;
  
  private final Path _path;
  
  private boolean _isZip = true;
  private OutFactoryH3 _serializer;
    
  public RestoreTableParser(Path path)
  {
    Objects.requireNonNull(path);
    
    _path = path;
    
    _serializer = H3.newOutFactory().get();
  }
  
  public RestoreTableParser zip(boolean isZip)
  {
    _isZip = isZip;
    
    return this;
  }
    
  public void exec()
    throws IOException
  {
    try (InputStream is = Files.newInputStream(_path)) {
      InputStream zIs = is;
      
      if (_isZip) {
        GZIPInputStream zipIs = new GZIPInputStream(is);
        
        zIs = zipIs;
      }
      
      String archiveVersion = readLine(zIs);
      
      if (! ARCHIVE_VERSION.equals(archiveVersion)) {
        throw new IOException(L.l("mismatched kelp archive version file={0} with server={1}",
                                  archiveVersion, ARCHIVE_VERSION));
      }
      
      try (InH3 hIn = _serializer.in(zIs)) {
        String section;
      
        while ((section = hIn.readString()) != null) {
          switch (section) {
          case HEADER:
            readHeader(hIn);
          
            if (isHeaderOnly()) {
              return;
            }
            break;
          
          case DATA:
            readData(hIn);
            break;
          
          default:
            throw new IOException(L.l("Unknown section {0}", section));
          }
        }
      }
      
      if (_isZip) {
        zIs.close();
      }
    }
  }
  
  protected boolean isHeaderOnly()
  {
    return false;
  }
  
  protected boolean readHeader(InH3 hIn)
    throws IOException
  {
    String key;
    
    while ((key = hIn.readString()) != null) {
      Object value = hIn.readObject();
      
      parseHeader(key, value);
    }
    
    return false;
  }
  
  protected void parseHeader(String key, Object value)
    throws IOException
  {
    
  }
  
  protected void readData(InH3 hIn)
    throws IOException
  {
  }
  
  protected IOException error(String msg, Object ...args)
  {
    return new IOException(L.l(msg, args));
  }
  
  protected String readLine(InputStream is)
    throws IOException
  {
    StringBuilder sb = new StringBuilder();
    
    int ch;
    
    while ((ch = is.read()) > 0 && ch != '\n') {
      sb.append((char) ch);
    }
    
    return sb.toString();
  }
}
