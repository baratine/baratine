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

package com.caucho.v5.subsystem;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * Root service for the root and data directories.
 *
 */
public class RootDirectorySystem extends SubSystemBase 
{
  private static final Logger log
    = Logger.getLogger(RootDirectorySystem.class.getName());
  
  public static final int START_PRIORITY_ROOT_DIRECTORY = 20;

  private static final L10N L = new L10N(RootDirectorySystem.class);
  
  private static final ConcurrentHashMap<Path,LockItem> _lockMap
    = new ConcurrentHashMap<>();
  
  private final Path _rootDirectory;
  private final Path _dataDirectory;

  public RootDirectorySystem(Path rootDirectory, Path dataDirectory) 
    throws IOException
  {
    Objects.requireNonNull(rootDirectory);
    Objects.requireNonNull(dataDirectory);
    
    /*
    if (dataDirectory instanceof MemoryPath) { // QA
      dataDirectory = 
        WorkDir.getTmpWorkDir().lookup("qa/" + dataDirectory.getFullPath());
    }
    */
    
    _rootDirectory = rootDirectory;
    _dataDirectory = dataDirectory;
    
    Files.createDirectories(rootDirectory);
    Files.createDirectories(dataDirectory);
  }
  
  public static RootDirectorySystem createAndAddSystem(Path rootDirectory)
      throws IOException
  {
    return createAndAddSystem(rootDirectory, 
                               rootDirectory.resolve("data"));
  }

  public static RootDirectorySystem createAndAddSystem(Path rootDirectory,
                                                         Path dataDirectory)
    throws IOException
  {
    SystemManager system = preCreate(RootDirectorySystem.class);
    
    RootDirectorySystem service =
      new RootDirectorySystem(rootDirectory, dataDirectory);
    system.addSystem(RootDirectorySystem.class, service);
    
    return service;
  }

  public static RootDirectorySystem getCurrent()
  {
    return SystemManager.getCurrentSystem(RootDirectorySystem.class);
  }

  /**
   * Returns the data directory for current active directory service.
   */
  public static Path getCurrentDataDirectory()
  {
    RootDirectorySystem rootService = getCurrent();
    
    if (rootService == null)
      throw new IllegalStateException(L.l("{0} must be active for getCurrentDataDirectory().",
                                          RootDirectorySystem.class.getSimpleName()));
    
    return rootService.dataDirectory();
  }
  
  /**
   * Returns the root directory.
   */
  public Path rootDirectory()
  {
    return _rootDirectory;
  }

  /**
   * Returns the internal data directory.
   */
  public Path dataDirectory()
  {
    return _dataDirectory;
  }
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY_ROOT_DIRECTORY;
  }
  
  @Override
  public void start() throws Exception
  {
    super.start();
    
    LockItem lockItem = getLockItem(dataDirectory());
    
    /* XXX:
    if (! lockItem.lock()) {
      throw new IllegalStateException(L.l("Cannot open lock to work directory {0}. Check for conflicting servers or permissions.",
                                          getDataDirectory()));
    }
    */
  }
  
  @Override
  public void stop(ShutdownModeAmp mode) throws Exception
  {
    super.stop(mode);
    
    LockItem lockItem = getLockItem(dataDirectory());
    
    lockItem.unlock();
  }

  public static boolean isFree(Path dir)
  {
    FileTime lastModified;
    
    try {
      lastModified = Files.getLastModifiedTime(dir);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    // baratine/8121
    if (CurrentTime.currentTime() - lastModified.toMillis() < 5000
        && ! CurrentTime.isTest()) {
      return false;
    }
    
    LockItem lockItem = getLockItem(dir);
    
    return lockItem.isFree();
  }
  
  private static LockItem getLockItem(Path dir)
  {
    Path lockPath = dir.resolve("lock.txt");
    
    _lockMap.putIfAbsent(lockPath, new LockItem(lockPath));
    
    return _lockMap.get(lockPath);
  }
  
  private static class LockItem
  {
    private Path _path;
    
    private FileChannel _osFile;
    private FileLock _lock;
    
    LockItem(Path path)
    {
      _path = path;
    }
    
    synchronized boolean lock()
    {
      if (_lock != null) {
        return false;
      }
      
      try {
         FileChannel osFile = (FileChannel) Files.newByteChannel(_path);
        _lock = osFile.tryLock();
        
        if (_lock == null) {
          osFile.close();
          return false;
        }
        
        _osFile = osFile;
        
        return true;
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);

        return false;
      }
    }
    
    synchronized void unlock()
    {
      FileChannel osFile = _osFile;
      _osFile = null;
      
      FileLock lock = _lock;
      _lock = null;
      
      if (lock != null) {
        try {
          lock.close();
        } catch (Exception e) {
          log.log(Level.FINEST, e.toString(), e);
        }
      }
      
      if (osFile != null) {
        try {
          osFile.close();
        } catch (Exception e) {
          log.log(Level.FINEST, e.toString(), e);
        }
      }
    }
    
    synchronized boolean isFree()
    {
      if (_lock != null) {
        return false;
      }
      
      if (! Files.isReadable(_path)) {
        return true;
      }
      
      try (FileOutputStream fOs = (FileOutputStream) Files.newOutputStream(_path)) {
        FileLock lock = fOs.getChannel().tryLock();
        
        if (lock != null) {
          lock.close();
          
          return true;
        }
        else {
          return false;
        }
      } catch (Exception e) {
        log.finer(_path + ": isFree " + e);
        log.log(Level.FINEST, e.toString(), e);

        return true;
      }
    }
  }
}
