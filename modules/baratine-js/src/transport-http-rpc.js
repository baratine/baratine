Jamp.HttpRpcTransport = function (url, client)
{
  this.url = url;
  this.client = client;
  this.isClosed = false;

  return this;
};

Jamp.HttpRpcTransport.prototype.submitRequest = function (request)
{
  if (this.isClosed)
    throw this.toString() + " was already closed.";

  var httpRequest;

  httpRequest = this.initRpcRequest();

  var msg = request.msg;

  var json = msg.serialize();
  json = "[" + json + "]";

  var client = this.client;

  httpRequest.onreadystatechange = function ()
  {
    if (httpRequest.readyState != 4) {
      return;
    }

    if (httpRequest.status == "200") {
      var json = httpRequest.responseText;

      var list = JSON.parse(json);

      request.sent(client);

      client.onMessageArray(list);
    }
    else {
      request.error(client,
                    "error submitting query "
                    + httpRequest.status
                    + " "
                    + httpRequest.statusText
                    + " : "
                    + httpRequest.responseText);
    }
  };

  httpRequest.send(json);
};

Jamp.HttpRpcTransport.prototype.initRpcRequest = function ()
{
  var httpRequest = new XMLHttpRequest();
  httpRequest.withCredentials = true;

  httpRequest.open("POST", this.url, true);
  httpRequest.setRequestHeader("Content-Type", "x-application/jamp-rpc");

  return httpRequest;
};

Jamp.HttpRpcTransport.prototype.close = function ()
{
  this.isClosed = true;
};

Jamp.HttpRpcTransport.prototype.toString = function ()
{
  return "Jamp.HttpRpcTransport[" + this.url + "]";
};
