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

package web;

import java.io.IOException;

import com.caucho.junit.Http;
import com.caucho.junit.HttpClient;
import com.caucho.junit.ServiceTest;
import com.caucho.junit.WebRunnerBaratine;
import io.baratine.service.Modify;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.vault.Asset;
import io.baratine.vault.Id;
import io.baratine.vault.IdAsset;
import io.baratine.vault.Vault;
import io.baratine.web.Get;
import io.baratine.web.Path;
import io.baratine.web.Query;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(WebRunnerBaratine.class)
@ServiceTest(QwebRunVaultTest.Q_fooVault.class)
@Http(port = 8086)
public class QwebRunVaultTest
{
  @Test
  public void test(HttpClient client) throws IOException
  {
    HttpClient.Response response = client.get("/create?v=Hello+World!").go();

    String x = response.body();

    Assert.assertEquals("\"DVS1aMAAR3I\"", x);

    response = client.get("/foo/DVS1aMAAR3I").go();

    x = response.body();

    Assert.assertEquals("{\"id\":\"DVS1aMAAR3I\",\"value\":\"Hello World!\"}",
                        x);

  }

  @Service
  public abstract static class Q_fooVault implements Vault<IdAsset,Q_fooBean>
  {
    @Get("/create")
    public abstract void createWithValue(@Query("v") String value,
                                         Result<IdAsset> result);

    @Get("/foo/{id}")
    public void get(@Path("id") Q_fooBean foo, Result<Q_fooBean> result)
    {
      foo.value(result.then());
    }
  }

  @Asset
  @Path("/foo")
  public static class Q_fooBean
  {
    @Id
    private IdAsset id;

    private String value;

    public Q_fooBean()
    {
    }

    @Modify
    public void createWithValue(String value, Result<IdAsset> result)
    {
      this.value = value;

      result.ok(id);
    }

    public void value(Result<Q_fooBean> result)
    {
      result.ok(this);
    }

    @Override
    public String toString()
    {
      return this.getClass().getSimpleName() + "[" + value + "]";
    }
  }
}
