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

package com.caucho.v5.h3.ser;

import java.lang.reflect.Type;

import com.caucho.v5.h3.SerializerH3;
import com.caucho.v5.h3.context.ContextH3;
import com.caucho.v5.h3.io.InH3Amp;
import com.caucho.v5.h3.io.InRawH3;
import com.caucho.v5.h3.query.PathH3Amp;

/**
 * H3 typed serializer.
 */
public interface SerializerH3Amp<T> extends SerializerH3<T>
{
  default int typeSequence()
  {
    return 0;
  }
  
  default void introspect(ContextH3 context)
  {
  }

  Type type();

  T readObject(InRawH3 is, InH3Amp in);

  default SerializerH3Amp<T> schema(ContextH3 contextH3)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default void scan(InRawH3 is, PathH3Amp path, InH3Amp in, Object []values)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default void skip(InRawH3 is,InH3Amp in)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
