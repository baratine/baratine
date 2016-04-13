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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.util.CharBuffer;

/**
 * Facade to create useful Path and Stream objects.
 *
 * <code><pre>
 * Path path = Vfs.lookup("foo.html");
 * </pre><code>
 *
 * <p>The default scheme is the file scheme.  Other schemes are
 * available using the full url.
 *
 * <code><pre>
 * Path mail = Vfs.lookup("mailto:drofnats@foo.com.test?subject='hi'");
 * Stream body = mail.openWrite();
 * body.writeln("How's it going?");
 * body.close();
 * </pre><code>
 */
public final class VfsOld
{
  private static final EnvironmentLocal<PathImpl> ENV_PWD
    = new EnvironmentLocal<>("caucho.vfs.pwd");
  
  private static final SchemeMap DEFAULT_SCHEME_MAP;
  
  private static final EnvironmentLocal<SchemeMap> _localSchemeMap
    = new EnvironmentLocal<>();
  
  static FilesystemPath PWD;

  private static AtomicBoolean _isInitJNI = new AtomicBoolean();

  private VfsOld() {}
  
  /**
   * Returns a new path relative to the current directory.
   * 
   * @param url a relative or absolute url
   * @return the new path.
   */
  public static PathImpl lookup(String url)
  {
    PathImpl pwd = getPwd();

    if (! url.startsWith("/")) {
      return pwd.lookup(url, null);
    }
    else {
      return PWD.lookup(url, null);
    }
  }

  public static FilesystemPath getGlobalPwd()
  {
    return PWD;
  }
  
  /**
   * Returns a path for the current directory.
   */
  public static PathImpl getPwd()
  {
    PathImpl pwd = ENV_PWD.get();
    
    if (pwd == null) {
      if (PWD == null) {
        /* JNI set later
        PWD = JniFilePath.create();

        if (PWD == null)
          PWD = new FilePath(null);
        */
        PWD = new FilePath(null);
      }
      pwd = PWD;
      ENV_PWD.setGlobal(pwd);
    }

    return pwd;
  }

  public static SchemeMap getLocalScheme()
  {
    synchronized (_localSchemeMap) {
      SchemeMap map = _localSchemeMap.getLevel();

      if (map == null) {
        map = _localSchemeMap.get().copy();

        if (map == null) {
          map = DEFAULT_SCHEME_MAP.copy();
        }

        _localSchemeMap.set(map);
      }

      return map;
    }
  }

  public static SchemeMap getDefaultScheme()
  {
    return DEFAULT_SCHEME_MAP;
  }

  /**
   * Returns a path for the current directory.
   */
  public static PathImpl getPwd(ClassLoader loader)
  {
    return ENV_PWD.get(loader);
  }

  /**
   * Sets a path for the current directory in the current environment.
   */
  public static void setPwd(PathImpl pwd)
  {
    setPwd(pwd, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Sets a path for the current directory in the current environment.
   */
  public static void setPwd(PathImpl pwd, ClassLoader loader)
  {
    ENV_PWD.set(pwd, loader);
  }

  /**
   * Returns a path for the current directory.
   */
  public static PathImpl lookup()
  {
    return getPwd();
  }

  /**
   * Returns a new path, including attributes.
   * <p>For example, an application may want to set locale headers
   * for an HTTP request.
   *
   * @param url the relative url
   * @param attr attributes used in searching for the url
   */
  public static PathImpl lookup(String url, Map<String,Object> attr)
  {
    return getPwd().lookup(url, attr);
  }

  /**
   * Returns a path using the native filesystem conventions.
   * <p>For example, on windows
   *
   * <code><pre>
   * Path path = Vfs.lookup("d:\\temp\\test.html");
   * </pre></code>
   *
   * @param url a relative path using the native filesystem conventions.
   */
  public static PathImpl lookupNative(String url)
  {
    return getPwd().lookupNative(url, null);
  }

  /**
   * Looks up a URL, decoding '%'
   *
   * @param url a relative path using the native filesystem conventions.
   */
  public static PathImpl lookup(URL url)
  {
    return getPwd().lookup(url);
  }

  /**
   * Returns a native filesystem path with attributes.
   *
   * @param url a relative path using the native filesystem conventions.
   * @param attr attributes used in searching for the url
   */
  public static PathImpl lookupNative(String url, Map<String,Object> attr)
  {
    return getPwd().lookupNative(url, attr);
  }

  public static ReadWritePair openReadWrite(InputStream is, OutputStream os)
  {
    VfsStreamOld s = new VfsStreamOld(is, os);
    WriteStreamOld writeStream = new WriteStreamOld(s);
    ReadStreamOld readStream = new ReadStreamOld(s);
    return new ReadWritePair(readStream, writeStream);
  }

  /**
   * Creates new ReadStream from an InputStream
   */
  public static ReadStreamOld openRead(InputStream is)
  {
    if (is instanceof ReadStreamOld)
      return (ReadStreamOld) is;
    
    VfsStreamOld s = new VfsStreamOld(is, null);
    return new ReadStreamOld(s);
  }

  public static ReadStreamOld openRead(InputStream is, WriteStreamOld ws)
  {
    VfsStreamOld s = new VfsStreamOld(is, null);
    return new ReadStreamOld(s);
  }

  /**
   * Creates a ReadStream from a Reader
   */
  public static ReadStreamOld openRead(Reader reader)
  {
    if (reader instanceof ReadStreamOld.StreamReader)
      return ((ReadStreamOld.StreamReader) reader).getStream();
    
    ReaderWriterStream s = new ReaderWriterStream(reader, null);
    ReadStreamOld is = new ReadStreamOld(s);
    try {
      is.setEncoding("utf-8");
    } catch (Exception e) {
    }

    return is;
  }

  /**
   * Create a ReadStream from a string.  utf-8 is used as the encoding
   */
  public static ReadStreamOld openRead(String path)
    throws IOException
  {
    return VfsOld.lookup(path).openRead();
  }

  public static ReadStreamOld openString(String string)
  {
    return com.caucho.v5.vfs.VfsStringReader.open(string);
  }

  public static WriteStreamOld openWrite(OutputStream os)
  {
    if (os instanceof WriteStreamOld)
      return ((WriteStreamOld) os);
    
    VfsStreamOld s = new VfsStreamOld(null, os);
    return new WriteStreamOld(s);
  }

  public static WriteStreamOld openWrite(Writer writer)
  {
    ReaderWriterStream s = new ReaderWriterStream(null, writer);
    WriteStreamOld os = new WriteStreamOld(s);
    
    try {
      os.setEncoding("utf-8");
    } catch (Exception e) {
    }

    return os;
  }

  /**
   * Creates a write stream to a CharBuffer.  This is the standard way
   * to write to a string.
   */
  public static WriteStreamOld openWrite(CharBuffer cb)
  {
    com.caucho.v5.vfs.VfsStringWriter s = new com.caucho.v5.vfs.VfsStringWriter(cb);
    WriteStreamOld os = new WriteStreamOld(s);
    
    try {
      os.setEncoding("utf-8");
    } catch (Exception e) {
    }

    return os;
  }

  public static WriteStreamOld openWrite(String path)
    throws IOException
  {
    return lookup(path).openWrite();
  }

  /*
  public static Path path(String root)
  {
    int p = root.indexOf(':');
    
    if (p > 0) {
      String scheme = root.substring(0, p);
      String tail = root.substring(p + 1);
      
      if (scheme.equals("classpath")) {
        FileSystem fileSystem = new FileSystemClasspath();
        
        return fileSystem.getPath(tail);
      }
    }
    
    if (root.startsWith("/")) {
      return FileSystems.getDefault().getPath(root);
    }
    else {
      throw new UnsupportedOperationException(root);
    }
  }
  */

  public static WriteStreamOld openAppend(String path)
    throws IOException
  {
    return lookup(path).openAppend();
  }

  public static String decode(String uri)
  {
    StringBuilder sb = new StringBuilder();

    int len = uri.length();

    for (int i = 0; i < len; i++) {
      char ch = uri.charAt(i);

      if (ch != '%' || len <= i + 2) {
        sb.append(ch);
        continue;
      }

      int d1 = uri.charAt(i + 1);
      int d2 = uri.charAt(i + 2);
      int v = 0;

      if ('0' <= d1 && d1 <= '9')
        v = 16 * v + d1 - '0';
      else if ('a' <= d1 && d1 <= 'f')
        v = 16 * v + d1 - 'a' + 10;
      else if ('A' <= d1 && d1 <= 'F')
        v = 16 * v + d1 - 'A' + 10;
      else {
        sb.append('%');
        continue;
      }

      if ('0' <= d2 && d2 <= '9')
        v = 16 * v + d2 - '0';
      else if ('a' <= d2 && d2 <= 'f')
        v = 16 * v + d2 - 'a' + 10;
      else if ('A' <= d2 && d2 <= 'F')
        v = 16 * v + d2 - 'A' + 10;
      else {
        sb.append('%');
        continue;
      }

      sb.append((char) v);
      i += 2;
    }

    return sb.toString();
  }

  /**
   * Initialize the JNI.
   */
  public static void initJNI()
  {
    if (_isInitJNI.getAndSet(true)) {
      return;
    }

    // order matters because of static init and license checking
    FilesystemPath jniFilePath = JniFilePath.create();

    if (jniFilePath != null) {
      DEFAULT_SCHEME_MAP.put("file", jniFilePath);

      SchemeMap localMap = _localSchemeMap.get();
      if (localMap != null)
        localMap.put("file", jniFilePath);

      localMap = _localSchemeMap.get(ClassLoader.getSystemClassLoader());
      if (localMap != null)
        localMap.put("file", jniFilePath);

      VfsOld.PWD = jniFilePath;
      VfsOld.setPwd(jniFilePath);
    }
  }
  
  private static class SchemeMapDefault extends SchemeMap
  {
    @Override
    protected PathImpl createLazyScheme(String scheme)
    {
      if (scheme == null) {
        return null;
      }

      switch (scheme) {
      case "memory":
        return new MemoryScheme();
        
      case "jar":
        return new JarScheme(null);
        
      case "classpath":
        return new ClasspathPath(null, "", "");
        
      case "http":
        return new HttpPath("127.0.0.1", 0);
        
      case "https":
        return new HttpsPath("127.0.0.1", 0);
        
      case "tcp":
        return new TcpPath(null, null, null, "127.0.0.1", 0);
        
      case "tcps":
        return new TcpsPath(null, null, null, "127.0.0.1", 0);
        
      case "merge":
        return new MergePath();

        /*
      case "stdout":
        return StdoutStream.create().getPath();

      case "stderr":
        return StderrStream.create().getPath();
        */

      case "null": {
        VfsStreamOld nullStream = new VfsStreamOld(null, null);
        return new ConstPath(null, nullStream);
      }
      
      case "spy":
        return new SpyScheme();
        
      default:
        return null;
      }
    }
  }

  static {
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(VfsOld.class.getClassLoader());

      DEFAULT_SCHEME_MAP = new SchemeMapDefault();

      PathImpl.setDefaultSchemeMap(DEFAULT_SCHEME_MAP);

      FilePath pwd = new FilePath(null);
      PWD = pwd;
      setPwd(pwd);
      ENV_PWD.setGlobal(pwd);
      ENV_PWD.set(pwd);
    
      _localSchemeMap.setGlobal(DEFAULT_SCHEME_MAP);
    
      DEFAULT_SCHEME_MAP.put("file", pwd);
    } finally {
      thread.setContextClassLoader(loader);
    }
  }
}
