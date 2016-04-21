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

package com.caucho.v5.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * convenience methods for io
 */
public class IoUtil
{
  private static final Logger log
    = Logger.getLogger(IoUtil.class.getName());

  public static String readln(InputStream is)
    throws IOException
  {
    StringBuilder sb = new StringBuilder();
    
    int ch;
    while ((ch = is.read()) >= 0 && ch != '\n') {
      sb.append((char) ch);
    }
    
    if (ch < 0) {
      return null;
    }

    return sb.toString();
  }
  
  public static int readInt(InputStream is)
    throws IOException
  {
    return ((is.read() << 24)
        + (is.read() << 16)
        + (is.read() << 8)
        + is.read());
  }

  public static void writeInt(OutputStream os, int v)
    throws IOException
  {
    os.write(v >> 24);
    os.write(v >> 16);
    os.write(v >> 8);
    os.write(v);
  }

  public static long readLong(InputStream is)
    throws IOException
  {
    return (((long) is.read() << 56)
        + ((long) is.read() << 48)
        + ((long) is.read() << 40)
        + ((long) is.read() << 32)
        + ((long) is.read() << 24)
        + ((long) is.read() << 16)
        + ((long) is.read() << 8)
        + ((long) is.read()));
  }

  public static void writeLong(OutputStream os, long v)
    throws IOException
  {
    os.write((int) (v >> 56));
    os.write((int) (v >> 48));
    os.write((int) (v >> 40));
    os.write((int) (v >> 32));
    os.write((int) (v >> 24));
    os.write((int) (v >> 16));
    os.write((int) (v >> 8));
    os.write((int) v);
  }

  public static long copy(InputStream is, OutputStream os)
    throws IOException
  {
    TempBuffer tBuf = TempBuffer.create();
    byte []buffer = tBuf.buffer();

    long total = 0;
    int sublen;

    while ((sublen = is.read(buffer, 0, buffer.length)) > 0) {
      os.write(buffer, 0, sublen);

      total += sublen;
    }

    tBuf.free();

    return total;
  }

  public static long copy(InputStream is, OutputStream os, int len)
    throws IOException
  {
    TempBuffer tBuf = TempBuffer.create();
    byte []buffer = tBuf.buffer();

    long total = 0;
    int sublen;

    while (len > 0 && (sublen = is.read(buffer, 0, buffer.length)) > 0) {
      os.write(buffer, 0, sublen);
      //System.out.println("COPY: " + new String(buffer, 0, sublen));
      len -= sublen;
      total += sublen;
    }

    tBuf.free();

    return total;
  }

  public static void close(AutoCloseable is)
  {
    try {
      if (is != null) {
        is.close();
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }

  public static void close(OutputStream os)
  {
    try {
      if (os != null)
        os.close();
    } catch (IOException e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }

  public static void close(Writer os)
  {
    try {
      if (os != null)
        os.close();
    } catch (IOException e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }

  public static int readAll(InputStream is,
                             byte[] buffer, int offset, int length)
    throws IOException
  {
    int readLength = 0;
    
    while (length > 0) {
      int sublen = is.read(buffer, offset, length);

      if (sublen <= 0) {
        return readLength;
      }

      length -= sublen;
      offset += sublen;
      readLength += sublen;
    }
    
    return readLength;
  }

  public static void walk(Path path, Consumer<Path> consumer)
    throws IOException
  {
    Files.walkFileTree(path, new DirConsumer(consumer));
  }

  public static void removeAll(Path dir)
    throws IOException
  {
    Files.walkFileTree(dir, new DirRemove());
  }
  
  private static class DirRemove implements FileVisitor<Path>
  {
    @Override
    public FileVisitResult preVisitDirectory(Path dir,
                                             BasicFileAttributes attrs)
                                                 throws IOException
    {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
      throws IOException
    {
      Files.delete(file);
      
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc)
        throws IOException
    {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
        throws IOException
    {
      Files.delete(dir);
      
      return FileVisitResult.CONTINUE;
    }
  }
  
  private static class DirConsumer implements FileVisitor<Path>
  {
    private Consumer<Path> _consumer;
    
    DirConsumer(Consumer<Path> consumer)
    {
      Objects.requireNonNull(consumer);
      
      _consumer = consumer;
    }
    
    @Override
    public FileVisitResult preVisitDirectory(Path dir,
                                             BasicFileAttributes attrs)
                                                 throws IOException
    {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
      throws IOException
    {
      _consumer.accept(file);
      
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc)
        throws IOException
    {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
        throws IOException
    {
      return FileVisitResult.CONTINUE;
    }
  }
}
