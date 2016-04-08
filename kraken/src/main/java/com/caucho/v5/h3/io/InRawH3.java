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

package com.caucho.v5.h3.io;

import java.io.Closeable;

import com.caucho.v5.h3.query.PathH3Amp;

/**
 * H3 output interface
 */
public interface InRawH3 extends Closeable
{
  void readNull();
  
  boolean readBoolean();
  
  long readLong();
  
  String readString();

  byte[] readBinary();

  Object readObject(InH3Amp inAmp);

  float readFloat();
  
  double readDouble();
  
  long readUnsigned();

  @Override
  void close();

  void scan(InH3Amp in, PathH3Amp path, Object[] values);

  void skip(InH3Amp in);

  static long chunkSize(long chunk)
  {
    return chunk >> 1;
  }

  static boolean chunkIsFinal(long chunk)
  {
    return (chunk & 0x1) == 0;
  }
}
