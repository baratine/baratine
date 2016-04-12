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

import io.baratine.files.Watch;
import io.baratine.service.Cancel;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.io.Dependency;
import com.caucho.v5.io.OutputStreamWithBuffer;
import com.caucho.v5.io.Path;
import com.caucho.v5.io.Source;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.util.Crc64;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.LruCache;
import com.caucho.v5.util.RandomUtil;

/**
 * A virtual filesystem path, essentially represented by a URL.
 * Its API resembles a combination of  the JDK File object and the URL object.
 *
 * <p>Paths are, in general, given with the canonical file separator of
 * forward slash, '/'.  The filesystems will take care of any necessary
 * translation.
 *
 * <p>Currently available filesystems:
 * <dl>
 * <dt>file:/path/to/file<dd>Java file
 * <dt>http://host:port/path/name?query<dd>HTTP request
 * <dt>tcp://host:port<dd>Raw TCP connection
 * <dt>mailto:user@host?subject=foo&cc=user2<dd>Mail to a user.
 * <dt>log:/group/subgroup/item<dd>Logging based on the configuration file.
 * <dt>stdout:<dd>System.out
 * <dt>stderr:<dd>System.err
 * <dt>null:<dd>The equivalent of /dev/null
 * </dl>
 */
public abstract class PathImpl implements Path, Source //, Comparable<PathImpl> {
{
  protected final static L10N L = new L10N(PathImpl.class);

  private static final Integer LOCK = new Integer(0);
  
  private static final LruCache<PathKey,PathImpl> _pathLookupCache
    = new LruCache<PathKey,PathImpl>(8192);

  private static boolean _isTestWindows;

  protected static char _separatorChar = File.separatorChar;
  protected static char _pathSeparatorChar = File.pathSeparatorChar;
  private static String _newline;

  private static final AtomicReference<PathKey> _key
    = new AtomicReference<PathKey>();

  private static SchemeMap _defaultSchemeMap;

  static long _startTime;

  protected SchemeMap _schemeMap = _defaultSchemeMap;

  /**
   * Creates a new Path object.
   *
   * @param root the new Path root.
   */
  protected PathImpl(PathImpl root)
  {
    if (root != null)
      _schemeMap = root._schemeMap;
    else
      _schemeMap = getDefaultSchemeMap();
  }

  private SchemeMap getDefaultSchemeMap()
  {
    synchronized (PathImpl.class) {
      if (_defaultSchemeMap == null) {
        _defaultSchemeMap = new SchemeMap();
        
        createDefaultSchemeMap(_defaultSchemeMap);
      }

      return _defaultSchemeMap;
    }
  }

  /**
   * Creates a new Path object.
   *
   * @param root the new Path root.
   */
  protected PathImpl(SchemeMap map)
  {
    _schemeMap = map;
  }

  /**
   * Looks up a new path based on the old path.
   *
   * @param name relative url to the new path
   * @return The new path.
   */
  public final PathImpl lookup(String name)
  {
    return lookup(name, null);
  }

  /**
   * Looks up a new path based on the old path.
   *
   * @param name relative url to the new path
   * @return The new path.
   */
  public final PathImpl lookupChild(String name)
  {
    return lookupImpl(name, null, false);
  }

  /**
   * Looks up a path by a URL.
   */
  public final PathImpl lookup(URL url)
  {
    String name = URLDecoder.decode(url.toString());

    return lookup(name, null);
  }

  /**
   * Returns a new path relative to the current one.
   *
   * <p>Path only handles scheme:xxx.  Subclasses of Path will specialize
   * the xxx.
   *
   * @param userPath relative or absolute path, essentially any url.
   * @param newAttributes attributes for the new path.
   *
   * @return the new path or null if the scheme doesn't exist
   */
  public PathImpl lookup(String userPath, Map<String,Object> newAttributes)
  {
    if (newAttributes != null) {
      return lookupImpl(userPath, newAttributes, true);
    }
    else if (userPath == null) {
      return this;
    }

    PathImpl path = getCache(userPath);

    if (path != null) {
      return path;
    }

    path = lookupImpl(userPath, null, true);

    if (_startTime == 0) {
      _startTime = System.currentTimeMillis();

      putCache(userPath, path);
    }

    return path;
  }

  protected PathImpl getCache(String subPath)
  {
    if (! isPathCacheable())
      return null;

    PathKey key = _key.getAndSet(null);

    if (key == null)
      key = new PathKey();

    key.init(this, subPath);

    PathImpl path = _pathLookupCache.get(key);

    _key.set(key);

    if (path != null)
      return path.cacheCopy();
    else
      return null;
  }

  protected void putCache(String subPath, PathImpl path)
  {
    if (! isPathCacheable())
      return;

    PathImpl copy = path.cacheCopy();

    if (copy != null) {
      _pathLookupCache.putIfNew(new PathKey(this, subPath), copy);
    }
  }

  /**
   * Returns true if the path itself is cacheable
   */
  protected boolean isPathCacheable()
  {
    return false;
  }

  /**
   * Returns a new path relative to the current one.
   *
   * <p>Path only handles scheme:xxx.  Subclasses of Path will specialize
   * the xxx.
   *
   * @param userPath relative or absolute path, essentially any url.
   * @param newAttributes attributes for the new path.
   *
   * @return the new path or null if the scheme doesn't exist
   */
  public PathImpl lookupImpl(String userPath,
                         Map<String,Object> newAttributes,
                         boolean isAllowRoot)
  {
    if (userPath == null) {
      return lookupImpl(getPath(), newAttributes, isAllowRoot);
    }

    if (! isAllowRoot) {
      return schemeWalk(userPath, newAttributes, userPath, 0);
    }

    String scheme = scanScheme(userPath);

    if (scheme == null) {
      return schemeWalk(userPath, newAttributes, userPath, 0);
    }

    PathImpl path;

    SchemeMap schemeMap = _schemeMap;

    // Special case to handle the windows special schemes
    // c:xxx -> file:/c:xxx
    if (isWindows()) {
      int length = scheme.length();
      char ch;

      if (length == 1
          && ('a' <= (ch = scheme.charAt(0)) && ch <= 'z'
              || 'A' <= ch && ch <= 'Z')) {
        userPath = Character.toLowerCase(ch) + userPath.substring(1);
        
        if (_isTestWindows)
          return schemeWalk(userPath, newAttributes, "/" + userPath, 0);

        path = schemeMap.get("file");

        if (path != null)
          return path.schemeWalk(userPath, newAttributes, "/" + userPath, 0);
        else
          return schemeWalk(userPath, newAttributes, "/" + userPath, 0);
      }
    }

    path = schemeMap.get(scheme);
    
    // assume the foo:bar is a subfile
    if (path == null) {
      return schemeWalk(userPath, newAttributes, userPath, 0);
    }
    else {
      return path.schemeWalk(userPath, newAttributes,
                             userPath, scheme.length() + 1);
    }
  }

  /**
   * Looks up a path using the local filesystem conventions. e.g. on
   * Windows, a name of 'd:\foo\bar\baz.html' will look up the baz.html
   * on drive d.
   *
   * @param name relative url using local filesystem separators.
   */
  public final PathImpl lookupNative(String name)
  {
    return lookupNative(name, null);
  }
  /**
   * Looks up a native path, adding attributes.
   */
  public PathImpl lookupNative(String name, Map<String,Object> attributes)
  {
    return lookup(name, attributes);
  }

  /**
   * Returns a native path relative to this native path if the passed path
   * is relative to this path, or an absolute path if the passed path is not
   * relative to this path.
   */
  public String lookupRelativeNativePath(PathImpl path)
  {
    String thisNative = getNativePath();
    String pathNative = path.getNativePath();

    if (pathNative.startsWith(thisNative)) {
      int i = thisNative.length();

      while (i < pathNative.length()) {
        if (pathNative.charAt(i) != getFileSeparatorChar())
          break;

        i++;
      }

      return i == pathNative.length() ? "" : pathNative.substring(i);
    }
    else
      return pathNative;
  }

  /**
   * Looks up all the resources matching a name.  (Generally only useful
   * with MergePath.
   */
  public ArrayList<PathImpl> getResources(String name)
  {
    ArrayList<PathImpl> list = new ArrayList<PathImpl>();
    PathImpl path = lookup(name);
    if (path.exists())
      list.add(path);

    return list;
  }

  /**
   * Looks up all the existing resources.  (Generally only useful
   * with MergePath.
   */
  public ArrayList<PathImpl> getResources()
  {
    ArrayList<PathImpl> list = new ArrayList<PathImpl>();

    //if (exists())
    list.add(this);

    return list;
  }

  /**
   * Returns the parent path.
   */
  public PathImpl getParent()
  {
    return this;
  }

  /**
   * Returns the scheme portion of a uri.  Since schemes are case-insensitive,
   * normalize them to lower case.
   */
  protected String scanScheme(String uri)
  {
    int i = 0;
    if (uri == null)
      return null;

    int length = uri.length();
    if (length == 0)
      return null;

    int ch = uri.charAt(0);
    if (ch >= 'a' && ch <= 'z' ||
        ch >= 'A' && ch <= 'Z') {
      for (i = 1; i < length; i++) {
        ch = uri.charAt(i);

        if (ch == ':')
          return uri.substring(0, i).toLowerCase(Locale.ENGLISH);

        if (! (ch >= 'a' && ch <= 'z' ||
               ch >= 'A' && ch <= 'Z' ||
               ch >= '0' && ch <= '0' ||
               ch == '+' || ch == '-' || ch == '.'))
          break;
      }
    }

    return null;
  }

  /**
   * Path-specific lookup.  Path implementations will override this.
   *
   * @param userPath the user's lookup() path.
   * @param newAttributes the attributes for the new path.
   * @param newPath the lookup() path
   * @param offset offset into newPath to start lookup.
   *
   * @return the found path
   */
  abstract public PathImpl schemeWalk(String userPath,
                                  Map<String,Object> newAttributes,
                                  String newPath, int offset);

  /**
   * Returns the full url for the given path.
   */
  public String getURL()
  {
    return escapeURL(getScheme() + ":" + getFullPath());
  }
  
  @Override
  public URL getNetURL()
  {
    try {
      return new URL(getURL());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the url scheme
   */
  public abstract String getScheme();

  /**
   * Returns the schemeMap
   */
  public SchemeMap getSchemeMap()
  {
    return _schemeMap;
  }

  /**
   * Returns the hostname
   */
  public String getHost()
  {
    throw new UnsupportedOperationException();
  }
  /**
   * Returns the port.
   */
  public int getPort()
  {
    throw new UnsupportedOperationException();
  }
  /**
   * Returns the path.  e.g. for HTTP, returns the part after the
   * host and port.
   */
  public abstract String getPath();

  /**
   * Returns the last segment of the path.
   *
   * <p>e.g. for http://www.caucho.com/products/index.html, getTail()
   * returns 'index.html'
   */
  public String getTail()
  {
    return "";
  }
  /**
   * Returns the query string of the path.
   */
  public String getQuery()
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the native representation of the path.
   *
   * On Windows, getNativePath() returns 'd:\\foo\bar.html',
   * getPath() returns '/d:/foo/bar.html'
   */
  public String getNativePath()
  {
    return getFullPath();
  }
  /**
   * Returns the last string used as a lookup, if available.  This allows
   * parsers to give intelligent error messages, with the user's path
   * instead of the whole path.
   *
   * The following will print '../test.html':
   * <code><pre>
   * Path path = Pwd.lookup("/some/dir").lookup("../test.html");
   * System.out.println(path.getUserPath());
   * </pre></code>
   *
   */
  public String getUserPath()
  {
    return getPath();
  }

  /**
   * Sets the user path.  Useful for temporary files caching another
   * URL.
   */
  public void setUserPath(String userPath)
  {
  }
  /**
   * Returns the full path, including the restricted root.
   *
   * <p>For the following, path.getPath() returns '/file.html', while
   * path.getFullPath() returns '/chroot/file.html'.
   * <code><pre>
   * Path chroot = Pwd.lookup("/chroot").createRoot();
   * Path path = chroot.lookup("/file.html");
   * </pre></code>
   */
  public String getFullPath()
  {
    return getPath();
  }

  /**
   * For union paths like MergePath, return the relative path into
   * that path.
   */
  public String getRelativePath()
  {
    return getPath();
  }

  /**
   * Returns true for windows security issues.
   */
  public boolean isWindowsInsecure()
  {
    String lower = getPath().toLowerCase(Locale.ENGLISH);

    int lastCh;

    if ((lastCh = lower.charAt(lower.length() - 1)) == '.'
        || lastCh == ' ' || lastCh == '*' || lastCh == '?'
        || ((lastCh == '/' || lastCh == '\\') && ! isDirectory())
        || lower.endsWith("::$data")
        || isWindowsSpecial(lower, "/con")
        || isWindowsSpecial(lower, "/aux")
        || isWindowsSpecial(lower, "/prn")
        || isWindowsSpecial(lower, "/nul")
        || isWindowsSpecial(lower, "/com1")
        || isWindowsSpecial(lower, "/com2")
        || isWindowsSpecial(lower, "/com3")
        || isWindowsSpecial(lower, "/com4")
        || isWindowsSpecial(lower, "/lpt1")
        || isWindowsSpecial(lower, "/lpt2")
        || isWindowsSpecial(lower, "/lpt3")) {
      return true;
    }

    return false;
  }

  private boolean isWindowsSpecial(String lower, String test)
  {
    int p = lower.indexOf(test);

    if (p < 0)
      return false;

    int lowerLen = lower.length();
    int testLen = test.length();
    char ch;

    if (lowerLen == p + testLen
        || (ch = lower.charAt(p + testLen)) == '/' || ch == '.')
      return true;
    else
      return false;
  }

  /**
   * Returns any signing certificates, e.g. for jar signing.
   */
  public Certificate []getCertificates()
  {
    return null;
  }

  /**
   * Tests if the file exists.
   */
  public boolean exists()
  {
    return false;
  }

  /**
   * Returns the mime-type of the file.
   * <p>Mime-type ignorant filesystems return 'application/octet-stream'
   */
  public String getContentType()
  {
    return "application/octet-stream";
  }

  /**
   * Tests if the path refers to a directory.
   */
  public boolean isDirectory()
  {
    return false;
  }

  /**
   * Tests if the path refers to a file.
   */
  public boolean isFile()
  {
    return false;
  }

  /**
   * Tests if the path refers to a symbolic link.
   */
  public boolean isLink()
  {
    return false;
  }

  /**
   * Tests if the path refers to a socket.
   */
  public boolean isSocket()
  {
    return false;
  }

  /**
   * Tests if the path refers to a FIFO.
   */
  public boolean isFIFO()
  {
    return false;
  }

  /**
   * Tests if the path refers to a block device.
   */
  public boolean isBlockDevice()
  {
    return false;
  }

  /**
   * Tests if the path refers to a block device.
   */
  public boolean isCharacterDevice()
  {
    return false;
  }

  /**
   * Tests if the path is marked as executable
   */
  public boolean isExecutable()
  {
    return false;
  }

  /**
   * Change the executable status of the of the path.
   *
   * @throws UnsupportedOperationException
   */
  public boolean setExecutable(boolean isExecutable)
  {
    return false;
  }

  /**
   * Tests if the path refers to a symbolic link.
   */
  public boolean isSymbolicLink()
  {
    return false;
  }

  /**
   * Tests if the path refers to a hard link.
   */
  public boolean isHardLink()
  {
    return false;
  }

  /**
   * Tests if the path refers to an object.
   */
  public boolean isObject()
  {
    return false;
  }

  /**
   * Clears any status cache
   */
  public void clearStatusCache()
  {
  }

  /**
   * Returns the length of the file in bytes.
   * @return 0 for non-files
   */
  public long length()
  {
    return 0;
  }

  /**
   * Returns the last modified time of the file.  According to the jdk,
   * this may not correspond to the system time.
   * @return 0 for non-files.
   */
  public long getLastModified()
  {
    return 0;
  }

  public void setLastModified(long time)
  {
  }

  /**
   * Returns the last access time of the file.
   *
   * @return 0 for non-files.
   */
  public long getLastAccessTime()
  {
    return getLastModified();
  }

  /**
   * Returns the create time of the file.
   *
   * @return 0 for non-files.
   */
  public long getCreateTime()
  {
    return getLastModified();
  }

  /**
   * Tests if the file can be read.
   */
  public boolean canRead()
  {
    return false;
  }

  /**
   * Tests if the file can be written.
   */
  public boolean canWrite()
  {
    return false;
  }

  //
  // POSIX stat() related calls
  //

  /**
   * Returns equivalent of struct stat.st_dev if appropriate.
   */
  public long getDevice()
  {
    return 0;
  }

  /**
   * Returns equivalent of struct stat.st_ino if appropriate.
   */
  public long getInode()
  {
    return 0;
  }

  /**
   * Returns equivalent of struct stat.st_mode if appropriate.
   */
  public int getMode()
  {
    return 0;
  }

  /**
   * Returns equivalent of struct stat.st_nlink if appropriate.
   */
  public int getNumberOfLinks()
  {
    return 0;
  }

  /**
   * Returns equivalent of struct stat.st_uid if appropriate.
   */
  public int getUser()
  {
    return 0;
  }

  /**
   * Returns equivalent of struct stat.st_gid if appropriate.
   */
  public int getGroup()
  {
    return 0;
  }

  /**
   * Returns equivalent of struct stat.st_rdev if appropriate.
   */
  public long getDeviceId()
  {
    return 0;
  }

  /**
   * Returns equivalent of struct stat.st_blksize if appropriate.
   */
  public long getBlockSize()
  {
    return 0;
  }

  /**
   * Returns equivalent of struct stat.st_blocks if appropriate.
   */
  public long getBlockCount()
  {
    return 0;
  }

  /**
   * Returns equivalent of struct stat.st_ctime if appropriate.
   */
  public long getLastStatusChangeTime()
  {
    return 0;
  }

  /**
   * Tests if the file can be read.
   */
  public boolean canExecute()
  {
    return canRead();
  }

  /**
   * Changes the group
   */
  public boolean changeGroup(int gid)
    throws IOException
  {
    return false;
  }

  /**
   * Changes the group
   */
  public boolean changeGroup(String groupName)
    throws IOException
  {
    return false;
  }

  /**
   * Changes the permissions
   *
   * @return true if successful
   */
  public boolean chmod(int value)
  {
    return false;
  }

  public int getOwner()
  {
    return getUser();
  }

  /**
   * Changes the owner
   *
   * @return true if successful
   */
  public boolean changeOwner(int uid)
    throws IOException
  {
    return false;
  }

  /**
   * Changes the owner
   *
   * @return true if successful
   */
  public boolean changeOwner(String ownerName)
    throws IOException
  {
    return false;
  }

  public long getDiskSpaceFree()
  {
    return 0;
  }

  public long getDiskSpaceTotal()
  {
    return 0;
  }

  /**
   * @return The contents of this directory or null if the path does not
   * refer to a directory.
   */
  public String []list() throws IOException
  {
    return new String[0];
  }

  /**
   * Returns a jdk1.2 Iterator for the contents of this directory.
   */
  public Iterator<String> iterator() throws IOException
  {
    String list[] = list();

    // Avoids NPE when subclasses override list() and
    // possibly return null, e.g. JarPath.
    if (list == null)
      list = new String[0];

    return new ArrayIterator(list);
  }

  /**
   * Creates the directory named by this path.
   * @return true if successful.
   */
  public boolean mkdir() throws IOException
  {
    return false;
  }

  /**
   * Creates the directory named by this path and any parent directories.
   * @return true if successful.
   */
  public boolean mkdirs() throws IOException
  {
    return false;
  }

  /**
   * Removes the file or directory named by this path.
   *
   * @return true if successful
   */
  public boolean remove() throws IOException
  {
    return false;
  }

  /**
   * Removes the all files and directories below this path.
   *
   * @return true if successful.
   */
  public boolean removeAll() throws IOException
  {
    if (isDirectory() && ! isLink()) {
      String []list = list();

      for (int i = 0; i < list.length; i++) {
        PathImpl subpath = lookup(list[i]);
        subpath.removeAll();
      }
    }

    return remove();
  }

  /**
   * Sets the length of the file to zero.
   *
   * @return true if successful
   */
  public boolean truncate()
    throws IOException
  {
    return truncate(0);
  }

  /**
   * Sets the length of the file.
   *
   * @return true if successful
   */
  public boolean truncate(long length)
    throws IOException
  {
    if (length == 0) {
      if (exists()) {
        StreamImpl stream = openWriteImpl();
        stream.close();

        clearStatusCache();

        return true;
      }
      else
        return false;
    }
    else
      throw new UnsupportedOperationException(getClass().getName() + ": truncate");
  }

  /**
   * Renames the file or directory to the name given by the path.
   * @return true if successful
   */
  public boolean renameTo(PathImpl path) throws IOException
  {
    return false;
  }

  /**
   * Renames the file or directory to the name given by the path.
   * @return true if successful
   */
  public final boolean renameTo(String path) throws IOException
  {
    return renameTo(lookup(path));
  }

  /**
   * Creates a restricted root, like the Unix chroot call.
   * Restricted roots cannot access schemes, so file:/etc/passwd cannot
   * be used.
   *
   * <p>createRoot is useful for restricting JavaScript scripts without
   * resorting to the dreadfully slow security manager.
   */
  public PathImpl createRoot()
  {
    return createRoot(SchemeMap.getNullSchemeMap());
  }

  public PathImpl createRoot(SchemeMap schemeMap)
  {
    throw new UnsupportedOperationException("createRoot");
  }

  /**
   * Binds the context to the current path.  Later lookups will return
   * the new context instead of the current path.  Essentially, this is a
   * software symbolic link.
   */
  public void bind(PathImpl context)
  {
    throw new UnsupportedOperationException("bind");
  }

  /**
   * unbinds a link.
   */
  public void unbind()
  {
    throw new UnsupportedOperationException("unbind");
  }

  /**
   * Gets the object at the path.  Normal filesystems will generally
   * typically return null.
   *
   * <p>A bean filesystem or a mime-type aware filesystem could deserialize
   * the contents of the file.
   */
  public Object getValue() throws Exception
  {
    throw new UnsupportedOperationException("getValue");
  }

  /**
   * Sets the object at the path.
   *
   * <p>Normal filesystems will generally do nothing. However, a bean
   * filesystem or a mime-type aware filesystem could serialize the object
   * and store it.
   */
  public void setValue(Object obj) throws Exception
  {
    throw new UnsupportedOperationException("setValue");
  }

  /**
   * Gets an attribute of the object.
   */
  public Object getAttribute(String name) throws IOException
  {
    return null;
  }

  /**
   * Returns a iterator of all attribute names set for this object.
   * @return null if path has no attributes.
   */
  public Iterator getAttributeNames() throws IOException
  {
    return null;
  }

  @Override
  public final ReadStream inputStream() throws IOException
  {
    return openRead();
  }
  
  /**
   * Opens a ReadStream for reading.
   */
  public final ReadStream openRead() throws IOException
  {
    clearStatusCache();

    StreamImpl impl = openReadImpl();
    //impl.setPath(this);

    return new ReadStream(impl);
  }

  /**
   * Opens a WriteStream for writing.
   */
  public final WriteStream openWrite() throws IOException
  {
    clearStatusCache();

    StreamImpl impl = openWriteImpl();
    //impl.setPath(this);
    return new WriteStream(impl);
  }

  /**
   * Opens a ReadWritePair for reading and writing.
   *
   * <p>A chat channel, for example, would open its socket using this
   * interface.
   */
  public ReadWritePair openReadWrite() throws IOException
  {
    return openReadWrite(false);
  }

  /**
   * Opens a ReadWritePair for reading and writing.
   *
   * <p>A chat channel, for example, would open its socket using this
   * interface.
   */
  public ReadWritePair openReadWrite(boolean isAutoFlush) throws IOException
  {
    clearStatusCache();

    StreamImpl impl = openReadWriteImpl();
    //impl.setPath(this);

    WriteStream writeStream = new WriteStream(impl);
    ReadStream readStream;

    if (isAutoFlush)
      readStream = new ReadStream(impl);
    else
      readStream = new ReadStream(impl);

    return new ReadWritePair(readStream, writeStream);
  }

  /**
   * Opens a ReadWritePair for reading and writing.
   *
   * <p>A chat channel, for example, would open its socket using this
   * interface.
   *
   * @param is pre-allocated ReadStream to be initialized
   * @param os pre-allocated WriteStream to be initialized
   */
  public void openReadWrite(ReadStream is, WriteStream os) throws IOException
  {
    clearStatusCache();

    StreamImpl impl = openReadWriteImpl();
    //impl.setPath(this);

    os.init(impl);
    is.init(impl);
  }

  /**
   * Opens a stream for appending.
   */
  public WriteStream openAppend() throws IOException
  {
    clearStatusCache();

    StreamImpl impl = openAppendImpl();
    return new WriteStream(impl);
  }
  
  public boolean isMmapSupported()
  {
    return false;
  }

  /**
   * Opens a random-access stream.
   */
  public RandomAccessStream openMemoryMappedFile(long fileSize)
    throws IOException
  {
    return null;
  }

  /**
   * Opens a random-access stream.
   */
  public RandomAccessStream openRandomAccess() throws IOException
  {
    return openFileRandomAccess();
  }

  /**
   * Opens a random-access stream.
   */
  public RandomAccessStream openFileRandomAccess() throws IOException
  {
    clearStatusCache();

    throw new UnsupportedOperationException(getClass().getName());
  }
  
  public FileChannel openFileChannel(OpenOption... options) throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Creates the file named by this Path and returns true if the
   * file is new.
   */
  public boolean createNewFile() throws IOException
  {
    synchronized (LOCK) {
      if (! exists()) {
        clearStatusCache();
        WriteStream s = openWrite();
        s.close();
        return true;
      }
    }

    return false;
  }

  /**
   * Creates a dependency.
   */
  public Dependency createDepend()
  {
    return new Depend(this);
  }
  
  //
  // watches (listeners)
  //

  /**
   * Adds a watch for changes to the path
   */
  public Cancel watch(Watch watch)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Creates a unique temporary file as a child of this directory.
   *
   * @param prefix filename prefix
   * @param suffix filename suffix, defaults to .tmp
   * @return Path to the new file.
   */
  public PathImpl createTempFile(String prefix, String suffix) throws IOException
  {
    if (prefix == null || prefix.length () == 0)
      prefix = "t";

    if (suffix == null)
      suffix = ".tmp";

    synchronized (LOCK) {
      for (int i = 0; i < 32768; i++) {
        int r = Math.abs((int) RandomUtil.getRandomLong());
        PathImpl file = lookup(prefix + r + suffix);

        if (file.createNewFile())
          return file;
      }
    }

    throw new IOException("cannot create temp file");
  }

  /**
   * Creates a link named by this path to another path.
   *
   * @param target the target of the link
   * @param hardLink true if the link should be a hard link
   */
  public boolean createLink(PathImpl target, boolean hardLink)
    throws IOException
  {
    throw new UnsupportedOperationException(getScheme() + ": doesn't support createLink");
  }

  /**
   * Returns the target path from the link.
   * Returns null for a non-link.
   */
  public String readLink()
  {
    return null;
  }

  /**
   * Returns the actual path from the link.
   */
  public String realPath()
  {
    return getFullPath();
  }

  /**
   * Utility to write the contents of this path to the destination stream.
   *
   * @param os destination stream.
   */
  public void writeToStream(OutputStream os)
    throws IOException
  {
    StreamImpl is = openReadImpl();
    TempBuffer tempBuffer = TempBuffer.allocate();
    try {
      byte []buffer = tempBuffer.buffer();
      int length = buffer.length;
      int len;

      while ((len = is.read(buffer, 0, length)) > 0) {
        os.write(buffer, 0, len);
      }
    } finally {
      TempBuffer.free(tempBuffer);
      tempBuffer = null;

      is.close();
    }
  }

  /**
   * Utility to write the contents of this path to the destination stream.
   *
   * @param os destination stream.
   */
  public void writeToStream(OutputStreamWithBuffer os)
    throws IOException
  {
    StreamImpl is = openReadImpl();

    try {
      byte []buffer = os.buffer();
      int offset = os.offset();
      int length = buffer.length;

      while (! os.isClosed()) {
        int sublen = length - offset;

        if (sublen <= 0) {
          buffer = os.nextBuffer(offset);
          offset = os.offset();
          sublen = length - offset;
        }

        sublen = is.read(buffer, offset, sublen);

        if (sublen <= 0) {
          os.offset(offset);
          return;
        }

        offset += sublen;
      }
    } finally {
      is.close();
    }
  }

  /**
   * Utility to write the contents of this path to the destination stream.
   *
   * @param os destination stream.
   */
  public void sendfile(OutputStream os, long offset, long length)
    throws IOException
  {
    if (os instanceof OutputStreamWithBuffer) {
      writeToStream((OutputStreamWithBuffer) os);
    }
    else {
      writeToStream(os);
    }
  }

  /**
   * Returns the crc64 code.
   */
  public long getCrc64()
  {
    try {
      if (isDirectory()) {
        String []list = list();

        long digest = 0x1;

        for (int i = 0; i < list.length; i++) {
          digest = Crc64.generate(digest, list[i]);
        }

        return digest;
      }
      else if (canRead()) {
        ReadStream is = openRead();

        try {
          long digest = 0x1;

          byte []buffer = is.buffer();
          while (is.fillBuffer() > 0) {
            int length = is.getLength();

            digest = Crc64.generate(digest, buffer, 0, length);
          }

          return digest;
        } finally {
          is.close();
        }
      }
      else {
        return 0; // Depend requires -1
      }
    } catch (IOException e) {
      // XXX: log
      e.printStackTrace();

      return -1;
    }
  }

  /**
   * Returns the object at this path.  Normally, only paths like JNDI
   * will support this.
   */
  public Object getObject()
    throws IOException
  {
    throw new UnsupportedOperationException(getScheme() + ": doesn't support getObject");
  }

  /**
   * Sets the object at this path.  Normally, only paths like JNDI
   * will support this.
   */
  public void setObject(Object obj)
    throws IOException
  {
    throw new UnsupportedOperationException(getScheme() + ": doesn't support setObject");
  }

  @Override
  public int hashCode()
  {
    return toString().hashCode();
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    else if (! (o instanceof PathImpl))
      return false;
    else
      return getURL().equals(((PathImpl) o).getURL());
  }

  public String toString()
  {
    return getFullPath();
  }

  public StreamImpl openReadImpl() throws IOException
  {
    throw new UnsupportedOperationException("openRead:" + getClass().getName());
  }

  public StreamImpl openWriteImpl() throws IOException
  {
    throw new UnsupportedOperationException("openWrite:" + getClass().getName());
  }

  public StreamImpl openReadWriteImpl() throws IOException
  {
    throw new UnsupportedOperationException("openReadWrite:" + getClass().getName());
  }

  public StreamImpl openAppendImpl() throws IOException
  {
    throw new UnsupportedOperationException("openAppend:" + getClass().getName());
  }

  protected static String escapeURL(String rawURL)
  {
    CharBuffer cb = null;
    int length = rawURL.length();

    for (int i = 0; i < length; i++) {
      char ch = rawURL.charAt(i);

      switch (ch) {
      case ' ':
        if (cb == null) {
          cb = new CharBuffer();
          cb.append(rawURL, 0, i);
        }
        cb.append("%20");
        break;

      case '#':
        if (cb == null) {
          cb = new CharBuffer();
          cb.append(rawURL, 0, i);
        }
        cb.append("%23");
        break;

      case '%':
        if (cb == null) {
          cb = new CharBuffer();
          cb.append(rawURL, 0, i);
        }
        cb.append("%25");
        break;

      case '+':
        if (cb == null) {
          cb = new CharBuffer();
          cb.append(rawURL, 0, i);
        }
        cb.append("%2B");
        break;

      default:
        if (cb != null)
          cb.append(ch);
        break;
      }
    }

    if (cb != null)
      return cb.toString();
    else
      return rawURL;
  }

  public PathImpl copy()
  {
    return this;
  }

  public PathImpl unwrap()
  {
    return this;
  }

  /**
   * Copy for caching.
   */
  protected PathImpl cacheCopy()
  {
    return this;
  }

  @Override
  public int compareTo(Path path)
  {
    return getURL().compareTo(path.getURL());
  }

  public static final void setDefaultSchemeMap(SchemeMap schemeMap)
  {
    _defaultSchemeMap = schemeMap;
    _pathLookupCache.clear();
  }

  public static final boolean isWindows()
  {
    return _separatorChar == '\\' || _isTestWindows;
  }

  public static final void setTestWindows(boolean isTest)
  {
    _isTestWindows = isTest;
  }

  protected static final char getSeparatorChar()
  {
    return _separatorChar;
  }

  public static final char getFileSeparatorChar()
  {
    return _separatorChar;
  }

  public static final char getPathSeparatorChar()
  {
    return _pathSeparatorChar;
  }

  protected static String getUserDir()
  {
    return System.getProperty("user.dir");
  }

  public static String getNewlineString()
  {
    if (_newline == null) {
      _newline = System.getProperty("line.separator");
      if (_newline == null)
        _newline = "\n";
    }

    return _newline;
  }

  private class ArrayIterator implements Iterator<String> {
    String []list;
    int index;

    public boolean hasNext() { return index < list.length; }
    public String next() { return index < list.length ? list[index++] : null; }
    public void remove() { throw new UnsupportedOperationException(); }

    ArrayIterator(String []list)
    {
      this.list = list;
      index = 0;
    }
  }

  static class PathKey {
    private PathImpl _parent;
    private String _lookup;

    PathKey()
    {
    }

    PathKey(PathImpl parent, String lookup)
    {
      _parent = parent;
      _lookup = lookup;
    }

    void init(PathImpl parent, String lookup)
    {
      _parent = parent;
      _lookup = lookup;
    }

    public int hashCode()
    {
      if (_parent != null)
        return _parent.hashCode() * 65521 + _lookup.hashCode();
      else
        return _lookup.hashCode();
    }

    public boolean equals(Object test)
    {
      if (! (test instanceof PathKey))
        return false;

      PathKey key = (PathKey) test;

      if (_parent != null)
        return (_parent.equals(key._parent) && _lookup.equals(key._lookup));
      else
        return (key._parent == null && _lookup.equals(key._lookup));
    }
  }

  private static void createDefaultSchemeMap(SchemeMap map)
  {
    map.put("file", new FilePath(null));

    //DEFAULT_SCHEME_MAP.put("jar", new JarScheme(null));
    map.put("http", new HttpPath("127.0.0.1", 0));
    map.put("https", new HttpsPath("127.0.0.1", 0));
    map.put("tcp", new TcpPath(null, null, null, "127.0.0.1", 0));
    map.put("tcps", new TcpsPath(null, null, null, "127.0.0.1", 0));

    StreamImpl stdout = StdoutStream.create();
    StreamImpl stderr = StderrStream.create();
    //map.put("stdout", stdout.getPath());
    //map.put("stderr", stderr.getPath());
    VfsStreamOld nullStream = new VfsStreamOld(null, null);
    map.put("null", new ConstPath(null, nullStream));
    //map.put("jndi", new JndiPath());
  }
}
