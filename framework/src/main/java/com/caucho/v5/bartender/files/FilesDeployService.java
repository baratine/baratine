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

package com.caucho.v5.bartender.files;

import com.caucho.v5.io.StreamSource;

import io.baratine.files.Status;
import io.baratine.service.Result;

/**
 * Admin deployment to the filesystem.
 */
public interface FilesDeployService
{
  public static final String PATH = "/bartender-files";

  String []list(String path);
  void list(String path, Result<String[]> result);

  Status getStatus(String path);
  void getStatus(String path, Result<Status> result);

  boolean putFile(String path, StreamSource data);
  void putFile(String path, StreamSource data, Result<Boolean> result);

  boolean removeFile(String path);
  void removeFile(String path, Result<Boolean> result);

  boolean removeAll(String path);
  void removeAll(String path, Result<Boolean> result);

  boolean copyFile(String src, String dest);
  void copyFile(String src, String dest, Result<Boolean> result);

  boolean copyAll(String src, String dest);
  void copyAll(String src, String dest, Result<Boolean> result);

  boolean moveFile(String src, String dest);
  void moveFile(String src, String dest, Result<Boolean> result);

  boolean moveAll(String src, String dest);
  void moveAll(String src, String dest, Result<Boolean> result);

  StreamSource getFile(String path);
  void getFile(String path, Result<StreamSource> result);
}
