package com.caucho.v5.json.ser;

import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.json.io.JsonWriter;
import com.caucho.v5.util.L10N;

public abstract class JsonSerializerBase<T>
  implements SerializerJson<T>
{
  private static final L10N L = new L10N(JsonSerializerBase.class);
  
  /*
  @Override
  public void write(JsonWriter out, Object value, boolean annotated)
      throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
  
  @Override
  public void write(JsonWriter out, T value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  /*
  @Override
  public void write(JsonWriter out, 
                    String fieldName, 
                    T value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
  
  @Override
  public void writeTop(JsonWriter out, T value)
  {
    out.writeStartArray();
    write(out, value);
    out.writeEndArray();
  }
  
  //
  // deserializer
  //
  
  @Override
  public T read(JsonReader in)
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
