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
 * @author Scott Ferguson
 */

package com.caucho.v5.boot;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;

/**
 * boot class for one-jar.
 */
public class BootFile
{
  private static final Logger log = Logger.getLogger(BootFile.class.getName());
  private Path _bootPath;
  private int _bootSize;
  private FileChannel _bootChannel;
  private MappedByteBuffer _bootMap;
  private HashMap<String,JarItem> _jarMap = new HashMap<>();
  private Manifest _manifest;
  private JarItem _manifestJarItem;
  
  private AtomicReference<Inflater> _inflaterRef = new AtomicReference<>();
  
  BootFile(Path bootPath)
    throws IOException
  {
    Objects.requireNonNull(bootPath);
    
    _bootPath = bootPath;
    long bootSize = Files.size(bootPath);
    
    if (bootSize <= 0) {
      throw new IllegalStateException("Unexpected boot size for " + bootPath);
    }
    
    if (bootSize >= Integer.MAX_VALUE - 1) {
      throw new IllegalStateException("Mmapped file is too large for " + bootPath + " " + _bootSize);
    }
    
    _bootSize = (int) bootSize;
    
    _bootChannel = (FileChannel) Files.newByteChannel(_bootPath, StandardOpenOption.READ);
    
    _bootMap = _bootChannel.map(MapMode.READ_ONLY, 0, _bootSize);
    
    readJar();
    
    readManifest();
  }

  public Manifest manifest()
  {
    return _manifest;
  }
  
  URL []urls()
  {
    URL []urls = new URL[_jarMap.size()];
    
    int i = 0;
    
    try {
      for (JarItem jarItem : _jarMap.values()) {
        URL url = jarItem.url();
      
        urls[i++] = url;
      } 
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    return urls;
  }
  
  public BootJar jar(String path)
  {
    return _jarMap.get(path);
  }

  public URLConnection openConnection(URL url)
  {
    String path = url.getPath();
    
    JarItem jarItem = _jarMap.get(path);

    if (jarItem != null) {
      return jarItem.openConnection(url);
    }
    else {
      return null;
    }
  }
  
  private void readJar()
    throws IOException
  {
    Supplier<InputStream> factory = ()->new BootInputStream("top", _bootMap, 0, _bootSize);
    
    try (BootZipScanner scanner = new BootZipScanner("top", factory, _bootSize)) {
      if (! scanner.open()) {
        throw new IllegalStateException();
      }
      
      while (scanner.next()) {
        String name = scanner.name();
        int position = scanner.offset();
        int size = scanner.sizeCompressed();
        
        if (name.endsWith(".jar")) {
          if (scanner.method() != ZipEntry.STORED
                || size <= 0
                || size >= Integer.MAX_VALUE
                || size != scanner.size()) {
            throw new IllegalStateException("Entry isn't stored: " + name);
          }
            
          JarItem jarItem = new JarItem(name, position, 
                                        scanner.sizeCompressed(),
                                        scanner.size(),
                                        scanner.method() == ZipEntry.DEFLATED);
          
          _jarMap.put(name, jarItem);
        }
        
        if (name.equalsIgnoreCase("meta-inf/manifest.mf")) {
          _manifestJarItem = new JarItem(name, position,
                                         scanner.sizeCompressed(),
                                         scanner.size(),
                                         scanner.method() == ZipEntry.DEFLATED);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();;
      log.log(Level.FINER, _bootPath + " " + e.toString(), e);
    }
  }
  
  private void readManifest()
    throws IOException
  {
    _manifest = new Manifest();
    
    try (InputStream is = _manifestJarItem.read()) {
      _manifest.read(is);
    }
  }
  
  private Inflater inflaterAlloc()
  {
    Inflater inflater = _inflaterRef.getAndSet(null);
    
    if (inflater == null) {
      inflater = new Inflater(true);
    }
    
    inflater.reset();
    
    return inflater;
  }
  
  private void inflaterFree(Inflater inflater)
  {
    _inflaterRef.compareAndSet(null, inflater);
  }
  
  class BootInputStream extends InputStream
  {
    private String _name;
    private ByteBuffer _buffer;
    private Inflater _inflater;
    
    BootInputStream(String name, 
                    ByteBuffer source, int offset, int size)
    {
      _name = name;
      
      _buffer = source.duplicate();
      _buffer.position(offset);
      _buffer.limit(offset + size);
    }
    
    BootInputStream(String name,
                    ByteBuffer source, int offset, int size,
                    Inflater inflater)
    {
      this(name, source, offset, size);
      
      _inflater = inflater;
    }
    
    public int position()
    {
      return _buffer.position();
    }
    
    @Override
    public int read()
    {
      if (_buffer.hasRemaining()) {
        return _buffer.get() & 0xff;
      }
      else {
        return -1;
      }
    }
    
    @Override
    public int read(byte []buffer, int offset, int length)
    {
      int remaining = _buffer.remaining();
      
      int sublen = Math.min(remaining, length);
      
      if (sublen <= 0) {
        return -1;
      }
      
      _buffer.get(buffer, offset, sublen);
      
      return sublen;
    }
    
    @Override
    public long skip(long n)
    {
      long tail = Math.min(_buffer.limit(), _buffer.position() + n);
      
      _buffer.position((int) tail);
      
      return n;
    }
    
    @Override
    public void close()
    {
      Inflater inflater = _inflater;
      _inflater = null;
      
      if (inflater != null) {
        inflaterFree(inflater);
      }
    }
  }
  
  public interface BootJar
  {
    String name();
    int size();
    
    InputStream read() throws IOException;
  }
  
  private class JarItemBase implements BootJar
  {
    private String _name;
    private int _offset;
    private int _sizeCompressed;
    private int _size;
    private int _dataOffset;
    private boolean _isCompressed;
    
    JarItemBase(String name, int offset, int sizeCompressed, int size,
                boolean isCompressed)
    {
      _name = name;
      _offset = offset;
      _sizeCompressed = sizeCompressed;
      _size = size;
      _isCompressed = isCompressed;
    }
    
    public String name()
    {
      return _name;
    }
    
    public int size()
    {
      return _size;
    }
    
    public int sizeCompressed()
    {
      return _sizeCompressed;
    }

    @Override
    public InputStream read()
      throws IOException
    {
      Inflater inflater = null;
      
      if (_isCompressed) {
        inflater = inflaterAlloc();
      }
      
      InputStream is = new BootInputStream(name(), _bootMap, dataOffset(), _sizeCompressed, inflater);
      
      if (! _isCompressed) {
        return is;
      }
      
      return new InflaterInputStream(is, inflater);
    }

    public URLConnection openConnection(URL url)
    {
      return new JarURLConnectionBase(url, this);
    }
    
    protected int dataOffset()
      throws IOException
    {
      if (_dataOffset <= 0) {
        try (BootInputStream bis = new BootInputStream(name(), _bootMap, _offset, _sizeCompressed)) {
          BootZipScanner.skipLocalHeader(bis);
          
          _dataOffset = bis.position();
        }
      }
      
      return _dataOffset;
    }
  }
  
  private class JarItem extends JarItemBase
  {
    private HashMap<String,JarItemEntry> _entryMap = new HashMap<>();
    
    JarItem(String name, int offset, int sizeCompressed, int size,
            boolean isCompressed)
      throws IOException
    {
      super(name, offset, sizeCompressed, size, isCompressed);

      if (name.endsWith(".jar")) {
        try {
          fillJarMap();
        } catch (Exception e) {
          log.log(Level.FINER, name + ": " + e.toString(), e);
        }
      }
    }

    private void fillJarMap()
      throws IOException
    {
      int dataPos = dataOffset();
      
      Supplier<InputStream> factory
        = ()->new BootInputStream(name(), _bootMap, dataPos, sizeCompressed());

      try (BootZipScanner scanner = new BootZipScanner(name(), factory, sizeCompressed())) {
        scanner.open();
        
        while (scanner.next()) {
          String name = scanner.name();
          int pos = scanner.offset();
            
          JarItemEntry jarEntry = new JarItemEntry(name,
                                                   pos,
                                                   scanner.sizeCompressed(),
                                                   scanner.size(),
                                                   scanner.method() == ZipEntry.DEFLATED);
          _entryMap.put(name, jarEntry);
        }
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }
    
    JarItemEntry get(String name)
    {
      return _entryMap.get(name);
    }
    
    URL url()
    {
      try {
        return new URL("boot", "", -1, name());
        //return new URL("boot", "", -1, name(), new BootStreamHandler(this));
        //return new URL("jar", "", -1, "boot:" + name() + "!/");
        //return new URL("jar", "", -1, "boot:" + name() + "!/", new BootStreamHandler(this));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + name() + "]";
    }
  }
  
  private class JarItemEntry extends JarItemBase
  {
    JarItemEntry(String name, 
                 int position,
                 int size,
                 int sizeCompressed, 
                 boolean isCompressed)
    {
      super(name, position, size, sizeCompressed, isCompressed);
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + name() + "]";
    }
  }
  
  /*
  private class BootStreamHandler extends URLStreamHandler
  {
    private JarItem _jar;
    
    BootStreamHandler(JarItem jar)
    {
      _jar = jar;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException
    {
      String urlPath = url.getPath();
      int p = urlPath.indexOf("!/");
      
      if (p < 0) {
        return _jar.openConnection(url);
      }
      
      String path = urlPath.substring(p + 2);
      
      JarItemEntry entry = _jar.get(path);
      
      System.out.println("PATH: " + path + " " + entry);
      
      if (entry != null) {
        return entry.openConnection(url);
      }
      else {
        return null;
      }
    }
  }
  */
  
  private class JarURLConnectionBase extends URLConnection
  {
    private JarItemBase _jarItem;
    
    JarURLConnectionBase(URL url, JarItemBase jarItem)
    {
      super(url);
      
      Objects.requireNonNull(this);
      
      _jarItem = jarItem;
    }

    @Override
    public void connect() throws IOException
    {
      System.out.println("CONN: " + this);
      throw new UnsupportedOperationException(getClass().getName());
    }
    
    @Override
    public InputStream getInputStream() throws IOException
    {
      InputStream is = _jarItem.read();

      return is;
    }
    
    @Override
    public int getContentLength()
    {
      return _jarItem.size();
    }
    
    @Override
    public long getContentLengthLong()
    {
      return _jarItem.size();
    }
    
    @Override
    public long getExpiration()
    {
      return super.getExpiration();
    }
    
    @Override
    public long getLastModified()
    {
      return super.getLastModified();
    }
    
    @Override
    public boolean getUseCaches()
    {
      return true;
    }
    
    @Override
    public String toString()
    {
      try {
      return getClass().getSimpleName() + "[" + _jarItem + "]";
      } catch (Throwable e) {
        e.printStackTrace();;
        throw e;
      }
    }
  }
}
