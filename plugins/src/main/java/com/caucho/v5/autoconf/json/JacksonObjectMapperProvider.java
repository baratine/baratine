package com.caucho.v5.autoconf.json;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface JacksonObjectMapperProvider
{
  ObjectMapper get();
}
