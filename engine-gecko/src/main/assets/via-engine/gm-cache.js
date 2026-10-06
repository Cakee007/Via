"use strict";

// Keep the existing Via value encoding. SQLite remains authoritative; only synchronous reads are local.
class ViaGmCache {
  constructor(send, syncRequest, notify) {
    this.send = send;
    this.syncRequest = syncRequest;
    this.notify = notify;
    this.reset(null);
  }

  reset(snapshot) {
    this.secret = snapshot?.secret;
    this.scripts = snapshot?.scripts || {};
  }

  update(identifier, name, value, remote = false) {
    const script = Object.hasOwn(this.scripts, identifier) && this.scripts[identifier];
    if (!script) return;
    const old = Object.hasOwn(script.values, name) ? script.values[name] : null;
    if (old === value) return;
    if (value === null) delete script.values[name];
    else Object.defineProperty(script.values, name, { value, writable: true, configurable: true, enumerable: true });
    this.notify?.(identifier, name, value, old, remote, script.notify);
  }

  call(message, secret) {
    if (!this.secret || secret !== this.secret) return null;
    let request;
    try { request = JSON.parse(message); } catch (_) { return null; }
    if (request.secret !== secret) return null;
    const script = Object.hasOwn(this.scripts, request.identifier) && this.scripts[request.identifier];
    if (!script) return null;
    const args = request.arguments || {};
    const allowed = (mask) => (script.grants & mask) !== 0;
    switch (request.name) {
      case "info": return script.info;
      case "getValue":
        if (!allowed(2097154)) return "undefined";
        return Object.hasOwn(script.values, args.name) ? script.values[args.name] : (args.value ?? "undefined");
      case "listValues": return allowed(4194308) ? Object.keys(script.values).join(",") : null;
      case "getResourceText":
        return allowed(32) && Object.hasOwn(script.resourceText, args.resource) ? script.resourceText[args.resource] : "undefined";
      case "getResourceURL":
        return allowed(67108880) && Object.hasOwn(script.resourceUrl, args.resource) ? script.resourceUrl[args.resource] : "undefined";
      case "setValue":
        if (!allowed(8388609)) return null;
        this.update(request.identifier, args.name, args.value ?? "undefined");
        break;
      case "deleteValue":
        if (!allowed(1048584)) return null;
        this.update(request.identifier, args.name, null);
        break;
      case "xmlhttpRequest": {
        if (!allowed(-2147483392)) return null;
        const details = JSON.parse(args.details);
        if (details.synchronous && this.syncRequest?.(details)) return null;
        break;
      }
    }
    this.send(message, secret);
    return null;
  }
}
