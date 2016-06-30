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
 * ConfigEnv is the configuration environment, which contains a
 * read-only properties map.
 */
public interface Config extends Map<String,String>
{
  String get(String key, String defaultValue);

  <T> T get(String key, Class<T> type, T defaultValue);

  <T> T get(String key, Class<T> type, String defaultValue);

  <T> void inject(T bean);

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
