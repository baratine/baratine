package com.caucho.v5.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

@FunctionalInterface
public interface SqlFunction<R> extends Function<Connection,R>
{
  R applyException(Connection t) throws SQLException;

  default R apply(Connection t)
  {
    try {
      return applyException(t);
    }
    catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  default void close()
  {
  }
}
