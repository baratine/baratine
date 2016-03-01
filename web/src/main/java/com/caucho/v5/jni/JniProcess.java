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

package com.caucho.v5.jni;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.jni.JniUtil.JniLoad;
import com.caucho.v5.util.HomeUtil;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.ModulePrivate;

/**
 * bootstrap class allowing for setuid.
 */
@ModulePrivate
public class JniProcess extends Process
{
  private static final Logger log = Logger.getLogger(JniProcess.class.getName());
  private static final L10N L = new L10N(JniProcess.class);

  private static final JniTroubleshoot _jniTroubleshoot;
  
  private int _stdoutFd = -1;
  private int _pid = -1;
  private int _status = -1;

  private ReadBuffer _is;

  public JniProcess()
  {
  }

  private JniProcess(ArrayList<String> args,
                     HashMap<String,String> env,
                     String chroot,
                     String pwd,
                     String user,
                     String group)
  {
    String []argv = new String[args.size()];
    args.toArray(argv);

    String []envp = new String[env.size()];

    int i = 0;
    for (Map.Entry<String,String> entry : env.entrySet()) {
      envp[i++] = entry.getKey() + '=' + entry.getValue();
    }

    int fdMax = setFdMax();

    if (HomeUtil.isUnix() && fdMax <= 0) {
      System.out.println(L.l("process file descriptors: {0}", fdMax));
    }

    if (! exec(argv, envp, chroot, pwd, user, group)) {
      throw new IllegalStateException(L.l("exec failed while launching {0} ", args));
    }

    int stdoutFd = _stdoutFd;
    _stdoutFd = -1;

    try {
      StreamImpl stream;
      
      stream = new JniFileStream(stdoutFd, true, false);
      
      _is = new ReadBuffer(stream);
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
  }

  public JniProcess create(ArrayList<String> args,
                           HashMap<String,String> env,
                           String chroot,
                           String pwd,
                           String user,
                           String group)
  {
    _jniTroubleshoot.checkIsValid();

    assert(isNativeBootAvailable());

    return new JniProcess(args, env, chroot, pwd, user, group);
  }

  public boolean isEnabled()
  {
    return _jniTroubleshoot.isEnabled() && isNative();
  }
  
  private boolean isNative()
  {
    try {
      return isNativeBootAvailable();
    } catch (UnsatisfiedLinkError e) {
      log.finer(e.toString());
      
      return false;
    }
  }
  
  public String getTroubleshootMessage()
  {
    return _jniTroubleshoot.getMessage();
  }
  
  public OutputStream getOutputStream()
  {
    return new NullOutputStream();
  }

  public InputStream getInputStream()
  {
    return _is;
  }

  public InputStream getErrorStream()
  {
    return getInputStream();
  }

  public int getPid()
  {
    return _pid;
  }

  public void chown(String path, String user, String group)
  {
    _jniTroubleshoot.checkIsValid();
    
    byte []name = path.getBytes();
    int len = name.length;

    nativeChown(name, len, user, group);
  }

  @Override
  public int waitFor()
  {
    int pid = _pid;
    _pid = 0;
    
    if (pid > 0) {
      _status = waitpid(pid, true);
    }

    return _status;
  }

  @Override
  public int exitValue()
  {
    if (_status >= 0)
      return _status;

    if (_pid > 0) {
      int result = waitpid(_pid, false);

      if (result < 0)
        throw new IllegalThreadStateException("Pid " + _pid + " not yet closed");
      _pid = 0;
      _status = result;
    }

    return _status;
  }

  public void destroy()
  {
  }

  static class NullOutputStream extends OutputStream {
    public void write(int ch)
    {
    }
    
    public void flush()
    {
    }
    
    public void close()
    {
    }
  }
  
  public static native boolean isNativeBootAvailable();
  
  public native boolean clearSaveOnExec();

  public native static int getFdMax();
  public native static int setFdMax();
  
  private native boolean exec(String []argv,
                              String []envp,
                              String chroot,
                              String pwd,
                              String user,
                              String group);
  
  private native int nativeChown(byte []name, int length,
                                  String user, String group);
  
  private native int waitpid(int pid, boolean isBlock);

  static {
    _jniTroubleshoot
    = JniUtil.load(JniProcess.class,
                   new JniLoad() { 
                     public void load(String path) { System.load(path); }},
                   "baratine");
  }
}
