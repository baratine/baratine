package io.baratine.web;

public interface WebSocketClose
{
  int code();

  public static enum WebSocketCloses implements WebSocketClose
  {
    CANNOT_ACCEPT {
      @Override
      public int code() { return 1003; }
    },
    CLOSED_ABNORMALLY {
      @Override
      public int code() { return 1006; }
    },
    GOING_AWAY {
      @Override
      public int code() { return 1001; }
    },
    NO_EXTENSION {
      @Override
      public int code() { return 1010; }
    },
    NO_STATUS_CODE {
      @Override
      public int code() { return 1005; }
    },
    NORMAL_CLOSURE {
      @Override
      public int code() { return 1000; }
    },
    NOT_CONSISTENT {
      @Override
      public int code() { return 1007; }
    },
    PROTOCOL_ERROR {
      @Override
      public int code() { return 1002; }
    },
    RESERVED {
      @Override
      public int code() { return 1004; }
    },
    SERVICE_RESTART {
      @Override
      public int code() { return 1012; }
    },
    TLS_HANDSHAKE_FAILURE {
      @Override
      public int code() { return 1015; }
    },
    TOO_BIG {
      @Override
      public int code() { return 1009; }
    },
    TRY_AGAIN_LATER {
      @Override
      public int code() { return 1013; }
    },
    UNEXPECTED_CONDITION {
      @Override
      public int code() { return 1011; }
    },
    VIOLATED_POLICY {
      @Override
      public int code() { return 1008; }
    },
    ;

    public static WebSocketClose of(int code)
    {
      switch (code) {
      case 1000:
        return NORMAL_CLOSURE;
        
      case 1001:
        return GOING_AWAY;
        
      case 1002:
        return PROTOCOL_ERROR;
        
      case 1003:
        return CANNOT_ACCEPT;
        
      case 1004:
        return RESERVED;
        
      case 1005:
        return NO_STATUS_CODE;
        
      case 1006:
        return CLOSED_ABNORMALLY;
        
      case 1007:
        return NOT_CONSISTENT;
        
      case 1008:
        return VIOLATED_POLICY;
        
      case 1009:
        return TOO_BIG;
        
      case 1010:
        return NO_EXTENSION;
        
      case 1011:
        return UNEXPECTED_CONDITION;
        
      case 1012:
        return SERVICE_RESTART;
        
      case 1013:
        return TRY_AGAIN_LATER;
        
      case 1015:
        return TLS_HANDSHAKE_FAILURE;
        
      default:
        System.out.println("UNKNOWN-CODE: " + code);
        return UNEXPECTED_CONDITION;
      }
    }
  }
}
