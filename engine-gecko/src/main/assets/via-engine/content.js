"use strict";
// Runs at document_start in each top-level document. Connects to the page's native GeckoPage, which
// sends "eval" requests and receives bridge calls made by page JavaScript.
(() => {
  if (window.top !== window) return;
  const page = window.wrappedJSObject;
  const port = browser.runtime.connectNative("via");
  // Answers for calls that page JavaScript expects to return synchronously; pushed by native code.
  let commands = {};
  let addons = "[]";
  let xhrToken;
  const cache = new ViaGmCache((message, secret) => port.postMessage({ type: "gm", message, secret }),
    details => viaSyncRequest(details, page, xhrToken));
  let initialized = false;

  function initialize(message) {
    if (initialized) return;
    initialized = true;
    commands = message.commands || {};
    addons = message.addons || "[]";
    cache.reset(message.gm);
    xhrToken = message.xhrToken;
    if (message.registration) browser.runtime.sendMessage({ type: "injected", registration: message.registration });
    if (message.page) browser.runtime.sendMessage({ type: "page", page: message.page });
    function phase(number) {
      for (const code of message.phases?.[number] || []) {
        try { run(code); } catch (error) { console.warn("via: injection failed", error); }
      }
      if (number) port.postMessage({ type: "phase", phase: number });
    }
    // Userscripts need true document_start even when the parser has not created <head> yet.
    phase(0);
    if (document.head) phase(1);
    else {
      const observer = new MutationObserver(() => {
        if (document.head) { observer.disconnect(); phase(1); }
      });
      observer.observe(document, { childList: true, subtree: true });
    }
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", () => phase(2), { once: true });
    else phase(2);
    if (document.readyState !== "complete") window.addEventListener("load", () => phase(4), { once: true });
    else phase(4);
  }

  function text(value) {
    return value === undefined || value === null ? null : String(value);
  }

  function stringify(result) {
    try {
      const json = page.JSON.stringify(result);
      return json === undefined ? "null" : json;
    } catch (error) {
      return "null";
    }
  }

  // Page realm first, so scripts see and define page globals like WebView's evaluateJavascript.
  // Pages whose CSP forbids eval fall back to this content script's realm, which still sees the DOM.
  function run(code) {
    try {
      return stringify(page.eval(code));
    } catch (error) {
      if (!(error instanceof EvalError) && !String(error).includes("Content Security Policy")) throw error;
    }
    const result = window.eval(code);
    try {
      const json = JSON.stringify(result);
      return json === undefined ? "null" : json;
    } catch (error) {
      return "null";
    }
  }

  port.onMessage.addListener((message) => {
    switch (message.type) {
      case "eval": {
        let value = "null";
        try {
          value = run(message.code);
        } catch (error) {
          console.warn("via: evaluation failed", error);
        }
        if (message.id) port.postMessage({ type: "result", id: message.id, value });
        break;
      }
      case "state":
        if (message.page) browser.runtime.sendMessage({ type: "page", page: message.page });
        initialize(message);
        break;
      case "value":
        cache.update(message.script, message.name, message.value);
        break;
    }
  });

  function call(method, ...args) {
    port.postMessage({ type: "bridge", method, args: args.map(text) });
  }

  const via = new page.Object();
  exportFunction((command) => {
    const value = command | 0;
    port.postMessage({ type: "cmd", command: value });
    return commands[value] | 0;
  }, via, { defineAs: "cmd" });
  exportFunction((token, url, data) => call("download", token, url, data), via, { defineAs: "download" });
  exportFunction((token, json) => call("postMessage", token, json), via, { defineAs: "postMessage" });
  exportFunction((url, selector) => call("record", url, selector), via, { defineAs: "record" });
  exportFunction((id) => call("addon", id), via, { defineAs: "addon" });
  exportFunction((value) => call("toast", value), via, { defineAs: "toast" });
  exportFunction(() => addons, via, { defineAs: "getInstalledAddonID" });
  page.via = via;
  page.via_page = via;
  window.via = via;
  window.via_page = via;

  const gm = new page.Object();
  exportFunction((message, secret) => cache.call(text(message), text(secret)), gm, { defineAs: "call" });
  page.via_gm = gm;
  window.via_gm = gm;

  async function reportIcons() {
    const touch = document.querySelector('link[rel~="apple-touch-icon"],link[rel~="apple-touch-icon-precomposed"]');
    if (touch) port.postMessage({ type: "touchIcon", url: touch.href });
    const icon = document.querySelector('link[rel~="icon"]')?.href ||
      (/^https?:$/.test(location.protocol) ? new URL("/favicon.ico", location.href).href : null);
    if (!icon) return;
    try {
      const response = await fetch(icon, { credentials: "include" });
      if (!response.ok) return;
      const blob = await response.blob();
      if (blob.size > 512 * 1024) return;
      const reader = new FileReader();
      reader.onload = () => port.postMessage({ type: "icon", data: reader.result.split(",")[1] });
      reader.readAsDataURL(blob);
    } catch (_) { /* An icon failure must not affect the document. */ }
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", reportIcons, { once: true });
  else reportIcons();
  globalThis.viaInitialize = initialize;
  if (globalThis.viaEarlyPayload) initialize(globalThis.viaEarlyPayload);
})();
