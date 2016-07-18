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

package com.caucho.v5.scan;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.bytecode.scan.ScanClass;
import com.caucho.v5.bytecode.scan.ScanListenerByteCode;
import com.caucho.v5.bytecode.scan.ScanManagerByteCode;
import com.caucho.v5.io.Vfs;
import com.caucho.v5.scan.ScanManager.ScanBuilder;
import com.caucho.v5.scan.ScanManager.ScanListener;

import io.baratine.config.Include;
import io.baratine.service.Service;

/**
 * Manages classloader scanning.
 */
class ScanBuilderImpl implements ScanBuilder, ScanListenerByteCode
{
  private static final Logger log
    = Logger.getLogger(ScanBuilderImpl.class.getName());
  
  private ScanListener _listener;
  
  private ArrayList<String> _packageNames = new ArrayList<>();
  private HashSet<String> _annTypes = new HashSet<>();
  
  private HashSet<String> _classNames = new HashSet<>();

  private URLClassLoader _loader;
  
  private Predicate<String> _isClassName;
  
  ScanBuilderImpl(ScanListener listener)
  {
    Objects.requireNonNull(listener);
    
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    if (! (loader instanceof URLClassLoader)) {
      throw new IllegalStateException(String.valueOf(loader));
    }
    
    _loader = (URLClassLoader) loader;
    
    _listener = listener;
    
    _annTypes.add(Include.class.getName());
    _annTypes.add(Service.class.getName());
  }
  
  @Override
  public ScanBuilderImpl basePackage(Package pkg)
  {
    Objects.requireNonNull(pkg);
    
    String packageName = pkg.getName();
    
    /*
    int p = className.lastIndexOf('.');
    
    if (p > 0) {
      String packageName = className.substring(0, p + 1);
      _packageNames.add(packageName);
    }
    */
    _packageNames.add(packageName);
    
    return this;
  }
  
  @Override
  public ScanBuilderImpl classNameTest(Predicate<String> classTest)
  {
    Objects.requireNonNull(classTest);
    
    _isClassName = classTest;
    
    return this;
  }

  @Override
  public void go()
  {
    if (_isClassName == null) {
      _isClassName = new PackagePredicate();
    }
    
    ScanManagerByteCode scanManager = new ScanManagerByteCode(this);
    
    ArrayList<URL> urlList = new ArrayList<>();
    
    fillUrls(urlList, _loader);
    
    Collections.sort(urlList, (x,y)->x.toString().compareTo(y.toString()));
    
    for (URL url : urlList) {
      try {
        scanManager.scan(_loader, Vfs.path(url), null);
      } catch (Exception e) {
        log.log(Level.WARNING, e.toString(), e);
      }
    }
  }
  
  private void fillUrls(ArrayList<URL> urlList, ClassLoader loader)
  {
    if (loader == null) {
      return;
    }
    
    if (loader != ClassLoader.getSystemClassLoader()) {
      fillUrls(urlList, loader.getParent());
    }
    
    if (! (loader instanceof URLClassLoader)) {
      return;
    }
    
    URLClassLoader urlLoader = (URLClassLoader) loader;
    
    for (URL url : urlLoader.getURLs()) {
      if (! urlList.contains(url)) {
        urlList.add(url);
      }
    }
  }
  
  private void addClass(String className)
  {
    if (_classNames.contains(className)) {
      return;
    }
    
    _classNames.add(className);
    
    try {
      Class<?> cl = Class.forName(className, false, _loader);
      
      _listener.onClass(cl);
    } catch (Exception e) {
      log.log(Level.FINEST, e.toString(), e);
    }
  }

  @Override
  public boolean isScanClassName(String name)
  {
    if (_isClassName.test(name)) {
      return true;
    }
    else {
      return false;
    }
  }
  
  @Override
  public ScanClass scanClass(Path root, 
                             String packageRoot, 
                             String name, 
                             int modifiers)
  {
  //  if (_isClassName.match.(
    if (_isClassName.test(name)) {
      return new ScanClassImpl(name);
    }
    
    return null;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  private class ScanClassImpl implements ScanClass
  {
    private String _className;
    
    ScanClassImpl(String className)
    {
      _className = className;
    }
    
    /**
     * Adds a class annotation
     */
    @Override
    public void addClassAnnotation(char [] buffer, int offset, int length)
    {
      String annType = new String(buffer, offset, length);

      if (_annTypes.contains(annType)) {
        addClass(_className);
      }
    }
  }
  
  private class PackagePredicate implements Predicate<String>
  {
    @Override
    public boolean test(String className)
    {
      for (String packageName : _packageNames) {
        if (className.startsWith(packageName)) {
          return true;
        }
      }

      return false;
    }
    
  }
}
