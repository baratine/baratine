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

package com.caucho.v5.vfs;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.ByteArrayBuffer;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public class MemoryPath extends FilesystemPath {
  private static L10N L = new L10N(MemoryPath.class);

  private Node _rootNode;

  protected MemoryPath(FilesystemPath root,
                       String userPath, Map<String,Object> attributes,
                       String path)
  {
    super(root, userPath, path);

    if (root instanceof MemoryPath)
      _rootNode = ((MemoryPath) root)._rootNode;
    else
      _root = this;
  }

  public MemoryPath()
  {
    this(null, "/", null, "/");

    _root = this;
    _rootNode = new Node("", null, Node.DIR);
  }

  public PathImpl fsWalk(String userPath,
                     Map<String,Object> attributes,
                     String path)
  {
    return new MemoryPath(_root, userPath, attributes, path);
  }

  public String getScheme()
  {
    return "memory";
  }

  public String getURL()
  {
    return getScheme() + ":" + getFullPath();
  }
  
  public String getNativePath()
  {
    // http/1071 -- XXX: needed by quercus
    // return getFullPath();
    return getURL();
  }

  @Override
  public boolean isPathCacheable()
  {
    return false;
  }

  public boolean exists()
  {
    synchronized (_rootNode) {
      return lookupAll() != null;
    }
  }

  private Node lookupAll()
  {
    String fullPath = getFullPath();

    int head = 0;
    Node node = _rootNode;

    while (node != null && head < fullPath.length()) {
      int tail = fullPath.indexOf('/', head);

      if (tail == -1) {
        if (head < fullPath.length())
          return node.lookup(fullPath.substring(head));
        else
          return node;
      }

      if (head != tail)
        node = node.lookup(fullPath.substring(head, tail));

      head = tail + 1;
    }

    return node;
  }

  private Node lookupAllButTail()
  {
    String fullPath = getFullPath();

    int head = 0;
    Node node = this._rootNode;

    while (node != null && head < fullPath.length()) {
      int tail = fullPath.indexOf('/', head);

      if (tail == -1 || tail == fullPath.length() - 1)
        return node;

      if (head != tail)
        node = node.lookup(fullPath.substring(head, tail));

      head = tail + 1;
    }

    return node;
  }

  public boolean isDirectory()
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      return node != null && node.type == node.DIR;
    }
  }

  public boolean isFile()
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      return node != null && node.type == node.FILE;
    }
  }

  public boolean isObject()
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      return node != null && node.type == node.OBJECT;
    }
  }

  public boolean setExecutable(boolean isExecutable)
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      if (node != null && (node.type == node.FILE || node.type == node.DIR)) {
        node.isExecutable = isExecutable;
        return true;
      }
      else
        return false;
    }
  }

  public boolean isExecutable()
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      if (node != null && (node.type == node.FILE || node.type == node.DIR))
        return node.isExecutable;
      else
        return false;
    }
  }

  public long length()
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      if (node != null && node.type == node.FILE)
        return ((ByteArrayBuffer) node.data).length();
      else
        return 0;
    }
  }

  public long getLastModified()
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      return node == null ? 0 : node.lastModified;
    }
  }

  public boolean canRead()
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      return node != null;
    }
  }

  public boolean canWrite()
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      return node != null;
    }
  }

  public String []list()
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      if (node == null || node.data != null)
        return new String[0];

      ArrayList<String> a = new ArrayList<String>();
      for (Node child = node.firstChild; child != null; child = child.next) {
        a.add(child.name);
      }

      return (String []) a.toArray(new String[a.size()]);
    }
  }

  // XXX: could have iterator

  private boolean mkdir(boolean parent)
  {
    synchronized (_rootNode) {
      String fullPath = getFullPath();

      int head = 0;
      Node node = this._rootNode;

      while (node != null && head < fullPath.length()) {
        int tail = fullPath.indexOf('/', head);
        String name;

        if (tail == head) {
          head = tail + 1;
          continue;
        }
        if (tail == -1) {
          name = fullPath.substring(head);
          if (node.lookup(name) != null)
            return false;
          node.createDir(name);
          return true;
        }

        name = fullPath.substring(head, tail);
        Node next = node.lookup(name);

        if (next == null && parent)
          next = node.createDir(name);

        if (next == null || next.type != next.DIR)
          return false;

        node = next;
        head = tail + 1;
      }

      return false;
    }
  }

  public boolean mkdir()
  {
    return mkdir(false);
  }

  public boolean mkdirs()
  {
    return mkdir(true);
  }

  public boolean remove()
  {
    synchronized (_rootNode) {
      Node node = lookupAllButTail();
      String tail = getTail();

      if (node == null)
        return false;

      Node child = node.lookup(tail);
      if (child == null || child.firstChild != null)
        return false;

      node.remove(tail);

      return true;
    }
  }

  public boolean renameTo(PathImpl path)
  {
    synchronized (_rootNode) {
      if (! (path instanceof MemoryPath))
        return false;

      MemoryPath file = (MemoryPath) path;
      if (_rootNode != file._rootNode)
        return false;

      Node oldParent = lookupAllButTail();
      if (oldParent == null)
        return false;

      Node child = oldParent.lookup(getTail());
      if (child == null)
        return false;

      Node newParent = file.lookupAllButTail();
      if (newParent == null || newParent.type != Node.DIR)
        return false;

      if (newParent.lookup(file.getTail()) != null)
        return false;

      oldParent.remove(getTail());
      child.name = file.getTail();
      newParent.create(child);

      return true;
    }
  }

  @Override
  public StreamImpl openReadImpl() throws IOException
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      if (node == null)
        throw new FileNotFoundException(getPath());
      else if (node.type != node.FILE)
        throw new IOException("is directory: " + getPath());

      return new MemoryStream(node, (ByteArrayBuffer) node.data, false);
    }
  }

  @Override
  public StreamImpl openWriteImpl() throws IOException
  {
    return openWriteImpl(false);
  }

  @Override
  public StreamImpl openAppendImpl() throws IOException
  {
    return openWriteImpl(true);
  }

  private StreamImpl openWriteImpl(boolean append)
    throws IOException
  {
    synchronized (_rootNode) {
      Node node = lookupAllButTail();
      String tail = getTail();

      if (node == null || node.type != Node.DIR)
        throw new IOException(L.l("can't create file {0}", getFullPath()));

      Node child = node.lookup(tail);
      if (child == null)
        child = node.createFile(tail, new ByteArrayBuffer(256));
      else if (! append) {
        node.remove(tail);
        child = node.createFile(tail, new ByteArrayBuffer(256));
      }
      else if (child.type != child.FILE)
        throw new IOException(L.l("can't create file {0}", getFullPath()));
      return new MemoryStream(child, (ByteArrayBuffer) child.data, true);
    }
  }

  @Override
  public Object getValue() throws IOException
  {
    synchronized (_rootNode) {
      Node node = lookupAll();

      if (node == null || node.type != node.OBJECT)
        throw new IOException("no such object: " + getFullPath().toString());

      return node.data;
    }
  }

  @Override
  synchronized public void setValue(Object object) throws IOException
  {
    synchronized (_rootNode) {
      Node node = lookupAllButTail();
      String tail = getTail();

      if (node == null || node.type != Node.DIR)
        throw new IOException(L.l("can't set object {0}", getFullPath()));

      Node child = node.lookup(tail);
      if (child == null)
        child = node.createObject(tail, object);
      else if (child.type == child.OBJECT)
        child.data = object;
      else
        throw new IOException(L.l("can't set object {0}", getFullPath()));
    }
  }

  public PathImpl copyCache()
  {
    return null;
  }

  public MemoryPath copyDeep()
  {
    MemoryPath path = new MemoryPath();

    path._rootNode = _rootNode.copy();

    return path;
  }

  @Override
  public boolean equals(Object o)
  {
    if (o == null || ! getClass().equals(o.getClass()))
      return false;

    MemoryPath mp = (MemoryPath) o;

    return getURL().equals(mp.getURL()) && _rootNode == mp._rootNode;
  }

  private static class Node {
    static final int DIR = 0;
    static final int FILE = DIR + 1;
    static final int OBJECT = FILE + 1;

    String name;
    Node next;
    Node firstChild;
    long lastModified;
    int type;
    Object data;
    boolean isExecutable;

    Node(String name, Object data, int type)
    {
      if (name == null)
        throw new NullPointerException();
      this.name = name;
      this.data = data;
      this.type = type;
      this.lastModified = CurrentTime.currentTime();
    }

    Node lookup(String name)
    {
      for (Node node = firstChild; node != null; node = node.next) {
        if (node.name.equals(name)) {
          return node;
        }
      }

      return null;
    }

    private Node create(String name, Object data, int type)
    {
      for (Node node = firstChild; node != null; node = node.next) {
        if (node.name.equals(name))
          return null;
      }

      Node newNode = new Node(name, data, type);
      newNode.next = firstChild;
      firstChild = newNode;
      lastModified = CurrentTime.currentTime();

      return newNode;
    }

    Node createDir(String name)
    {
      return create(name, null, DIR);
    }

    Node createFile(String name, ByteArrayBuffer data)
    {
      return create(name, data, FILE);
    }

    Node createObject(String name, Object data)
    {
      return create(name, data, OBJECT);
    }

    Node create(Node newNode)
    {
      newNode.next = firstChild;
      firstChild = newNode;

      return newNode;
    }

    boolean remove(String name)
    {
      Node last = null;
      for (Node node = firstChild; node != null; node = node.next) {
        if (node.name.equals(name)) {
          if (node.firstChild != null)
            return false;

          if (last != null)
            last.next = node.next;
          else
            firstChild = node.next;

          return true;
        }

        last = node;
      }

      return false;
    }

    Node copy()
    {
      Node newNode = new Node(name, data, type);

      if (type == DIR) {
        for (Node child = firstChild; child != null; child = child.next) {
          Node newChild = child.copy();

          newChild.next = newNode.firstChild;
          newNode.firstChild = newChild;
        }
      }

      return newNode;
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + this.name + "]";
    }
  }

  public class MemoryStream extends StreamImpl {
    Node _node;
    ByteArrayBuffer _bb;
    int _offset;
    boolean _write;

    MemoryStream(Node node, ByteArrayBuffer bb, boolean write)
    {
      //setPath(MemoryPath.this);

      _node = node;
      if (write)
        node.lastModified = CurrentTime.currentTime();
      _write = write;

      _bb = bb;
    }

    @Override
    public int getAvailable()
    {
      return _bb.length() - _offset;
    }

    @Override
    public boolean canRead() { return true; }

    @Override
    public int read(byte []buf, int bufOffset, int length) throws IOException
    {
      synchronized (_bb) {
        int sublen = _bb.length() - _offset;
        if (length < sublen)
          sublen = length;

        if (sublen <= 0)
          return -1;

        System.arraycopy(_bb.getBuffer(), _offset, buf, bufOffset, sublen);
        _offset += sublen;

        return sublen;
      }
    }

    public int getPosition()
    {
      return _offset;
    }

    @Override
    public void seekStart(long pos)
    {
      _offset = (int) pos;
      if (_offset < 0)
        _offset = 0;
      if (_offset > _bb.length())
        _offset = _bb.length();
    }

    @Override
    public boolean canWrite() { return true; }

    /**
     * Writes a buffer to the underlying stream.
     *
     * @param buffer the byte array to write.
     * @param offset the offset into the byte array.
     * @param length the number of bytes to write.
     * @param isEnd true when the write is flushing a close.
     */
    @Override
    public void write(byte []buf, int offset, int length, boolean isEnd)
      throws IOException
    {
      synchronized (_bb) {
        _bb.add(buf, offset, length);
      }

      _node.lastModified = CurrentTime.currentTime();
    }

    @Override
    public void flushToDisk()
    {
    }

    public String toString()
    {
      return getClass().getSimpleName() + "[" + _node + "]";
    }
  }
}
