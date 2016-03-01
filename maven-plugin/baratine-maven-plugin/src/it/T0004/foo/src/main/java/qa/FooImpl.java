package qa;

import javax.inject.*;
import io.baratine.core.*;

@Service("public://foo/foo-service")
public class FooImpl
{
  @Inject @Lookup("pod://bar/bar-service")
  private Bar _bar;

  public void test(Result<String> result) {
    _bar.test(result);
  }
}