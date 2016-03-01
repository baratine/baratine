package example;

import io.baratine.core.Result;

/**
 * Sync interfaces are used to integrated with existing blocking
 * clients or for QA. They should normally be avoided inside Baratine
 * services because blocking a Baratine service will block processing
 * of all messages.
 */
public interface HelloSync extends Hello
{
  String hello(String arg);
}
