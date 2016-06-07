/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.v5.oauth;

public class State
{
  public enum StateEnum {
    INIT,
    CODE,
    TOKEN,
    GRANTED
  }

  private StateEnum _state = StateEnum.INIT;
  private String _code;
  private String _token;

  private String _url;

  public State()
  {
  }

  public State toInit()
  {
    _state = StateEnum.INIT;

    return this;
  }

  public State toCode(String url)
  {
    _state = StateEnum.CODE;

    _url = url;

    return this;
  }

  public State toToken(String code)
  {
    _state = StateEnum.TOKEN;

    _code = code;

    return this;
  }

  public State toGranted(String token)
  {
    _state = StateEnum.GRANTED;

    _token = token;

    return this;
  }

  public void url(String url)
  {
    _url = url;
  }

  public String url()
  {
    return _url;
  }

  public StateEnum state()
  {
    return _state;
  }

  public void code(String code)
  {
    _code = code;
  }

  public String code()
  {
    return _code;
  }

  public void token(String token)
  {
    _token = token;
  }

  public String token()
  {
    return _token;
  }
}
