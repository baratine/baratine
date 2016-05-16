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

import javax.inject.Inject;

import com.caucho.junit.RunnerBaratine;
import com.caucho.junit.ServiceTest;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RunnerBaratine.class)
@ServiceTest(QjunitServiceSet.Q_fooServiceImpl.class)
@ServiceTest(QjunitServiceSet.Q_barServiceImpl.class)
public class QjunitServiceSet
{
  @Inject
  @Service("/foo")
  private Q_service _foo;

  @Inject
  @Service("/bar")
  private Q_service _bar;

  @Test
  public void test()
  {
    Assert.assertEquals("Hello ServiceRefLocal[/foo]", _foo.test());

    Assert.assertEquals("Hello ServiceRefLocal[/bar]", _bar.test());
  }

  @Test
  public void test(@Service("/foo") Q_service foo,
                   @Service("/bar") Q_service bar)
  {
    Assert.assertEquals("Hello ServiceRefLocal[/foo]", foo.test());

    Assert.assertEquals("Hello ServiceRefLocal[/bar]", bar.test());
  }

  @Test
  public void testFoo()
  {
    Assert.assertEquals("Hello ServiceRefLocal[/foo]", _foo.test());
  }

  @Test
  public void testBar()
  {
    Assert.assertEquals("Hello ServiceRefLocal[/bar]", _bar.test());
  }

  public interface Q_service
  {
    String test();
  }

  @Service("/foo")
  public static class Q_fooServiceImpl
  {
    public void test(Result<String> result)
    {
      result.ok("Hello " + ServiceRef.current());
    }
  }

  @Service("/bar")
  public static class Q_barServiceImpl
  {
    public void test(Result<String> result)
    {
      result.ok("Hello " + ServiceRef.current());
    }
  }
}
