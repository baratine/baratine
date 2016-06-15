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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import com.caucho.v5.h3.OutH3;
import com.caucho.v5.h3.context.ContextH3;
import com.caucho.v5.h3.io.ConstH3;
import com.caucho.v5.h3.io.InH3Amp;
import com.caucho.v5.h3.io.InRawH3;
import com.caucho.v5.h3.io.OutRawH3;
import com.caucho.v5.h3.io.StreamSourceH3;
import com.caucho.v5.h3.query.PathH3Amp;
import com.caucho.v5.io.TempOutputStream;
import com.caucho.v5.util.L10N;

/**
 * H3 typed list serializer.
 */
public class SerializerH3StreamSource<T extends StreamSourceH3>
  extends SerializerH3Base<T>
{
  private static final L10N L = new L10N(SerializerH3StreamSource.class);
  
  private Class<?> _type;
  
  SerializerH3StreamSource()
  {
    _type = StreamSourceH3.class;
  }
  
  @Override
  public Type type()
  {
    return _type;
  }
  
  @Override
  public int typeSequence()
  {
    return ConstH3.DEF_STREAMSOURCE;
  }

  @Override
  public SerializerH3Amp<T> schema(ContextH3 context)
  {
    return null;
  }
  
  @Override
  public void writeDefinition(OutRawH3 os, int defIndex)
  {
  }

  @Override
  public void writeObject(OutRawH3 os, int defIndex, T stream, OutH3 out)
  {
    os.writeObject(defIndex);
   
    try (InputStream is = stream.get()) {
      out.writeBinary(is);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Introspect the class.
   */
  @Override
  public void introspect(ContextH3 context)
  {
  }

  /**
   * read the list from the input stream
   */
  @Override
  public T readObject(InRawH3 is, InH3Amp in)
  {
    TempOutputStream tos = new TempOutputStream();
    
    is.readBinary(tos);
    
    StreamSourceH3 ss = ()->tos.getInputStream();
    
    return (T) ss;
  }
  
  @Override
  public void scan(InRawH3 is, PathH3Amp path, InH3Amp in, Object []values)
  {
    is.skip(in);
  }

  @Override
  public void skip(InRawH3 is, InH3Amp in)
  {
    is.skip(in);
  }
}
