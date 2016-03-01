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

package com.caucho.v5.cli.args;

import java.util.ArrayList;


public interface ValueCliArg
{
  OptionCli<?> getOption();
  
  String getString();
  
  default boolean getBoolean()
  {
    String value = getString();
    
    if (value == null || "no".equals(value) || "false".equals(value)) {
      return false;
    }
    else {
      return true;
    }
  }
  
  default Iterable<String> getList()
  {
    String value = getString();
    
    ArrayList<String> list = new ArrayList<>();
    
    if (value != null) {
      list.add(value);
    }
    
    System.out.println("ARGL: " + list);
    
    return list;
  }
  
  abstract static class Base implements ValueCliArg
  {
    private OptionCli<?> _option;
    
    Base(OptionCli<?> option)
    {
      _option = option;
    }

    @Override
    public OptionCli<?> getOption()
    {
      return _option;
    }
  }
  
  public static class ValueString extends Base
  {
    private String _value;
    
    public ValueString(OptionCli<?> option, String value)
    {
      super(option);
      
      _value = value;
    }

    @Override
    public String getString()
    {
      return _value;
    }
  }
  
  public static class ValueMultiString extends Base
  {
    private String _value;
    
    public ValueMultiString(OptionCli<?> option, String value)
    {
      super(option);
      
      _value = value;
    }

    @Override
    public String getString()
    {
      return _value;
    }
  }
}
