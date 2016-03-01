package qa;

import io.baratine.core.*;

@Service("pod://bar/bar-service")
public class BarImpl
{
  public void test(Result<String> result) {
    result.complete("hello world!");
  }
}