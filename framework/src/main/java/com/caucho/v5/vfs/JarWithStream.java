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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.make.CachedDependency;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.LruCache;

/**
 * Jar is a cache around a jar file to avoid scanning through the whole
 * file on each request.
 *
 * <p>When the Jar is created, it scans the file and builds a directory
 * of the Jar entries.
 */
public class JarWithStream extends Jar
{
  private static ZipEntry NULL_ZIP = new ZipEntry("null");
  
  private LruCache<String,ZipEntry> _zipEntryCache
    = new LruCache<String,ZipEntry>(64);
  
  private AtomicInteger _changeSequence = new AtomicInteger();
  
  // saved last modified time
  private long _lastModified;
  // saved length
  private long _length;
  // last time the file was checked
  private long _lastTime;

  /**
   * Creates a new Jar.
   *
   * @param backing canonical path
   */
  JarWithStream(PathImpl backing)
  {
    super(backing);
  }

  @Override
  public int getChangeSequence()
  {
    return _changeSequence.get();
  }

  /**
   * Lists all the files in this directory.
   */
  @Override
  public String []list(String path) throws IOException
  {
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    
    if (! path.endsWith("/")) {
      path = path + "/";
    }

    ArrayList<String> names = new ArrayList<>();
    
    try (ZipInputStream zIn = new ZipInputStream(getBacking().openRead())) {
      ZipEntry entry;
      
      while ((entry = zIn.getNextEntry()) != null) {
        String name = entry.getName();

        if (name.startsWith(path)) {
          String tail = name.substring(path.length());
          int p = tail.indexOf('/');
          
          if (p >= 0) {
            tail = tail.substring(0, p);
          }
          
          if (! tail.equals("") && ! names.contains(tail)) {
            names.add(tail);
          }
        }
      }
    }
    
    String []list = new String[names.size()];
    
    names.toArray(list);
    
    return list;
  }
  
  public StreamImpl openReadImpl(String pathName)
    throws IOException
  {
    if (pathName.length() > 0 && pathName.charAt(0) == '/') {
      pathName = pathName.substring(1);
    }

    ZipEntry entry;
    StreamImpl zipIs = null;
    
    ZipInputStream zIn = null;

    try {
      zIn = new ZipInputStream(getBacking().openRead());
      
      while ((entry = zIn.getNextEntry()) != null) {
        if (entry.getName().equals(pathName)) {
          zipIs = new ZipStreamImpl(entry, zIn, pathName);
          
          zIn = null;
          
          return zipIs;
        }
      }
      
      throw new FileNotFoundException("jar:" + getBacking().getURL() + "!" + pathName);
    } finally {
      if (zIn != null) {
        zIn.close();
      }
    }
  }

  /**
   * Clears any cached JarFile.
   */
  @Override
  public void clearCache()
  {
  }

  @Override
  public ZipEntry getZipEntry(String path)
    throws IOException
  {
    ZipEntry entry = _zipEntryCache.get(path);
    
    if (entry != null && isCacheValid()) {
      if (entry == NULL_ZIP)
        return null;
      else
        return entry;
    }
    
    entry = getZipEntryImpl(path);
    
    if (entry != null) {
      _zipEntryCache.put(path, entry);
    }
    else {
      _zipEntryCache.put(path, NULL_ZIP);
    }
    
    return entry;
  }

  private ZipEntry getZipEntryImpl(String path)
    throws IOException
  {
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    
    try (InputStream is = getBacking().openRead()) {
      try (ZipInputStream zIn = new ZipInputStream(is)) {
        ZipEntry entry;

        while ((entry = zIn.getNextEntry()) != null) {
          if (entry.getName().equals(path)) {
            ZipEntry entryResult = new ZipEntry(entry);
            
            if (entry.getSize() < 0 && ! entry.isDirectory()) {
              long size = 0;
              TempBuffer tBuf = TempBuffer.allocate();
              byte []buffer = tBuf.buffer();
              
              int sublen;
              while ((sublen = zIn.read(buffer, 0, buffer.length)) > 0) {
                size += sublen;
              }
              
              tBuf.freeSelf();
              
              entryResult.setSize(size);
            }
            
            return entryResult;
          }
        }
      }
    }
    
    return null;
  }
  
  /**
   * Returns the last modified time for the path.
   *
   *
   * @return the last modified time of the jar in milliseconds.
   */
  private boolean isCacheValid()
  {
    long now = CurrentTime.getCurrentTime();

    if ((now - _lastTime < 100) && ! CurrentTime.isTest())
      return true;

    long oldLastModified = _lastModified;
    long oldLength = _length;
    
    long newLastModified = getBacking().getLastModified();
    long newLength = getBacking().length();
    
    _lastTime = now;

    if (newLastModified == oldLastModified && newLength == oldLength) {
      _lastTime = now;
      return true;
    }
    else {
      _changeSequence.incrementAndGet();
      
      // If the file has changed, close the old file
      clearCache();
      
      _zipEntryCache.clear();
      
      _lastModified = newLastModified;
      _length = newLength;
      
      _lastTime = now;

      return false;
    }
  }

  @Override
  public void close()
  {
    removeEvent();
  }
  
  @Override
  public void removeEvent()
  {
    clearCache();
  }

  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    else if (o == null || ! getClass().equals(o.getClass()))
      return false;

    JarWithStream jar = (JarWithStream) o;

    return getBacking().equals(jar.getBacking());
  }

  @Override
  public String toString()
  {
    return getBacking().toString();
  }

  /**
   * StreamImpl to read from a ZIP file.
   */
  private class ZipStreamImpl extends StreamImpl {
    private ZipEntry _zipEntry;
    private InputStream _zis;
    
    private String _pathName;

    /**
     * Create the new stream  impl.
     *
     * @param zipFile
     * @param zipEntry
     * @param zis the underlying zip stream.
     * @param pathName
     */
    ZipStreamImpl(ZipEntry zipEntry,
                  ZipInputStream zIn,
                  String pathName)
    {
      _zipEntry = zipEntry;
      _zis = zIn;
      
      // System.out.println("JAR-OPEN: " + pathName + " " + zis);
      // setPath(path);
    }
    
    public ZipEntry getZipEntry()
    {
      return _zipEntry;
    }

    /**
     * Returns true since this is a read stream.
     */
    @Override
    public boolean canRead() { return true; }
 
    @Override
    public int getAvailable() throws IOException
    {
      InputStream zis = _zis;
      
      if (zis == null)
        return -1;
      
      return _zis.available();
    }
 
    @Override
    public int read(byte []buf, int off, int len) throws IOException
    {
      InputStream zis = _zis;
      
      if (zis == null)
        return -1;
      
      return zis.read(buf, off, len);
    }
 
    @Override
    public void close() throws IOException
    {
      InputStream zis = _zis;
      _zis = null;
      
      try {
        IoUtil.close(zis);
//      //  System.out.println("JAR-CLOSE: " + zis + " " + _pathName);
      } catch (Throwable e) {
      }
    }

    /*
    @Override
    protected void finalize()
      throws IOException
    {
      close();
    }
    */
  }

  private class JarDepend extends CachedDependency
    implements PersistentDependency {
    private Depend _depend;
    private boolean _isDigestModified;
    
    /**
     * Create a new dependency.
     *
     * @param depend the source file
     */
    JarDepend(Depend depend)
    {
      _depend = depend;
    }
    
    /**
     * Create a new dependency.
     *
     * @param depend the source file
     */
    JarDepend(Depend depend, long digest)
    {
      _depend = depend;

      _isDigestModified = _depend.getDigest() != digest;
    }

    /**
     * Returns the underlying depend.
     */
    Depend getDepend()
    {
      return _depend;
    }

    /**
     * Returns true if the dependency is modified.
     */
    @Override
    public boolean isModifiedImpl()
    {
      if (_isDigestModified || _depend.isModified()) {
        _changeSequence.incrementAndGet();
        return true;
      }
      else
        return false;
    }

    /**
     * Returns true if the dependency is modified.
     */
    @Override
    public boolean logModified(Logger log)
    {
      return _depend.logModified(log);
    }

    /**
     * Returns the string to recreate the Dependency.
     */
    @Override
    public String getJavaCreateString()
    {
      String sourcePath = _depend.getPath().getPath();
      long digest = _depend.getDigest();
      
      return ("new com.caucho.v5.vfs.Jar.createDepend(" +
          "com.caucho.v5.vfs.Vfs.lookup(\"" + sourcePath + "\"), " +
          digest + "L)");
    }

    public String toString()
    {
      return "Jar$JarDepend[" + _depend.getPath() + "]";
    }
  }

  static class JarDigestDepend implements PersistentDependency {
    private JarDepend _jarDepend;
    private Depend _depend;
    private boolean _isDigestModified;
    
    /**
     * Create a new dependency.
     *
     * @param jarDepend the source file
     */
    JarDigestDepend(JarDepend jarDepend, long digest)
    {
      _jarDepend = jarDepend;
      _depend = jarDepend.getDepend();

      _isDigestModified = _depend.getDigest() != digest;
    }

    /**
     * Returns true if the dependency is modified.
     */
    public boolean isModified()
    {
      return _isDigestModified || _jarDepend.isModified();
    }

    /**
     * Returns true if the dependency is modified.
     */
    public boolean logModified(Logger log)
    {
      return _depend.logModified(log) || _jarDepend.logModified(log);
    }

    /**
     * Returns the string to recreate the Dependency.
     */
    public String getJavaCreateString()
    {
      String sourcePath = _depend.getPath().getPath();
      long digest = _depend.getDigest();
      
      return ("new " + Jar.class.getName() + "Jar.createDepend(" +
              VfsOld.class.getName() + ".lookup(\"" + sourcePath + "\"), " +
              digest + "L)");
    }
  }
}
