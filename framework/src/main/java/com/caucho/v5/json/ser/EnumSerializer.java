package com.caucho.v5.json.ser;

import com.caucho.v5.json.io.JsonWriter;

public class EnumSerializer extends JsonSerializerBase<Enum<?>>
{
  static final JsonSerializer<?> SER = new EnumSerializer();

  /*
  @Override 
  public void write(JsonWriter out, String name, Enum<?> value)
  {
    out.write(name, value.name());
  }
  */

  @Override 
  public void write(JsonWriter out, Enum<?> value)
  {
    out.write(value.name());
  }
}
