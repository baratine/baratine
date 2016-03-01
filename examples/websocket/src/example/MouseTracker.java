package example;

import io.baratine.core.Service;

@Service("public:///mouse-tracker")
public class MouseTracker
{
  public MousePointer track(MousePointer pointer)
  {
    return pointer;
  }
}
