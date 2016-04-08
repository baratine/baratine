/*
 * Copyright (c) 2001-2016 Caucho Technology, Inc.  All rights reserved.
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.h3;

import java.io.Closeable;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * H3 output interface
 */
public interface OutH3 extends Closeable
{
  /**
   * null
   */
  void writeNull();

  /**
   * boolean
   */
  void writeBoolean(boolean value);

  /**
   * long integer
   */
  void writeLong(long value);
  
  /**
   * double
   */
  void writeDouble(double value);
  
  /**
   * fixed-length string
   */
  void writeString(String value);
  
  /**
   * fixed-length binary
   */
  void writeBinary(byte []buffer, int offset, int length);
  
  default void writeBinary(byte []buffer)
  {
    writeBinary(buffer, 0, buffer.length);
  }
  
  default void writeBinary(InputStream is)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  /**
   * Writes an object
   */
  <T> void writeObject(T value);

  default void writeChunkedString()
  {
  }

  default void writeChunkedBinary()
  {
  }
  
  /**
   * partial string
   */
  void writeStringPart(String value);
  
  /**
   * partial binary
   */
  void writeBinaryPart(byte []buffer, int offset, int length);
  
  /**
   * returns a typed serializer
   */
  <T> SerializerH3<T> serializer(Class<T> type);
  
  /**
   * returns a typed serializer from a generic type.
   */
  <T> SerializerH3<T> serializer(Type type);
  
  <T> void writeObject(T object, SerializerH3<T> serializer);
  
  default void flush() {}
  
  @Override
  void close();

}
