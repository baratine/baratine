var Jamp = {};

Jamp.BaratineClient = function (url, rpc)
{
  this.client = new Jamp.Client(url, rpc);

  this.onSession;

  var _this = this;

  this.client.onOpen = function() {
    if (_this.onSession !== undefined)
      _this.onSession();
  };

  return this;
};

Jamp.BaratineClient.prototype.send = function (service, method, args, headers)
{
  this.client.send(service, method, args, headers);
};

Jamp.BaratineClient.prototype.query = function (service,
                                                method,
                                                args,
                                                callback,
                                                headers)
{
  this.client.query(service, method, args, callback, headers);
};

Jamp.BaratineClient.prototype.lookup = function (path)
{
  var target = new Jamp.BaratineClientProxy(this, path);

  try {
    if (Proxy !== undefined)
      try {
        target = Proxy.create(handlerMaker(target));
      } catch (err) {
        console.log(err);
      }
  } catch (err) {
  }

  return target;
};

Jamp.BaratineClient.prototype.createListener = function ()
{
  return new Jamp.ServiceListener();
};

Jamp.BaratineClient.prototype.close = function () {
  this.client.close();
};

Jamp.BaratineClient.prototype.toString = function ()
{
  return "BaratineClient[" + this.client + "]";
};

Jamp.BaratineClientProxy = function (client, path)
{
  this.client = client;
  this.path = path;

  return this;
};

Jamp.BaratineClientProxy.prototype.$send = function (method, args, headers)
{
  this.client.send(this.path, method, args, headers);
};

Jamp.BaratineClientProxy.prototype.$query = function (method,
                                                      args,
                                                      callback,
                                                      headers)
{
  this.client.query(this.path, method, args, callback, headers);
};

Jamp.BaratineClientProxy.prototype.$lookup = function (path)
{
  var target = new Jamp.BaratineClientProxy(this.client, this.path + path);

  try {
    if (Proxy !== undefined)
      try {
        target = Proxy.create(handlerMaker(target));
      } catch (err) {
        console.log(err);
      }
  } catch (err) {
  }

  return target;
};

Jamp.BaratineClientProxy.prototype.toString = function ()
{
  return "Jamp.BaratineClientProxy[" + this.path + "]";
};

Jamp.ServiceListener = function ()
{
  this.___isListener = true;

  return this;
};
