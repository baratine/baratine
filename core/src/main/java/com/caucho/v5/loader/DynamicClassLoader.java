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

package com.caucho.v5.loader;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.io.AlwaysModified;
import com.caucho.v5.io.Dependency;
import com.caucho.v5.io.Path;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.ByteArrayBuffer;
import com.caucho.v5.util.ConcurrentArrayList;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.TimedCache;

/**
 * Class loader which checks for changes in class files and automatically
 * picks up new jars.
 *
 * <p>DynamicClassLoaders can be chained creating one virtual class loader.
 * From the perspective of the JDK, it's all one classloader.  Internally,
 * the class loader chain searches like a classpath.
 */
public class DynamicClassLoader extends java.net.URLClassLoader
  implements Dependency // , Make
{
  private static L10N _L;
  private static Logger _log;

  private final static URL NULL_URL;
  private final static URL []NULL_URL_ARRAY = new URL[0];

  private static long _globalDependencyCheckInterval = 2000L;
  private static boolean _isJarCacheEnabled = true;
  private static ArrayList<String> _classPathList;
  private static String _classPath;

  private String _id;

  private final boolean _isVerbose;
  private int _verboseDepth;

  // List of resource loaders
  private ArrayList<Loader> _loaders = new ArrayList<>();
  
  // List of import loaders
  private ConcurrentArrayList<DynamicClassLoader> _importLoaders
    = new ConcurrentArrayList<>(DynamicClassLoader.class);

  /*
  private JarLoader _jarLoader;
  private PathLoader _pathLoader;
  */

  private boolean _isDirectoryLoader;

  private ArrayList<Path> _nativePath = new ArrayList<Path>();

  // List of cached classes
  private ConcurrentHashMap<String,ClassEntry> _entryCache
    = new ConcurrentHashMap<>(8);

  private TimedCache<String,URL> _resourceCache;
  
  private final Object _packageLock = new Object();

  private boolean _isDisableURLs;

  // Dependencies
  private DependencyContainer _dependencies = new DependencyContainer(this);
  private boolean _isEnableDependencyCheck = false;

  // Makes
  private ArrayList<Loader> _makeList = new ArrayList<>();

  // List of packages where the parent has priority.
  private String []_parentPriorityPackages;

  // List of packages where this has priority.
  private String []_priorityPackages;

  // Array of listeners
  // XXX: There's still some questions of what kind of reference this
  // should be.  It either needs to be a WeakReference or
  // a normal reference.  Anything in between makes no sense.
  //
  // server/10w3 indicates that it needs to be a regular reference
  private ArrayList<EnvLoaderListener> _listeners
    = new ArrayList<>();

  // The security manager for the loader
  // private SecurityManager _securityManager;

  // List of permissions allowed in this context
  private ArrayList<Permission> _permissions;

  // The security code source for the loader
  private CodeSource _codeSource;

  // Any enhancer
  //private ArrayList<ClassFileTransformer> _classFileTransformerList;

  private URL []_urls = NULL_URL_ARRAY;

  private WeakStopListener _closeListener;

  // Lifecycle
  private final Lifecycle _lifecycle = new Lifecycle();
  
  // marker for a closed classloader to help heap dumps
  @SuppressWarnings("unused")
  private ZombieClassLoaderMarker _zombieMarker;

  private boolean _hasNewLoader = true;
  private final AtomicBoolean _isScanning = new AtomicBoolean();

  /**
   * Create a new class loader.
   *
   * @param parent parent class loader
   */
  public DynamicClassLoader(ClassLoader parent)
  {
    this(parent, true, false);
  }

  /**
   * Create a new class loader.
   *
   * @param parent parent class loader
   */
  public DynamicClassLoader(ClassLoader parent,
                            boolean enableDependencyCheck,
                            boolean isRoot)
  {
    super(NULL_URL_ARRAY, getInitParent(parent, isRoot));

    parent = getParent();

    // _securityManager = System.getSecurityManager();

    _isEnableDependencyCheck = enableDependencyCheck;

    _dependencies.setCheckInterval(_globalDependencyCheckInterval);
    if (! CurrentTime.isTest()) {
      _dependencies.setAsync(true);
    }

    for (; parent != null; parent = parent.getParent()) {
      if (parent instanceof DynamicClassLoader) {
        DynamicClassLoader loader = (DynamicClassLoader) parent;

        loader.init();

        addPermissions(loader.getPermissions());

        // loader.addNotificationListener(this);

        _dependencies.add(loader);
        _dependencies.setCheckInterval(loader.getDependencyCheckInterval());

        break;
      }
    }

    if (System.getProperty("baratine.verbose.classpath") != null) {
      _isVerbose = true;

      int depth = 0;

      while (parent != null) {
        depth++;
        parent = parent.getParent();
      }

      _verboseDepth = depth;
    }
    else
      _isVerbose = false;
  }

  /**
   * Returns the initialization parent, i.e. the parent if given
   * or the context class loader if not given.
   */
  private static ClassLoader getInitParent(ClassLoader parent,
                                           boolean isRoot)
  {
    if (parent == null)
      parent = Thread.currentThread().getContextClassLoader();

    if (isRoot || parent instanceof DynamicClassLoader)
      return parent;
    else {
      //return RootDynamicClassLoader.create(parent);
      return parent;
    }
  }

  /**
   * Returns true if jar entries should be cached.
   */
  public static boolean isJarCacheEnabledDefault()
  {
    return _isJarCacheEnabled;
  }

  public boolean isRoot()
  {
    return false;
  }

  /**
   * Returns true if jar entries should be cached.
   */
  public boolean isJarCacheEnabled()
  {
    return isJarCacheEnabledDefault();
  }

  /**
   * Returns true if jar entries should be cached.
   */
  public static void setJarCacheEnabled(boolean isEnabled)
  {
    _isJarCacheEnabled = isEnabled;
  }

  private void verbose(String name, String msg)
  {
    if (_isVerbose) {
      for (int i = _verboseDepth; i > 0; i--)
        System.err.print(' ');

      System.err.println(toString() + " " + name + " " + msg);
    }
  }

  /**
   * Returns the global dependency check interval.
   */
  public static long getGlobalDependencyCheckInterval()
  {
    return _globalDependencyCheckInterval;
  }

  /**
   * Sets the global dependency check interval.
   */
  public static void setGlobalDependencyCheckInterval(long interval)
  {
    if (interval < 0)
      interval = Integer.MAX_VALUE;

    _globalDependencyCheckInterval = interval;
  }

  /**
   * Sets the name.
   */
  public void setId(String id)
  {
    _id = id;
  }

  /**
   * Gets the name.
   */
  public String getId()
  {
    return _id;
  }

  /**
   * Returns true if the class loader is closed.
   */
  public boolean isDestroyed()
  {
    return _lifecycle.isDestroyed();
  }
  
  protected boolean isStopped()
  {
    return _lifecycle.isStopped();
  }

  /**
   * Returns true for a class-loader that contains a WEB-INF/classes
   * style directory.
   */
  public boolean isDirectoryLoader()
  {
    if (_isDirectoryLoader)
      return true;

    ClassLoader parent = getParent();

    if (parent instanceof DynamicClassLoader)
      return ((DynamicClassLoader) parent).isDirectoryLoader();
    else
      return false;
  }

  /**
   * Adds a resource loader to the end of the list.
   */
  public void addLoader(Loader loader)
  {
    if (_loaders == null) {
      throw new IllegalStateException(toString());
    }
    
    int p = _loaders.indexOf(loader);

    // overriding loaders are inserted before the loader they override
    // server/10ih
    if (p >= 0) {
      Loader oldLoader = _loaders.get(p);

      if (oldLoader != loader) {
        addLoader(loader, p);
      }
    }
    else {
      addLoader(loader, _loaders.size());
    }

    _hasNewLoader = true;
  }

  /**
   * Removes a loader (this should only be used by generating code, for example
   * EJB to deal with package-private).
   */
  public void removeLoader(Loader loader)
  {
    synchronized (_loaders) {
      for (int i = _loaders.size() - 1; i >= 0; i--) {
        if (_loaders.get(i) == loader) {
          _loaders.remove(i);
        }
      }
    }
  }

  /**
   * Adds a resource loader.
   */
  public void addLoader(Loader loader, int offset)
  {
    if (_loaders.indexOf(loader) >= 0) {
      return;
    }
    
    if (_lifecycle.isDestroyed()) {
      throw new IllegalStateException(L().l("can't add loaders after initialization"));
    }

    if (log().isLoggable(Level.FINEST)) {
      log().finest(this + " adding loader " + loader);
    }

    _loaders.add(offset, loader);

    if (loader.getClassLoader() == null) {
      loader.setLoader(this);
    }
    else {
      assert(loader.getClassLoader() == this);
    }

    if (loader instanceof Dependency) {
      _dependencies.add((Dependency) loader);
    }

    _makeList.add(loader);

    /*
    if (loader instanceof ClassLoaderListener) {
      addListener(new WeakLoaderListener((ClassLoaderListener) loader));
    }
    */

    if (loader.isDirectoryLoader()) {
      _isDirectoryLoader = true;
    }
    
    loader.init();

    _hasNewLoader = true;
  }

  public ArrayList<Loader> getLoaders()
  {
    return _loaders;
  }
  
  public void addImportLoader(DynamicClassLoader loader)
  {
    Objects.requireNonNull(loader);
    
    synchronized (this) {
      _importLoaders.add(loader);
      
      _dependencies.add(loader);
    }
  }

  /**
   * Adds jars based on a manifest classpath.
   */
  /*
  public void addJarManifestClassPath(Path path)
    throws IOException
  {
    Path contextPath;
    Path manifestPath;

    if (path.isDirectory()) {
      manifestPath = path.lookup("META-INF/MANIFEST.MF");
      contextPath = path;
    }
    else {
      JarPath jar = JarPath.create(path);
      manifestPath = jar.lookup("META-INF/MANIFEST.MF");
      contextPath = path.getParent();
    }

    if (manifestPath.canRead()) {
      ReadStream is = manifestPath.openRead();
      try {
        Manifest manifest = new Manifest(is);

        Attributes main = manifest.getMainAttributes();

        String classPath = main.getValue("Class-Path");

        addManifestClassPath(classPath, contextPath);
      } catch (IOException e) {
        log().log(Level.WARNING, e.toString(), e);

        return;
      } finally {
        is.close();
      }
    }
  }
  */

  /**
   * Adds jars based on a manifest classpath.
   */
  /*
  public void addManifestClassPath(String classPath, Path pwd)
  {
    if (classPath == null)
      return;

    String []urls = Pattern.compile("[\\s,]+").split(classPath);
    if (urls == null || urls.length == 0)
      return;

    for (int i = 0; i < urls.length; i++) {
      String url = urls[i];

      if (url.equals(""))
        continue;

      Path jar = pwd.lookup(url);

      if (jar.canRead())
        addJar(jar);
    }

    // ejb/0i20, #3601
    _hasNewLoader = true;
  }
  */

  /**
   * Adds a native path.
   */
  public void addNative(Path path)
  {
    _nativePath.add(path);
  }

  /**
   * Adds a jar loader.
   */
  public void addJar(Path jar)
  {
    // XXX: addRoot(jar);
  }

  /**
   * Adds a root loader.
   */
  /*
  public void addRoot(Path root)
  {
    if (_lifecycle.isDestroyed())
      throw new IllegalStateException(L().l("can't add roots after closing"));

    URL url = pathToURL(root);

    if (containsURL(url)) {
      log().finer(this + " skipping duplicate URL " + url);
      return;
    }

    if (root instanceof JarPath
        || root.getPath().endsWith(".jar")
        || root.getPath().endsWith(".zip")) {
      if (_jarLoader == null) {
        _jarLoader = new JarLoader(this);
        _jarLoader.init();
      }

      _jarLoader.addJar(root);
    }
    else {
      SimpleLoader loader = new SimpleLoader(this);
      loader.setPath(root);
      loader.init();

      if (! _loaders.contains(loader))
        addLoader(loader);
    }

    addURL(root);
  }
  */

  /**
   * Adds a jar loader.
   */
  /*
  public void addPathClass(String className, Path path)
  {
    if (_pathLoader == null) {
      _pathLoader = new PathLoader();
      // ejb/0i00
      _loaders.add(0, _pathLoader);
    }

    _pathLoader.put(className, path);
  }
  */

  public boolean addURL(Path path)
  {
    return addURL(path, true);
  }

  /**
   * Adds the URL to the URLClassLoader.
   */
  public boolean addURL(Path path, boolean isScanned)
  {
    URL url = pathToURL(path);

    if (url == null)
      return false;
    else if (containsURL(url))
      return false;
    else {
      addURL(url, isScanned);

      return true;
    }
  }

  public void setDisableURLs(boolean isDisable)
  {
    _isDisableURLs = isDisable;
  }

  public boolean isDisableURLs()
  {
    return _isDisableURLs;
  }

  /**
   * Adds the URL to the URLClassLoader.
   */
  public boolean containsURL(Path path)
  {
    URL url = pathToURL(path);

    if (url == null)
      return false;
    else
      return containsURL(url);
  }

  private URL pathToURL(Path path)
  {
    try {
      if (path.getScheme().equals("memory"))
        return null;

      if (path.getScheme().equals("jar")) {
      }
      /* XXX:
      else if (path.getFullPath().endsWith(".jar")) {
        path = JarPath.create(path);
      }
      */
      else if (! path.getURL().endsWith("/")) {
        path = path.lookup("./");
      }

      return path.getNetURL();
      /*
    } catch (MalformedURLException e) {
      log().warning(e.toString());
      */
    } catch (Exception e) {
      log().log(Level.WARNING, e.toString(), e);
    }

    return null;
  }

  @Override
  public void addURL(URL url)
  {
    addURL(url, true);
  }

  /**
   * Adds the URL to the URLClassLoader.
   */
  public void addURL(URL url, boolean isScanned)
  {
    addURL(_urls.length, url);
  }

  /**
   * Adds the URL to the URLClassLoader.
   */
  public void addURL(int index, URL url)
  {
    if (containsURL(url))
      return;

    super.addURL(url);

    URL []newURLs = new URL[_urls.length + 1];

    for (int i = 0; i < index; i++) {
      newURLs[i] = _urls[i];
    }

    newURLs[index] = url;

    for (int i = index + 1; i < newURLs.length; i++) {
      newURLs[i] = _urls[i - 1];
    }

    _urls = newURLs;
  }

  /**
   * Adds a class loader for instrumentation (jdk 1.6).
   */
  public void appendToClassPathForInstrumentation(String path)
  {
    //addRoot(com.caucho.v5.vfs.Vfs.lookup(path));
  }

  /**
   * Returns the URLs.
   */
  @Override
  public URL []getURLs()
  {
    if (isDisableURLs()) {
      return new URL[0];
    }
    else {
      return _urls;
    }
  }

  /**
   * Returns true if the loader contains the url.
   */
  protected boolean containsURL(URL url)
  {
    if (_urls != null) {
      for (URL testURL : _urls) {
        if (url.equals(testURL))
          return true;
      }
    }

    ClassLoader parent = getParent();

    if (parent instanceof DynamicClassLoader) {
      DynamicClassLoader dynParent = (DynamicClassLoader) parent;

      if (dynParent.containsURL(url)) {
        return true;
      }
    }
    
    for (DynamicClassLoader loaderImport : _importLoaders) {
      if (loaderImport.containsURL(url)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Sets the dependency check interval.
   */
  public void setDependencyCheckInterval(long interval)
  {
    _dependencies.setCheckInterval(interval);
  }

  /**
   * Gets the dependency check interval.
   */
  public long getDependencyCheckInterval()
  {
    if (_dependencies != null)
      return _dependencies.getCheckInterval();
    else
      return 0;
  }

  /**
   * Enables the dependency checking.
   */
  public void setEnableDependencyCheck(boolean enable)
  {
    _isEnableDependencyCheck = enable;
  }

  /**
   * Adds a dependency.
   */
  public void addDependency(Dependency dependency)
  {
    if (_dependencies != null)
      _dependencies.add(dependency);
  }

  public void addPermission(String path, String actions)
  {
    addPermission(new FilePermission(path, actions));
  }

  /**
   * Adds a permission to the loader.
   */
  public void addPermission(Permission permission)
  {
    if (_permissions == null)
      _permissions = new ArrayList<Permission>();

    _permissions.add(permission);
  }

  public ArrayList<Permission> getPermissions()
  {
    return _permissions;
  }

  public void addPermissions(ArrayList<Permission> perms)
  {
    if (perms == null)
      return;

    if (_permissions == null)
      _permissions = new ArrayList<Permission>();

    _permissions.addAll(perms);
  }

  /**
   * Returns the security manager.
   */
  /*
  public SecurityManager getSecurityManager()
  {
    return _securityManager;
  }
  */

  /**
   * Set true if the loader should use the servlet spec's hack.
   */
  /*
  public void setServletHack(boolean servletHack)
  {
    _useServletHack = servletHack;

    if (_parentPriorityPackages == null)
      _parentPriorityPackages = new String[0];
  }
  */

  /**
   * Adds a listener to detect class loader changes.
   */
  public void addListener(EnvLoaderListener listener)
  {
    ArrayList<EnvLoaderListener> listeners = _listeners;

    if (_lifecycle.isDestroyed() || listeners == null) {
      IllegalStateException e = new IllegalStateException(L().l("attempted to add listener to a closed classloader {0}", this));

      log().log(Level.WARNING, e.toString(), e);

      return;
    }

    /*
    WeakCloseListener closeListener = null;

    if (listeners != null && listeners.size() == 0) {
      closeListener = new WeakCloseListener(this);
        //_closeListener = closeListener;
    }

    if (closeListener != null) {
      for (ClassLoader parent = getParent();
           parent != null;
           parent = parent.getParent()) {
        if (parent instanceof DynamicClassLoader) {
          ((DynamicClassLoader) parent).addListener(closeListener);
          break;
        }
      }
    }
    */

    synchronized (listeners) {
      for (int i = listeners.size() - 1; i >= 0; i--) {
        EnvLoaderListener oldListener = listeners.get(i);

        if (listener == oldListener) {
          return;
        }
        else if (oldListener == null)
          listeners.remove(i);
      }

      listeners.add(listener);
    }

    if (_lifecycle.isActive())
      listener.classLoaderInit(this);
  }

  /**
   * Adds a listener to detect class loader changes.
   */
  public void removeListener(EnvLoaderListener listener)
  {
    ArrayList<EnvLoaderListener> listeners = _listeners;

    if (listeners == null)
      return;

    synchronized (listeners) {
      for (int i = listeners.size() - 1; i >= 0; i--) {
        EnvLoaderListener oldListener = listeners.get(i);

        if (listener == oldListener) {
          listeners.remove(i);
          return;
        }
        else if (oldListener == null)
          listeners.remove(i);
      }
    }
  }

  /**
   * Returns the listeners.
   */
  protected ArrayList<EnvLoaderListener> getListeners()
  {
    ArrayList<EnvLoaderListener> listeners;
    listeners = new ArrayList<EnvLoaderListener>();

    ArrayList<EnvLoaderListener> listenerList;
    listenerList = _listeners;

    if (listenerList != null) {
      synchronized (listenerList) {
        for (int i = 0; i < listenerList.size(); i++) {
          EnvLoaderListener listener = listenerList.get(i);

          if (listener != null)
            listeners.add(listener);
          else {
            listenerList.remove(i);
            i--;
          }
        }
      }
    }

    return listeners;
  }

  public final void updateScan()
  {
    ClassLoader parent = getParent();

    if (parent instanceof DynamicClassLoader) {
      DynamicClassLoader dynLoader = (DynamicClassLoader) parent;

      dynLoader.updateScan();
    }

    sendAddLoaderEvent();
  }

  /**
   * Adds a listener to detect class loader changes.
   */
  protected final void sendAddLoaderEvent()
  {
    if (_hasNewLoader && _isScanning.compareAndSet(false, true)) {
      try {
        while (_hasNewLoader) {
          _hasNewLoader = false;

          scan();

          configureEnhancerEvent();
          configurePostEnhancerEvent();
        }
      } finally {
        _isScanning.set(false);
      }
    }
  }

  /**
   * Sends an event to notify than an event has changed.
   */
  protected void configureEnhancerEvent()
  {
  }

  /**
   * Sends an event to notify than an event has changed.
   */
  protected void configurePostEnhancerEvent()
  {
  }

  /**
   * Add to the list of packages that don't use the hack.
   */
  public void addParentPriorityPackages(String []pkg)
  {
    for (int i = 0; pkg != null && i < pkg.length; i++) {
      addParentPriorityPackage(pkg[i]);
    }
  }

  /**
   * Add to the list of packages that don't use the {@link #setServletHack(boolean)}.
   */
  public void addParentPriorityPackage(String pkg)
  {
    if (_parentPriorityPackages == null)
      _parentPriorityPackages = new String[0];

    int oldLength = _parentPriorityPackages.length;
    String []newPkgs = new String[oldLength + 1];

    System.arraycopy(_parentPriorityPackages, 0, newPkgs, 0, oldLength);

    if (! pkg.endsWith("."))
      pkg = pkg + '.';

    newPkgs[oldLength] = pkg;
    _parentPriorityPackages = newPkgs;
  }

  /**
   * Add to the list of packages that take priority over the parent
   */
  public void addPriorityPackage(String pkg)
  {
    if (_priorityPackages == null)
      _priorityPackages = new String[0];

    int oldLength = _priorityPackages.length;
    String []newPkgs = new String[oldLength + 1];

    System.arraycopy(_priorityPackages, 0, newPkgs, 0, oldLength);

    if (! pkg.endsWith("."))
      pkg = pkg + '.';

    newPkgs[oldLength] = pkg;
    _priorityPackages = newPkgs;
  }

  /**
   * Returns the permission collection for the given code source.
   */
  @Override
  protected PermissionCollection getPermissions(CodeSource codeSource)
  {
    PermissionCollection perms = super.getPermissions(codeSource);

    ArrayList<Permission> permissions = _permissions;

    int size = permissions != null ? permissions.size() : 0;
    for (int i = 0; i < size; i++) {
      Permission permission = permissions.get(i);

      perms.add(permission);
    }

    return perms;
  }

  protected void addCodeBasePath(String path)
  {
  }

  /**
   * Sets any enhancer.
   */
  /*
  public void addTransformer(ClassFileTransformer transformer)
  {
    if (_classFileTransformerList == null) {
      _classFileTransformerList = new ArrayList<ClassFileTransformer>();
    }

    _classFileTransformerList.add(transformer);
  }

  protected ArrayList<ClassFileTransformer> getTransformerList()
  {
    return _classFileTransformerList;
  }
  */

  public static final String getHash(ClassLoader loader)
  {
    if (! (loader instanceof DynamicClassLoader))
      return loader.getClass().getName();

    DynamicClassLoader dynLoader = (DynamicClassLoader) loader;

    return dynLoader.getHash();
  }

  public String getHash()
  {
    return String.valueOf(getHashCrc());
  }

  public long getHashCrc()
  {
    long crc64 = getParentHashCrc();

    /*
    ArrayList<String> list = new ArrayList<String>();

    buildSelfClassPath(list);

    for (int i = 0; i < list.size(); i++) {
      crc64 = Crc64.generate(crc64, list.get(i));
    }
    */

    // buildImportClassPath(cp);

    ArrayList<Loader> loaders = getLoaders();
    if (loaders != null) {
      for (int i = 0; i < loaders.size(); i++) {
        Loader loader = loaders.get(i);

        crc64 = loader.getHashCrc(crc64);
      }
    }

    return crc64;
  }

  private long getParentHashCrc()
  {
    ClassLoader parent = getParent();

    if (parent == null)
      return 0;
    else if (parent instanceof DynamicClassLoader)
      return ((DynamicClassLoader) parent).getHashCrc();
    else
      return parent.hashCode();
  }

  /**
   * Fill data for the class path.  fillClassPath() will add all
   * .jar and .zip files in the directory list.
   */
  public final String getClassPath()
  {
    ArrayList<String> list = new ArrayList<String>();

    buildClassPath(list);

    return toClassPath(list);
  }

  /**
   * Fill data for the class path.  fillClassPath() will add all
   * .jar and .zip files in the directory list.
   */
  public final void buildClassPath(ArrayList<String> cp)
  {
    buildParentClassPath(cp);
    
    for (DynamicClassLoader loaderImport : _importLoaders) {
      loaderImport.buildSelfClassPath(cp);
    }

    buildSelfClassPath(cp);
  }

  private void buildParentClassPath(ArrayList<String> cp)
  {
    ClassLoader parent = getParent();

    if (parent instanceof DynamicClassLoader)
      ((DynamicClassLoader) parent).buildClassPath(cp);
    else {
      cp.addAll(getClassPathList());

      for (; parent != null; parent = parent.getParent()) {
        // XXX: should be reverse order
        if (parent instanceof URLClassLoader) {
          URLClassLoader urlLoader = (URLClassLoader) parent;

          for (URL url : urlLoader.getURLs()) {
            String urlString = url.toString();
            if (urlString.startsWith("file:"))
              urlString = urlString.substring("file:".length());

            if (! cp.contains(urlString))
              cp.add(urlString);
          }
        }
      }
    }
  }

  private void buildSelfClassPath(ArrayList<String> cp)
  {
    buildImportClassPath(cp);

    ArrayList<Loader> loaders = getLoaders();
    if (loaders != null) {
      for (int i = 0; i < loaders.size(); i++) {
        Loader loader = loaders.get(i);

        loader.buildClassPath(cp);
      }
    }
  }

  protected void buildImportClassPath(ArrayList<String> sb)
  {
  }

  /**
   * Fill data for the class path.  fillClassPath() will add all
   * .jar and .zip files in the directory list.
   */
  public final String getLocalClassPath()
  {
    ArrayList<String> cp = new ArrayList<String>();

    buildClassPath(cp);

    return toClassPath(cp);
  }

  /**
   * Returns the source path.  The source path is used for looking up
   * resources.
   */
  public final String getSourcePath()
  {
    ArrayList<String> cp = new ArrayList<String>();

    buildSourcePath(cp);

    return toClassPath(cp);
  }

  /**
   * Fill data for the class path.  fillSourcePath() will add all
   * .jar and .zip files in the directory list.
   */
  protected final void buildSourcePath(ArrayList<String> cp)
  {
    ClassLoader parent = getParent();

    if (parent instanceof DynamicClassLoader) {
      ((DynamicClassLoader) parent).buildSourcePath(cp);
    }
    else {
      cp.addAll(getClassPathList());
    }

    ArrayList<Loader> loaders = getLoaders();
    for (int i = 0; i < loaders.size(); i++) {
      Loader loader = loaders.get(i);

      loader.buildSourcePath(cp);
    }
  }

  /**
   * Returns the resource path with most specific first.
   */
  public final String getResourcePathSpecificFirst()
  {
    ArrayList<String> pathList = new ArrayList<String>();

    buildResourcePathSpecificFirst(pathList);

    StringBuilder sb = new StringBuilder();
    char sep = File.pathSeparatorChar;

    if (pathList.size() == 0)
      return "";

    sb.append(pathList.get(0));
    for (int i = 1; i < pathList.size(); i++) {
      sb.append(sep);
      sb.append(pathList.get(i));
    }

    return sb.toString();
  }

  /**
   * Returns the resource path with most specific first.
   */
  protected final void
    buildResourcePathSpecificFirst(ArrayList<String> pathList)
  {
    ClassLoader parent = getParent();

    ArrayList<Loader> loaders = getLoaders();
    int size = loaders != null ? loaders.size() : 0;
    for (int i = 0; i < size; i++) {
      Loader loader = loaders.get(i);

      loader.buildSourcePath(pathList);
    }

    if (parent instanceof DynamicClassLoader)
      ((DynamicClassLoader) parent).buildResourcePathSpecificFirst(pathList);
    else {
      String tail = getSystemClassPath();

      if (tail != null) {
        char sep = File.pathSeparatorChar;

        String []values = tail.split("[" + sep + "]");

        for (int i = 0; i < values.length; i++) {
          pathList.add(values[i]);
        }
      }
    }
  }

  protected static String toClassPath(ArrayList<String> list)
  {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      if (i != 0)
        sb.append(File.pathSeparatorChar);

      sb.append(list.get(i));
    }

    return sb.toString();
  }

  /**
   * Returns true if any of the classes have been modified.
   */
  @Override
  public final boolean isModified()
  {
    return isModified(_isEnableDependencyCheck);
  }

  /**
   * Returns true if any of the classes have been modified.
   */
  public final boolean isModified(boolean isEnable)
  {
    if (isStopped()) {
      return true;
    }

    DependencyContainer dependencies = _dependencies;

    if (dependencies == null) {
      return true;
    }

    if (isEnable) {
      boolean isModified = dependencies.isModified();

      return isModified;
    }
    else {
      boolean isModified = isModified(getParent());

      return isModified;
    }
  }

  /**
   * Returns true if any of the classes have been modified, forcing a check.
   */
  public final boolean isModifiedNow()
  {
    if (_lifecycle.isDestroyed())
      return true;

    DependencyContainer dependencies = _dependencies;

    if (dependencies == null)
      return true;

    return dependencies.isModifiedNow();
  }

  /**
   * Logs the reason for modification.
   */
  public final boolean logModified(Logger log)
  {
    if (_lifecycle.isDestroyed())
      return true;

    DependencyContainer dependencies = _dependencies;

    if (dependencies != null)
      return dependencies.logModified(log);
    else
      return false;
  }

  /**
   * Returns true if any of the classes have been modified.
   */
  public final void resetDependencyCheckInterval()
  {
    if (_lifecycle.isDestroyed())
      return;

    DependencyContainer dependencies = _dependencies;

    if (dependencies == null)
      return;

    dependencies.resetDependencyCheckInterval();
  }

  /**
   * Returns true if any of the classes have been modified.
   */
  public final void clearModified()
  {
    if (_lifecycle.isDestroyed())
      return;

    DependencyContainer dependencies = _dependencies;

    if (dependencies == null)
      return;

    dependencies.clearModified();
  }

  /**
   * Returns true if any of the classes have been modified.
   */
  public static boolean isModified(ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof DynamicClassLoader) {
        return ((DynamicClassLoader) loader).isModified();
      }
    }

    return false;
  }

  /**
   * Makes any changed classes for the virtual class loader.
   */
  //@Override
  public final void make()
    throws Exception
  {
    makeParents(getParent());

    if (_makeList != null) {
      for (Loader loader : _makeList) {
        loader.make();
      }
    }
  }

  private final void makeParents(ClassLoader loader)
    throws Exception
  {
    if (loader == null)
      return;

    makeParents(loader.getParent());

    /*
    if (loader instanceof Make) {
      ((Make) loader).make();
    }
    */
    if (loader instanceof DynamicClassLoader) {
      ((DynamicClassLoader) loader).make();
    }
  }

  /**
   * Defines a new package.
   */
  @Override
  protected Package definePackage(String name,
                                  String a1, String a2, String a3,
                                  String b1, String b2, String b3,
                                  URL url)
  {
    name = name.replace('/', '.');
    name = name.replace('\\', '.');

    if (name.endsWith(".")) {
      name = name.substring(0, name.length() - 1);
    }
    
    Package pkg = super.definePackage(name, a1, a2, a3, b1, b2, b3, url);
    
    return pkg;
  }

  /**
   * Initialize the class loader.
   */
  public void init()
  {
    if (! _lifecycle.toActive()) {
      return;
    }

    try {
      sendAddLoaderEvent();

      ArrayList<EnvLoaderListener> listeners = getListeners();

      if (listeners != null) {
        for (int i = 0; i < listeners.size(); i++) {
          EnvLoaderListener listener = listeners.get(i);

          listener.classLoaderInit(this);
        }
      }
    } catch (Exception e) {
      log().log(Level.WARNING, e.toString(), e);
    }
  }
  
  protected Throwable getConfigException()
  {
    return null;
  }

  /**
   * Validates the class loader.
   */
  public void validate()
    throws ConfigException
  {
    ArrayList<Loader> loaders = getLoaders();

    if (loaders == null)
      throw new IllegalStateException(_L.l("Class loader {0} is closed during initialization.", this));

    for (int i = 0; i < loaders.size(); i++)
      loaders.get(i).validate();
  }

  public void addScanRoot()
  {
    _hasNewLoader = true;
  }

  protected void scan()
  {
  }

  @Override
  public Class<?> loadClass(String name)
    throws ClassNotFoundException
  {
    // the Sun JDK implementation of ClassLoader delegates this call
    // to loadClass(name, false), but there is no guarantee that other
    // implementations do.
    return loadClass(name, false);
  }

  /**
   * Load a class using this class loader
   *
   * @param name the classname to load
   * @param resolve if true, resolve the class
   *
   * @return the loaded classes
   */
  @Override
  protected Class<?> loadClass(String name, boolean resolve)
    throws ClassNotFoundException
  {
    // XXX: removed sync block, since handled below
    Class<?> cl = null;

    try {
      cl = loadClassImpl(name, resolve);
    } catch (ClassNotFoundException e) {
      /*
      if (getConfigException() != null) {
        throw new ClassNotFoundException(e.getMessage() + " (in " + this + ")"
                                        + "\n  " + getConfigException().getMessage(),
                                         e);
        
      }
      else {
      }
      */
      throw new ClassNotFoundException(e.getMessage() + " (in " + this + ")", e);
    } catch (NoClassDefFoundError e) {
      log().finer(e.toString() + " (in " + this + ")");
      throw e;
    }

    if (cl != null)
      return cl;
    else {
      ClassNotFoundException exn = new ClassNotFoundException(name + " (in " + this + ")");

      throw exn;
    }
  }

  /**
   * Load a class using this class loader
   *
   * @param name the classname to load
   * @param resolve if true, resolve the class
   *
   * @return the loaded classes
   */
  public Class<?> loadClassImpl(String name, boolean resolve)
    throws ClassNotFoundException
  {
    if (_entryCache != null) {
      ClassEntry entry = _entryCache.get(name);

      if (entry != null) {
        Class<?> cl = entry.getEntryClass();

        if (cl != null)
          return cl;
      }
    }

    // The JVM has already cached the classes, so we don't need to
    Class<?> cl = findLoadedClass(name);

    if (cl != null) {
      if (resolve)
        resolveClass(cl);
      return cl;
    }

    if (_lifecycle.isDestroyed()) {
      log().fine(L().l("Loading class {0} when class loader {1} has been closed.",
                       name, this));

      return super.loadClass(name, resolve);
    }

    boolean normalJdkOrder = isNormalJdkOrder(name);

    if (_lifecycle.isBeforeInit())
      init();

    // ioc/0h41 vs ioc/0043 - can't scan during class loading
    // Force scanning if any loaders have been added
    // sendAddLoaderEvent();

    if (normalJdkOrder) {
      ClassLoader parent = getParent();

      try {
        if (parent instanceof DynamicClassLoader)
          cl = ((DynamicClassLoader) parent).loadClassImpl(name, resolve);
        else if (parent != null) {
          cl = Class.forName(name, false, parent);
        }
        else {
          cl = findSystemClass(name);
        }
      } catch (ClassNotFoundException e) {
      } catch (Error e) {
        if (! (parent instanceof DynamicClassLoader)) {
          log().finer(e + "\n  while loading " + name + " (in " + this + ")");
        }
        
        throw e;
      }


      if (cl == null) {
        // delegated classloader imports
        cl = findImportClass(name);
      }

      if (cl == null) {
        cl = findClassImpl(name);
      }
    }
    else {
      try {
        cl = findClass(name);
      } catch (ClassNotFoundException e) {
        ClassLoader parent = getParent();
        if (parent != null)
          cl = Class.forName(name, false, parent);
        else
          cl = findSystemClass(name);
      }
    }

    if (resolve && cl != null)
      resolveClass(cl);

    return cl;
  }

  /**
   * Returns any import class, e.g. from an osgi bundle
   */
  protected Class<?> findImportClass(String name)
  {
    for (DynamicClassLoader loader : _importLoaders) {
      try {
        Class<?> cl = loader.findClassImpl(name);
      
        if (cl != null) {
          return cl;
        }
      } catch (Exception e) {
        log().log(Level.ALL, e.toString(), e);
      }
    }
    
    return null;
  }

  /**
   * Load a class using this class loader
   *
   * @param name the classname using either '/' or '.'
   *
   * @return the loaded class
   */
  @Override
  protected Class<?> findClass(String name)
    throws ClassNotFoundException
  {
    Class<?> cl = findClassImpl(name);

    if (cl != null)
      return cl;
    else
      throw new ClassNotFoundException(name);
  }

  /**
   * Load a class using this class loader
   *
   * @param name the classname using either '/' or '.'
   *
   * @return the loaded class
   */
  public Class<?> findClassImpl(String name)
    throws ClassNotFoundException
  {
    if (_isVerbose)
      verbose(name, "findClass");

    if (_lifecycle.isDestroyed()) {
      log().fine("Class loader has been closed.");
      return super.findClass(name);
    }

    if (_lifecycle.isBeforeInit())
      init();

    /*
    if (! _lifecycle.isActive())
      return super.findClass(name);
    */

    // server/2439
    if (name.indexOf('/') >= 0)
      name = name.replace('/', '.');

    if (name.indexOf('\\') >= 0)
      name = name.replace('\\', '.');

    ClassEntry entry = null;

    entry = _entryCache == null ? null : _entryCache.get(name);

    if (entry == null) {
      int len = _loaders.size();

      for (int i = 0; i < len; i++) {

        Class<?> cl = _loaders.get(i).loadClass(name);

        if (cl != null) {
          return cl;
        }
      }

      entry = getClassEntry(name);
    }

    if (entry == null)
      return null;

    if (entry != null && _isVerbose)
      verbose(name, (isNormalJdkOrder(name) ? "found" : "found (took priority from parent)"));

    if (_isEnableDependencyCheck) {
      entry.addDependencies(_dependencies);
    }

    // Currently, the entry must be in the entry cache for synchronization
    // to work.  The same entry must be returned for two separate threads
    // trying to load the class at the same time.

    ClassEntry oldEntry = _entryCache.putIfAbsent(name, entry);

    if (oldEntry != null)
      entry = oldEntry;

    try {
      return loadClassEntry(entry);
    } catch (RuntimeException e) {
      throw e;
    } catch (ClassNotFoundException e) {
      throw e;
    } catch (Exception e) {
      log().log(Level.FINEST, e.toString(), e);

      throw new ClassNotFoundException(name + " [" + e + "]", e);
    }
  }

  /**
   * Returns the matching class entry.
   */
  protected ClassEntry getClassEntry(String name)
    throws ClassNotFoundException
  {
    String pathName = name.replace('.', '/') + ".class";

    ArrayList<Loader> loaders = getLoaders();
    for (int i = 0; i < loaders.size(); i++) {
      Loader loader = loaders.get(i);

      ClassEntry entry = loader.getClassEntry(name, pathName);

      if (entry != null)
        return entry;
    }

    return null;
  }

  /**
   * Loads the class from the loader.  The loadClass must be in the
   * top classLoader because the defineClass must be owned by the top.
   */
  protected Class<?> loadClassEntry(ClassEntry entry)
    throws IOException, ClassNotFoundException
  {
    Class<?> cl = null;

    byte []bBuf;
    int bLen;

    synchronized (entry) {
      cl = entry.getEntryClass();

      if (cl != null)
        return cl;

      entry.preLoad();

      String name = entry.getName();
      int p = name.lastIndexOf('.');
      if (p > 0) {
        String packageName = name.substring(0, p);
        Package pkg = getPackage(packageName);

        ClassPackage classPackage = entry.getClassPackage();

        if (pkg == null) {
          synchronized (_packageLock) {
            pkg = getPackage(packageName);

            if (pkg != null) {
            }
            else if (classPackage != null) {
              definePackage(packageName,
                            classPackage.getSpecificationTitle(),
                            classPackage.getSpecificationVersion(),
                            classPackage.getSpecificationVendor(),
                            classPackage.getImplementationTitle(),
                            classPackage.getImplementationVersion(),
                            classPackage.getImplementationVendor(),
                            null);
            }
            else {
              definePackage(packageName,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
            }
          }
        }
      }

      ByteArrayBuffer buffer = new ByteArrayBuffer();

      entry.load(buffer);

      bBuf = buffer.getBuffer();
      bLen = buffer.length();

      /*
      if (_classFileTransformerList != null) {
        Class<?> redefineClass = null;
        String className = name.replace('.', '/');

        if (bBuf.length != bLen) {
          byte []tempBuf = new byte[bLen];
          System.arraycopy(bBuf, 0, tempBuf, 0, bLen);
          bBuf = tempBuf;
        }

        ProtectionDomain protectionDomain = null;

        for (int i = 0; i < _classFileTransformerList.size(); i++) {
          ClassFileTransformer transformer = _classFileTransformerList.get(i);

          try {
            byte []enhancedBuffer = transformer.transform(this,
                                                          className,
                                                          redefineClass,
                                                          protectionDomain,
                                                          bBuf);

            if (enhancedBuffer != null) {
              bBuf = enhancedBuffer;
              bLen = enhancedBuffer.length;

              if (_isVerbose)
                verbose(name, String.valueOf(transformer));
            }
          } catch (Throwable e) {
            log().log(Level.WARNING, e.toString(), e);
          }
        }
      }
  */
    }

    try {
      cl = findLoadedClass(entry.getName());

      // #3673
      if (cl != null) {
        if (entry.getEntryClass() == null)
          entry.setEntryClass(cl);

        return cl;
      }

      // #3423 - defineClass must be outside ClassEntry synchronized
      // block because it can force recursive definitions,
      // possibly causing deadlocks
      cl = defineClass(entry.getName(),
                       bBuf, 0, bLen,
                       entry.getCodeSource());

      entry.setEntryClass(cl);
    } catch (RuntimeException e) {
      log().log(Level.FINER, entry.getName() + " [" + e.toString() + "]", e);

      throw e;
    } catch (Exception e) {
      log().log(Level.FINER, entry.getName() + " [" + e.toString() + "]", e);

      ClassNotFoundException exn;
      exn = new ClassNotFoundException(entry.getName() + " [" + e + "]", e);
      //exn.initCause(e);

      throw exn;
    } catch (LinkageError e) {
      // #3673
      cl = findLoadedClass(entry.getName());

      if (cl != null) {
        log().log(Level.FINE, e.toString(), e);
        return cl;
      }
      else
        throw e;
    }

    if (entry.postLoad()) {
      _dependencies.add(AlwaysModified.create());
      _dependencies.setModified(true);
    }

    return cl;
  }

  public Class<?> loadClass(String className, byte []bytecode)
  {
    Class<?> cl = defineClass(className,
                              bytecode, 0, bytecode.length);

    return cl;
  }

  /**
   * Gets the named resource
   *
   * @param name name of the resource
   */
  @Override
  public URL getResource(String name)
  {
    if (_resourceCache == null) {
      long expireInterval = getDependencyCheckInterval();

      _resourceCache = new TimedCache<String,URL>(256, expireInterval);
    }

    URL url = _resourceCache.get(name);

    if (url == NULL_URL)
      return null;
    else if (url != null)
      return url;

    if (name.startsWith("/"))
      name = name.substring(1);

    // String alias = getResourceAlias(name);

    /*
    if (name.endsWith("/"))
      name = name.substring(0, name.length() - 1);
      */

    boolean isNormalJdkOrder = isNormalJdkOrder(name);

    if (isNormalJdkOrder) {
      url = getParentResource(name);

      if (url != null)
        return url;
    }

    url = getImportResource(name);

    if (url != null)
      return url;

    ArrayList<Loader> loaders = getLoaders();

    for (int i = 0; loaders != null && i < loaders.size(); i++) {
      Loader loader = loaders.get(i);

      try {
        url = loader.getResource(name);
      } catch (Exception e) {
        log().log(Level.FINER, e.toString(), e);
      }

      if (url != null) {
        _resourceCache.put(name, url);

        return url;
      }
    }

    if (! isNormalJdkOrder) {
      url = getParentResource(name);

      if (url != null)
        return url;
    }

    _resourceCache.put(name, NULL_URL);

    return null;
  }

  protected String getResourceAlias(String name)
  {
    return null;
  }

  /**
   * Get resource from OSGi
   */
  protected URL getImportResource(String name)
  {
    return null;
  }

  private URL getParentResource(String name)
  {
    ClassLoader parent = getParent();

    ClassLoader systemLoader = ClassLoader.getSystemClassLoader();

    URL url = null;

    if (parent != null) {
      try {
        url = parent.getResource(name);
      } catch (Exception e) {
        log().log(Level.FINER, e.toString(), e);
      }

      if (url != null) {
        _resourceCache.put(name, url);

        return url;
      }
    }
    else if (this != systemLoader) {
      url = getSystemResource(name);

      if (url != null) {
        _resourceCache.put(name, url);

        return url;
      }
    }

    return null;
  }


  /**
   * Opens a stream to a resource somewhere in the classpath
   *
   * @param name the path to the resource
   *
   * @return an input stream to the resource
   */
  @Override
  public InputStream getResourceAsStream(String name)
  {
    if (name.startsWith("/"))
      name = name.substring(1);

    if (name.endsWith("/"))
      name = name.substring(0, name.length() - 1);

    // String alias = getResourceAlias(name);

    boolean isNormalJdkOrder = isNormalJdkOrder(name);
    InputStream is = null;

    if (isNormalJdkOrder) {
      is = getParentResourceAsStream(name);

      if (is != null)
        return is;
    }

    ArrayList<Loader> loaders = getLoaders();

    for (int i = 0; loaders != null && i < loaders.size(); i++) {
      Loader loader = loaders.get(i);

      try {
        is = loader.getResourceAsStream(name);
      } catch (Exception e) {
        log().log(Level.FINER, e.toString(), e);
      }

      if (is != null)
        return is;
    }

    if (! isNormalJdkOrder) {
      is = getParentResourceAsStream(name);
    }

    return is;
  }

  private InputStream getParentResourceAsStream(String name)
  {
    ClassLoader parent = getParent();

    ClassLoader systemLoader = ClassLoader.getSystemClassLoader();

    InputStream is = null;

    if (parent != null) {
      try {
        is = parent.getResourceAsStream(name);
      } catch (Exception e) {
        log().log(Level.FINER, e.toString(), e);
      }

      if (is != null) {
        return is;
      }
    }
    else if (this != systemLoader) {
      is = getSystemResourceAsStream(name);

      if (is != null) {
        return is;
      }
    }

    return null;
  }

  @Override
  public Enumeration<URL> getResources(String name)
    throws IOException
  {
    Vector<URL> resources = new Vector<URL>();

    getResources(resources, name);

    if (name.startsWith("/"))
      name = name.substring(1);

    String alias = getResourceAlias(name);

    if (alias != null)
      getResources(resources, alias);

    return resources.elements();
  }

  private void getResources(Vector<URL> resources, String name)
    throws IOException
  {
    ClassLoader parent = getParent();

    if (parent == null) {
    }
    else if (parent instanceof DynamicClassLoader) {
      DynamicClassLoader dynParent = (DynamicClassLoader) parent;

      dynParent.getResources(resources, name);
    }
    else {
      Enumeration<URL> parentResources = parent.getResources(name);

      while (parentResources.hasMoreElements()) {
        URL url = parentResources.nextElement();

        resources.add(url);
      }
    }

    fillResources(resources, name);
  }

  /**
   * Returns an enumeration of matching resources.
   */
  @Override
  public Enumeration<URL> findResources(String name)
  {
    if (name.startsWith("/"))
      name = name.substring(1);

    // server/249b, env/009b
    /*
    if (name.endsWith("/"))
      name = name.substring(0, name.length() - 1);
      */

    Vector<URL> resources = new Vector<URL>();

    fillResources(resources, name);

    String alias = getResourceAlias(name);

    if (alias != null)
      fillResources(resources, alias);

    return resources.elements();
  }

  private void fillResources(Vector<URL> resources, String name)
  {
    if (name.startsWith("/"))
      name = name.substring(1);

    ArrayList<Loader> loaders = getLoaders();
    if (loaders != null) {
      for (int i = 0; i < loaders.size(); i++) {
        Loader loader = loaders.get(i);

        loader.getResources(resources, name);
      }
    }
  }

  /**
   * Returns the full library path for the name.
   */
  @Override
  public String findLibrary(String name)
  {
    String systemName = System.mapLibraryName(name);

    ArrayList<Loader> loaders = getLoaders();
    for (int i = 0; i < loaders.size(); i++) {
      Loader loader = loaders.get(i);

      Path path = loader.getPath(systemName);

      if (path != null && path.canRead()) {
        return path.getNativePath();
      }
      
      path = loader.getPath("native/" + systemName);                                      

      if (path != null && path.canRead()) {
        return path.getNativePath();                                                      
      } 
    }

    for (int i = 0; i < _nativePath.size(); i++) {
      Path path = _nativePath.get(i);

      if (path.canRead())
        return path.getNativePath();
    }

    return super.findLibrary(name);
  }

  /**
   * Returns the matching single-level path.
   */
  public Path findPath(String name)
  {
    ArrayList<Loader> loaders = getLoaders();
    for (int i = 0; i < loaders.size(); i++) {
      Loader loader = loaders.get(i);

      Path path = loader.getPath(name);

      if (path != null && path.canRead()) {
        return path;
      }
    }

    return null;
  }

  /**
   * Returns true if the class loader should use the normal order,
   * i.e. looking at the parents first.
   */
  private boolean isNormalJdkOrder(String className)
  {
    return true;
    /*
    if (_priorityPackages != null) {
      String canonName = className.replace('/', '.');
      canonName = canonName.replace('\\', '.');

      for (String priorityPackage : _priorityPackages) {
        if (canonName.startsWith(priorityPackage))
          return false;
      }
    }
    */

    //return true;
    /*
    if (! _useServletHack)
      return true;

    String canonName = className.replace('/', '.');
    canonName = canonName.replace('\\', '.');
    String []pkgs = _parentPriorityPackages;

    for (int i = 0; pkgs != null && i < pkgs.length; i++) {
      if (canonName.startsWith(pkgs[i]))
        return true;
    }

    return false;
    */
  }

  /**
   * stops the class loader.
   */
  public void stop()
  {
  }
  
  @Override
  public void close()
  {
    destroy();
    
    try {
      super.close();
    } catch (Throwable e) {
    }
  }

  /**
   * Destroys the class loader.
   */
  public void destroy()
  {
    if (_zombieMarker == null) {
      _zombieMarker = new ZombieClassLoaderMarker();
    }

    try {
      stop();
    } catch (Throwable e) {
    }

    if (! _lifecycle.toDestroying()) {
      return;
    }

    try {
      ClassLoader parent = getParent();
      for (; parent != null; parent = parent.getParent()) {
        if (parent instanceof DynamicClassLoader) {
          DynamicClassLoader loader = (DynamicClassLoader) parent;

          if (_closeListener != null) {
            loader.removeListener(_closeListener);
          }
        }
      }

      ArrayList<EnvLoaderListener> listeners = _listeners;
      _listeners = null;

      Thread thread = Thread.currentThread();
      ClassLoader oldLoader = thread.getContextClassLoader();

      try {
        if (listeners != null) {
          // Sort listeners for QA consistent close
          Collections.sort(listeners, LISTENER_SORT);
          for (int i = listeners.size() - 1; i >= 0; i--) {
            EnvLoaderListener listener = listeners.get(i);

            try {
              thread.setContextClassLoader(this);

              listener.classLoaderDestroy(this);
            } catch (Throwable e) {
              log().log(Level.FINE, e.toString(), e);
            }
          }
        }
      } finally {
        thread.setContextClassLoader(oldLoader);
      }

      ArrayList<Loader> loaders = getLoaders();
      for (int i = loaders.size() - 1; i >= 0; i--) {
        Loader loader = loaders.get(i);

        try {
          loader.destroy();
        } catch (Throwable e) {
          log().log(Level.FINE, e.toString(), e);
        }
      }
    } finally {
      _loaders = null;
      //_jarLoader = null;
      //_pathLoader = null;

      _nativePath = null;
      _entryCache = null;
      _resourceCache = null;

      _dependencies = null;
      _makeList = null;

      _listeners = null;
      // _securityManager = null;
      _permissions = null;
      _codeSource = null;

      //_classFileTransformerList = null;
      _urls = null;
      //_closeListener = null;

      _lifecycle.toDestroy();
    }
  }

  /**
   * Sets the old loader if not destroyed.
   */
  public static void setOldLoader(Thread thread, ClassLoader oldLoader)
  {
    if (! (oldLoader instanceof DynamicClassLoader)) {
      thread.setContextClassLoader(oldLoader);
      return;
    }

    DynamicClassLoader dynLoader = (DynamicClassLoader) oldLoader;

    if (! dynLoader.isDestroyed())
      thread.setContextClassLoader(oldLoader);
    else
      thread.setContextClassLoader(ClassLoader.getSystemClassLoader());
  }

  public ClassLoader getInstrumentableClassLoader()
  {
    return this;
  }

  public ClassLoader getThrowawayClassLoader()
  {
    return getNewTempClassLoader();
  }

  public ClassLoader getNewTempClassLoader()
  {
    //return new TempDynamicClassLoader(this);
    return null;
  }
  
  /*
  public ContextClassLoader inContext()
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    thread.setContextClassLoader(this);
    
    return new ContextClassLoader(oldLoader);
  }
  */

  /**
   * Copies the loader.
   */
  /*
  protected void replace(DynamicClassLoader source)
  {
    _id = source._id;

    _loaders.addAll(source._loaders);
    _jarLoader = source._jarLoader;

    _dependencies = source._dependencies;

    _makeList = source._makeList;
    _useServletHack = source._useServletHack;
    _parentPriorityPackages = source._parentPriorityPackages;

    if (source._listeners != null) {
      if (_listeners == null)
        _listeners = new ArrayList<ClassLoaderListener>();

      _listeners.addAll(source._listeners);
      source._listeners.clear();
    }

    // _securityManager = source._securityManager;
    if (source._permissions != null) {
      if (_permissions == null)
        _permissions = new ArrayList<Permission>();

      _permissions.addAll(source._permissions);
    }

    _codeSource = source._codeSource;

    _lifecycle.copyState(source._lifecycle);
  }
  */

  @Override
  public String toString()
  {
    if (_id != null)
      return getClass().getSimpleName() +  "[" + _id + "]";
    else
      return getClass().getSimpleName() + getLoaders();
  }

  private static L10N L()
  {
    if (_L == null)
      _L = new L10N(DynamicClassLoader.class);

    return _L;
  }

  private static Logger log()
  {
    if (_log == null)
      _log = Logger.getLogger(DynamicClassLoader.class.getName());

    return _log;
  }

  // XXX: GC issues
  /*
  protected void finalize()
  {
    destroy();
  }
  */
  
  private Object writeReplace()
  {
    if (true) { throw new IllegalStateException(); }
    Thread.dumpStack();
    
    return null;
  }

  private static final ListenerComparator LISTENER_SORT
    = new ListenerComparator();

  static class ListenerComparator implements Comparator<Object> {
    public int compare(Object a, Object b)
    {
      return a.getClass().getName().compareTo(b.getClass().getName());
    }
  }

  /**
   * Returns the system classpath, including the bootpath
   */
  public static ArrayList<String> getClassPathList()
  {
    if (_classPathList != null)
      return _classPathList;

    ArrayList<String> list = new ArrayList<String>();

    String classPath = getSystemClassPath();
    String []classPathArray
      = classPath.split("[" + File.pathSeparatorChar + "]");

    for (int i = 0; i < classPathArray.length; i++) {
      if (! list.contains(classPathArray[i]))
        list.add(classPathArray[i]);
    }

    _classPathList = list;

    return _classPathList;
  }

  /**
   * Returns the system classpath, including the bootpath
   */
  public static String getSystemClassPath()
  {
    if (_classPath != null) {
      return _classPath;
    }

    String cp = System.getProperty("java.class.path");

    String boot = System.getProperty("sun.boot.class.path");
    if (boot != null && ! boot.equals(""))
      cp = cp + File.pathSeparatorChar + boot;

    Pattern pattern = Pattern.compile("" + File.pathSeparatorChar);

    String []path = pattern.split(cp);

    StringBuilder cb = new StringBuilder();

    for (int i = 0; i < path.length; i++) {
      //PathImpl subpath = Vfs.lookup(path[i]);

      if (true) { // subpath.canRead() || subpath.isDirectory()) {
        if (cb.length() > 0)
          cb.append(File.pathSeparatorChar);

        cb.append(path[i]);
      }
    }

    _classPath = cb.toString();

    return _classPath;
  }

  static {
    URL url = null;

    try {
      url = new URL("file:/caucho/null");
    } catch (Throwable e) {
    }

    NULL_URL = url;
  }

}
