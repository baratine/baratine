/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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

package com.caucho.v5.autoconf.json;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import io.baratine.config.Config;
import io.baratine.config.Include;
import io.baratine.inject.Bean;
import io.baratine.inject.Priority;

import com.caucho.v5.config.IncludeOnClass;
import com.caucho.v5.inject.impl.Opt;
import com.caucho.v5.json.JsonEngine;
import com.caucho.v5.json.JsonEngineDefault;

import com.fasterxml.jackson.databind.ObjectMapper;

@Include
@IncludeOnClass(ObjectMapper.class)
public class JsonEngineProviderJackson
{
  private static Logger LOG = Logger.getLogger(JsonEngineProviderJackson.class.getName());

  @Inject
  private Config _c;

  @Opt
  @Inject
  private JacksonObjectMapperProvider _mapperProvider;

  @Bean
  @Priority(-10)
  public JsonEngine getJsonEngine()
  {
    JsonJacksonConfig config = new JsonJacksonConfig();

    _c.inject(config);

    if (! config.enabled()) {
      LOG.log(Level.FINER, "found Jackson on the classpath, but not enabled for json serialization (json.jackson.enabled=false)");

      return new JsonEngineDefault();
    }

    ObjectMapper mapper = _mapperProvider != null ? _mapperProvider.get() : null;

    LOG.log(Level.CONFIG, "using Jackson for JSON serialization");

    JsonEngineJackson engine;

    if (mapper != null) {
      LOG.log(Level.CONFIG, "using injected ObjectMapper for Jackson");

      engine = new JsonEngineJackson(mapper);
    }
    else {
      mapper = new ObjectMapper();

      config.configure(mapper);

      engine = new JsonEngineJackson(mapper);
    }

    return engine;
  }
}
