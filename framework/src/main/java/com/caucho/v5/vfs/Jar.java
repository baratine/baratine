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

import java.io.IOException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.make.CachedDependency;
import com.caucho.v5.util.CacheListener;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.LruCache;

/**
 * Jar is a cache around a jar file to avoid scanning through the whole
 * file on each request.
 *
 * <p>When the Jar is created, it scans the file and builds a directory
 * of the Jar entries.
 */
abstract public class Jar implements CacheListener
{
  private static final Logger log = Logger.getLogger(Jar.class.getName());
  private static final L10N L = new L10N(Jar.class);
  
  private static LruCache<PathImpl,Jar> _jarCache;

  private static EnvironmentLocal<Integer> _jarSize
    = new EnvironmentLocal<Integer>("caucho.vfs.jar-size");
  
  private static ZipEntry NULL_ZIP = new ZipEntry("null");
  
  private LruCache<String,ZipEntry> _zipEntryCache
    = new LruCache<String,ZipEntry>(64);
  
  private PathImpl _backing;
  private boolean _backingIsFile;

  private AtomicInteger _changeSequence = new AtomicInteger();
  
  private JarDepend _depend;
  
  // saved last modified time
  private long _lastModified;
  // saved length
  private long _length;
  // last time the file was checked
  private long _lastTime;

  private Boolean _isSigned;

  Jar(PathImpl backing)
  {
    Objects.requireNonNull(backing);
    
    if (backing instanceof JarPath)
      throw new IllegalStateException();
    
    _backing = backing;
    
    updateBacking();
  }
  
  /**
   * Return a Jar for the path.  If the backing already exists, return
   * the old jar.
   */
  static Jar create(PathImpl backing)
  {
    if (_jarCache == null) {
      int size = 256;

      Integer iSize = _jarSize.get();

      if (iSize != null)
        size = iSize.intValue();
      
      _jarCache = new LruCache<PathImpl,Jar>(size);
    }
    
    Jar jar = _jarCache.get(backing);
    
    if (jar != null) {
      jar.updateBacking();
    }
    else if (backing.getScheme().equals("file")) {
      jar = new JarWithFile(backing);
      jar = _jarCache.putIfNew(backing, jar);
    }
    else {
      jar = new JarWithStream(backing);
      jar = _jarCache.putIfNew(backing, jar);
    }
    
    return jar;
  }

  /**
   * Return a Jar for the path.  If the backing already exists, return
   * the old jar.
   */
  static Jar getJar(PathImpl backing)
  {
    if (_jarCache != null) {
      Jar jar = _jarCache.get(backing);

      return jar;
    }

    return null;
  }

  /**
   * Return a Jar for the path.  If the backing already exists, return
   * the old jar.
   */
  public static PersistentDependency createDepend(PathImpl backing)
  {
    Jar jar = create(backing);

    return jar.getDepend();
  }

  /**
   * Return a Jar for the path.  If the backing already exists, return
   * the old jar.
   */
  public static PersistentDependency createDepend(PathImpl backing, long digest)
  {
    Jar jar = create(backing);

    return new JarDigestDepend(jar.getJarDepend(), digest);
  }
  
  protected void updateBacking()
  {
  }

  /**
   * Returns the backing path.
   */
  PathImpl getBacking()
  {
    return _backing;
  }

  /**
   * Returns the dependency.
   */
  public PersistentDependency getDepend()
  {
    return getJarDepend();
  }

  /**
   * Returns the dependency.
   */
  private JarDepend getJarDepend()
  {
    if (_depend == null || _depend.isModified())
      _depend = new JarDepend(new Depend(getBacking()));

    return _depend;
  }
  
  public int getChangeSequence()
  {
    return _changeSequence.get();
  }

  private boolean isSigned()
  {
    Boolean isSigned = _isSigned;

    if (isSigned != null)
      return isSigned;

    try {
      Manifest manifest = getManifest();

      if (manifest == null) {
        _isSigned = Boolean.FALSE;
        return false;
      }

      Map<String,Attributes> entries = manifest.getEntries();

      if (entries == null) {
        _isSigned = Boolean.FALSE;
        return false;
      }
      
      for (Attributes attr : entries.values()) {
        for (Object key : attr.keySet()) {
          String keyString = String.valueOf(key);

          if (keyString.contains("Digest")) {
            _isSigned = Boolean.TRUE;

            return true;
          }
        }
      }
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }

    _isSigned = Boolean.FALSE;
    
    return false;
  }

  /**
   * Returns Manifest
   *
   */
  public Manifest getManifest()
    throws IOException
  {
    return null;
  }

  /**
   * Returns any certificates.
   */
  public Certificate []getCertificates(String path)
  {
    return null;
  }

  /**
   * Returns true if the entry exists in the jar.
   *
   * @param path the path name inside the jar.
   */
  public boolean exists(String path)
  {
    // server/249f, server/249g
    // XXX: facelets vs issue of meta-inf (i.e. lower case)

    try {
      ZipEntry entry = getZipEntry(path);

      return entry != null;
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }

    return false;
  }

  /**
   * Returns true if the entry is a directory in the jar.
   *
   * @param path the path name inside the jar.
   */
  public boolean isDirectory(String path)
  {
    try {
      ZipEntry entry = getZipEntry(path);

      return entry != null && entry.isDirectory();
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }

    return false;
  }

  /**
   * Returns true if the entry is a file in the jar.
   *
   * @param path the path name inside the jar.
   */
  public boolean isFile(String path)
  {
    try {
      ZipEntry entry = getZipEntry(path);

      return entry != null && ! entry.isDirectory();
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }

    return false;
  }

  /**
   * Returns the last-modified time of the entry in the jar file.
   *
   * @param path full path to the jar entry
   * @return the length of the entry
   */
  public long getLastModified(String path)
  {
    try {
      // this entry time can cause problems ...
      ZipEntry entry = getZipEntry(path);

      return entry != null ? entry.getTime() : -1;
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }

    return  -1;
  }

  /**
   * Returns the length of the entry in the jar file.
   *
   * @param path full path to the jar entry
   * @return the length of the entry
   */
  public long getLength(String path)
  {
    try {
      ZipEntry entry = getZipEntry(path);
      
      long length = entry != null ? entry.getSize() : -1;

      return length;
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
      
      return -1;
    }
  }

  /**
   * Readable if the jar is readable and the path refers to a file.
   */
  public boolean canRead(String path)
  {
    try {
      ZipEntry entry = getZipEntry(path);

      return entry != null && ! entry.isDirectory();
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);

      return false;
    }
  }

  /**
   * Can't write to jars.
   */
  public boolean canWrite(String path)
  {
    return false;
  }

  /**
   * Lists all the files in this directory.
   */
  abstract public String []list(String path) throws IOException;

  /**
   * Opens a stream to an entry in the jar.
   *
   * @param path relative path into the jar.
   */
  public StreamImpl openReadImpl(PathImpl path) 
    throws IOException
  {
    String pathName = path.getPath();
    
    return openReadImpl(pathName);
  }
  
  abstract public StreamImpl openReadImpl(String pathName)
    throws IOException;

  /**
   * Clears any cached JarFile.
   */
  public void clearCache()
  {
  }

  abstract public ZipEntry getZipEntry(String path)
    throws IOException;

  public ZipFile getZipFile()
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void closeZipFile(ZipFile zipFile)
  {
  }
  
  /**
   * Returns the last modified time for the path.
   *
   *
   * @return the last modified time of the jar in milliseconds.
   */
  private long getLastModifiedImpl()
  {
    isCacheValid();
    
    return _lastModified;
  }
  
  /**
   * Returns the last modified time for the path.
   *
   *
   * @return the last modified time of the jar in milliseconds.
   */
  private boolean isCacheValid()
  {
    long now = CurrentTime.currentTime();

    if ((now - _lastTime < 100) && ! CurrentTime.isTest())
      return true;

    long oldLastModified = _lastModified;
    long oldLength = _length;
    
    long newLastModified = _backing.getLastModified();
    long newLength = _backing.length();
    
    _lastTime = now;

    if (newLastModified == oldLastModified && newLength == oldLength) {
      _lastTime = now;
      return true;
    }
    else {
      _changeSequence.incrementAndGet();
      
      // If the file has changed, close the old file
      clearCache();
      
      _depend = null;
      _isSigned = null;
      _zipEntryCache.clear();
      
      _lastModified = newLastModified;
      _length = newLength;
      
      _lastTime = now;

      return false;
    }
  }

  public void close()
  {
    removeEvent();
  }
  
  @Override
  public void removeEvent()
  {
    clearCache();
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    else if (o == null || ! getClass().equals(o.getClass()))
      return false;

    Jar jar = (Jar) o;

    return _backing.equals(jar._backing);
  }

  /**
   * Clears all the cached files in the jar.  Needed to avoid some
   * windows NT issues.
   */
  public static void clearJarCache()
  {
    LruCache<PathImpl,Jar> jarCache = _jarCache;
    
    if (jarCache == null)
      return;

    ArrayList<Jar> jars = new ArrayList<Jar>();
    
    synchronized (jarCache) {
      Iterator<Jar> iter = jarCache.values();
      
      while (iter.hasNext())
        jars.add(iter.next());
    }

    for (int i = 0; i < jars.size(); i++) {
      Jar jar = jars.get(i);
        
      if (jar != null)
        jar.clearCache();
    }
  }

  @Override
  public String toString()
  {
    return _backing.toString();
  }

  class JarDepend extends CachedDependency
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
      
      return ("new " + Jar.class.getName() + ".createDepend(" +
              VfsOld.class.getName() + ".lookup(\"" + sourcePath + "\"), " +
              digest + "L)");
    }
  }
}
