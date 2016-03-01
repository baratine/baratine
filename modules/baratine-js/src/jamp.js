Jamp.unserialize = function (json)
{
  var array = JSON.parse(json);

  return Jamp.unserializeArray(array);
};

Jamp.unserializeArray = function (array)
{
  var type = array[0];

  switch (type) {
  case "reply":
    if (array.length < 5) {
      throw new Error("incomplete message for JAMP type: " + type);
    }

    var headers = array[1];
    var fromAddress = array[2];
    var queryId = array[3];
    var result = array[4];

    var msg = new Jamp.ReplyMessage(headers, fromAddress, queryId, result);

    return msg;

  case "error":
    if (array.length < 5) {
      throw new Error("incomplete message for JAMP type: " + type);
    }

    var headers = array[1];
    var toAddress = array[2];
    var queryId = array[3];
    var result = array[4];

    if (array.length > 5) {
      var resultArray = new Array();

      for (var i = 4; i < array.length; i++) {
        resultArray.push(array[i]);
      }

      result = resultArray;
    }

    var msg = new Jamp.ErrorMessage(headers, toAddress, queryId, result);

    return msg;

  case "query":
    if (array.length < 6) {
      throw new Error("incomplete message for JAMP type: " + type);
    }

    var headers = array[1];
    var fromAddress = array[2];
    var queryId = array[3];
    var toAddress = array[4];
    var methodName = array[5];

    var args = null;

    if (array.length > 6) {
      args = new Array();

      for (var i = 6; i < array.length; i++) {
        args.push(array[i]);
      }
    }

    var msg = new Jamp.QueryMessage(headers,
                                    fromAddress,
                                    queryId,
                                    toAddress,
                                    methodName,
                                    args);

    return msg;

  case "send":
    if (array.length < 4) {
      throw new Error("incomplete message for JAMP type: " + type);
    }

    var headers = array[1];
    var toAddress = array[2];
    var methodName = array[3];

    var parameters = null;

    if (array.length > 4) {
      parameters = new Array();

      for (var i = 4; i < array.length; i++) {
        parameters.push(array[i]);
      }
    }

    var msg = new Jamp.SendMessage(headers,
                                   toAddress,
                                   methodName,
                                   parameters);

    return msg;

  default:
    throw new Error("unknown JAMP type: " + type);
  }
};

Jamp.Message = function (headers)
{
  if (headers == null) {
    headers = {};
  }

  this.headers = headers;

};

Jamp.Message.prototype.serialize = function ()
{
  var array = new Array();

  this.serializeImpl(array);

  var json = JSON.stringify(array);

  return json;
};

Jamp.Message.prototype.serializeImpl;

Jamp.SendMessage = function (headers, address, method, parameters)
{
  Jamp.Message.call(this, headers);

  this.address = address;
  this.method = method;

  this.parameters = parameters;
};

Jamp.SendMessage.prototype = Object.create(Jamp.Message.prototype);

Jamp.SendMessage.prototype.serializeImpl = function (array)
{
  array.push("send");
  array.push(this.headers);
  array.push(this.address);
  array.push(this.method);

  if (this.parameters != null) {
    for (var i = 0; i < this.parameters.length; i++) {
      array.push(this.parameters[i]);
    }
  }
};

Jamp.QueryMessage = function (headers,
                              fromAddress,
                              queryId,
                              address,
                              method,
                              args)
{
  Jamp.Message.call(this, headers);

  this.fromAddress = fromAddress
  this.queryId = queryId;

  this.address = address;
  this.method = method;

  this.args;

  this.listenerAddresses;
  this.listeners;

  if (args !== undefined) {
    this.args = new Array();

    for (var i = 0; i < args.length; i++) {
      var arg = args[i];
      if (arg["___isListener"]) {
        this.args.push(this.addListener(arg, queryId));
      } else {
        this.args.push(arg);
      }
    }
  }

  if (fromAddress == null) {
    this.fromAddress = "me";
  }
};

Jamp.QueryMessage.prototype = Object.create(Jamp.Message.prototype);

Jamp.QueryMessage.prototype.serializeImpl = function (array)
{
  array.push("query");
  array.push(this.headers);
  array.push(this.fromAddress);
  array.push(this.queryId);
  array.push(this.address);
  array.push(this.method);

  if (this.args !== undefined) {
    for (var i = 0; i < this.args.length; i++) {
      array.push(this.args[i]);
    }
  }
};

Jamp.QueryMessage.prototype.addListener = function (listener, queryId)
{
  if (this.listeners === undefined) {
    this.listeners = new Array();
    this.listenerAddresses = new Array();
  }

  this.listeners.push(listener);

  var callbackAddress = "/callback-" + queryId;
  this.listenerAddresses.push(callbackAddress);

  return callbackAddress;
};

Jamp.ReplyMessage = function (headers, fromAddress, queryId, result)
{
  Jamp.Message.call(this, headers);

  this.fromAddress = fromAddress;
  this.queryId = queryId;

  this.result = result;
};

Jamp.ReplyMessage.prototype = Object.create(Jamp.Message.prototype);

Jamp.ReplyMessage.prototype.serializeImpl = function (array)
{
  array.push("reply");
  array.push(this.headers);
  array.push(this.fromAddress);
  array.push(this.queryId);
  array.push(this.result);
};

Jamp.ErrorMessage = function (headers, toAddress, queryId, result)
{
  Jamp.Message.call(this, headers);

  this.address = toAddress;
  this.queryId = queryId;

  this.result = result;
};

Jamp.ErrorMessage.prototype = Object.create(Jamp.Message.prototype);

Jamp.ErrorMessage.prototype.serializeImpl = function (array)
{
  array.push("error");
  array.push(this.headers);
  array.push(this.address);
  array.push(this.queryId);
  array.push(this.result);
};

function handlerMaker(obj)
{
  return {
    getOwnPropertyDescriptor: function (name)
    {
      var desc = Object.getOwnPropertyDescriptor(obj, name);
      if (desc !== undefined) {
        desc.configurable = true;
      }
      return desc;
    },
    getPropertyDescriptor: function (name)
    {
      var desc = Object.getPropertyDescriptor(obj, name);

      if (desc !== undefined) {
        desc.configurable = true;
      }
      return desc;
    },
    getOwnPropertyNames: function ()
    {
      return Object.getOwnPropertyNames(obj);
    },
    getPropertyNames: function ()
    {
      return Object.getPropertyNames(obj);
    },
    defineProperty: function (name, desc)
    {
      Object.defineProperty(obj, name, desc);
    },
    delete: function (name)
    {
      return delete obj[name];
    },
    fix: function ()
    {
      if (Object.isFrozen(obj)) {
        var result = {};
        Object.getOwnPropertyNames(obj).forEach(function (name)
                                                {
                                                  result[name]
                                                    = Object.getOwnPropertyDescriptor(obj,
                                                                                      name);
                                                });
        return result;
      }
      return undefined;
    },

    has: function (name)
    {
      return name in obj;
    },
    hasOwn: function (name)
    {
      return ({}).hasOwnProperty.call(obj, name);
    },
    get: function (receiver, name)
    {
      try {
        if (obj[name] !== undefined)
          return obj[name];
      } catch (err) {
        console.log("get (" + name + "): " + err);
      }

      return function ()
      {
        var args = new Array();

        var method = name;
        var callback;
        var headers;

        for (var i = 0; i < arguments.length; i++) {
          var arg = arguments[i];
          if ((typeof arg) === "function" && callback === undefined) {
            callback = arg;
          }
          else if ((typeof arg) === "function") {
            throw "function expected at " + arg;
          }
          else if (callback !== undefined) {
            headers = arg;

            break;
          }
          else {
            args.push(arg);
          }
        }

        if (callback === undefined) {
          callback = function(data) {
            console.log(data);
          };
        };

        receiver.$query(method, args, callback, headers);
      };
    },
    set: function (receiver, name, val)
    {
      obj[name] = val;
      return true;
    }, // bad behavior when set fails in non-strict mode
    enumerate: function ()
    {
      var result = [];
      for (var name in obj) {
        result.push(name);
      }
      ;
      return result;
    },
    keys: function ()
    {
      return Object.keys(obj);
    }
  };
};

