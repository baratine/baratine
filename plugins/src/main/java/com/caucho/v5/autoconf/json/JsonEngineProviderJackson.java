package com.caucho.v5.autoconf.json;

import javax.inject.Inject;

import com.caucho.v5.config.IncludeOnClass;
import com.caucho.v5.config.Priority;
import com.caucho.v5.json.JsonEngine;
import com.caucho.v5.json.JsonEngineDefault;
import com.caucho.v5.json.JsonEngineProviderDefault;

import io.baratine.config.Config;
import io.baratine.config.Include;
import io.baratine.inject.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

@Include
@IncludeOnClass(ObjectMapper.class)
public class JsonEngineProviderJackson
{
  private @Inject Config _config;

  @Bean
  @Priority(-10)
  public JsonEngine getJsonEngine()
  {
    System.err.println("JsonEngineProviderJackson.getJsonEngine0");
    Thread.dumpStack();

    return new JsonEngineDefault();
  }
}
