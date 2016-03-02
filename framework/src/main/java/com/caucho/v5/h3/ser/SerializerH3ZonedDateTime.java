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

import java.lang.reflect.Type;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.caucho.v5.h3.OutH3;
import com.caucho.v5.h3.io.ConstH3;
import com.caucho.v5.h3.io.InH3Amp;
import com.caucho.v5.h3.io.InRawH3;
import com.caucho.v5.h3.io.OutRawH3;

public class SerializerH3ZonedDateTime extends SerializerH3Base<ZonedDateTime>
{
  @Override
  public int typeSequence()
  {
    return ConstH3.DEF_ZONED_DATE_TIME;
  }

  @Override
  public Type type()
  {
    return ZonedDateTime.class;
  }

  @Override
  public ZonedDateTime readObject(InRawH3 is, InH3Amp in)
  {
    long l = is.readLong();
    int nano = (int) is.readLong();

    int year = (int) ((l & 0xFFFF000000000000L) >> 48);
    int month = (int) ((l & 0x0000FF0000000000L) >> 40);
    int day = (int) ((l & 0x000000FF00000000L) >> 32);

    int t = (int) (l & 0x00000000FFFFFFFFL);

    int hour = t / 60 / 60;
    int minute = (t - hour * 60 * 60) / 60;
    int second = (t - hour * 60 * 60 - minute * 60);

    return ZonedDateTime.of(year,
                            month,
                            day,
                            hour,
                            minute,
                            second,
                            nano,
                            ZoneId.of(is.readString()));
  }

  @Override
  public void writeObject(OutRawH3 os,
                          int defId,
                          ZonedDateTime time,
                          OutH3 out)
  {
    os.writeObject(typeSequence());

    long l = time.getYear() << 8;

    l |= time.getMonthValue();
    l <<= 8;

    l |= time.getDayOfMonth();
    l <<= 32;

    l |= (time.getHour() * 60 * 60 + time.getMinute() * 60 + time.getSecond());

    os.writeLong(l);
    os.writeLong(time.getNano());

    os.writeString(time.getZone().getId());
  }
}
