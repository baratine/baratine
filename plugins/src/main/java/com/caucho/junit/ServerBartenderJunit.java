package com.caucho.junit;

import com.caucho.v5.bartender.ClusterBartender;
import com.caucho.v5.bartender.RootBartender;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.ServerBartenderState;

public class ServerBartenderJunit extends ServerBartender
{
  public ServerBartenderJunit(String address, int port)
  {
    super(address, port);
  }

  @Override
  public String getDisplayName()
  {
    return "test-junit";
  }

  @Override
  public ClusterBartender getCluster()
  {
    return new ClusterBartenderTest("test-junit");
  }

  @Override
  public ServerBartenderState getState()
  {
    return null;
  }

  class ClusterBartenderTest extends ClusterBartender
  {
    public ClusterBartenderTest(String id)
    {
      super(id);
    }

    @Override
    public RootBartender getRoot()
    {
      return null;
    }

    @Override
    public Iterable<? extends ServerBartender> getServers()
    {
      return null;
    }
  }
}

