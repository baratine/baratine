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

package com.caucho.v5.loader;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.caucho.v5.vfs.JarPath;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.ZipScanner;

/**
 * Class loader which checks for changes in class files and automatically
 * picks up new jars.
 */
public class JarMap {
  private static final Logger log
    = Logger.getLogger(JarMap.class.getName());
  
  private static final AtomicReference<JarList> _key
    = new AtomicReference<JarList>();
  
  // list of the jars in the directory
  private JarList []_entries;
  private int _mask;
  private int _size;

  /**
   * Creates a new jar map
   */
  public JarMap()
  {
    _entries = new JarList[1024];
    _mask = _entries.length - 1;
  }

  public JarList add(String name, JarEntry entry)
  {
    // XXX: efficiency issues

    int length = name.length();
    char []cbuf = new char[length];
    name.getChars(0, length, cbuf, 0);

    return add(cbuf, length, entry);
  }

  public JarList get(String name)
  {
    JarList key = _key.getAndSet(null);

    if (key == null)
      key = new JarList();
    
    key.init(name);

    int hash = key.hashCode() & _mask;
    
    for (JarList ptr = _entries[hash]; ptr != null; ptr = ptr._nextHash) {
      if (ptr.equals(key)) {
        _key.set(key);
        
        return ptr;
      }
    }

    _key.set(key);

    return null;
  }

  public Iterator<String> keys()
  {
    return new JarKeyIterator();
  }

  private void resize()
  {
  }

  public void scan(PathImpl jar)
  {
    JarEntry jarEntry = new JarEntry(JarPath.create(jar));

    scan(jar, jarEntry);
  }

  public void scan(PathImpl jar, JarEntry jarEntry)
  {
    ZipScanner scan = null;
      
    try {
      boolean isScan = true;
      boolean isValidScan = false;

      try {
        if (isScan && jar.canRead()) {
          scan = new ZipScanner(jar);
        }

        if (scan != null && scan.open()) {
          scanZip(scan, jarEntry);

          isValidScan = true;
        }
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);

        isScan = false;
      }

      if (! isValidScan && jar.canRead()) {
        try (ZipFile file = new ZipFile(jar.getNativePath())) {
          Enumeration<? extends ZipEntry> e = file.entries();
          while (e.hasMoreElements()) {
            ZipEntry entry = e.nextElement();
            String name = entry.getName();

            add(name, jarEntry);

            // server/249b
            /*
            if (name.endsWith("/"))
            name = name.substring(0, name.length() - 1);
             */
          }
        }
      }
    } catch (IOException e) {
      if (jar.canRead()) {
        log.log(Level.WARNING, jar.getURL() + ": " + e.toString(), e);
      }
      else {
        log.log(Level.FINER, jar.getURL() + ": " + e.toString(), e);
      }
    } finally {
      if (scan != null) {
        scan.close();
      }
    }
  }
  
  private void scanZip(ZipScanner scan, JarEntry jarEntry) 
    throws IOException
  {
    while (scan.next()) {
      char []buffer = scan.getNameBuffer();
      int length = scan.getNameLength();
    
      // server/249b, env/009r
      if (length > 0 && buffer[length - 1] == '/') {
        length--;
      }

      add(buffer, length, jarEntry);
    }
  }
  
  public JarList add(char []name, int length, JarEntry entry)
  {
    if (_entries.length <= _size) {
      resize();
    }
    
    JarList key = new JarList();

    key.init(name, length);

    key._entry = entry;

    int hash = key.hashCode() & _mask;

    for (JarList ptr = _entries[hash]; ptr != null; ptr = ptr._nextHash) {
      if (ptr.equals(key)) {
        key.clearName();
        key._next = ptr._next;
        ptr._next = key;

        return ptr;
      }
    }

    _size++;
    
    key.copyName();
    key._nextHash = _entries[hash];
    _entries[hash] = key;

    return key;
  }

  public void clear()
  {
    _size = 0;

    for (int i = 0; i < _entries.length; i++) {
      _entries[i] = null;
    }
  }

  static final class JarList {
    private JarList _nextHash;
    
    private JarList _next;
    private JarEntry _entry;

    private char []_name;
    private int _length;

    JarList()
    {
    }
    
    JarList(JarEntry entry, JarList next)
    {
      _entry = entry;
      _next = next;
    }

    void init(char []name, int length)
    {
      _name = name;
      _length = length;
    }

    void init(String name)
    {
      int length = name.length();
      
      if (_name == null || _name.length <= length)
        _name = new char[length];

      name.getChars(0, length, _name, 0);

      _length = length;
    }

    int getLength()
    {
      return _length;
    }

    void clearName()
    {
      _name = null;
      _length = 0;
    }
    
    void copyName()
    {
      char []newName = new char[_length];
      System.arraycopy(_name, 0, newName, 0, _length);
      _name = newName;
    }

    String getNameString()
    {
      return new String(_name, 0, _length);
    }

    JarEntry getEntry()
    {
      return _entry;
    }

    JarList getNext()
    {
      return _next;
    }

    @Override
    public int hashCode()
    {
      char []name = _name;
      int hash = 37;
      
      for (int i = _length - 1; i >= 0; i--) {
        hash = 65521 * hash + name[i];
      }

      return hash;
    }

    @Override
    public boolean equals(Object o)
    {
      if (this == o)
        return true;
      else if (! (o instanceof JarList))
        return false;

      JarList entry = (JarList) o;
      
      return equals(entry);
    }

    public boolean equals(JarList entry)
    {
      if (this == entry)
        return true;

      int length = _length;

      if (length != entry._length)
        return false;

      char []nameA = _name;
      char []nameB = entry._name;

      for (int i = length - 1; i >= 0; i--) {
        if (nameA[i] != nameB[i])
          return false;
      }

      return true;
    }

    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
              + "[" + new String(_name, 0, _length) + "]");
    }
  }

  class JarKeyIterator implements Iterator<String> {
    private int _index;
    private JarList _entry;

    JarKeyIterator()
    {
      for (; _index < _entries.length; _index++) {
        _entry = _entries[_index];

        if (_entry != null)
          break;
      }
    }

    public boolean hasNext()
    {
      return _entry != null;
    }

    public String next()
    {
      JarList next = _entry;
      
      if (_entry != null)
        _entry = _entry._nextHash;
      
      if (_entry == null) {
        for (_index++; _index < _entries.length; _index++) {
          _entry = _entries[_index];

          if (_entry != null)
            break;
        }
      }

      if (next != null)
        return next.getNameString();
      else
        return null;
    }

    public void remove()
    {
      throw new UnsupportedOperationException(getClass().getName());
    }
  }
}
