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

import com.caucho.v5.util.CharBuffer;

import java.util.Map;

/**
 * Abstract FilesystemPath, the parent of hierarchical Paths like
 * FilePath or HttpPath.
 */
abstract public class FilesystemPath extends PathImpl
{
  protected FilesystemPath _root;
  protected BindPath _bindRoot;
  protected String _pathname;
  protected String _userPath;

  /**
   * Create a new filesystemPath
   *
   * @param root Root of url space
   * @param userPath the user's path
   * @param pathname Canonical path
   */
  protected FilesystemPath(FilesystemPath root,
                           String userPath,
                           String pathname)
  {
    super(root);

    init(root, userPath, pathname);
  }

  protected void init(FilesystemPath root,
                      String userPath,
                      String pathname)
  {
    if (pathname == null)
      throw new NullPointerException();

    _userPath = userPath;
    _pathname = pathname;

    if (root != null) {
      _root = root;
      _bindRoot = root._bindRoot;
    }
  }

  /**
   * Return the parent Path
   */
  @Override
  public PathImpl getParent()
  {
    if (_pathname.length() <= 1)
      return lookup("/");

    int length = _pathname.length();
    int lastSlash = _pathname.lastIndexOf('/');

    if (lastSlash < 1)
      return lookup("/");

    if (lastSlash == length - 1) {
      lastSlash = _pathname.lastIndexOf('/', length - 2);
      if (lastSlash < 1)
        return lookup("/");
    }

    return lookup(_pathname.substring(0, lastSlash));
  }

  /**
   * schemeWalk is called by Path for a scheme lookup like file:/tmp/foo
   *
   * @param userPath the user's lookup() path
   * @param attributes the user's attributes
   * @param filePath the actual lookup() path
   * @param offset offset into filePath
   */
  @Override
  public PathImpl schemeWalk(String userPath,
                         Map<String,Object> attributes,
                         String filePath,
                         int offset)
  {
    String canonicalPath;

    if (offset < filePath.length()
        && (filePath.charAt(offset) == '/'
            || filePath.charAt(offset) == _separatorChar))
      canonicalPath = normalizePath("/", filePath, offset, _separatorChar);
    else
      canonicalPath = normalizePath(_pathname, filePath, offset,
                                    _separatorChar);

    return fsWalk(userPath, attributes, canonicalPath);
  }

  /**
   * Lookup a path relative to the current filesystem's root.
   * Filesystems will specialize fsWalk.
   *
   * @param userPath the exact string passed by the user's lookup()
   * @param newAttributes the user's new attributes
   * @param newPath the normalized real path
   *
   * @return the matching path
   */
  abstract public PathImpl fsWalk(String userPath,
                              Map<String,Object> newAttributes,
                              String newPath);

  /**
   * wrapper for the real normalize path routine to use CharBuffer.
   *
   * @param oldPath The parent Path's path
   * @param newPath The user's new path
   * @param offset Offset into the user path
   *
   * @return the normalized path
   */
  static protected String normalizePath(String oldPath,
                                        String newPath,
                                        int offset,
                                        char separatorChar)
  {
    CharBuffer cb = new CharBuffer();
    normalizePath(cb, oldPath, newPath, offset, separatorChar);
    return cb.toString();
  }

  /**
   * Normalizes a filesystemPath path.
   *
   * <ul>
   * <li>foo//bar -> foo/bar
   * <li>foo/./bar -> foo/bar
   * <li>foo/../bar -> bar
   * <li>/../bar -> /bar
   * </ul>
   *
   * @param cb charBuffer holding the normalized result
   * @param oldPath the parent path
   * @param newPath the relative path
   * @param offset where in the child path to start
   */
  static protected void normalizePath(CharBuffer cb, String oldPath,
                                      String newPath, int offset,
                                      char separatorChar)
  {
    cb.clear();
    cb.append(oldPath);
    if (cb.length() == 0 || cb.lastChar() != '/')
      cb.append('/');

    int length = newPath.length();
    int i = offset;
    while (i < length) {
      char ch = newPath.charAt(i);
      char ch2;

      switch (ch) {
      default:
        if (ch != separatorChar) {
          cb.append(ch);
          i++;
          break;
        }
        // the separator character falls through to be treated as '/'

      case '/':
        // "//" -> "/"
        if (cb.lastChar() != '/')
          cb.append('/');
        i++;
        break;

      case '.':
        if (cb.lastChar() != '/') {
          cb.append('.');
          i++;
          break;
        }

        // "/." -> ""
        if (i + 1 >= length) {
          i += 2;
          break;
        }

        switch (newPath.charAt(i + 1)) {
        default:
          if (newPath.charAt(i + 1) != separatorChar) {
            cb.append('.');
            i++;
            break;
          }
          // the separator falls through to be treated as '/'

          // "/./" -> "/"
        case '/':
          i += 2;
          break;

          // "foo/.." -> ""
        case '.':
          if ((i + 2 >= length ||
               (ch2 = newPath.charAt(i + 2)) == '/' || ch2 == separatorChar) &&
              cb.lastChar() == '/') {
            int segment = cb.lastIndexOf('/', cb.length() - 2);
            if (segment == -1) {
              cb.clear();
              cb.append('/');
            } else
              cb.length(segment + 1);

            i += 3;
          } else {
            cb.append('.');
            i++;
          }
          break;
        }
      }

    }

    // strip trailing "/"
    /*
    if (cb.length() > 1 && cb.getLastChar() == '/')
      cb.setLength(cb.length() - 1);
    */
  }

  /**
   * Returns the root.
   */
  public FilesystemPath getRoot()
  {
    return _root;
  }

  /**
   * Returns the path portion of the URL.
   */
  @Override
  public String getPath()
  {
    return _pathname;
  }

  /**
   * Return's the application's name for the path, e.g. for
   * a relative path.
   */
  public String getUserPath()
  {
    return _userPath != null ? _userPath : _pathname;
  }

  public void setUserPath(String path)
  {
    _userPath = path;
  }

  /**
   * For chrooted filesystems return the real system path.
   */
  public String getFullPath()
  {
    if (_root == this || _root == null)
      return getPath();

    String rootPath = _root.getFullPath();
    String path = getPath();

    if (rootPath.length() <= 1)
      return path;
    else if (path.length() <= 1)
      return rootPath;
    else
      return rootPath + path;
  }
  
  @Override
  public String getNativePath()
  {
    return getURL();
  }

  @Override
  public String getTail()
  {
    String path = getPath();

    int length = path.length();
    int p = path.lastIndexOf('/');
    if (p == -1)
      return "";
    else if (p < length - 1)
      return path.substring(p + 1);
    else {
      p = path.lastIndexOf('/', length - 2);
      if (p < 0)
        return "";
      return path.substring(p + 1, length - 1);
    }
  }

  /**
   * Essentially chroot
   */
  public PathImpl createRoot(SchemeMap schemeMap)
  {
    FilesystemPath restriction = (FilesystemPath) copy();

    restriction._schemeMap = schemeMap;
    restriction._root = this;
    restriction._pathname = "/";
    restriction._userPath = "/";

    return restriction;
  }

  public void bind(PathImpl context)
  {
    if (_bindRoot == null)
      _bindRoot = _root._bindRoot;

    if (_bindRoot == null) {
      _bindRoot = new BindPath(_root);
      _root._bindRoot = _bindRoot;
    }

    _bindRoot.bind(getPath(), context);
  }

  public int hashCode()
  {
    return getURL().hashCode();
  }

  public boolean equals(Object b)
  {
    if (this == b)
      return true;
    else if (b == null || ! getClass().equals(b.getClass()))
      return false;

    PathImpl bPath = (PathImpl) b;

    return getURL().equals(bPath.getURL());
  }
}
