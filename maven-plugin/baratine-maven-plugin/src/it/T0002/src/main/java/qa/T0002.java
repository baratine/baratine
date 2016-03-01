package qa;

import io.baratine.core.*;

@Service("public:///T0002")
public class T0002
{
  public void test(Result<String> result) {
    result.complete("hello world!");
  }
}