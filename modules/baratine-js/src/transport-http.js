Jamp.HttpTransport = function (url, client)
{
  this.url = url;
  this.client = client;
  this.isClosed = false;

  return this;
};

Jamp.HttpTransport.prototype.submitRequest = function (request)
{
  if (this.isClosed)
    throw this.toString() + " was already closed.";

  var httpRequest;

  httpRequest = this.initPushRequest();

  var msg = request.msg;

  var json = msg.serialize();
  json = "[" + json + "]";

  var client = this.client;
  var transport = this;

  httpRequest.onreadystatechange = function ()
  {
    if (httpRequest.readyState != 4) {
      return;
    }

    if (httpRequest.status == "200") {

      request.sent(client);

      transport.pull(client);
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

Jamp.HttpTransport.prototype.pull = function (client)
{
  if(this.isClosed)
    return;

  var httpRequest = this.initPullRequest();
  this.pullRequest = httpRequest;

  httpRequest.send("[]");

  var transport = this;

  httpRequest.onreadystatechange = function ()
  {
    if (httpRequest.readyState != 4) {
      return;
    }

    if (httpRequest.status == "200") {
      var json = httpRequest.responseText;

      var list = JSON.parse(json);

      client.onMessageArray(list);

      transport.pull(client);
    }
    else {
      console.log(this,
                  "error submitting query "
                  + httpRequest.status
                  + " "
                  + httpRequest.statusText
                  + " : "
                  + httpRequest.responseText);
    }

    transport.pullRequest = undefined;
  };

  httpRequest.ontimeout = function ()
  {
    if (! transport.isClosed)
      transport.pull(client);
  };
};

Jamp.HttpTransport.prototype.initPushRequest = function ()
{
  var httpRequest = new XMLHttpRequest();
  httpRequest.withCredentials = true;

  httpRequest.open("POST", this.url, true);
  httpRequest.setRequestHeader("Content-Type", "x-application/jamp-push");

  return httpRequest;
};

Jamp.HttpTransport.prototype.initPullRequest = function ()
{
  var httpRequest = new XMLHttpRequest();
  httpRequest.withCredentials = true;

  httpRequest.open("POST", this.url, true);
  httpRequest.setRequestHeader("Content-Type", "x-application/jamp-pull");

  return httpRequest;
};

Jamp.HttpTransport.prototype.close = function ()
{
  this.isClosed = true;

  var pullRequest = this.pullRequest;

  if (pullRequest !== undefined) {
    try {
      pullRequest.abort();
    } catch (err) {
    }
  }
};

Jamp.HttpTransport.prototype.toString = function ()
{
  return "Jamp.HttpTransport[" + this.url + "]";
};
