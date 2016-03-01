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

package com.caucho.v5.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.BasePlugin;

/**
 * gradle plugin
 */
public class GradleBaratine implements Plugin<Project>
{
  @Override
  public void apply(Project project)
  {
    //project.getPlugins().apply(JavaPlugin.class);
    project.getPlugins().apply("java");
    project.getPlugins().apply("application");
    project.getExtensions().create("baratine", GradleBaratineExtension.class);
    
    /*
    if (! project.getExtensions().getExtraProperties().has("mainClassName")) {
      project.getExtensions().getExtraProperties().set("mainClassName", null);
    }
    */
    
    bootJarTask(project);
  }
  
  private void bootJarTask(Project project)
  {
    project.getConvention().getPlugins().put("baratine", new BaratineConvention(project));
    
    GradlePackageTask task;
    
    task = project.getTasks().create("jarBoot", 
                                     GradlePackageTask.class);
    
    task.setDescription("Assembles an executable boot jar containing the application and any dependencies");
    task.setGroup("build");
    
    project.getTasks().getByName(BasePlugin.ASSEMBLE_TASK_NAME).dependsOn(task);
    
    Task jarTask = project.getTasks().getByName("jar");
    
    task.dependsOn(jarTask);
  }
  
  public static class BaratineConvention
  {
    private Project _project;
    
    private String _applicationName;
    private String _mainClassName;
    
    BaratineConvention(Project project)
    {
      _project = project;
    }
    
    public void setApplicationName(String name)
    {
      _applicationName = name;
    }
    
    public String getApplicationName()
    {
      return _applicationName;
    }
    
    public void setMainClassName(String className)
    {
      _mainClassName = className;
    }
    
    public String getMainClassName()
    {
      return _mainClassName;
    }
  }
}
