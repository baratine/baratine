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
 * @author Alex Rojkov
 */

package com.caucho.v5.h3.ser;

import com.caucho.v5.h3.OutH3;
import com.caucho.v5.h3.io.ConstH3;
import com.caucho.v5.h3.io.InH3Amp;
import com.caucho.v5.h3.io.InRawH3;
import com.caucho.v5.h3.io.OutRawH3;

import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class SerializerH3InetAddress extends SerializerH3Base<InetAddress>
{
  @Override
  public int typeSequence()
  {
    return ConstH3.DEF_INETADDRESS;
  }

  @Override
  public Type type()
  {
    return InetAddress.class;
  }

  @Override
  public InetAddress readObject(InRawH3 is, InH3Amp in)
  {
    byte[] bytes = is.readBinary();

    try {
      InetAddress result = InetAddress.getByAddress(bytes);

      return result;
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void skip(InRawH3 is, InH3Amp in)
  {
    is.skip(in);
  }

  @Override
  public void writeObject(OutRawH3 os,
                          int defId,
                          InetAddress value,
                          OutH3 out)
  {
    os.writeObject(typeSequence());
    byte[] addressBytes = value.getAddress();
    os.writeBinary(addressBytes, 0, addressBytes.length);
  }
}
