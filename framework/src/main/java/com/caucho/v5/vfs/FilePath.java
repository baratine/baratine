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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.security.AccessControlException;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.CharBuffer;

/**
 * FilePath implements the native filesystem.
 */
public class FilePath extends FilesystemPath
{
  private static Logger log = Logger.getLogger(FilePath.class.getName());

  // The underlying Java File object.
  private static byte []NEWLINE = getNewlineString().getBytes();

  private static FilesystemPath PWD;

  private File _file;
  private boolean _isWindows;

  /**
   * @param path canonical path
   */
  public FilePath(FilesystemPath root, String userPath, String path)
  {
    super(root, userPath, path);

    _separatorChar = getFileSeparatorChar();
    _isWindows = _separatorChar == '\\';
  }

  public FilePath(String path)
  {
    this(null,  //PWD != null ? PWD._root : null,
         path, normalizePath("/", initialPath(path),
                             0, getFileSeparatorChar()));

    if (_root == null) {
      _root = new FilePath(null, "/", "/");
      _root._root = _root;

      if (PWD == null) {
        PWD = _root;
      }
    }

    _separatorChar = _root._separatorChar;
    _isWindows = ((FilePath) _root)._isWindows;
  }

  protected static String initialPath(String path)
  {
    if (path == null)
      return getPwd();
    else if (path.length() > 0 && path.charAt(0) == '/')
      return path;
    else if (path.length() > 1 && path.charAt(1) == ':' && isWindows())
      //return convertFromWindowsPath(path);
      return path;
    else {
      String dir = getPwd();

      if (dir.length() > 0 && dir.charAt(dir.length() - 1) == '/')
        return dir + path;
      else
        return dir + "/" + path;
    }
  }

  /**
   * Gets the system's user dir (pwd) and convert it to the server's format.
   */
  public static String getPwd()
  {
    String path = getUserDir();

    path = path.replace(getFileSeparatorChar(), '/');

    if (isWindows()) {
      path = convertFromWindowsPath(path);
    }

    return path;
  }

  /**
   * a:xxx -> /a:xxx
   * ///a:xxx -> /a:xxx
   * //xxx -> /:/xxx
   *
   */
  private static String convertFromWindowsPath(String path)
  {
    int colon = path.indexOf(':');
    int length = path.length();
    char ch;

    if (colon == 1 && (ch = path.charAt(0)) != '/' && ch != '\\')
      return "/" + path.charAt(0) + ":/" + path.substring(2);
    else if (length > 1
             && ((ch = path.charAt(0)) == '/' || ch == '\\')
             && ((ch = path.charAt(1)) == '/' || ch == '\\')) {
      if (colon < 0)
        return "/:" + path;

      for (int i = colon - 2; i > 1; i--) {
        if ((ch = path.charAt(i)) != '/' && ch != '\\')
          return "/:" + path;
      }

      ch = path.charAt(colon - 1);

      if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z')
        return path.substring(colon - 2);
      else
        return "/:" + path;
    }
    else
      return path;
  }

  @Override
  public long getDiskSpaceFree()
  {
    try {
      // JDK 1.6+ only
      return _file.getFreeSpace();
    } catch (Exception e) {
      return 0;
    }
  }

  @Override
  public long getDiskSpaceTotal()
  {
    try {
      // JDK 1.6+ only
      return _file.getTotalSpace();
    } catch (Exception e) {
      return 0;
    }
  }

  /**
   * Lookup the path, handling windows weirdness
   */
  @Override
  public PathImpl schemeWalk(String userPath,
                         Map<String,Object> attributes,
                         String filePath,
                         int offset)
  {
    if (! isWindows()) {
      return super.schemeWalk(userPath, attributes, filePath, offset);
    }

    String canonicalPath;

    if (filePath.length() < offset + 2) {
      return super.schemeWalk(userPath, attributes, filePath, offset);
    }

    char ch1 = filePath.charAt(offset + 1);
    char ch2 = filePath.charAt(offset);

    if ((ch2 == '/' || ch2 == _separatorChar)
        && (ch1 == '/' || ch1 == _separatorChar))
      return super.schemeWalk(userPath, attributes,
                              convertFromWindowsPath(filePath.substring(offset)), 0);
    else
      return super.schemeWalk(userPath, attributes, filePath, offset);
  }

  /**
   * Lookup the actual path relative to the filesystem root.
   *
   * @param userPath the user's path to lookup()
   * @param attributes the user's attributes to lookup()
   * @param path the normalized path
   *
   * @return the selected path
   */
  public PathImpl fsWalk(String userPath,
                        Map<String,Object> attributes,
                        String path)
  {
    return new FilePath(_root, userPath, path);
  }

  /**
   * Returns true if the path itself is cacheable
   */
  @Override
  protected boolean isPathCacheable()
  {
    return true;
  }

  public String getScheme()
  {
    return "file";
  }

  /**
   * Returns the full url for the given path.
   */
  public String getURL()
  {
    if (! isWindows())
      return escapeURL("file:" + getFullPath());

    String path = getFullPath();
    int length = path.length();
    CharBuffer cb = new CharBuffer();

    // #2725, server/1495
    cb.append("file:");

    char ch;
    int offset = 0;
    // For windows, convert /c: to c:
    if (length >= 3
        && path.charAt(0) == '/'
        && path.charAt(2) == ':'
        && ('a' <= (ch = path.charAt(1)) && ch <= 'z'
            || 'A' <= ch && ch <= 'Z')) {
      // offset = 1;
    }
    else if (length >= 3
             && path.charAt(0) == '/'
             && path.charAt(1) == ':'
             && path.charAt(2) == '/') {
      cb.append('/');
      cb.append('/');
      cb.append('/');
      cb.append('/');
      offset = 3;
    }

    for (; offset < length; offset++) {
      ch = path.charAt(offset);

      if (ch == '\\')
        cb.append('/');
      else
        cb.append(ch);
    }

    return escapeURL(cb.toString());

  }

  /**
   * Returns the native path.
   */
  @Override
  public String getNativePath()
  {
    if (! isWindows()) {
      return getFullPath();
    }

    String path = getFullPath();
    int length = path.length();
    CharBuffer cb = new CharBuffer();
    char ch;
    int offset = 0;

    // For windows, convert /c: to c:
    if (length >= 3
        && path.charAt(0) == '/'
        && path.charAt(2) == ':'
        && ('a' <= (ch = path.charAt(1)) && ch <= 'z'
            || 'A' <= ch && ch <= 'Z')) {
      offset = 1;
    }
    else if (length >= 3
             && path.charAt(0) == '/'
             && path.charAt(1) == ':'
             && path.charAt(2) == '/') {
      cb.append('\\');
      cb.append('\\');
      offset = 3;
    }

    for (; offset < length; offset++) {
      ch = path.charAt(offset);

      if (ch == '/')
        cb.append(_separatorChar);
      else
        cb.append(ch);
    }

    return cb.toString();
  }

  @Override
  public boolean exists()
  {
    try {
      if (_isWindows && isAux())
        return false;
      else
        return getFile().exists();
    } catch (AccessControlException e) {
      log.finer(e.toString());

      return false;
    }
  }

  @Override
  public int getMode()
  {
    int perms = 0;

    if (isDirectory()) {
      perms += 01000;
      perms += 0111;
    }

    if (canRead())
      perms += 0444;

    if (canWrite())
      perms += 0220;

    return perms;
  }

  @Override
  public boolean isDirectory()
  {
    try {
      return getFile().isDirectory();
    } catch (AccessControlException e) {
      log.finer(e.toString());

      return false;
    }
  }

  @Override
  public boolean isFile()
  {
    try {
      if (_isWindows && isAux())
        return false;
      else
        return getFile().isFile();
    } catch (AccessControlException e) {
      log.finer(e.toString());

      return false;
    }
  }

  @Override
  public boolean isLink()
  {
    try {
      return isLinkImpl();
    } catch (Throwable e) {
      if (log.isLoggable(Level.FINEST)) {
        log.finest(e.toString());
      }
      return false;
    }
  }
  
  private boolean isLinkImpl()
  {
    return Files.isSymbolicLink(getFile().toPath());
  }

  public long length()
  {
    try {
      return getFile().length();
    } catch (AccessControlException e) {
      log.finer(e.toString());

      return -1;
    }
  }

  public long getLastModified()
  {
    try {
      return getFile().lastModified();
    } catch (AccessControlException e) {
      log.finer(e.toString());

      return -1;
    }
  }

  // This exists in JDK 1.2
  public void setLastModified(long time)
  {
    getFile().setLastModified(time);
  }

  public boolean canRead()
  {
    try {
      File file = getFile();

      if (_isWindows && isAux())
        return false;
      else
        return file.canRead();
    } catch (AccessControlException e) {
      log.finer(e.toString());

      return false;
    }
  }

  public boolean canWrite()
  {
    try {
      File file = getFile();

      if (_isWindows && isAux())
        return false;
      else
        return file.canWrite();
    } catch (AccessControlException e) {
      log.finer(e.toString());

      return false;
    }
  }

  /**
   * Returns a list of files in the directory.
   */
  public String []list() throws IOException
  {
    try {
      String []list = getFile().list();

      if (list != null)
        return list;
    } catch (AccessControlException e) {
      log.finer(e.toString());
    }

    return new String[0];
  }

  public boolean mkdir()
    throws IOException
  {
    boolean value = getFile().mkdir();

    if (! value && ! getFile().isDirectory())
      throw new IOException("cannot create directory");

    return value;
  }

  public boolean mkdirs()
    throws IOException
  {
    File file = getFile();

    boolean value;

    synchronized (file) {
      value = file.mkdirs();
    }

    clearStatusCache();

    if (! value && ! file.isDirectory())
      throw new IOException("Cannot create directory: " + getFile());

    return value;
  }

  @Override
  public boolean remove()
  {
    if (getFile().delete()) {
      clearStatusCache();

      return true;
    }

    /*
    if (getPath().endsWith(".jar")) {
      // XXX:
      // Jar.create(this).clearCache();
      return getFile().delete();
    }
    */

    return false;
  }

  @Override
  public boolean truncate(long length)
    throws IOException
  {
    File file = getFile();

    clearStatusCache();

    FileOutputStream fos = new FileOutputStream(file);

    try {
      fos.getChannel().truncate(length);

      return true;
    } finally {
      fos.close();
    }
  }

  public boolean renameTo(PathImpl path)
  {
    if (! (path instanceof FilePath))
      return false;

    FilePath file = (FilePath) path;

    clearStatusCache();
    file.clearStatusCache();

    return this.getFile().renameTo(file.getFile());
  }

  /**
   * Returns the stream implementation for a read stream.
   */
  public StreamImpl openReadImpl() throws IOException
  {
    if (_isWindows && isAux())
      throw new FileNotFoundException(_file.toString());

    /* XXX: only for Solaris (?)
    if (isDirectory())
      throw new IOException("is directory");
    */

    return new FileReadStream(new FileInputStream(getFile()), this);
  }

  public StreamImpl openWriteImpl() throws IOException
  {
    FileWriteStream fws = new FileWriteStream(
      new FileOutputStream(getFile()),
      this);

    fws.setNewline(NEWLINE);

    return fws;
  }

  public StreamImpl openAppendImpl() throws IOException
  {
    FileOutputStream fos = null;

    try {
      fos = new FileOutputStream(getFile().toString(), true);
    } catch (IOException e) {
      // MacOS hack
      fos = new FileOutputStream(getFile().toString());
    }

    FileWriteStream fws = new FileWriteStream(fos);

    fws.setNewline(NEWLINE);

    return fws;
  }

  @Override
  public StreamImpl openReadWriteImpl() throws IOException
  {
    VfsStreamOld os;

    os = new VfsStreamOld(new FileInputStream(getFile()),
                       new FileOutputStream(getFile()));

    os.setNewline(NEWLINE);

    return os;
  }

  /**
   * Returns the stream implementation for a random-access stream.
   */
  @Override
  public RandomAccessStream openFileRandomAccess() throws IOException
  {
    if (_isWindows && isAux())
      throw new FileNotFoundException(_file.toString());

    return new FileRandomAccessStream(new RandomAccessFile(getFile(), "rw"));
  }

  /**
   * Returns the stream implementation for a random-access stream.
   */
  @Override
  public FileChannel openFileChannel(OpenOption... options) throws IOException
  {
    return FileChannel.open(getJdkPath(), options);
  }
  
  private java.nio.file.Path getJdkPath()
  {
    return getFile().toPath();
  }

  @Override
  public PathImpl copy()
  {
    return new FilePath(getRoot(), getUserPath(), getPath());
  }

  public int hashCode()
  {
    return getFullPath().hashCode();
  }

  public boolean equals(Object b)
  {
    if (this == b)
      return true;

    if (! (b instanceof FilePath))
      return false;

    FilePath file = (FilePath) b;

    return getFullPath().equals(file.getFullPath());
  }

  /**
   * Lazily returns the native File object.
   */
  public File getFile()
  {
    if (_file != null)
      return _file;

    _file = new File(getNativePath());

    return _file;
  }
  
  protected void setWindows(boolean isWindows)
  {
    _isWindows = isWindows;
  }
  
  /**
   * Special case for the evil windows special
   */
  public boolean isAux()
  {
    if (! _isWindows)
      return false;

    File file = getFile();

    String path = getFullPath().toLowerCase(Locale.ENGLISH);

    int len = path.length();
    int p = path.indexOf("/aux");
    int ch;
    if (p >= 0 && (len <= p + 4 || path.charAt(p + 4) == '.'))
      return true;

    p = path.indexOf("/con");
    if (p >= 0 && (len <= p + 4 || path.charAt(p + 4) == '.'))
      return true;

    p = path.indexOf("/lpt");
    if (p >= 0
        && (len <= p + 5 || path.charAt(p + 5) == '.')
        && '0' <= (ch = path.charAt(p + 4)) && ch <= '9') {
      return true;
    }

    p = path.indexOf("/nul");
    if (p >= 0 && (len <= p + 4 || path.charAt(p + 4) == '.'))
      return true;

    return false;
  }
}
