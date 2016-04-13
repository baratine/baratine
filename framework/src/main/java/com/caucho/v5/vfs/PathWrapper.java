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

import com.caucho.v5.io.OutputStreamWithBuffer;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.L10N;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

/**
 * Wraps a path object.
 */
public abstract class PathWrapper extends PathImpl {
  protected final static L10N L = new L10N(PathWrapper.class);

  private final PathImpl _path;

  /**
   * Creates a new Path object.
   *
   * @param path the new Path root.
   */
  protected PathWrapper(PathImpl path)
  {
    super(path);

    _path = path;
  }

  /**
   * Returns the wrapped path.
   */
  public PathImpl getWrappedPath()
  {
    return _path;
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
     return getWrappedPath().lookup(userPath, newAttributes);
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
 @Override
  public PathImpl lookupImpl(String userPath, 
                         Map<String,Object> newAttributes,
                         boolean isAllowRoot)
  {
    return getWrappedPath().lookupImpl(userPath, newAttributes, isAllowRoot);
  }

  /**
   * Looks up a native path, adding attributes.
   */
  public PathImpl lookupNative(String name, Map<String,Object> attributes)
  {
    return getWrappedPath().lookupNative(name, attributes);
  }

  /**
   * Looks up all the resources matching a name.  (Generally only useful
   * with MergePath.
   */
  public ArrayList<PathImpl> getResources(String name)
  {
    return getWrappedPath().getResources(name);
  }

  /**
   * Looks up all the existing resources.  (Generally only useful
   * with MergePath.
   */
  public ArrayList<PathImpl> getResources()
  {
    return getWrappedPath().getResources();
  }

  /**
   * Returns the parent path.
   */
  public PathImpl getParent()
  {
    return getWrappedPath().getParent();
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
  @Override
  public PathImpl schemeWalk(String userPath,
                         Map<String,Object> newAttributes,
                         String newPath, int offset)
  {
    return getWrappedPath().schemeWalk(userPath, newAttributes,
                                       newPath, offset);
  }

  /**
   * Returns the full url for the given path.
   */
  public String getURL()
  {
    return getWrappedPath().getURL();
  }

  /**
   * Returns the url scheme
   */
  public String getScheme()
  {
    return getWrappedPath().getScheme();
  }

  /**
   * Returns the hostname
   */
  public String getHost()
  {
    return getWrappedPath().getHost();
  }

  /**
   * Returns the port.
   */
  public int getPort()
  {
    return getWrappedPath().getPort();
  }

  /**
   * Returns the path.  e.g. for HTTP, returns the part after the
   * host and port.
   */
  public String getPath()
  {
    return getWrappedPath().getPath();
  }

  /**
   * Returns the last segment of the path.
   *
   * <p>e.g. for http://www.caucho.com/products/index.html, getTail()
   * returns 'index.html'
   */
  public String getTail()
  {
    return getWrappedPath().getTail();
  }

  /**
   * Returns the query string of the path.
   */
  public String getQuery()
  {
    return getWrappedPath().getQuery();
  }

  /**
   * Returns the native representation of the path.
   *
   * On Windows, getNativePath() returns 'd:\\foo\bar.html',
   * getPath() returns '/d:/foo/bar.html'
   */
  public String getNativePath()
  {
    return getWrappedPath().getNativePath();
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
    return getWrappedPath().getUserPath();
  }

  /**
   * Sets the user path.  Useful for temporary files caching another
   * URL.
   */
  public void setUserPath(String userPath)
  {
    getWrappedPath().setUserPath(userPath);
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
    return getWrappedPath().getFullPath();
  }

  /**
   * For union paths like MergePath, return the relative path into
   * that path.
   */
  public String getRelativePath()
  {
    return getWrappedPath().getRelativePath();
  }

  /**
   * Tests if the file exists.
   */
  public boolean exists()
  {
    return getWrappedPath().exists();
  }

  /**
   * Returns the mime-type of the file.
   * <p>Mime-type ignorant filesystems return 'application/octet-stream'
   */
  public String getContentType()
  {
    return getWrappedPath().getContentType();
  }

  /**
   * Tests if the path refers to a directory.
   */
  public boolean isDirectory()
  {
    return getWrappedPath().isDirectory();
  }

  /**
   * Tests if the path refers to a file.
   */
  public boolean isFile()
  {
    return getWrappedPath().isFile();
  }

  /**
   * Tests if the path refers to an object.
   */
  public boolean isObject()
  {
    return getWrappedPath().isObject();
  }

  /**
   * Returns the length of the file in bytes.
   * @return 0 for non-files
   */
  public long length()
  {
    return getWrappedPath().length();
  }

  /**
   * Returns the last modified time of the file.  According to the jdk,
   * this may not correspond to the system time.
   * @return 0 for non-files.
   */
  public long getLastModified()
  {
    return getWrappedPath().getLastModified();
  }

  public void setLastModified(long time)
  {
    getWrappedPath().setLastModified(time);
  }

  /**
   * Returns the last access time of the file.
   *
   * @return 0 for non-files.
   */
  public long getLastAccessTime()
  {
    return getWrappedPath().getLastAccessTime();
  }

  /**
   * Returns the create time of the file.
   *
   * @return 0 for non-files.
   */
  public long getCreateTime()
  {
    return getWrappedPath().getCreateTime();
  }

  /**
   * Tests if the file can be read.
   */
  public boolean canRead()
  {
    return getWrappedPath().canRead();
  }

  /**
   * Tests if the file can be written.
   */
  public boolean canWrite()
  {
    return getWrappedPath().canWrite();
  }

  /**
   * Changes the permissions
   */
  public boolean chmod(int value)
  {
    return getWrappedPath().chmod(value);
  }

  /**
   * @return The contents of this directory or null if the path does not
   * refer to a directory.
   */
  public String []list() throws IOException
  {
    return getWrappedPath().list();
  }

  /**
   * Returns a jdk1.2 Iterator for the contents of this directory.
   */
  public Iterator<String> iterator() throws IOException
  {
    return getWrappedPath().iterator();
  }

  /**
   * Creates the directory named by this path.
   * @return true if successful.
   */
  public boolean mkdir() throws IOException
  {
    return getWrappedPath().mkdir();
  }

  /**
   * Creates the directory named by this path and any parent directories.
   * @return true if successful.
   */
  public boolean mkdirs() throws IOException
  {
    return getWrappedPath().mkdirs();
  }

  /**
   * Removes the file or directory named by this path.
   * @return true if successful.
   */
  public boolean remove() throws IOException
  {
    return getWrappedPath().remove();
  }

  /**
   * Removes the all files and directories below this path.
   *
   * @return true if successful.
   */
  public boolean removeAll() throws IOException
  {
    return getWrappedPath().removeAll();
  }

  /**
   * Renames the file or directory to the name given by the path.
   * @return true if successful
   */
  public boolean renameTo(PathImpl path) throws IOException
  {
    return getWrappedPath().renameTo(path);
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
    return getWrappedPath().createRoot();
  }

  public PathImpl createRoot(SchemeMap schemeMap)
  {
    return getWrappedPath().createRoot(schemeMap);
  }

  /**
   * Binds the context to the current path.  Later lookups will return
   * the new context instead of the current path.  Essentially, this is a
   * software symbolic link.
   */
  public void bind(PathImpl context)
  {
    getWrappedPath().bind(context);
  }

  /**
   * unbinds a link.
   */
  public void unbind()
  {
    getWrappedPath().unbind();
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
    return getWrappedPath().getValue();
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
    getWrappedPath().setValue(obj);
  }

  /**
   * Gets an attribute of the object.
   */
  public Object getAttribute(String name) throws IOException
  {
    return getWrappedPath().getAttribute(name);
  }

  /**
   * Returns a iterator of all attribute names set for this object.
   * @return null if path has no attributes.
   */
  public Iterator getAttributeNames() throws IOException
  {
    return getWrappedPath().getAttributeNames();
  }

  /**
   * Opens a ReadWritePair for reading and writing.
   *
   * <p>A chat channel, for example, would open its socket using this
   * interface.
   */
  public ReadWritePair openReadWrite() throws IOException
  {
    return getWrappedPath().openReadWrite();
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
  public void openReadWrite(ReadStreamOld is, WriteStreamOld os) throws IOException
  {
    getWrappedPath().openReadWrite(is, os);
  }

  /**
   * Opens a stream for appending.
   */
  public WriteStreamOld openAppend() throws IOException
  {
    return getWrappedPath().openAppend();
  }

  /**
   * Opens a random-access stream.
   */
  public RandomAccessStream openRandomAccess() throws IOException
  {
    return getWrappedPath().openRandomAccess();
  }

  /**
   * Creates the file named by this Path and returns true if the
   * file is new.
   */
  public boolean createNewFile() throws IOException
  {
    return getWrappedPath().createNewFile();
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
    return getWrappedPath().createTempFile(prefix, suffix);
  }

  /**
   * Utility to write the contents of this path to the destination stream.
   *
   * @param os destination stream.
   */
  public void writeToStream(OutputStream os)
    throws IOException
  {
    getWrappedPath().writeToStream(os);
  }

  /**
   * Utility to write the contents of this path to the destination stream.
   *
   * @param os destination stream.
   */
  public void writeToStream(OutputStreamWithBuffer os)
    throws IOException
  {
    getWrappedPath().writeToStream(os);
  }

  /**
   * Returns the crc64 code.
   */
  public long getCrc64()
  {
    return getWrappedPath().getCrc64();
  }

  /**
   * Returns the object at this path.  Normally, only paths like JNDI
   * will support this.
   */
  public Object getObject()
    throws IOException
  {
    return getWrappedPath().getObject();
  }

  /**
   * Sets the object at this path.  Normally, only paths like JNDI
   * will support this.
   */
  public void setObject(Object obj)
    throws IOException
  {
    getWrappedPath().setObject(obj);
  }

  public long getInode()
  {
    return getWrappedPath().getInode();
  }

  public boolean isExecutable()
  {
    return getWrappedPath().isExecutable();
  }

  public boolean setExecutable(boolean isExecutable)
  {
    return getWrappedPath().setExecutable(isExecutable);
  }

  public int getGroup()
  {
    return getWrappedPath().getGroup();
  }

  public boolean changeGroup(int gid)
    throws IOException
  {
    return getWrappedPath().changeGroup(gid);
  }

  public boolean changeGroup(String groupName)
    throws IOException
  {
    return getWrappedPath().changeGroup(groupName);
  }

  public int getOwner()
  {
    return getWrappedPath().getOwner();
  }

  public boolean changeOwner(int uid)
    throws IOException
  {
    return getWrappedPath().changeOwner(uid);
  }

  public boolean changeOwner(String ownerName)
    throws IOException
  {
    return getWrappedPath().changeOwner(ownerName);
  }

  public long getDiskSpaceFree()
  {
    return getWrappedPath().getDiskSpaceFree();
  }

  public long getDiskSpaceTotal()
  {
    return getWrappedPath().getDiskSpaceTotal();
  }

  public int hashCode()
  {
    return getWrappedPath().hashCode();
  }

  public boolean equals(Object o)
  {
    return o.equals(getWrappedPath());
  }

  public String toString()
  {
    return getWrappedPath().toString();
  }

  public StreamImpl openReadImpl() throws IOException
  {
    return getWrappedPath().openReadImpl();
  }

  public StreamImpl openWriteImpl() throws IOException
  {
    return getWrappedPath().openWriteImpl();
  }

  public StreamImpl openReadWriteImpl() throws IOException
  {
    return getWrappedPath().openReadWriteImpl();
  }

  public StreamImpl openAppendImpl() throws IOException
  {
    return getWrappedPath().openAppendImpl();
  }
}
