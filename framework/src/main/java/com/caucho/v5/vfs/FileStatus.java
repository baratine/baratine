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
 * @author Emil Ong
 */

package com.caucho.v5.vfs;

import java.io.File;

public class FileStatus {
  public static long S_IFMT = 00170000;
  public static long S_IFSOCK = 0140000;
  public static long S_IFLNK        = 0120000;
  public static long S_IFREG = 0100000;
  public static long S_IFBLK = 0060000;
  public static long S_IFDIR = 0040000;
  public static long S_IFCHR = 0020000;
  public static long S_IFIFO = 0010000;

  private long _st_dev;
  private long _st_ino;
  private int _st_mode;
  private int _st_nlink;
  private int _st_uid;
  private int _st_gid;
  private long _st_rdev;
  private long _st_size;
  private long _st_blksize;
  private long _st_blocks;
  private long _st_atime;
  private long _st_mtime;
  private long _st_ctime;

  private boolean _isRegularFile;
  private boolean _isDirectory;
  private boolean _isCharacterDevice;
  private boolean _isBlockDevice;
  private boolean _isFIFO;
  private boolean _isLink;
  private boolean _isSocket;

  public FileStatus()
  {
  }
  
  public void init(long st_dev, long st_ino, int st_mode, int st_nlink,
                   int st_uid, int st_gid, long st_rdev, long st_size,
                   long st_blksize, long st_blocks,
                   long st_atime, long st_mtime, long st_ctime,
                   boolean isRegularFile, boolean isDirectory,
                   boolean isCharacterDevice, boolean isBlockDevice,
                   boolean isFIFO, boolean isLink, boolean isSocket)
  {
    _st_dev = st_dev;
    _st_ino = st_ino;
    _st_mode = st_mode;
    _st_nlink = st_nlink;
    _st_uid = st_uid;
    _st_gid = st_gid;
    _st_rdev = st_rdev;
    _st_size = st_size;
    _st_blksize = st_blksize;
    _st_blocks = st_blocks;
    _st_atime = st_atime;
    _st_mtime = st_mtime;
    _st_ctime = st_ctime;

    _isRegularFile = isRegularFile;
    _isDirectory = isDirectory;
    _isCharacterDevice = isCharacterDevice;
    _isBlockDevice = isBlockDevice;
    _isFIFO = isFIFO;
    _isLink = isLink;
    _isSocket = isSocket;
  }
  
  public void updateWindowsTime(File file)
  {
    _st_mtime = file.lastModified();
    _st_atime = _st_mtime;
    _st_ctime = _st_mtime;
  }

  public long getDev()
  {
    return _st_dev;
  }

  public long getIno()
  {
    return _st_ino;
  }

  public int getMode()
  {
    return _st_mode;
  }

  public int getNlink()
  {
    return _st_nlink;
  }

  public int getUid()
  {
    return _st_uid;
  }

  public int getGid()
  {
    return _st_gid;
  }

  public long getRdev()
  {
    return _st_rdev;
  }

  public long getSize()
  {
    return _st_size;
  }

  public long getBlksize()
  {
    return _st_blksize;
  }

  public long getBlocks()
  {
    return _st_blocks;
  }

  public long getAtime()
  {
    return _st_atime;
  }

  public long getMtime()
  {
    return _st_mtime;
  }

  public long getCtime()
  {
    return _st_ctime;
  }

  public boolean isRegularFile() 
  {
    return _isRegularFile;
  }

  public boolean isDirectory() 
  {
    return _isDirectory;
  }

  public boolean isCharacterDevice() 
  {
    return _isCharacterDevice;
  }

  public boolean isBlockDevice() 
  {
    return _isBlockDevice;
  }

  public boolean isFIFO() 
  {
    return _isFIFO;
  }

  public boolean isLink() 
  {
    return _isLink;
  }

  public boolean isSocket() 
  {
    return _isSocket;
  }

  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + String.format("%o", _st_mode)
            + ",len=" + _st_size
            + "]");
  }
}
