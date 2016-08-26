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

package io.baratine.config;

import java.util.Map;

import io.baratine.convert.ConvertFrom;

/**
 * Config is the configuration environment, which contains a
 * read-only properties map.
 *
 * Config is specified using --conf &lt;file&gt; as arguments to {@code Web.start(..)}
 * or {@code Web.go(...)}.
 * <p>
 * Config file must be a valid YAML file.
 *
 * e.g.
 * <blockquote><pre>
 *     "key": value
 * </pre></blockquote>
 *
 * Instance of Config can be obtained with &#64;Inject as so:
 *
 * <blockquote><pre>
 *   &#64;Service
 *   public class MyService {
 *     &#64;Inject
 *     Config _config;
 *   }
 * </pre></blockquote>
 *
 * @see Var
 */
public interface Config extends Map<String,String>
{
  /**
   * Returns property value for a key or default if property is not set
   *
   * @param key property key
   * @param defaultValue default value
   * @return property value
   */
  String get(String key, String defaultValue);

  /**
   * Returns converted to specified type property value for a given key or default value
   * if property is not set.
   *
   * <blockquote><pre>
   *   int value = config.get("key", int.class, 5);
   * </pre></blockquote>
   *
   * @param key property key
   * @param type target type for conversion
   * @param defaultValue default value to use when property is not set
   * @param <T> type of property value
   * @return property value
   */
  <T> T get(String key, Class<T> type, T defaultValue);

  /**
   * Returns converted to specified type property value for a given key or converted
   * from default value.
   *
   * @param key property key
   * @param type target type for conversion
   * @param defaultValue default value in string representation
   * @param <T> type of property value
   * @return property value
   */
  <T> T get(String key, Class<T> type, String defaultValue);

  /**
   * Injects fields of a given bean with values from configuration:
   * conf.yml:
   * <blockquote><pre>
   *   greeting: Hello World!
   * </pre></blockquote>
   *
   *
   * <blockquote><pre>
   *   public class MyBean {
   *     String greeting;
   *   }
   * </pre></blockquote>
   *
   * <blockquote>
   *   <pre>
   *     MyBean bean = new MyBean();
   *     Config.inject(bean);
   *   </pre>
   * </blockquote>
   *
   * @see Var
   * @param bean bean to inject from configuration
   * @param <T> type of bean
   */
  <T> void inject(T bean);

  /**
   * Injects fields of a given bean with values from configuration using prefix
   * and field name as a a key.
   * conf.yml:
   * <blockquote><pre>
   *   bean1.greeting: Hello World!
   *   bean2.greeting: Hello World 2!
   * </pre></blockquote>
   *
   * <blockquote><pre>
   *   public class MyBean {
   *     String greeting;
   *   }
   * </pre></blockquote>
   *
   * <blockquote>
   *   <pre>
   *     MyBean bean1 = new MyBean();
   *     Config.inject(bean1, "bean1");
   *
   *     MyBean bean2 = new MyBean();
   *     Config.inject(bean2, "bean2");
   *   </pre>
   * </blockquote>
   * @param bean bean to inject from configuration
   * @param prefix property prefix
   * @param <T> type of bean
   */
  <T> void inject(T bean, String prefix);

  ConfigBuilder newChild();

  public interface ConfigBuilder
  {
    ConfigBuilder add(String key, String value);

    default ConfigBuilder add(String key, Object value)
    {
      return add(key, String.valueOf(value));
    }

    default ConfigBuilder addDefault(String key, String value)
    {
      if (! get().containsKey(key)) {
        add(key, value);
      }

      return this;
    }

    default ConfigBuilder addDefault(String key, Object value)
    {
      return addDefault(key, String.valueOf(value));
    }

    default ConfigBuilder add(Config env)
    {
      for (Map.Entry<String,String> entry : env.entrySet()) {
        add(entry.getKey(), entry.getValue());
      }

      return this;
    }

    void converter(ConvertFrom<String> convertManager);

    Config get();
  }
}
