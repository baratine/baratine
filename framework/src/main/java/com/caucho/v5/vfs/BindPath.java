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
import java.util.Map;

import com.caucho.v5.io.StreamImpl;

class BindPath extends FilesystemPath {
  private Node _node;
  private PathImpl _backing;
  
  BindPath(PathImpl backing)
  {
    this(null, "/", null, "/", null, backing);

    _root = this;
    if (backing instanceof FilesystemPath)
      _separatorChar = ((FilesystemPath) backing)._separatorChar;
  }

  /**
   * @param path canonical path
   */
  private BindPath(BindPath root,
                   String userPath, Map<String,Object> attributes,
                   String path, Node node, PathImpl backing)
  {
    super(root, userPath, path);

    if (backing == null)
      throw new IllegalArgumentException("backing must not be null");

    if (node == null)
      node = new Node("", backing);

    if (backing == null)
      backing = node._backing;

    _node = node;
    _backing = backing;
    
    if (backing instanceof FilesystemPath)
      _separatorChar = ((FilesystemPath) backing)._separatorChar;
  }

  @Override
  public PathImpl fsWalk(String userPath,
                     Map<String,Object> attributes,
                     String path)
  {
    Node ptr = _node;

    int offset = 0;
    while (offset + 1 < path.length()) {
      if (ptr.firstChild == null)
        return ptr._backing.lookup(path.substring(offset), attributes);

      int p = path.indexOf(_separatorChar, offset + 1);
      String segment;
      if (p == -1)
        segment = path.substring(offset + 1);
      else
        segment = path.substring(offset + 1, p);

      Node next = ptr.findChild(segment);

      if (next == null)
        return ptr._backing.lookup(path.substring(offset), attributes);

      offset = p;
      ptr = next;
    }
    
    return new BindPath(this, userPath, attributes, path, _node, null);
  }

  public String getScheme()
  {
    return _root.getScheme();
  }

  public boolean exists()
  {
    return _backing.exists();
  }

  public boolean isDirectory()
  {
    return _backing.isDirectory();
  }

  public boolean isFile()
  {
    return _backing.isFile();
  }

  public long length()
  {
    return _backing.length();
  }

  public long getLastModified()
  {
    return _backing.getLastModified();
  }

  public boolean canRead()
  {
    return _backing.canRead();
  }

  public boolean canWrite()
  {
    return _backing.canWrite();
  }
  
  public String []list() throws IOException
  {
    String []list = _backing.list();
    
    if (_node.firstChild == null)
      return list;

    String []newList = new String[list.length + _node.size()];

    int i = 0;
    for (Node ptr = _node.firstChild; ptr != null; ptr = ptr.next)
      newList[i++] = ptr.name;

    for (int j = 0; j < list.length; j++)
      newList[i++] = list[j++];

    return newList;
  }
  
  public boolean mkdir()
    throws IOException
  {
    return _backing.mkdir();
  }
  
  public boolean mkdirs()
    throws IOException
  {
    return _backing.mkdirs();
  }
  
  public boolean remove()
    throws IOException
  {
    return _backing.remove();
  }
  
  public boolean renameTo(PathImpl path)
    throws IOException
  {
    return _backing.renameTo(path);
  }

  public StreamImpl openReadImpl() throws IOException
  {
    return _backing.openReadImpl();
  }

  public StreamImpl openWriteImpl() throws IOException
  {
    return _backing.openWriteImpl();
  }

  public StreamImpl openReadWriteImpl() throws IOException
  {
    return _backing.openReadWriteImpl();
  }

  public StreamImpl openAppendImpl() throws IOException
  {
    return _backing.openAppendImpl();
  }

  void bind(String path, PathImpl context)
  {
    Node ptr = _node;

    int offset = 0;
    while (offset + 1 < path.length()) {
      int p = path.indexOf(_separatorChar, offset + 1);
      String segment;
      if (p == -1)
        segment = path.substring(offset + 1);
      else
        segment = path.substring(offset + 1, p);

      Node next = ptr.findChild(segment);

      if (next == null)
        next = ptr.addChild(segment, ptr._backing);
    }

    ptr._backing = context;
  }

  public int hashCode()
  {
    return _backing.hashCode();
  }

  public boolean equals(Object b)
  {
    return _backing.equals(b);
  }

  public String toString()
  {
    return _backing.toString();
  }

  static class Node {
    Node parent;
    Node firstChild;
    Node next;

    String name;
    PathImpl _backing;

    Node(String name, PathImpl backing)
    {
      this.name = name;
      this._backing = backing;
    }

    int size()
    {
      int size = 0;

      for (Node ptr = firstChild; ptr != null; ptr = ptr.next)
        size++;

      return size;
    }

    Node findChild(String name)
    {
      for (Node ptr = firstChild; ptr != null; ptr = ptr.next) {
        if (ptr.name.equals(name))
          return ptr;
      }

      return null;
    }

    Node addChild(String name, PathImpl backing)
    {
      for (Node ptr = firstChild; ptr != null; ptr = ptr.next) {
        if (ptr.name.equals(name)) {
          ptr._backing = backing;
          return ptr;
        }
      }

      Node node = new Node(name, backing);
      node.next = firstChild;
      node.parent = this;
      firstChild = node;

      return node;
    }
  }
}

