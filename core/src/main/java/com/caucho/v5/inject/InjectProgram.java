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
 * @author Scott Ferguson;
 */

package com.caucho.v5.inject;

import java.util.Objects;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.inject.impl.InjectContext;
import com.caucho.v5.inject.impl.InjectContextImpl;
import com.caucho.v5.util.L10N;

/**
 * A saved program for configuring an object.
 */
public abstract class InjectProgram implements Comparable<InjectProgram>
{
  private static final L10N L = new L10N(InjectProgram.class);
  
  //private final Config _config;
  
  /*
  protected InjectProgram(Config config)
  {
    _config = config;
  }
  
  protected Config getConfig()
  {
    return _config;
  }
  */
  
  /**
   * Returns the program's QName
   */
  /*
  public NameCfg getQName()
  {
    return null;
  }
  */
  
  public int getPriority()
  {
    return 0;
  }
  
  /**
   * Returns the declaring class.
   */
  public Class<?> getDeclaringClass()
  {
    return getClass();
  }
  
  /**
   * Returns the name.
   */
  public String getName()
  {
    return getClass().getName();
  }
  
  /**
   * Configures the bean using the current program.
   * 
   * @param bean the bean to configure
   * @param env the Config environment
   */
  abstract public <T> void inject(T bean, InjectContext env);
  
  /**
   * Configures a bean field.
   * 
   * @param bean the bean to configure
   * @param type the introspected bean type
   * @param env the creational context
   */
  /*
  public <T> void inject(T bean, ConfigType<T> type, InjectContext env)
    throws ConfigException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  /**
   * Binds the injection point
   */
  public void bind()
  {
  }

  public void addProgram(InjectProgram program)
  {
    throw new UnsupportedOperationException(L.l("{0}: Cannot add a program ({1}) to a BuilderProgram. You probably need a BuilderProgramContainer.",
                                                this, program));
  }
  
  /*
  public ContainerProgram toContainer()
  {
    throw new UnsupportedOperationException(L.l("{0}: Cannot convert to a container program.",
                                                this));
  }
  */

  /**
   * Configures a bean with a configuration file.
   */
  public final void configure(Object bean)
  {
    Objects.requireNonNull(bean);
    
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();
    
    /*
    ContextConfig oldContext = ContextConfig.getCurrent();
    
    try {
      ContextConfig.setCurrent(new ContextConfig(new Config()));
      
      ConfigType<?> type = TypeFactoryConfig.getType(bean);

      configure(bean, type);
    } finally {
      ContextConfig.setCurrent(oldContext);
    }
    */
  }

  /**
   * Configures the object.
   */
  final
  public void configureImpl(Object bean) // , ConfigType<?> type)
    throws ConfigException
  {
    Objects.requireNonNull(bean);
    //Objects.requireNonNull(type);
    
    try {
      //type.beforeConfigure(bean);
      
      // ioc/23e7
      InjectContext env = InjectContextImpl.CONTEXT;
      injectTop(bean, env);
    
      //type.init(bean);
    } finally {
      //type.afterConfigure(bean);
    }
  }

  final
  public <T> T configure(Class<T> type)
    throws ConfigException
  {
    return configureImpl(type);//, _config.currentOrCreateContext());
  }
  
  final
  public void inject(Object bean)
    throws ConfigException
  {
    Objects.requireNonNull(bean);
    
    // ioc/23e7
    InjectContext context = null;
    inject(bean, context);
  }

  /**
   * Configures a bean given a class to instantiate.
   */
  final
  protected <T> T configureImpl(Class<T> type) // , ContextConfig env)
    throws ConfigException
  {
    try {
      T value = type.newInstance();

      InjectContext context = null;
      injectTop(value, context);
      
      //Config.init(value);

      return value;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
  }
  
  public <T> void injectTop(T bean, InjectContext env)
  {
    inject(bean, env);
  }

  /**
   * Configures a bean given a class to instantiate.
   */
  final
  protected <T> T create(Class<T> type, InjectContext env)
    throws ConfigException
  {
    try {
      T value = type.newInstance();

      inject(value, env);
      
      return value;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
  }

  /*
  final
  public <T> T create(ConfigType<T> type)
    throws ConfigException
  {
    InjectContext env = null;
    return create(type, env);
  }

  public <T> T create(ConfigType<T> type, InjectContext env)
    throws ConfigException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  public void init(Object bean)
    throws ConfigException
  {
    //Config.init(bean);
  }

  @Override
  public int compareTo(InjectProgram peer)
  {
    // ioc/0119, ioc/011e
    int cmp = getPriority() - peer.getPriority();
    
    // ioc/011f - static fields have priority;
    if ((getPriority() < 0) != (peer.getPriority() < 0))
      return cmp;
    
    Class<?> selfClass = getDeclaringClass();
    Class<?> peerClass = peer.getDeclaringClass();
    
    if (selfClass == peerClass) {
      if (cmp != 0)
        return cmp;

      return getName().compareTo(peer.getName());
    }
    else if (selfClass.isAssignableFrom(peerClass))
      return -1;
    else if (peerClass.isAssignableFrom(selfClass))
      return 1;
    else {
      if (cmp != 0)
        return cmp;

      return selfClass.getName().compareTo(peerClass.getName());
    }
  }
}
