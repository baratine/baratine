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

package plain;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.caucho.junit.RunnerBaratine;
import com.caucho.junit.ServiceTest;
import io.baratine.service.Modify;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.service.Service;
import io.baratine.vault.Asset;
import io.baratine.vault.Id;
import io.baratine.vault.IdAsset;
import io.baratine.vault.Vault;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RunnerBaratine.class)
@ServiceTest(QjunitServiceVault.Q_entityVault.class)
public class QjunitServiceVault
{
  @Inject
  @Service
  private Q_entityVault _vault;

  @Test
  public void test()
  {
    ResultFuture<IdAsset> id = new ResultFuture<>();

    _vault.create("Hello World!", id);

    Assert.assertEquals("DVS1aMAAR3I", id.get(1, TimeUnit.SECONDS).toString());

    ResultFuture<Q_entity> e = new ResultFuture<>();

    _vault.findByValue("Hello World!", e);

    Assert.assertEquals("DVS1aMAAR3I: Hello World!",
                        e.get(1, TimeUnit.SECONDS).asString());
  }

  @Test
  public void test(@Service Q_entityVault vault)
  {
    ResultFuture<IdAsset> id = new ResultFuture<>();

    vault.create("Hello World!", id);

    Assert.assertEquals("DVS1aMAAR3I", id.get(1, TimeUnit.SECONDS).toString());

    ResultFuture<Q_entity> e = new ResultFuture<>();

    vault.findByValue("Hello World!", e);

    Assert.assertEquals("DVS1aMAAR3I: Hello World!",
                        e.get(1, TimeUnit.SECONDS).asString());
  }

  @Service
  public interface Q_entityVault extends Vault<IdAsset,Q_entity>
  {
    void create(String value, Result<IdAsset> id);

    void findByValue(String value, Result<Q_entity> result);
  }

  @Asset
  public static class Q_entity
  {
    @Id
    private IdAsset id;
    private String value;

    @Modify
    public void create(String value, Result<IdAsset> id)
    {
      this.value = value;
      id.ok(this.id);
    }

    public String asString()
    {
      return id + ": " + value;
    }
  }
}
