"use strict";
// Request filtering and cookie access for Via. Native code decides each request over a port.
const port = browser.runtime.connectNative("via");
let nextId = 1;
const pending = new Map();
// Content scripts report which native page owns their tab; native code also learns it from first loads.
const pages = new Map();
const xhrToken = crypto.randomUUID();

function ask(message) {
  const id = nextId++;
  message.id = id;
  return new Promise((resolve) => {
    pending.set(id, resolve);
    port.postMessage(message);
  });
}

port.onMessage.addListener(async (message) => {
  switch (message.type) {
    case "decision": {
      const resolve = pending.get(message.id);
      pending.delete(message.id);
      if (resolve) resolve(message);
      break;
    }
    case "cookies": {
      let value = null;
      try {
        const cookies = await browser.cookies.getAll({ url: message.url });
        cookies.sort((a, b) => b.path.length - a.path.length);
        if (cookies.length) value = cookies.map((c) => c.name + "=" + c.value).join("; ");
      } catch (error) {
        console.warn("via: cookies failed", error);
      }
      port.postMessage({ type: "reply", id: message.id, value });
      break;
    }
  }
});

browser.runtime.onMessage.addListener((message, sender) => {
  if (message && message.type === "page" && sender.tab) pages.set(sender.tab.id, message.page);
  if (message?.type === "injected" && sender.tab) {
    const record = registrations.get(message.registration);
    if (record?.tabId === sender.tab.id) return releaseRegistration(message.registration);
  }
});

browser.tabs.onRemoved.addListener((tabId) => pages.delete(tabId));

// Register the prepared payload before releasing response headers. Unlike synchronous blob XHR,
// this does not spin a nested event loop that can let page scripts run during a cold Gecko startup.
const registrations = new Map();
async function releaseRegistration(requestId) {
  const registration = registrations.get(requestId);
  registrations.delete(requestId);
  if (registration) await registration.script.unregister();
}
browser.webRequest.onHeadersReceived.addListener(async (details) => {
  if (details.tabId < 0 || (details.statusCode >= 300 && details.statusCode < 400 && details.statusCode !== 304)) return {};
  const result = await ask({ type: "injection", page: pages.get(details.tabId) ?? -1,
    url: details.url, requestType: details.type });
  if (!result.payload) return {};
  result.payload.xhrToken = xhrToken;
  result.payload.registration = details.requestId;
  // Mapping to a native page always comes from that session's native port, not a URL registration.
  delete result.payload.page;
  const target = new URL(details.url);
  target.hash = "";
  const code = `if (location.href.split('#')[0] === ${JSON.stringify(target.href)}) {
    globalThis.viaEarlyPayload = ${JSON.stringify(result.payload)};
    if (globalThis.viaInitialize) globalThis.viaInitialize(globalThis.viaEarlyPayload);
  }`;
  try {
    await releaseRegistration(details.requestId);
    const registration = await browser.contentScripts.register({
      matches: [target.protocol + "//" + target.hostname + "/*"], js: [{ code }], runAt: "document_start",
    });
    registrations.set(details.requestId, { script: registration, tabId: details.tabId });
    setTimeout(() => releaseRegistration(details.requestId), 60000);
  } catch (error) { console.warn("via: early injection unavailable; using native messaging", error); }
  return {};
}, { urls: ["http://*/*", "https://*/*"], types: ["main_frame"] }, ["blocking", "responseHeaders"]);
browser.webRequest.onErrorOccurred.addListener(d => releaseRegistration(d.requestId), { urls: ["<all_urls>"], types: ["main_frame"] });

browser.webRequest.onBeforeRequest.addListener(async (details) => {
  if (details.tabId < 0 || !/^(https?|wss?):/.test(details.url)) return {};
  const decision = await ask({
    type: "request",
    page: pages.has(details.tabId) ? pages.get(details.tabId) : -1,
    url: details.url,
    requestType: details.type,
  });
  if (decision.page > 0) pages.set(details.tabId, decision.page);
  switch (decision.action) {
    case "cancel": return { cancel: true };
    case "redirect": return { redirectUrl: decision.url };
    default: return {};
  }
}, { urls: ["<all_urls>"] }, ["blocking"]);

// Installation completes before this background script necessarily registers its listeners.
browser.webRequest.onBeforeSendHeaders.addListener((details) => {
  const marker = details.requestHeaders.find(h => h.name.toLowerCase() === "x-via-sync-xhr");
  if (!marker) return {};
  // Never send the marker or its header values to a server, including malformed requests.
  if (!marker.value.startsWith(xhrToken + " ")) return { cancel: true };
  try {
    const overrides = JSON.parse(decodeURIComponent(marker.value.slice(xhrToken.length + 1)));
    const headers = details.requestHeaders.filter(h => h !== marker);
    for (const [name, value] of Object.entries(overrides)) {
      if (!/^(cookie|referer|origin|user-agent)$/i.test(name) || /[\r\n]/.test(value)) return { cancel: true };
      const index = headers.findIndex(h => h.name.toLowerCase() === name.toLowerCase());
      if (index >= 0) headers.splice(index, 1);
      headers.push({ name, value });
    }
    return { requestHeaders: headers };
  } catch (_) { return { cancel: true }; }
}, { urls: ["http://*/*", "https://*/*"] }, ["blocking", "requestHeaders"]);

port.postMessage({ type: "ready" });
