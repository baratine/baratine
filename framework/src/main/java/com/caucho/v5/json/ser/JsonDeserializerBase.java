package com.caucho.v5.json.ser;

import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.util.L10N;

public class JsonDeserializerBase implements JsonDeserializer
{
  private static final L10N L = new L10N(JsonDeserializerBase.class);
  
  @Override
  public Object read(JsonReader in)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void readField(JsonReader in, Object bean, String fieldName)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  protected JsonException error(String msg, Object ...args)
  {
    return new JsonException(L.l(msg, args));
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
