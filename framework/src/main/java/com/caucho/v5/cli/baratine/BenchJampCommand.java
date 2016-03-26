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
 * @author Alex Rojkov
 */

package com.caucho.v5.cli.baratine;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.cli.daemon.ArgsDaemon;
import com.caucho.v5.cli.server.ServerCommandBase;
import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;

import io.baratine.service.MethodRef;
import io.baratine.service.Result;
import io.baratine.service.ServiceClient;
import io.baratine.service.ServiceRef;

/**
 * Command to benchmark a jamp service.
 */
public class BenchJampCommand extends ServerCommandBase<ArgsCli>
{
  private static final L10N L = new L10N(BenchJampCommand.class);

  @Override
  public String getDescription()
  {
    return "jamp benchmarking";
  }

  @Override
  public String getUsageTailArgs()
  {
    return " address method arg...";
  }

  @Override
  public int getTailArgsMinCount()
  {
    return 1;
  }

  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();

    addIntValueOption("request-count", "count", "number of requests").tiny("n");
    addIntValueOption("connections", "count", "number of socket connections").tiny("c");
    addIntValueOption("threads", "count", "number of threads per connections").tiny("th");
    addIntValueOption("async-batch", "count", "async messages in a batch").tiny("a");
    addIntValueOption("warmup", "count", "warmup count").tiny("w");
    addValueOption("host", "host", "host address").tiny("h");
    addValueOption("pod", "pod", "pod name");
    addValueOption("url", "url", "url").tiny("u");
  }

  @Override
  public ExitCode doCommandImpl(ArgsCli args) // , ConfigBoot config)
    throws CommandArgumentException
  {
    //ServerConfigBoot server = config.findServer(args);

    try {
      int port = args.config().get("server.port", int.class, 8080);
      // server.getPort();

      String host = args.getArg("host");

      if (host == null) {
        host = "localhost";
      }

      String pod = args.getArg("pod");

      if (pod == null)
        pod = "pod";

      String url = args.getArg("url");
      
      if (url == null) {
        url = "http://" + host + ":" + port + "/s/" + pod + "/";
      }

      String address = args.getTail(0);

      if (address == null) {
        throw new CommandArgumentException(L.l("bench-jamp needs an address"));
      }

      if (address.startsWith("/")) {
        address = "remote://" + address;
      }

      String methodName = args.getTail(1);

      if (methodName == null) {
        throw new CommandArgumentException(L.l("bench-jamp needs a method"));
      }
      
      ArrayList<String> tail = args.getTailArgs();
      
      Object []argList = new Object[tail.size() - 2];
      
      for (int i = 0; i < argList.length; i++) {
        argList[i] = tail.get(i + 2);
      }
      
      ProfileContext profile = new ProfileContext(args,
                                                  url, address, methodName, argList);

      profile.warmup();
      
      profile.benchmark();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandArgumentException(e);
    }

    return ExitCode.OK;
  }

  private class ProfileContext {
    private ArgsDaemon _args;
    private String _url;
    private String _address;
    private String _methodName;
    private Object []_methodArgs;

    private CountDownLatch _prepareSignal;
    private CountDownLatch _startSignal;
    private CountDownLatch _doneSignal;

    private Object []_helloClients;

    ProfileContext(ArgsDaemon args,
                   String url,
                   String address,
                   String methodName,
                   Object []methodArgs)
    {
      _args = args;

      _url = url;
      _address = address;
      _methodName = methodName;
      _methodArgs = methodArgs;
    }

    private void benchmark()
      throws Exception
    {
      int count = _args.getArgInt("request-count", 1);
      int conns = _args.getArgInt("connections", 1);
      int threads = _args.getArgInt("threads", 1);
      int asyncCount = _args.getArgInt("async-batch", 0);

      initClients(count, conns, threads, asyncCount);

      _prepareSignal.await();

      long startTime = System.currentTimeMillis();

      _startSignal.countDown();

      _doneSignal.await();

      long endTime = System.currentTimeMillis();

      long delta = endTime - startTime;
      double time = (double) delta / 1000.0;

      System.out.println("Time: " + delta);
      System.out.println("OPS: " + count / Math.max(time, 1e-6));
      System.out.println("  Connections: " + conns);
      System.out.println("  Threads: " + threads);
      System.out.println("  Count: " + count);

      if (asyncCount > 0) {
        System.out.println("  Async: " + asyncCount);
      }
    }

    private void warmup()
      throws Exception
    {
      int count = _args.getArgInt("warmup", 0);
      
      if (count <= 0) {
        return;
      }
      
      int conns = 1;
      int threads = 1;
      int asyncCount = 0;

      initClients(count, conns, threads, asyncCount);

      _prepareSignal.await();

      _startSignal.countDown();

      _doneSignal.await();
    }

    void initClients(int count,
                     int conns,
                     int threads,
                     int asyncCount)
       throws Exception
    {
      int clients = conns * threads;

      _prepareSignal = new CountDownLatch(clients);
      _startSignal = new CountDownLatch(1);
      _doneSignal = new CountDownLatch(clients);

      _helloClients = new Object[clients];

      int threadCount = Math.max(1, count / clients);

      for (int i = 0; i < conns; i++) {
        ServiceClient client = ServiceClient.newClient(_url).build();

        client.connect();

        ServiceRef serviceRef = client.service(_address);
        MethodRef methodRef = serviceRef.getMethod(_methodName);

        for (int j = 0; j < threads; j++) {
          int index = i * threads + j;

          if (asyncCount > 0) {
            _helloClients[index]
               = new ProfileAsync(this, client, methodRef, _methodArgs, threadCount, asyncCount);
          }
          else {
            _helloClients[index]
               = new ProfileTask(this, methodRef, _methodArgs, threadCount);
          }
        }
      }
    }

    private void waitForStart() throws InterruptedException
    {
      _prepareSignal.countDown();

      _startSignal.await();
    }

    private void onComplete()
    {
      _doneSignal.countDown();
    }
  }

  public interface HelloAsyncApi {
    void start();
  }

  private class ProfileTask implements Runnable {
    private ProfileContext _context;
    private MethodRef _methodRef;
    private Object []_args;
    private int _count;

    ProfileTask(ProfileContext context,
                MethodRef methodRef,
                Object []args,
                int count)
    {
      _context = context;
      _methodRef = methodRef;
      _args = args;
      _count = count;

      new Thread(this).start();
    }

    @Override
    public void run()
    {
      try {
        int count = _count ;
        MethodRefAmp methodRef = (MethodRefAmp)_methodRef;
        Object []args = _args;
        
        ServiceManagerAmp manager = methodRef.serviceRef().manager();

        _context.waitForStart();

        for (int i = 0; i < count; i++) {
          manager.run(60, TimeUnit.SECONDS, r-> { _methodRef.query(r, args); });
        }
      } catch (Throwable e) {
        failed(e);
      } finally {
        complete();
      }
    }

    public void failed(Throwable e)
    {
      e.printStackTrace();
    }

    public void complete()
    {
      _context.onComplete();
    }
  }

  public class ProfileAsync implements Result<Object>
  {
    private ProfileContext _context;
    private MethodRef _methodRef;
    private Object []_args;
    private final int _count;
    private final int _batchCount;

    private int _i;
    private boolean _isDone;

    private ProfileAsync() { _count = 0; _batchCount = 0;}

    ProfileAsync(ProfileContext context,
                 ServiceClient client,
                 MethodRef methodRef,
                 Object []args,
                 int count,
                 int batchCount)
    {
      _context = context;
      _methodRef = methodRef;
      _args = args;
      _count = count;
      _batchCount = Math.max(1, batchCount);

      client.newService(this).as(HelloAsyncApi.class).start();
    }

    public void start()
    {
      try {
        _context.waitForStart();

        for (int i = 0; i < _batchCount; i++) {
          _methodRef.query(this, _args);
        }
      } catch (Throwable e) {
        e.printStackTrace();
        fail(e);
      }
    }

    @Override
    public void fail(Throwable e)
    {
      e.printStackTrace();

      _context.onComplete();
    }

    @Override
    public void ok(Object value)
    {
      int i = _i++;

      if (_count <= i) {
        if (! _isDone) {
          _isDone = true;
          _context.onComplete();
        }

        return;
      }

      _methodRef.query(this, _args);
    }
    
    @Override
    public void handle(Object value, Throwable exn)
    {
      if (exn != null) {
        fail(exn);
      }
      else {
        ok(value);
      }
    }
  }
}
