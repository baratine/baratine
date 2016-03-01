package example.arena;

import java.util.logging.Logger;

import io.baratine.core.ResourceService;

@ResourceService("public:///event")
public class EventService extends Event
{
  private static final Logger log 
    = Logger.getLogger(EventService.class.getName());
}
