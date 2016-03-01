/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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
 * @author Nam Nguyen
 */

package com.caucho.v5.bartender.files.provider;

import io.baratine.files.BfsFileSync;
import io.baratine.files.Status;
import io.baratine.files.WriteOption;
import io.baratine.service.ResultFuture;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceRef;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.bartender.files.BartenderFileSystem;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.util.L10N;

/**
 * BFS implementation of JDK FileSystemProvider.
 */
public class JFileSystemProvider extends FileSystemProvider
{
  private static final L10N L = new L10N(JFileSystemProvider.class);

  private static EnvironmentLocal<JFileSystem> _localSystem
    = new EnvironmentLocal<>(); 

  @Override
  public String getScheme()
  {
    return "bfs";
  }

  @Override
  public FileSystem newFileSystem(URI uri, Map<String, ?> env)
      throws IOException
  {
    return getLocalSystem();
  }

  @Override
  public FileSystem getFileSystem(URI uri)
  {
    return getLocalSystem();
  }

  @Override
  public Path getPath(URI uri)
  {
    JFileSystem fileSystem = getLocalSystem();

    String path = uri.toString();

    return new JPath(fileSystem, path);
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path,
                                            Set<? extends OpenOption> options,
                                            FileAttribute<?>... attrs)
    throws IOException
  {
    JPath jPath = (JPath) path;

    if (options.contains(StandardOpenOption.WRITE)) {
      if (options.contains(StandardOpenOption.APPEND)) {
        throw new UnsupportedOperationException();
      }

      // default for writes is StandOpenOption.TRUNCATE_EXISTING

      OutputStream os = jPath.getBfsFile().openWrite(WriteOption.Standard.CLOSE_WAIT_FOR_PUT);
      //OutputStream os = jPath.getBfsFile().openWrite();

      return new JOutputByteChannel(jPath.getBfsFile(), os);
    }
    else {
      // default is to read

      InputStream is = jPath.getBfsFile().openRead();

      if (is == null) {
        throw new IOException(L.l("cannot open for reading: {0}", jPath.toUri()));
      }

      return new JInputByteChannel(jPath.getBfsFile(), is);
    }
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Path dir,
                                                  Filter<? super Path> filter)
    throws IOException
  {
    JPath jPath = (JPath) dir;

    return new JDirectoryStream(jPath, filter);
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs)
    throws IOException
  {
    throw new UnsupportedOperationException(L.l("bfs does not support explicit directories"));
  }

  @Override
  public void delete(Path path) throws IOException
  {
    JPath jPath = (JPath) path;
    BfsFileSync file = jPath.getBfsFile();

    ResultFuture<Boolean> result = new ResultFuture<>();

    String []list = file.list();

    if (list.length != 0) {
      throw new DirectoryNotEmptyException(path.toUri().toString());
    }

    file.remove(result);

    boolean isSuccessful;

    try {
      isSuccessful = result.get(10, TimeUnit.SECONDS);
    }
    catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      }
      else {
        if (e instanceof ServiceException) {
          Throwable cause = ((ServiceException) e).unwrap();

          if (cause instanceof IOException) {
            throw (IOException) cause;
          }
        }

        throw new IOException(e);
      }
    }

    if (! isSuccessful) {
      throw new IOException(L.l("failed to delete file: {0}", path));
    }
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options)
    throws IOException
  {
    JPath jSource = (JPath) source;
    JPath jTarget = (JPath) target;

    try (InputStream is = jSource.getBfsFile().openRead()) {
      if (is == null) {
        throw new IOException(L.l("unable to read from file {0}", source.toUri()));
      }

      ArrayList<WriteOption> bfsOptionList = new ArrayList<>();
      for (CopyOption option : options) {
        if (option == StandardCopyOption.REPLACE_EXISTING) {
          bfsOptionList.add(WriteOption.Standard.OVERWRITE);
        }
      }

      WriteOption []bfsOptions = new WriteOption[bfsOptionList.size()];
      bfsOptionList.toArray(bfsOptions);

      try (OutputStream os = jTarget.getBfsFile().openWrite(bfsOptions)) {
        if (os == null) {
          throw new IOException(L.l("unable to write to file {0}", target.toUri()));
        }

        IoUtil.copy(is, os);
      }
    }
  }

  @Override
  public void move(Path source, Path target, CopyOption... options)
    throws IOException
  {
    JPath jPath = (JPath) target;

    copy(source, target, options);

    Status status = jPath.getBfsFile().getStatus();

    if (status.getType() == Status.FileType.NULL) {
      throw new IOException(L.l("unable to move {0} to {1}", source.toUri(), target.toUri()));
    }

    delete(source);
  }

  @Override
  public boolean isSameFile(Path path, Path path2) throws IOException
  {
    return path.normalize().compareTo(path2.normalize()) == 0;
  }

  @Override
  public boolean isHidden(Path path) throws IOException
  {
    return false;
  }

  @Override
  public FileStore getFileStore(Path path) throws IOException
  {
    return new JFileStore(this);
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException
  {
    JPath jPath = (JPath) path;
    Status status;

    try {
      status = jPath.getBfsFile().getStatus();
    }
    catch (Exception e) {
      throw createIOException(e);
    }

    if (status.getType() == Status.FileType.FILE) {
      // do nothing
    }
    else if (status.getType() == Status.FileType.DIRECTORY) {
      // do nothing
    }
    else {
      throw new NoSuchFileException(path.toUri().toString());
    }
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Path path,
                                                              Class<V> type,
                                                              LinkOption... options)
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException();
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(Path path,
                                                          Class<A> type,
                                                          LinkOption... options)
    throws IOException
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes,
                                            LinkOption... options)
    throws IOException
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value,
                           LinkOption... options) throws IOException
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getLocalSystem() + "]";
  }

  private JFileSystem getLocalSystem()
  {
    synchronized (_localSystem) {
      JFileSystem localSystem = _localSystem.getLevel();
      
      if (localSystem == null) {
        BartenderFileSystem fileSystem = BartenderFileSystem.getCurrent();

        if (fileSystem == null) {
          throw new FileSystemNotFoundException(L.l("cannot find local bfs file system"));
        }

        ServiceRef root = fileSystem.getRootServiceRef();

        localSystem = new JFileSystem(this, root);
        
        _localSystem.set(localSystem);
      }
      
      return localSystem;
    }
  }

  static IOException createIOException(Throwable t)
  {
    if (t instanceof ServiceException) {
      t = ((ServiceException) t).unwrap();
    }

    if (t instanceof IOException) {
      return (IOException) t;
    }
    else {
      return new IOException(t);
    }
  }

}
