"use strict";

// MV2 content scripts have extension-origin XHR privileges. The async path still uses Via's Kotlin
// transport; this path must complete callbacks before GM_xmlhttpRequest returns to its caller.
function viaSyncRequest(details, page, token) {
  if (!token) return false; // A document without an early payload uses the native async fallback.
  const binary = ["arraybuffer", "blob", "stream"].includes(details.responseType);
  const response = { readyState: 1, status: 0, statusText: "", context: details.context };
  function deliver(name) {
    const callback = details[name];
    if (typeof page[callback] !== "function") return;
    try { page[callback](cloneInto(response, page)); }
    catch (error) { console.warn("via: GM XHR callback failed", error); }
  }
  try {
    const xhr = new XMLHttpRequest({ mozAnon: !!details.anonymous });
    const method = (details.method || (details.data == null ? "GET" : "POST")).toUpperCase();
    xhr.open(method, details.url, false, details.user, details.password);
    xhr.withCredentials = !details.anonymous;
    if (binary) xhr.overrideMimeType("text/plain; charset=x-user-defined");
    else if (details.overrideMimeType) xhr.overrideMimeType(details.overrideMimeType);
    const rewritten = {};
    for (const [name, value] of Object.entries(details.headers || {})) {
      if (/^(cookie|referer|origin|user-agent)$/i.test(name)) rewritten[name] = String(value);
      else if (!/^(host|content-length|x-via-sync-xhr)$/i.test(name)) xhr.setRequestHeader(name, String(value));
    }
    if (details.cookie && !details.anonymous) rewritten.Cookie = [rewritten.Cookie, details.cookie].filter(Boolean).join("; " );
    if (details.anonymous) {
      for (const name of Object.keys(rewritten)) if (name.toLowerCase() === "cookie") delete rewritten[name];
    }
    xhr.setRequestHeader("X-Via-Sync-Xhr", token + " " + encodeURIComponent(JSON.stringify(rewritten)));
    if (details.timeout || details.onprogress) console.warn("via: synchronous GM XHR does not support timeout or progress events");
    deliver("onloadstart");
    xhr.send(details.data ?? null);
    Object.assign(response, { readyState: 4, status: xhr.status, statusText: xhr.statusText,
      finalUrl: xhr.responseURL, responseHeaders: xhr.getAllResponseHeaders() });
    if (binary) {
      const bytes = Uint8Array.from(xhr.responseText, c => c.charCodeAt(0) & 255);
      response.response = details.responseType === "arraybuffer" ? bytes.buffer :
        new Blob([bytes], { type: xhr.getResponseHeader("Content-Type") || "application/octet-stream" });
    } else {
      response.responseText = xhr.responseText;
      response.response = details.responseType === "json" ? JSON.parse(xhr.responseText) : xhr.responseText;
    }
    deliver("onreadystatechange");
    deliver(xhr.status >= 200 && xhr.status < 300 ? "onload" : "onerror");
  } catch (error) {
    response.readyState = 4;
    deliver("onreadystatechange");
    deliver("onerror");
  }
  return true;
}
