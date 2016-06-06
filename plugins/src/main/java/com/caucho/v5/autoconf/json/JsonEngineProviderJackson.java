package com.caucho.v5.autoconf.json;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import com.caucho.v5.config.IncludeOnClass;
import com.caucho.v5.config.Priority;
import com.caucho.v5.json.JsonEngine;

import io.baratine.config.Config;
import io.baratine.config.Include;
import io.baratine.inject.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

@Include
@IncludeOnClass(ObjectMapper.class)
public class JsonEngineProviderJackson
{
  private static Logger _logger = Logger.getLogger(JsonEngineProviderJackson.class.toString());

  @Inject
  private Config _config;

  @Inject
  private ObjectMapper _mapper;

  @Bean
  @Priority(-10)
  public JsonEngine getJsonEngine()
  {
    _logger.log(Level.CONFIG, "found Jackson on the classpath, using Jackson for JSON serialization");

    JsonEngineJackson engine;

    if (_mapper != null) {
      _logger.log(Level.CONFIG, "using injected ObjectMapper");

      engine = new JsonEngineJackson(_mapper);
    }
    else {
      engine = new JsonEngineJackson();
    }

    return engine;
  }
}
