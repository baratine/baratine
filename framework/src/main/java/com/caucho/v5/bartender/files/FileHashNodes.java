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

package com.caucho.v5.bartender.files;


/**
 * Calculates a path hash.
 */
public class FileHashNodes implements FileHash
{
  private String _prefix;
  
  FileHashNodes(String prefix)
  {
    _prefix = prefix;
  }

  @Override
  public int hash(String path)
  {
    int pathLen = path.length();
    int prefixLen = _prefix.length();
    
    if (pathLen < prefixLen) {
      return 0;
    }
    
    int hash = 0;
    
    for (int i = prefixLen; i < pathLen; i++) {
      char ch = path.charAt(i);
      
      if ('0' <= ch && ch <= '9') {
        hash = 10 * hash + ch - '0';
      }
      else {
        return hash;
      }
    }
    
    return hash;
  }
}
