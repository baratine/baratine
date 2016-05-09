package plain;

import static org.junit.Assert.assertEquals;

import com.caucho.junit.RunnerBaratine;
import com.caucho.junit.ServiceTest;
import com.caucho.junit.State;
import io.baratine.pipe.Message;
import io.baratine.pipe.PipeIn;
import io.baratine.pipe.PipesSync;
import io.baratine.service.Service;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RunnerBaratine.class)
@ServiceTest(QpipeTest.Q_subString.class)
public class QpipeTest
{
  @Test
  public void test(@Service("pipe:///test") PipesSync<Message<String>> pipes)
  {
    pipes.send(Message.newMessage("hello"));

    State.sleep(10);

    assertEquals("\nonMessage(hello)", State.state());
  }

  @Service
  public static class Q_subString
  {
    @PipeIn("pipe:///test")
    private void onMessage(String msg)
    {
      State.add("\nonMessage(" + msg + ")");
    }
  }
}
