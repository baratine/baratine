package example.user.impl;

public enum ServiceState
{
  UNINITIALIZED {
  },
  INITIALIZED {
  },
  DELETED {
  };

  public ServiceState toInitialized()
  {
    return INITIALIZED;
  }

  public ServiceState toDeleted()
  {
    return DELETED;
  }
}
