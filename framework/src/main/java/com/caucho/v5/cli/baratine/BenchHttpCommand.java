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

import io.baratine.service.ResultFuture;

import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.cli.spi.CommandBase;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.http.protocol.ClientHttp1;
import com.caucho.v5.http.protocol2.ClientHttp2;
import com.caucho.v5.http.protocol2.InputStreamClient;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.L10N;

/**
 * Command to benchmark a http server.
 */
public class BenchHttpCommand extends CommandBase<ArgsCli>
{
  private static final L10N L = new L10N(BenchHttpCommand.class);

  @Override
  public String getDescription()
  {
    return "http benchmarking";
  }

  @Override
  public String getUsageTailArgs()
  {
    return " url";
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

    addIntValueOption("request", "count", "number of requests").tiny("n");
    addIntValueOption("keepalive", "count", "number of keepalive").tiny("k");
    addIntValueOption("clients", "count", "number of clients").tiny("c");

    addValueOption("title", "title", "title for the report");

    addFlagOption("http-2", "http/2.0 requests").tiny("h2");
  }

  @Override
  protected ExitCode doCommandImpl(ArgsCli args)
    throws CommandArgumentException
  {
    if (args.getArgFlag("http-2")) {
      return doHttp20(args);
    }
    else {
      return doHttp11(args);
    }
  }

  private ExitCode doHttp11(ArgsCli args)
    throws CommandArgumentException
  {
    String url = args.getTail(0);

    if (url == null) {
      throw new CommandArgumentException(L.l("http-bench needs a URL"));
    }

    int c = args.getArgInt("clients", 1);

    ClientBench []clients = new ClientBench[c];

    for (int i = 0; i < clients.length; i++) {
      clients[i] = new ClientBenchHttp11(args);
    }

    for (int i = 0; i < clients.length; i++) {
      ThreadPool.current().execute(clients[i]);
    }

    for (int i = 0; i < clients.length; i++) {
      clients[i].waitForComplete();
    }

    print(args, clients);

    return ExitCode.OK;
  }

  private ExitCode doHttp20(ArgsCli args)
    throws CommandArgumentException
  {
    String url = args.getTail(0);

    if (url == null) {
      throw new CommandArgumentException(L.l("http-bench needs a URL"));
    }

    int c = args.getArgInt("clients", 1);

    ClientBench []clients = new ClientBench[c];

    for (int i = 0; i < clients.length; i++) {
      clients[i] = new ClientBenchHttp20(args);
    }

    for (int i = 0; i < clients.length; i++) {
      ThreadPool.current().execute(clients[i]);
    }

    for (int i = 0; i < clients.length; i++) {
      clients[i].waitForComplete();
    }

    print(args, clients);

    return ExitCode.OK;
  }

  private void print(ArgsCli args,
                     ClientBench []clients)
  {
    long time = 0;
    String proto = null;
    long countRequest = 0;
    long countConn = 0;
    long keepalive = 0;
    long count0 = 0;
    long count200 = 0;
    long count404 = 0;
    long countOther = 0;
    long countException = 0;
    long length = 0;

    for (ClientBench conn : clients) {
      time = Math.max(time, conn.getTime());

      if (proto == null) {
        proto = conn.getProtocol();
      }

      countRequest += conn.getCountRequest();
      countConn += conn.getCountConnection();

      keepalive += conn.getKeepalive();

      count0 += conn.getCount0();
      count200 += conn.getCount200();
      count404 += conn.getCount404();
      countOther += conn.getCountOther();

      countException += conn.getCountException();

      length += conn.getLength();
    }

    System.out.println("{");

    String title = args.getArg("title");

    if (title != null) {
      System.out.println("\"title\" : " + title);
    }

    System.out.println("\"proto\" : " + proto);
    System.out.println("\"time\" : " + time);
    System.out.println("\"requests\" : " + countRequest);
    System.out.println("\"connections\" : " + countConn);
    System.out.println("\"clients\" : " + clients.length);

    if (count0 > 0) {
      System.out.println("\"status-0\" : " + count0);
    }

    System.out.println("\"status-200\" : " + count200);

    if (count404 > 0) {
      System.out.println("\"status-404\" : " + count404);
    }

    if (countOther > 0) {
      System.out.println("\"status-other\" : " + countOther);
    }

    if (countException > 0) {
      System.out.println("\"exception\" : " + countException);
    }

    System.out.println("\"len/req\" : " + (length / Math.max(countRequest, 1)));
    System.out.println("\"req/conn\" : " + ((double) countRequest / Math.max(countConn, 1)));

    System.out.println("\"ops\" : " + (1000.0 * countRequest / Math.max(time, 1)));
    System.out.println("}");
  }

  abstract private class ClientBench implements Runnable
  {
    abstract void waitForComplete();

    abstract public String getProtocol();

    abstract public long getTime();

    abstract public long getCountConnection();

    abstract public long getCountRequest();

    abstract public long getKeepalive();

    abstract public long getCount0();

    abstract public long getCount200();

    abstract public long getCount404();

    abstract public long getCountOther();

    abstract public long getCountException();

    abstract public long getLength();
  }

  private class ClientBenchHttp11 extends ClientBench
  {
    private ArgsCli _args;
    private ResultFuture<Boolean> _future = new ResultFuture<>();
    private String _protocol;
    private int _keepalive;
    private int _count0;
    private int _count200;
    private int _count404;
    private int _countOther;
    private long _time;
    private int _countRequest;
    private long _length;
    private int _countConn;
    private int _countException;

    ClientBenchHttp11(ArgsCli args)
    {
      _args = args;
    }

    @Override
    public long getTime()
    {
      return _time;
    }

    @Override
    public long getCountRequest()
    {
      return _countRequest;
    }

    @Override
    public long getCountConnection()
    {
      return _countConn;
    }

    @Override
    public long getKeepalive()
    {
      return _keepalive;
    }

    @Override
    public long getCount0()
    {
      return _count0;
    }

    @Override
    public long getCount200()
    {
      return _count200;
    }

    @Override
    public long getCount404()
    {
      return _count404;
    }

    @Override
    public long getCountOther()
    {
      return _countOther;
    }

    @Override
    public long getCountException()
    {
      return _countException;
    }

    @Override
    public long getLength()
    {
      return _length;
    }

    public String getProtocol()
    {
      if (_protocol != null) {
        return _protocol;
      }
      else {
        return "http/1.1";
      }
    }

    @Override
    public void run()
    {
      try {
        String url = _args.getTail(0);

        if (url == null) {
          throw new CommandArgumentException(L.l("http-bench needs a URL"));
        }

        int n = _args.getArgInt("request", 1);
        int k = _args.getArgInt("keepalive", Integer.MAX_VALUE / 2);
        int c = _args.getArgInt("connections", 1);

        n = Math.max(1, n / c);

        URI uri = new URI(url);

        String address = uri.getScheme() + "://" + uri.getAuthority();

        TempBuffer tBuf = TempBuffer.create();
        byte []buffer = tBuf.buffer();

        long startTime = System.currentTimeMillis();

        int countRequest = 0;
        _countConn = 0;
        String proto = null;
        _count0 = 0;
        _count200 = 0;
        _count404 = 0;
        _countOther = 0;
        long length = 0;

        while (countRequest < n) {
          try (ClientHttp1 client = new ClientHttp1(address)) {
            _countConn++;

            if (proto == null) {
              proto = client.getProtocol();
            }

            String path = uri.getPath();

            if (uri.getQuery() != null) {
              path = path + '?' + uri.getQuery();
            }

            for (int i = 0; countRequest < n && i < k; i++) {
              int status = -1;

              try (InputStreamClient is = client.get(path)) {
                status = is.getStatus();

                switch (status) {
                case 0:
                  _count0++;
                  break;

                case 200:
                  _count200++;
                  break;

                case 404:
                  _count404++;
                  break;

                default:
                  _countOther++;
                  break;
                }

                int sublen;

                while ((sublen = is.read(buffer, 0, buffer.length)) > 0) {
                  _length += sublen;
                  if (_args.isVerbose()) {
                    System.out.print(new String(buffer, 0, sublen));
                  }
                }
              } finally {
                countRequest++;
              }
            }
          } catch (Exception e) {
            System.err.println("Exn: " + e);
            _countException++;
          }
        }

        _countRequest = countRequest;

        long endTime = System.currentTimeMillis();

        _time = endTime - startTime;
      } catch (RuntimeException e) {
        _future.fail(e);

        throw e;
      } catch (Exception e) {
        _future.fail(e);

        throw new CommandArgumentException(e);
      } finally {
        _future.ok(true);
      }
    }

    @Override
    void waitForComplete()
    {
      _future.get(1, TimeUnit.HOURS);
    }
  }

  private class ClientBenchHttp20 extends ClientBench
  {
    private ArgsCli _args;
    private ResultFuture<Boolean> _future = new ResultFuture<>();
    private int _keepalive;
    private int _count0;
    private int _count200;
    private int _count404;
    private int _countOther;
    private long _time;
    private int _countRequest;
    private int _countException;
    private long _length;
    private int _countConn;
    private String _protocol;

    ClientBenchHttp20(ArgsCli args)
    {
      _args = args;
    }

    @Override
    public long getTime()
    {
      return _time;
    }

    @Override
    public long getCountRequest()
    {
      return _countRequest;
    }

    @Override
    public long getCountConnection()
    {
      return _countConn;
    }

    @Override
    public long getKeepalive()
    {
      return _keepalive;
    }

    @Override
    public long getCount0()
    {
      return _count0;
    }

    @Override
    public long getCount200()
    {
      return _count200;
    }

    @Override
    public long getCount404()
    {
      return _count404;
    }

    @Override
    public long getCountOther()
    {
      return _countOther;
    }

    @Override
    public long getCountException()
    {
      return _countException;
    }

    @Override
    public long getLength()
    {
      return _length;
    }

    public String getProtocol()
    {
      if (_protocol != null) {
        return _protocol;
      }
      else {
        return "http/2.0";
      }
    }

    @Override
    public void run()
    {
      try {
        String url = _args.getTail(0);

        if (url == null) {
          throw new CommandArgumentException(L.l("http-bench needs a URL"));
        }

        int n = _args.getArgInt("request", 1);
        int k = _args.getArgInt("keepalive", Integer.MAX_VALUE / 2);
        int c = _args.getArgInt("clients", 1);

        n = Math.max(1, n / c);

        URI uri = new URI(url);

        String address = uri.getScheme() + "://" + uri.getAuthority();

        TempBuffer tBuf = TempBuffer.create();
        byte []buffer = tBuf.buffer();

        long startTime = System.currentTimeMillis();

        int countRequest = 0;
        _countConn = 0;
        _count0 = 0;
        _count200 = 0;
        _count404 = 0;
        _countOther = 0;
        long length = 0;

        while (countRequest < n) {
          try (ClientHttp2 client = new ClientHttp2(address)) {
            _countConn++;

            if (_protocol == null) {
              _protocol = client.getProtocol();
            }

            String path = uri.getPath();

            if (uri.getQuery() != null) {
              path = path + '?' + uri.getQuery();
            }

            for (int i = 0; countRequest < n && i < k; i++) {
              int status = -1;

              try (InputStreamClient is = client.get(path)) {
                status = is.getStatus();

                switch (status) {
                case 0:
                  _count0++;
                  break;

                case 200:
                  _count200++;
                  break;

                case 404:
                  _count404++;
                  break;

                default:
                  _countOther++;
                  break;
                }

                int sublen;

                while ((sublen = is.read(buffer, 0, buffer.length)) > 0) {
                  _length += sublen;
                  if (_args.isVerbose()) {
                    System.out.print(new String(buffer, 0, sublen));
                  }
                }
              } finally {
                countRequest++;
              }
            }
          } catch (Exception e) {
            System.err.println("Exn: " + e);
            _countException++;
          }
        }

        _countRequest = countRequest;

        long endTime = System.currentTimeMillis();

        _time = endTime - startTime;
      } catch (RuntimeException e) {
        _future.fail(e);

        throw e;
      } catch (Exception e) {
        _future.fail(e);

        throw new CommandArgumentException(e);
      } finally {
        _future.ok(true);
      }
    }

    @Override
    void waitForComplete()
    {
      _future.get(1, TimeUnit.HOURS);
    }
  }
}
