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

package com.caucho.v5.bytecode.scan;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.caucho.v5.bytecode.ByteCodeClassMatcher;
import com.caucho.v5.bytecode.ByteCodeClassScanner;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.Vfs;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.util.ModulePrivate;

/**
 * Interface for a scan manager
 */
@ModulePrivate
public class ScanManagerByteCode {
  private static final Logger log
    = Logger.getLogger(ScanManagerByteCode.class.getName());
  
  /*
  private static ConcurrentHashMap<PathImpl,Depend> _nullScanPathMap
    = new ConcurrentHashMap<PathImpl,Depend>();
    */

  private final ScanListenerByteCode []_listeners;

  public ScanManagerByteCode(ScanListenerByteCode ...listeners)
  {
    _listeners = listeners;
  }

  public ScanManagerByteCode(ArrayList<ScanListenerByteCode> listeners)
  {
    _listeners = new ScanListenerByteCode[listeners.size()];
    
    listeners.toArray(_listeners);
  }

  public void scan(EnvironmentClassLoader loader, URL url, String packageRoot)
  {
    // #3576
    scan(loader, Vfs.path(url.toString()), packageRoot);
  }
  
  public void scan(ClassLoader loader, 
                   Path root,
                   String packageRoot)
  {
    /*
    if (root.getPath().endsWith(".jar") && ! (root instanceof JarPath)) {
      root = JarPath.create(root);
    }
    */

    ScanListenerByteCode []listeners = new ScanListenerByteCode[_listeners.length];

    boolean hasListener = false;
    for (int i = 0; i < _listeners.length; i++) {
      if (_listeners[i].isRootScannable(root, packageRoot)) {
        listeners[i] = _listeners[i];
        hasListener = true;
      }
    }

    if (! hasListener) {
      return;
    }

    ByteCodeClassScanner scanner = new ByteCodeClassScanner();

    String packagePath = null;

    if (packageRoot != null) {
      packagePath = packageRoot.replace('.', '/');
    }

/*
    String javahome = System.getProperty("java.home");
    if (root.toString().contains(javahome))
      return;
*/

    if (root.toString().endsWith(".jar")) {
      JarByteCodeMatcher matcher
        = new JarByteCodeMatcher(loader, root, packageRoot, listeners);

      // scanForJarClasses(jar, packageRoot, scanner, matcher);
      scanForJarClasses(root, packagePath, scanner, matcher);

      matcher.completePath(root);
    }
    else {
      PathByteCodeMatcher matcher
        = new PathByteCodeMatcher(loader, root, packageRoot, listeners);
      
      Path scanRoot = root;

      if (packagePath != null) {
        scanRoot = scanRoot.resolve(packagePath);
      }
      
      try {
        Files.walkFileTree(scanRoot, new ScanVisitor(root, scanner, matcher));
      } catch (IOException e) {
        e.printStackTrace();
      }

      //scanForClasses(root, scanRoot, scanner, matcher);

      matcher.completePath(root);
    }
  }
  
  private void scanClass(Path root, 
                         Path path,
                         ByteCodeClassScanner classScanner,
                         PathByteCodeMatcher matcher)
  {
    String fullPath = path.toString();
    String rootPath = root.toString();
    
    String subPath = fullPath.substring(rootPath.length());
    
    if (subPath.startsWith("/")) {
      subPath = subPath.substring(1);
    }
    
    subPath = subPath.substring(0, subPath.length() - ".class".length());
    String className = subPath.replace('/', '.');
    
    if (className.startsWith("java.") || className.startsWith("javax.")) {
      return;
    }
    
    if (! matcher.isClassNameMatch(className)) {
      return;
    }
    
    matcher.init(root, path);

    try (ReadStream is = new ReadStream(Files.newInputStream(path))) {
      classScanner.init(path.toString(), is, matcher);

      classScanner.scan();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /*
  private void scanForClasses(Path root,
                              Path path,
                              ByteCodeClassScanner classScanner,
                              PathByteCodeMatcher matcher)
  {
    try {
      if (Files.isDirectory(path)) {
        Files.list(path).forEach(child->{
          if (child.getFileName().toString().indexOf(':') >= 0) {
            return;
          }

          scanForClasses(root, child, classScanner, matcher);
        });

        return;
      }

      if (! path.endsWith(".class")) {
        return;
      }
      
      
      String fullPath = path.toString();
      String rootPath = root.toString();
      
      String subPath = fullPath.substring(rootPath.length());
      
      if (subPath.startsWith("/")) {
        subPath = subPath.substring(1);
      }
      
      subPath = subPath.substring(0, subPath.length() - ".class".length());
      String className = subPath.replace('/', '.');
      
      if (className.startsWith("java.") || className.startsWith("javax.")) {
        return;
      }
      
      if (! matcher.isClassNameMatch(className)) {
        return;
      }
      
      matcher.init(root, path);

      try (ReadBuffer is = new ReadBuffer(Files.newInputStream(path))) {
        classScanner.init(path.toString(), is, matcher);

        classScanner.scan();
      }
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  */

  private void scanForJarClasses(Path path,
                                 String packagePath,
                                 ByteCodeClassScanner classScanner,
                                 JarByteCodeMatcher matcher)
  {
    /*
    if (isNullScanPath(path)) {
      return;
    }
    */
    
    //ZipFile zipFile = null;
    //Jar jar = JarPath.create(path).getJar();

    try (InputStream fIs = Files.newInputStream(path)) {
      try (ZipInputStream zIs = new ZipInputStream(fIs)) { 
        ZipEntry entry;

        while ((entry = zIs.getNextEntry()) != null) {
          String entryName = entry.getName();
        
          if (! entryName.endsWith(".class")) {
            continue;
          }

          if (packagePath != null && ! entryName.startsWith(packagePath)) {
            continue;
          }
          
          String subPath = entryName;
          
          subPath = subPath.substring(0, subPath.length() - ".class".length());
          String className = subPath.replace('/', '.');
          
          if (className.startsWith("java.") || className.startsWith("javax.")) {
            continue;
          }
          
          if (! matcher.isClassNameMatch(className)) {
            continue;
          }
          
          matcher.init(); // path, path);
          
          try (InputStream isEntry = new InputStreamEntry(zIs)) {
            try (ReadStream is = new ReadStream(isEntry)) {
              classScanner.init(entryName, is, matcher);

              classScanner.scan();
            }
          }
        }
      }
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
      System.out.println("IOE: " + e);
      e.printStackTrace();
    }
  }
  
  /**
   * Adds a jar where none of the classes have a scanned match.
   */
  
  /*
  private void addNullScanPath(Path path)
  {
    _nullScanPathMap.put(path, new Depend(path));
  }
  */
  
  /**
   * Returns true if the jar is known to have no scanned classes. 
   */
  /*
  private boolean isNullScanPath(PathImpl path)
  {
    Depend depend = _nullScanPathMap.get(path);
    
    return depend != null && ! depend.isModified();
  }
  */

  static class JarByteCodeMatcher extends ScanByteCodeMatcher
  {
    JarByteCodeMatcher(ClassLoader loader,
                       Path root,
                       String packageName,
                       ScanListenerByteCode []listeners)
    {
      super(loader, root, packageName, listeners);
    }
  }
  
  static class InputStreamEntry extends InputStream
  {
    private ZipInputStream _zIs;
    
    InputStreamEntry(ZipInputStream zIs)
    {
      _zIs = zIs;
    }
    
    @Override
    public int read()
      throws IOException
    {
      return _zIs.read();
    }
    
    @Override
    public int read(byte []buffer, int offset, int length)
      throws IOException
    {
      return _zIs.read(buffer, offset, length);
    }
    
    @Override
    public long skip(long n)
      throws IOException
    {
      return _zIs.skip(n);
    }
    
    @Override
    public void close()
      throws IOException
    {
      ZipInputStream zIs = _zIs;
      _zIs = null;
      
      if (zIs != null) {
        zIs.closeEntry();
      }
    }
  }

  static class PathByteCodeMatcher extends ScanByteCodeMatcher {
    private Path _root;
    private Path _path;

    PathByteCodeMatcher(ClassLoader loader,
                        Path root,
                        String packageName,
                        ScanListenerByteCode []listeners)
    {
      super(loader, root, packageName, listeners);
    }

    void init(Path root, Path path)
    {
      super.init();

      _root = root;
      _path = path;
    }

    String getClassName()
    {
      String rootName = _root.toString();
      String name = _path.toString();
      
      int p = name.lastIndexOf('.');

      String className = name.substring(rootName.length(), p);

      return className.replace('/', '.');
    }
  }
    
  abstract static class ScanByteCodeMatcher implements ByteCodeClassMatcher {
    private Path _root;
    private String _packageRoot;
    
    private final ScanListenerByteCode []_listeners;
    private final ScanListenerByteCode []_currentListeners;
    private final ScanClass []_currentClasses;

    ScanByteCodeMatcher(ClassLoader loader,
                        Path root,
                        String packageRoot,
                        ScanListenerByteCode []listeners)
    {
      _root = root;
      _packageRoot = packageRoot;
      
      _listeners = listeners;
      _currentListeners = new ScanListenerByteCode[listeners.length];
      _currentClasses = new ScanClass[listeners.length];
    }

    void init()
    {
      for (int i = 0; i < _listeners.length; i++) {
        _currentListeners[i] = _listeners[i];
        _currentClasses[i] = null;
      }
    }
    
    /**
     * Returns true if the annotation class is a match.
     */
    @Override
    public boolean scanClass(String className, int modifiers)
    {
      int activeCount = 0;

      for (int i = _listeners.length - 1; i >= 0; i--) {
        ScanListenerByteCode listener = _currentListeners[i];

        if (listener == null)
          continue;

        ScanClass scanClass = listener.scanClass(_root, _packageRoot, 
                                                 className, modifiers);

        if (scanClass != null) {
          activeCount++;
          _currentClasses[i] = scanClass;
        }
        else {
          _currentListeners[i] = null;
        }
      }

      return activeCount > 0;
    }
    
    /**
     * Returns true if the annotation class is a match.
     */
    //@Override
    public boolean isClassNameMatch(String className)
    {
      int activeCount = 0;

      for (int i = _listeners.length - 1; i >= 0; i--) {
        //ScanListenerByteCode listener = _currentListeners[i];
        ScanListenerByteCode listener = _listeners[i];

        if (listener == null)
          continue;

        if (listener.isScanClassName(className)) {
          return true;
        }
      }

      return false;
    }

    @Override
    public void addInterface(char[] buffer, int offset, int length)
    {
      for (ScanClass scanClass : _currentClasses) {
        if (scanClass != null) {
          scanClass.addInterface(buffer, offset, length);
        }
      }
    }

    @Override
    public void addSuperClass(char[] buffer, int offset, int length)
    {
      for (ScanClass scanClass : _currentClasses) {
        if (scanClass != null) {
          scanClass.addSuperClass(buffer, offset, length);
        }
      }
    }

    @Override
    public void addClassAnnotation(char[] buffer, int offset, int length)
    {
      for (ScanClass scanClass : _currentClasses) {
        if (scanClass != null) {
          scanClass.addClassAnnotation(buffer, offset, length);
        }
      }
    }

    @Override
    public void addPoolString(char[] buffer, int offset, int length)
    {
      for (ScanClass scanClass : _currentClasses) {
        if (scanClass != null) {
          scanClass.addPoolString(buffer, offset, length);
        }
      }
    }

    @Override
    public boolean finishScan()
    {
      boolean isScanValue = false;

      for (ScanClass scanClass : _currentClasses) {
        if (scanClass != null) {
          if (scanClass.finishScan()) {
            isScanValue = true;
          }
        }
      }
      
      return isScanValue;
    }

    public void completePath(Path path)
    {
      for (ScanListenerByteCode listener : _listeners) {
        if (listener != null) {
          listener.completePath(path);
        }
      }
    }
 
    /**
     * Returns true if the annotation class is a match.
     */
    public boolean isAnnotationMatch(StringBuilder annotationClassName)
    {
      int activeCount = 0;

      for (int i = _listeners.length - 1; i >= 0; i--) {
        ScanListenerByteCode listener = _currentListeners[i];

        if (listener == null)
          continue;

        if (listener.isScanMatchAnnotation(annotationClassName)) {
          _currentListeners[i] = null;
        }
        else
          activeCount++;
      }

      return activeCount == 0;
    }
  }
  
  private class ScanVisitor extends SimpleFileVisitor<Path>
  {
    private Path _root;
    private ByteCodeClassScanner _classScanner;
    private PathByteCodeMatcher _matcher;
    
    public ScanVisitor(Path root, 
                       ByteCodeClassScanner scanner,
                       PathByteCodeMatcher matcher)
    {
      _root = root;
      _classScanner = scanner;
      _matcher = matcher;
    }
    
    /*
    @Override
    public FileVisitResult preVisitDirectory(Path dir,
                                             BasicFileAttributes attrs)
                                                 throws IOException
    {
      return FileVisitResult.CONTINUE;
    }
    */
    
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
        throws IOException
    {
      if (file.toString().endsWith(".class")) {
        scanClass(_root, file, _classScanner, _matcher);
      }
      
      return FileVisitResult.CONTINUE;
    }
    /*
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
    */
  }
}
