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
 * @author Nam Nguyen
 */

package com.caucho.v5.cli.baratine;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.cli.spi.CommandBase;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;

/**
 * Generates an async, non-blocking interface for a given class.
 */
public class GenerateIfaceCommand extends CommandBase<ArgsCli>
{
  private static final L10N L = new L10N(GenerateIfaceCommand.class);

  public static void main(String []args)
  {
    String fullClassName = null;
    boolean isRecursive = false;

    for (int i = 0; i < args.length; i++) {
      String argName = args[i];

      if ("-c".equals(argName)) {
        fullClassName = args[++i];
      }
      else if ("-r".equals(argName)) {
        isRecursive = true;
      }
      else {
        throw new RuntimeException(L.l("unknown argument: {0}", argName));
      }
    }

    if (fullClassName == null) {
      throw new RuntimeException(L.l("class name is not set"));
    }

    Class cls = null;

    try {
      cls = Class.forName(fullClassName);
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(L.l("class not found in the classpath: {0}", fullClassName), e);
    }

    String outputInterfaceName = cls.getSimpleName() + "Interface";

    generateInterface(cls, outputInterfaceName, isRecursive);
  }

  private static Process launchProcess(String className, String classPath, boolean isRecursive)
    throws IOException
  {
    String systemClassPath = System.getProperty("java.class.path");

    ProcessBuilder builder = new ProcessBuilder(
        "java",
        "-cp",
        systemClassPath + File.pathSeparatorChar + classPath,
        GenerateIfaceCommand.class.getCanonicalName(),
        "-c",
        className
    );

    builder.inheritIO();

    Process process = builder.start();

    return process;
  }

  @Override
  protected void initBootOptions()
  {
    addValueOption("classpath", "NAME", "class path for the target class (required)").tiny("cp");
    addFlagOption("recursive", "generate methods from current class and all ancestors").tiny("r");

    super.initBootOptions();
  }

  @Override
  public String getDescription()
  {
    return "generates async .java service interface for a blocking-oriented class";
  }

  @Override
  public int getTailArgsMinCount()
  {
    return 1;
  }

  @Override
  protected ExitCode doCommandImpl(ArgsCli args)
  {
    String classpath = args.getArg("classpath");

    if (classpath == null) {
      throw new CommandArgumentException(L.l("--classpath argument is required"));
    }

    boolean isRecursive = args.getArgFlag("recursive");

    ArrayList<String> classList = args.getTailArgs();

    for (String fullClassName : classList) {
      Process process = null;
      boolean result = false;

      try {
        process = launchProcess(fullClassName, classpath, isRecursive);

        result = process.waitFor(5000, TimeUnit.MILLISECONDS);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      finally {
        if (process != null && process.isAlive()) {
          process.destroy();
        }
      }
    }

    return ExitCode.OK;
  }

  private static void generateInterface(Class cls,
                                        String outputInterfaceName,
                                        boolean isRecursive)
  {
    LinkedHashMap<String,Method> methodMap = new LinkedHashMap<String,Method>();

    getMethods(methodMap, cls, isRecursive);

    PrintStream os = null;

    try {
      File file = new File(outputInterfaceName + ".java");

      try {
        if (! file.createNewFile()) {
          throw new RuntimeException(L.l("file already exists: {0}", file.getCanonicalPath()));
        }
      }
      catch (IOException e) {
        throw new RuntimeException(L.l("cannot write to file: {0}", file.getCanonicalPath()));
      }

      os = new PrintStream(file);

      os.println("/* Generated by Baratine on " + new Date() + "*/");

      if (cls.getPackage() != null) {
        os.println("package " + cls.getPackage().getName() + ";");
        os.println();
      }

      printImports(cls, methodMap, os);

      os.println();
      os.print("public interface " + outputInterfaceName);

      TypeVariable []typeVars = cls.getTypeParameters();

      if (typeVars.length > 0) {
        os.print("<");

        for (int i = 0; i < typeVars.length; i++) {
          if (i != 0) {
            os.print(",");
          }

          os.print(typeVars[i]);
        }

        os.print(">");
      }

      os.println();

      os.println("{");
      printMethods(methodMap, os, outputInterfaceName);
      os.println("}");

      os.println();

      System.out.println(L.l("generated {0}", file.getCanonicalPath()));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      if (os != null) {
        os.close();
      }
    }
  }

  private static void getMethods(LinkedHashMap<String,Method> map,
                                 Class cls,
                                 boolean isRecursive)
  {
    LinkedHashMap<String,Method> currentMap = new LinkedHashMap<String,Method>();

    Method []methods = cls.getDeclaredMethods();
    StringBuilder sb = new StringBuilder();

    for (Method m : methods) {
      int modifiers = m.getModifiers();

      if (Modifier.isStatic(modifiers)) {
      }
      else if (Modifier.isPublic(modifiers)) {
        methodToString(sb, m, false);

        currentMap.put(sb.toString(), m);

        sb.setLength(0);
      }
    }

    for (Map.Entry<String,Method> entry : currentMap.entrySet()) {
      map.putIfAbsent(entry.getKey(), entry.getValue());
    }

    Class parent = cls.getSuperclass();

    if (isRecursive && parent != null && parent != Object.class) {
      getMethods(map, parent, true);
    }
  }

  private static void methodToString(StringBuilder sb,
                                     Method method,
                                     boolean isUseReturnType)
  {
    if (isUseReturnType) {
      TypeVariable []typeVars = method.getTypeParameters();

      if (typeVars.length > 0) {
        sb.append("<");

        for (int i = 0; i < typeVars.length; i++) {
          if (i != 0) {
            sb.append(",");
          }

          sb.append(typeVars[i]);
        }

        sb.append(">");
        sb.append(" ");
      }

      sb.append("void " + method.getName() + "(");
    }

    Parameter []parameters = method.getParameters();
    for (int i = 0; i < parameters.length; i++ ) {
      if (i > 0) {
        sb.append(", ");
      }

      Parameter param = parameters[i];

      if (isUseReturnType && param.getParameterizedType() != null) {
        typeToString(sb, param.getParameterizedType(), false);
      }
      else {
        typeToString(sb, param.getType(), false);
      }

      sb.append(" " + param.getName());
    }

    if (isUseReturnType) {
      Type returnType = method.getGenericReturnType();

      if (returnType != void.class) {
        if (parameters.length > 0) {
          sb.append(", ");
        }

        sb.append("Result<");
        typeToString(sb, returnType, true);
        sb.append("> __result");
      }
    }

    sb.append(")");
  }

  private static void printImports(Class cls,
                                        HashMap<String,Method> methodMap,
                                        PrintStream os)
    throws IOException
  {
    HashMap<String,Class> map = new HashMap<String,Class>();

    Class parent = cls.getSuperclass();
    if (parent != null) {
      addImport(map, parent);
    }

    Class []interfaces = cls.getInterfaces();
    for (Class iface : interfaces) {
      addImport(map, iface);
    }

    for (Method method : methodMap.values()) {
      Class returnType = method.getReturnType();

      addImport(map, returnType);

      Class []paramTypes = method.getParameterTypes();
      for (Class paramType : paramTypes) {
        addImport(map, paramType);
      }
    }

    os.println("import io.baratine.service.*;");

    String []classNames = new String[map.size()];
    map.keySet().toArray(classNames);

    Arrays.sort(classNames);

    for (String name : classNames) {
      os.println("import " + name + ";");
    }
  }

  private static void addImport(HashMap<String,Class> map, Class cls)
  {
    while (cls.isArray()) {
      cls = cls.getComponentType();
    }

    if (! cls.isPrimitive()) {
      if (cls.getCanonicalName().equals("java.lang." + cls.getSimpleName())) {
      }
      else {
        map.put(cls.getCanonicalName(), cls);
      }
    }
  }

  private static void printMethods(HashMap<String,Method> methodMap,
                                   PrintStream os,
                                   String outputInterfaceName)
    throws IOException
  {
    StringBuilder sb = new StringBuilder();

    Class declaringClass = null;

    boolean isPrintUsage = true;

    for (Method m : methodMap.values()) {
      if (declaringClass == null || m.getDeclaringClass() != declaringClass) {
        declaringClass = m.getDeclaringClass();
        os.println("  // methods below are generated from class " + declaringClass.getCanonicalName());
        os.println();
      }

      if (isPrintUsage && m.getReturnType() != void.class) {
        isPrintUsage = false;

        os.println("  // example async non-blocking call:");
        os.println("  //   " + outputInterfaceName + " service = manager.lookup(serviceUrl).as(" + outputInterfaceName + ".class);");
        os.println("  //   service." + m.getName() + "(..., data -> System.out.println(\"result is \" + data));");
        os.println();
      }

      os.print("  ");
      methodToString(sb, m, true);

      os.print(sb);
      os.println(";");

      os.println();

      sb.setLength(0);
    }
  }

  private static void typeToString(StringBuilder sb,
                                   Class cls,
                                   boolean isAutobox)
  {
    int levels = 0;

    while (cls.isArray()) {
      levels++;

      cls = cls.getComponentType();
    }

    if (isAutobox && cls.isPrimitive()) {
      if (cls == int.class) {
        cls = Integer.class;
      }
      else if (cls == long.class) {
        cls = Long.class;
      }
      else if (cls == short.class) {
        cls = Short.class;
      }
      else if (cls == float.class) {
        cls = Float.class;
      }
      else if (cls == double.class) {
        cls = Double.class;
      }
      else if (cls == byte.class) {
        cls = Byte.class;
      }
      else if (cls == boolean.class) {
        cls = Boolean.class;
      }
      else if (cls == char.class) {
        cls = Character.class;
      }
      else {
        throw new RuntimeException(L.l("unknown primitive type: {0}", cls));
      }
    }

    sb.append(cls.getSimpleName());

    while (levels-- > 0) {
      sb.append("[]");
    }
  }

  private static void typeToString(StringBuilder sb,
                                   Type type,
                                   boolean isAutobox)
  {
    if (type instanceof Class) {
      typeToString(sb, (Class) type, isAutobox);

      return;
    }
    else {
      sb.append(type);
    }
  }
}
