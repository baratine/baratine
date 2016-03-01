/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.profile;

/**
 * A stack entry
 */
public class StackEntry
{
  private final String _className;
  private final String _methodName;
  private final String _arg;

  public StackEntry(String className, String methodName, String arg)
  {
    _className = className;
    _methodName = methodName;
    
    if (arg == null)
      arg = "";
    
    _arg = arg;
  }
  
  public String getClassName()
  {
    return _className;
  }
  
  public String getMethodName()
  {
    return _methodName;
  }

  public String getArg()
  {
    return _arg;
  }

  public String getDescription()
  {
    return getClassName() + "." + getMethodName() + "(" + getArg() + ")";
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + _className + "," + _methodName + "]";
  }
}
