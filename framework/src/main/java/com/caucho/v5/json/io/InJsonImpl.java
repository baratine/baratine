/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Baratine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Baratine; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.json.io;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;

import com.caucho.v5.json.ser.JsonException;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.VfsOld;

public class InJsonImpl implements InJson
{
  private static final L10N L = new L10N(InJsonImpl.class);

  private int _peek;
  private Reader _is;

  private int _line;
  private int _offset;

  private Event _event;

  private boolean _isInt;

  private String _stringValue;
  private long _longValue;

  private BigDecimal _bigDecimalValue;

  protected InJsonImpl()
  {
  }

  public InJsonImpl(Reader is)
  {
    init(is);
  }

  /*
  public InJsonImpl(InputStream is)
  {
    init(Vfs.openRead(is).getReader());
  }
  */

  public void init(Reader is)
  {
    _is = is;

    _line = 1;
    _offset = 0;
  }

  @Override
  public final boolean hasNext()
  {
    Event event = _event;

    if (event == null) {
      _event = event = scanNext();
    }

    return event != null;
  }

  public final Event peek()
  {
    Event event = _event;

    if (event == null) {
      _event = event = scanNext();
    }

    return event;
  }

  @Override
  public final Event next()
  {
    Event event = _event;
    _event = null;

    if (event == null) {
      event = scanNext();
    }

    return event;
  }

  @Override
  public final String getString()
  {
    return _stringValue;
  }

  @Override
  public boolean isIntegralNumber()
  {
    return _isInt;
  }

  @Override
  public final int getInt()
  {
    return (int) _longValue;
  }

  @Override
  public final long getLong()
  {
    return _longValue;
  }

  public final double getDoubleValue()
  {
    if (isIntegralNumber()) {
      return _longValue;
    }
    else {
      return getBigDecimal().doubleValue();
    }
  }

  @Override
  public BigDecimal getBigDecimal()
  {
    return _bigDecimalValue;
  }

  private Event scanNext()
  {
    while (true) {
      int ch = read();

      switch (ch) {
      case ' ': case '\t': case '\r':
        break;

      case '\n':
        _line++;
        _offset = 0;
        break;

      case ',':
        break;

      case '[':
        return Event.START_ARRAY;

      case ']':
        return Event.END_ARRAY;

      case '{':
        return Event.START_OBJECT;

      case '}':
        return Event.END_OBJECT;

      case '"':
        _stringValue = parseString();

        return peekKey();

      case '-':
        parseNumberValue(-1, 0);
        return Event.VALUE_LONG;

      case '+':
        parseNumberValue(1, 0);
        return Event.VALUE_LONG;

      case '0': case '1': case '2': case '3': case '4':
      case '5': case '6': case '7': case '8': case '9':
        parseNumberValue(1, ch - '0');

        return Event.VALUE_LONG;

      case 'n':
        if ((ch = read()) != 'u'
            || (ch = read()) != 'l'
            || (ch = read()) != 'l') {
          throw error(L.l("Unexpected character 0x{0} '{1}' while parsing JSON 'null'",
                          Integer.toHexString(ch),
                          String.valueOf((char) ch)));
        }
        return Event.VALUE_NULL;

      case 'f':
        if ((ch = read()) != 'a'
            || (ch = read()) != 'l'
            || (ch = read()) != 's'
            || (ch = read()) != 'e') {
          throw error(L.l("Unexpected character 0x{0} '{1}' while parsing JSON 'false'",
                          Integer.toHexString(ch),
                          String.valueOf((char) ch)));
        }
        return Event.VALUE_FALSE;

      case 't':
        if ((ch = read()) != 'r'
            || (ch = read()) != 'u'
            || (ch = read()) != 'e') {
          throw error(L.l("Unexpected character 0x{0} '{1}' while parsing JSON 'true'",
                          Integer.toHexString(ch),
                          String.valueOf((char) ch)));
        }
        return Event.VALUE_TRUE;

      default:
        if (ch < 0) {
          return null;
        }
        else {
          throw error(L.l("Unexpected character 0x{0} '{1}' while parsing JSON token.",
                          Integer.toHexString(ch),
                          String.valueOf((char) ch)));
        }
      }
    }
  }

  private String parseString()
  {
    int ch;

    StringBuilder sb = new StringBuilder();

    while ((ch = read()) >= 0 && ch != '"') {
      if (ch == '\\') {
        ch = read();

        switch (ch) {
        case 'r':
          sb.append('\r');
          break;
        case 'n':
          sb.append('\n');
          break;
        case 't':
          sb.append('\t');
          break;
        case 'f':
          sb.append('\f');
          break;
        default:
          sb.append((char) ch);
        }
      }
      else {
        sb.append((char) ch);
      }
    }

    if (ch < 0) {
      throw new RuntimeException(this + " closing '\"' expected at <EOF>");
    }

    return sb.toString();
  }

  private void parseNumberValue(int sign, long value)
  {
    int ch;

    while ((ch = read()) >= 0) {
      switch (ch) {
      case '0': case '1': case '2': case '3': case '4':
      case '5': case '6': case '7': case '8': case '9':
        value = 10 * value + ch - '0';
        break;

      case '.': case 'e': case 'E':
        parseDouble(sign, value, ch);
        _isInt = false;
        return;

      default:
        _peek = ch;
        _longValue = sign * value;

        _isInt = true;
        return;
      }
    }

    _longValue = sign * value;
  }

  private void parseDouble(int sign, long value, int ch)
  {
    StringBuilder sb = new StringBuilder();

    if (sign < 0) {
      sb.append('-');
    }

    sb.append(value);
    sb.append((char) ch);

    while ((ch = read()) >= 0) {
      switch (ch) {
      case '0': case '1': case '2': case '3': case '4':
      case '5': case '6': case '7': case '8': case '9':
        sb.append((char) ch);
        break;

      case '+': case '-':
      case '.': case 'e': case 'E':
        sb.append((char) ch);
        break;

      default:
        _peek = ch;

        _isInt = false;
        _bigDecimalValue = new BigDecimal(sb.toString());
        return;
      }
    }

    _isInt = false;
    _bigDecimalValue = new BigDecimal(sb.toString());
  }

  private Event peekKey()
  {
    while (true) {
      int ch = read();

      switch (ch) {
      case ' ': case '\t': case '\r': case '\n':
        break;

      case ':':
        return Event.KEY_NAME;

      default:
        _peek = ch;
        return Event.VALUE_STRING;
      }
    }
  }

  private int read()
  {
    try {
      int ch = _peek;

      if (ch <= 0) {
        ch = _is.read();

        _offset++;

        return ch;
      }
      else {
        _peek = -1;

        return ch;
      }
    } catch (Exception e) {
      throw new JsonException(L.l("Exception while parsing JSON '{0}'", e), e);
    }
  }

  private JsonException error(String msg)
  {
    return new JsonParsingException(":" + _line + ":" + _offset + ": " + msg);
  }

  @Override
  public void close()
  {
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
