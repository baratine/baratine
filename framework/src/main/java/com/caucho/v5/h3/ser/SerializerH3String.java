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

import com.caucho.v5.h3.OutH3;
import com.caucho.v5.h3.context.ContextH3;
import com.caucho.v5.h3.io.InH3Amp;
import com.caucho.v5.h3.io.InRawH3;
import com.caucho.v5.h3.io.OutRawH3;
import com.caucho.v5.h3.query.PathH3Amp;

/**
 * H3 string serializer.
 */
public class SerializerH3String extends SerializerH3Base<String>
{
  @Override
  public Type type()
  {
    return String.class;
  }
  
  @Override
  public int typeSequence()
  {
    return 1;
  }

  @Override
  public SerializerH3Base<String> schema(ContextH3 context)
  {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void writeDefinition(OutRawH3 os, int defIndex)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void writeObject(OutRawH3 os, int defIndex, String value, OutH3 out)
  {
    os.writeString(value);
  }

  @Override
  public String readObject(InRawH3 is, InH3Amp in)
  {
    return is.readString();
  }
  
  @Override
  public void scan(InRawH3 is, PathH3Amp path, InH3Amp in, Object []values)
  {
    is.skip(in);
  }
}
