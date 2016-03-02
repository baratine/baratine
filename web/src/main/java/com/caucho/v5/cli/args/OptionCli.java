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
import java.util.List;
import java.util.Objects;


public interface OptionCli<A extends ArgsBase>
{
  String name();
  
  default String configName(ArgsBase args)
  {
    return args.programName() + "." + name().replace('-', '.');
  }
  
  default void property(ArgsBase args, String value)
  {
    args.property(configName(args), value);
  }
  
  int parse(A args, String []argv, int index)
    throws CommandLineException;

  String usage(ArgsBase args);

  String getTinyName();

  ArgsType getType();

  String getValueDescription();

  String getDescription();

  boolean isFlag();

  //
  // builder
  //

  OptionCli<A> parser(OptionContainer<? extends A> parser);

  OptionCli<A> alias(String name);

  OptionCli<A> tiny(String name);

  OptionCli<A> config(String name);
  
  OptionCli<A> type(ArgsType type);

  OptionCli<A> deprecated();

  OptionCli<A> hide();

  OptionCli<A> required();

  public enum ArgsType {
    COMMAND,
    GENERAL,
    ADMIN,
    DEBUG,
    DEFAULT;
  }

  abstract public static class Base<X extends ArgsBase>
    implements OptionCli<X>
  {
    private OptionContainer<? extends X> _parser;
    private boolean _isDeprecated;
    private boolean _isRequired;

    private List<String> _tinyNameList;
    
    private String _configName;

    private ArgsType _type = ArgsType.DEFAULT;

    @Override
    public String name()
    {
      StringBuilder sb = new StringBuilder();

      String className = getClass().getSimpleName();

      for (int i = 0; i < className.length(); i++) {
        char ch = className.charAt(i);

        if (Character.isUpperCase(ch)) {
          if (i > 0) {
            sb.append("-");
          }

          sb.append(Character.toLowerCase(ch));
        }
        else {
          sb.append(ch);
        }
      }

      return sb.toString();
    }
    
    @Override
    public String configName(ArgsBase args)
    {
      if (_configName != null) {
        return _configName;
      }
      else {
        return args.programName() + "." + name().replace('-', '.');
      }
    }

    @Override
    public ArgsType getType()
    {
      return _type;
    }

    @Override
    public String getTinyName()
    {
      return null;
    }
    
    public String property()
    {
      return _configName;
    }

    @Override
    public String getDescription()
    {
      return null;
    }

    @Override
    public String getValueDescription()
    {
      return null;
    }

    @Override
    public boolean isFlag()
    {
      return false;
    }

    @Override
    public String usage(ArgsBase args)
    {
      if (_isDeprecated) {
        return null;
      }

      StringBuilder sb = new StringBuilder();

      sb.append("\n ");

      int padding = 9;

      if (_tinyNameList != null) {
        for (String tinyName : _tinyNameList) {
          sb.append("-").append(tinyName).append(", ");
          padding -= 1 + tinyName.length() + 2;
        }
      }

      for (int i = padding; i > 0; i--) {
        sb.append(" ");
      }

      String name = name();
      sb.append("--").append(name);

      String valueDesc = getValueDescription();

      if (! isFlag()) {
        if (valueDesc != null) {
          sb.append(" ").append(valueDesc);
        }
      }

      if (getDescription() != null) {
        int nameLen = name.length();

        if (valueDesc != null && ! isFlag()) {
          nameLen += valueDesc.length() + 1;
        }

        for (int i = nameLen; i < 20; i++) {
          sb.append(" ");
        }

        sb.append(" ").append(getDescription());
      }

      if (_isRequired) {
        sb.append(", required");
      }

      return sb.toString();
    }

    //
    // builder
    //

    @Override
    public OptionCli<X> alias(String name)
    {
      Alias<X> alias = new Alias<X>(this, name);

      _parser.option(alias);

      return alias;
    }

    @Override
    public OptionCli<X> tiny(String name)
    {
      Alias<X> alias = new Alias<X>(this, name);

      _parser.addTinyOption(alias);

      if (_tinyNameList == null) {
        _tinyNameList = new ArrayList<String>();
      }

      if (! _tinyNameList.contains(name)) {
        _tinyNameList.add(name);
      }

      return alias;
    }

  //@Override
    public OptionCli<X> config(String name)
    {
      _configName = name;

      return this;
    }

    @Override
    public OptionCli<X> deprecated()
    {
      _isDeprecated = true;

      return this;
    }

    @Override
    public OptionCli<X> hide()
    {
      _isDeprecated = true;

      return this;
    }

    @Override
    public OptionCli<X> required()
    {
      _isRequired = true;

      return this;
    }

    @Override
    public OptionCli<X> type(ArgsType type)
    {
      Objects.requireNonNull(type);

      _type = type;

      return this;
    }

    @Override
    public OptionCli<X> parser(OptionContainer<? extends X> parser)
    {
      _parser = parser;

      return this;
    }

    //
    // values
    //

    protected void addStringValue(X args, String value)
    {
      args.addOption(name(), new ValueCliArg.ValueString(this, value));
      
      property(args, value);
    }

    protected void addMultiStringValue(X args, String value)
    {
      args.addOption(name(), new ValueCliArg.ValueMultiString(this, value));
    }

    protected void addBooleanValue(X args, boolean value)
    {
      args.addOption(name(), new ValueCliArg.ValueString(this, String.valueOf(value)));
    }
  }

  static class Alias<X extends ArgsBase> implements OptionCli<X>
  {
    private OptionCli<X> _delegate;
    private String _name;
    private String _description;

    Alias(OptionCli<X> delegate, String name)
    {
      _delegate = delegate;
      _name = name;
    }

    @Override
    public String name()
    {
      return _name;
    }

    public ArgsType getType()
    {
      return ArgsType.COMMAND;
    }

    @Override
    public String getTinyName()
    {
      return null;
    }

    @Override
    public String getDescription()
    {
      return _description;
    }

    @Override
    public String getValueDescription()
    {
      return null;
    }

    @Override
    public boolean isFlag()
    {
      return _delegate.isFlag();
    }

    @Override
    public int parse(X args, String[] argv, int index)
        throws CommandLineException
    {
      return _delegate.parse(args, argv, index);
    }

    @Override
    public String usage(ArgsBase args)
    {
      return null;
    }

    @Override
    public OptionCli<X> alias(String name)
    {
      return _delegate.alias(name);
    }

    @Override
    public OptionCli<X> config(String name)
    {
      return _delegate.config(name);
    }

    @Override
    public OptionCli<X> tiny(String name)
    {
      return _delegate.tiny(name);
    }

    @Override
    public OptionCli<X> type(ArgsType type)
    {
      return _delegate.type(type);
    }

    @Override
    public OptionCli<X> deprecated()
    {
      _delegate.deprecated();

      return this;
    }

    @Override
    public OptionCli<X> hide()
    {
      _delegate.hide();

      return this;
    }

    @Override
    public OptionCli<X> required()
    {
      _delegate.required();

      return this;
    }

    @Override
    public OptionCli<X> parser(OptionContainer<? extends X> parser)
    {
      return this;
    }
   }

  static class IgnoreFlag extends Base<ArgsBase> {
    private String _name;
    private String _description;

    public IgnoreFlag(String name, String description)
    {
      _name = name;
      _description = description;
    }

    @Override
    public String name()
    {
      return _name;
    }

    @Override
    public String getDescription()
    {
      return _description;
    }

    @Override
    public int parse(ArgsBase args, String []argv, int index)
      throws CommandLineException
    {
      return index;
    }
  }

  static class IgnoreValue extends Base<ArgsBase> {
    private String _name;
    private String _description;

    public IgnoreValue(String name, String description)
    {
      _name = name;
      _description = description;
    }

    @Override
    public String name()
    {
      return _name;
    }

    @Override
    public String getDescription()
    {
      return _description;
    }

    @Override
    public int parse(ArgsBase args, String []argv, int index)
      throws CommandLineException
    {
      return index + 1;
    }
  }

}
