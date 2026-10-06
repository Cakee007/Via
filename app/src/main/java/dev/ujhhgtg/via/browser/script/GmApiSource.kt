package dev.ujhhgtg.via.browser.script

import java.util.UUID
import org.json.JSONObject



/** Userscript API source and native bridge message format. */
object GmApiSource {

    fun request(str: String, str2: String, str3: String, str4: String, str5: String?): String {
        var str5 = str5
        val sb2: StringBuilder = StringBuilder()
        sb2.append("window.")
        sb2.append(str2)
        sb2.append("(JSON.stringify({secret:'")
        sb2.append(str)
        sb2.append("', identifier:'")
        sb2.append(str3)
        sb2.append("', name:'")
        sb2.append(str4)
        sb2.append("', 'arguments':")
        if (str5 == null) {
            str5 = "{}"
        }
        sb2.append(str5)
        sb2.append("}), '")
        sb2.append(str)
        sb2.append("')")
        return sb2.toString()
    }

    fun installationRequest(str: String, str2: String, str3: String, str4: String?): String {
        var str4 = str4
        val sb2: StringBuilder = StringBuilder()
        sb2.append("window.")
        sb2.append(str2)
        sb2.append("(JSON.stringify({secret:'")
        sb2.append(str)
        sb2.append("', name:'")
        sb2.append(str3)
        sb2.append("', 'arguments':")
        if (str4 == null) {
            str4 = "{}"
        }
        sb2.append(str4)
        sb2.append("}), '")
        sb2.append(str)
        sb2.append("')")
        return sb2.toString()
    }

    fun scriptApi(scriptId: String, secret: String, grantMask: Int): String {
        var str: String
        var str2: String
        var j0Var: GmApiSource
        var str3: String
        var str4: String
        var str5: String
        val apiPrefix: String = identifier("GM_" + secret + '_')
        val bridgeName: String = identifier("NP_$secret")
        var str6: String = ((("const GM = {};\n" + captureBridge(bridgeName)) + "const " + apiPrefix + "info = function() {let cached; const obj = {}; Object.defineProperty(obj, 'VALUE', {get: function() {if (cached == undefined) {cached = JSON.parse(" + request(secret, bridgeName, scriptId, "info", null) + ");} return cached;}, configurable: false, enumerable: true}); return obj;}();\n") + "const GM_info = " + apiPrefix + "info.VALUE;\n") + "GM.info = " + apiPrefix + "info.VALUE;\n"
        if (grantMask == 0) {
            return str6
        }
        if ((10485763 and grantMask) != 0) {
            str = apiPrefix + "JSONStringify"
            str2 = apiPrefix + "JSONParse"
            str6 = (str6 + function(str, "v", "return JSON.stringify(v,function(k,v){if(typeof v==='bigint'){return undefined}if(v instanceof Number){return '{}';}if(v!==v){return 'NaN'}if(v===Infinity){return 'Infinity'}if(v===-Infinity){return '-Infinity'}return v});")) + function(str2, "v", "return JSON.parse(v,function(k,v){if(v==='{}'){return {}}if(v==='NaN'){return NaN}if(v==='Infinity'){return Infinity}if(v==='-Infinity'){return -Infinity}return v});")
        } else {
            str = "JSON.stringify"
            str2 = "JSON.parse"
        }
        var string: String = str6
        val str7: String = str
        val str8: String = str2
        if ((8388609 and grantMask) != 0) {
            val str9: String = apiPrefix + "setValue"
            var str10: String = string + function(str9, "name, value", "v = value; try {v = " + str7 + "(v);} catch(e) {};" + request(secret, bridgeName, scriptId, "setValue", "{name: name, value: v}") + ';')
            if ((grantMask and 1) == 1) {
                str10 = (str10 + legacyAlias(str9, "setValue")) + "var GM_setValues=function(a){if(a)for(var b in a)GM_setValue(b,a[b])};\n"
            }
            string = str10
            if ((grantMask and 8388608) == 8388608) {
                string = (string + modernAlias(str9, "setValue", "name, value", true)) + "GM.setValues=function(a){return new Promise(function(c,d){if(a){for(var b in a)" + str9 + "(b,a[b]);c()}else d(\"No data\")})};\n"
            }
        }
        if ((2097154 and grantMask) != 0) {
            val str11: String = apiPrefix + "getValue"
            var str12: String = string + function(str11, "name, defaultValue", "var dv = defaultValue;try {dv = " + str7 + "(defaultValue);} catch(e) {};var v = " + request(secret, bridgeName, scriptId, "getValue", "{name: name, value: dv}") + ";if(v == 'undefined') { return undefined; };try {v = " + str8 + "(v);} catch(e) {};return v;")
            if ((grantMask and 2) == 2) {
                str12 = (str12 + legacyAlias(str11, "getValue")) + "var GM_getValues=function(b){var c={};if(!b)return c;if(Array.isArray(b))for(var a in b)c[a]=GM_getValue(a);else for(a in b)c[a]=GM_getValue(a,b[a]);return c};\n"
            }
            string = str12
            if ((grantMask and 2097152) == 2097152) {
                string = (string + modernAlias(str11, "getValue", "name, defaultValue", true)) + "GM.getValues=function(a){return new Promise(function(d,c){if(a){c={};if(!a)return d({});if(Array.isArray(a))for(var b in a)c[b]=" + str11 + "(b);else for(b in a)c[b]=" + str11 + "(b,a[b]);d(c)}else c(\"No data\")})};\n"
            }
        }
        if ((1048584 and grantMask) != 0) {
            val str13: String = apiPrefix + "deleteValue"
            var str14: String = string + function(str13, "name", request(secret, bridgeName, scriptId, "deleteValue", "{name: name}") + ';')
            if ((grantMask and 8) == 8) {
                str14 = (str14 + legacyAlias(str13, "deleteValue")) + "var GM_deleteValues=function(a){if(a&&\"number\"===typeof a.length)for(var b=0,c=a.length;b<c;b++)try{GM_deleteValue(a[b])}catch(d){}};\n"
            }
            string = str14
            if ((grantMask and 1048576) == 1048576) {
                string = (string + modernAlias(str13, "deleteValue", "name", true)) + "GM.deleteValues=function(b){return new Promise(function(c,a){if(b&&\"number\"===typeof b.length){a=0;for(var d=b.length;a<d;a++)try{" + str13 + "(b[a])}catch(e){}c()}else a(\"No array\")})};\n"
            }
        }
        if ((4194308 and grantMask) != 0) {
            val str15: String = apiPrefix + "listValues"
            var str16: String = string + function(str15, "", "var s = " + request(secret, bridgeName, scriptId, "listValues", null) + ";return s.split(\",\");")
            if ((grantMask and 4) == 4) {
                str16 += legacyAlias(str15, "listValues")
            }
            string = str16
            if ((grantMask and 4194304) == 4194304) {
                string += modernAlias(str15, "listValues", "", true)
            }
        }
        if ((33554560 and grantMask) != 0) {
            val str17: String = apiPrefix + "addElement"
            var str18: String = string + function(str17, "parent, tag, attrs, callback", "if (typeof parent === 'string') { return GM_addElement(undefined, parent, tag || {}, attrs); }var e = document.createElement(tag);if (attrs) {for (var k in attrs) { if (k == 'textContent') { e.textContent = attrs[k]; continue; }; try{e.setAttribute(k, attrs[k]);}catch(e){} };};if (parent == undefined && document) { parent = document.head; }if (parent) { parent.appendChild(e); };if (callback) { callback() }return e;")
            if ((grantMask and 128) == 128) {
                str18 += legacyAlias(str17, "addElement")
            }
            string = str18
            if ((grantMask and 33554432) == 33554432) {
                string += modernAlias(str17, "addElement", "parent, tag, attrs, callback", true)
            }
        }
        if ((16777280 and grantMask) != 0) {
            val str19: String = apiPrefix + "addStyle"
            var str20: String = string + function(str19, "css, callback", "if (!css) { return; };var h = document.getElementsByTagName('head')[0];if (!h) { return; };var e = document.createElement('style');e.type = 'text/css';e.textContent = css;h.appendChild(e);if (callback) { callback() }return e;")
            if ((grantMask and 64) == 64) {
                str20 += legacyAlias(str19, "addStyle")
            }
            string = str20
            if ((grantMask and 16777216) == 16777216) {
                string += modernAlias(str19, "addStyle", "css, callback", true)
            }
        }
        if ((grantMask and 512) == 512) {
            string = string + "var GM_log = function(message) {" + request(secret, bridgeName, scriptId, "log", "{message: message}") + "};\n"
        }
        if ((67108880 and grantMask) != 0) {
            val str21: String = apiPrefix + "getResourceURL"
            val sb2: StringBuilder = StringBuilder()
            sb2.append("var r = ")
            j0Var = this
            sb2.append(j0Var.request(secret, bridgeName, scriptId, "getResourceURL", "{resource: resourceName}"))
            sb2.append(";if (r === 'undefined') {return null} return r;")
            var str22: String = string + j0Var.function(str21, "resourceName", sb2.toString())
            if ((grantMask and 16) == 16) {
                str22 += j0Var.legacyAlias(str21, "getResourceURL")
            }
            string = str22
            if ((67108864 and grantMask) == 67108864) {
                string += j0Var.modernAlias(str21, "getResourceUrl", "resourceName", true)
            }
        } else {
            j0Var = this
        }
        val i11 = grantMask and 32
        if (i11 != 0) {
            val str23: String = apiPrefix + "getResourceText"
            string += j0Var.function(str23, "resourceName", "var r = " + j0Var.request(secret, bridgeName, scriptId, "getResourceText", "{resource: resourceName}") + ";if (r === 'undefined') {return null} return r;")
            string += j0Var.legacyAlias(str23, "getResourceText")
        }
        if ((1073742848 and grantMask) != 0) {
            val str24: String = apiPrefix + "setClipboard"
            var str25: String = string + j0Var.function(str24, "data, type", j0Var.request(secret, bridgeName, scriptId, "setClipboard", "{data: data, type: type}") + ';')
            if ((grantMask and 1024) == 1024) {
                str25 += j0Var.legacyAlias(str24, "setClipboard")
            }
            string = str25
            if ((1073741824 and grantMask) == 1073741824) {
                string += j0Var.modernAlias(str24, "setClipboard", "data, type", false)
            }
        }
        if ((grantMask and 2048) == 2048) {
            val sb3: StringBuilder = StringBuilder()
            sb3.append(string)
            sb3.append("var GM_download = function(url, name) {if ((typeof url) == 'object') {name = url.name;url = url.url;};if (!name) { name = ''; };")
            str3 = scriptId
            sb3.append(j0Var.request(secret, bridgeName, str3, "download", "{url: url, name: name}"))
            sb3.append(";};\n")
            string = sb3.toString()
        } else {
            str3 = scriptId
        }
        if (((-2147483392) and grantMask) != 0) {
            val str26: String = (string + "unsafeWindow = (function() { if(window){return window;} try {var el = document.createElement('p'); el.setAttribute('onclick', 'return window;'); return el.onclick();}catch(e){return window;} }()); window.wrappedJSObject = unsafeWindow;\n") + "var generateRandomString=function(c){for(var a=\"\",b=0;b<c;b++)a+=\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz\".charAt(Math.floor(62*Math.random()));return a};\n"
            val str27: String = apiPrefix + "xmlhttpRequest"
            val strK3: String = j0Var.identifier("V_" + str3 + UUID.randomUUID().toString() + '_')
            var str28: String = ((str26 + j0Var.function(str27, "details", "var identifier = generateRandomString(8);if (details.onabort) { unsafeWindow[identifier+'" + strK3 + "GM_onAbortCallback'] = details.onabort;details.onabort = identifier+'" + strK3 + "GM_onAbortCallback'; }if (details.onerror) { unsafeWindow[identifier+'" + strK3 + "GM_onErrorCallback'] = details.onerror;details.onerror = identifier+'" + strK3 + "GM_onErrorCallback'; }if (details.onload) { unsafeWindow[identifier+'" + strK3 + "GM_onLoadCallback'] = details.onload;details.onload = identifier+'" + strK3 + "GM_onLoadCallback'; }if (details.onloadstart) { unsafeWindow[identifier+'" + strK3 + "GM_onLoadStartCallback'] = details.onloadstart;details.onloadstart = identifier+'" + strK3 + "GM_onLoadStartCallback'; }if (details.onprogress) { unsafeWindow[identifier+'" + strK3 + "GM_onProgressCallback'] = details.onprogress;details.onprogress = identifier+'" + strK3 + "GM_onProgressCallback'; }if (details.onreadystatechange) { unsafeWindow[identifier+'" + strK3 + "GM_onReadyStateChange'] = details.onreadystatechange;details.onreadystatechange = identifier+'" + strK3 + "GM_onReadyStateChange'; }if (details.ontimeout) { unsafeWindow[identifier+'" + strK3 + "GM_onTimeoutCallback'] = details.ontimeout;details.ontimeout = identifier+'" + strK3 + "GM_onTimeoutCallback'; }if (details.upload) {if (details.upload.onprogress) { unsafeWindow[identifier+'" + strK3 + "GM_uploadOnProgressCallback'] = details.upload.onprogress;details.upload.onprogress = identifier+'" + strK3 + "GM_uploadOnProgressCallback'; }}if (details.data instanceof FormData) { details.data = new URLSearchParams(details.data).toString(); }if (details.data instanceof Array) { details.data = details.data.join(','); }if (details.data instanceof URLSearchParams) { details.data = details.data.toString(); }details.v_ua = navigator.userAgent;" + j0Var.request(secret, bridgeName, str3, "xmlhttpRequest", "{details: JSON.stringify(details)}") + ';')) + str27 + ".UNSENT=0;" + str27 + ".OPENED=1;" + str27 + ".HEADERS_RECEIVED=2;" + str27 + ".LOADING=3;" + str27 + ".DONE=4;\n") + str27 + ".RESPONSE_TYPE_ARRAYBUFFER='arraybuffer';" + str27 + ".RESPONSE_TYPE_BLOB='blob';" + str27 + ".RESPONSE_TYPE_DOCUMENT='document';" + str27 + ".RESPONSE_TYPE_JSON='json';" + str27 + ".RESPONSE_TYPE_STREAM='stream';" + str27 + ".RESPONSE_TYPE_TEXT='text';\n"
            if ((grantMask and 256) == 256) {
                str28 += j0Var.legacyAlias(str27, "xmlhttpRequest")
            }
            string = str28
            if ((Int.MIN_VALUE and grantMask) == Int.MIN_VALUE) {
                string += j0Var.modernAlias(str27, "xmlHttpRequest", "details", false)
            }
        }
        if ((grantMask and (8192 or 16384)) != 0) {
            val notify = notifyName(scriptId, secret)
            string += "var ${apiPrefix}valueListeners = Object.create(null), ${apiPrefix}nextValueListener = 0;\n"
            string += "Object.defineProperty(window, ${JSONObject.quote(notify)}, {configurable: true, value: function(name, newRaw, oldRaw, remote) { var decode = function(v) { if (v === null || v === 'undefined') return undefined; try { return " + str8 + "(v); } catch(e) { return v; } }; var n = decode(newRaw), o = decode(oldRaw); for (var id in ${apiPrefix}valueListeners) { var item = ${apiPrefix}valueListeners[id]; if (item && item.name === name) { try { item.callback(name, o, n, !!remote); } catch(e) {} } } }});\n"
            if ((grantMask and 8192) == 8192) {
                string += "var GM_addValueChangeListener = function(name, callback) { if (typeof callback !== 'function') return 0; var id = ++${apiPrefix}nextValueListener; ${apiPrefix}valueListeners[id] = {name: name, callback: callback}; return id; };\n"
            }
        }
        if ((grantMask and 16384) == 16384) {
            string += "var GM_removeValueChangeListener = function(listenerId) { delete ${apiPrefix}valueListeners[listenerId]; return true; };\n"
        }
        if ((268468224 and grantMask) != 0) {
            val str29: String = apiPrefix + "openInTab"
            val sb4: StringBuilder = StringBuilder()
            sb4.append("if ((typeof options) != 'object') { options = { active: (!options ? false : true) }}; ")
            str4 = scriptId
            str5 = secret
            sb4.append(j0Var.request(str5, bridgeName, str4, "openInTab", "{url: url, options: JSON.stringify(options)}"))
            sb4.append(';')
            var str30: String = string + j0Var.function(str29, "url, options", sb4.toString())
            if ((32768 and grantMask) == 32768) {
                str30 += j0Var.legacyAlias(str29, "openInTab")
            }
            string = str30
            if ((268435456 and grantMask) == 268435456) {
                string += j0Var.modernAlias(str29, "openInTab", "url, options", false)
            }
        } else {
            str4 = scriptId
            str5 = secret
        }
        if ((536936448 and grantMask) != 0) {
            val str31: String = apiPrefix + "registerMenuCommand"
            var str32: String = string + j0Var.function(str31, "name, func, key",
                "if (!func) { return; };if (!window['$str5']) window['$str5'] = {};if (!window['$str5']['gm_menus']) window['$str5']['gm_menus'] = {};if (!window['$str5']['gm_menus']['$str4']) window['$str5']['gm_menus']['$str4'] = {};window['$str5']['gm_menus']['$str4'][name] = func;return name;"
            )
            if ((65536 and grantMask) == 65536) {
                str32 += j0Var.legacyAlias(str31, "registerMenuCommand")
            }
            string = if ((grantMask and 536870912) == 536870912) {
                str32 + j0Var.modernAlias(str31, "registerMenuCommand", "name, func, key", false)
            } else {
                str32
            }
        }
        if ((537001984 and grantMask) != 0) {
            val str33: String = apiPrefix + "unregisterMenuCommand"
            string = (string + j0Var.function(str33, "name",
                "if (!name) { return; };if (!window['$str5']) { return; }if (!window['$str5']['gm_menus']) { return; }if (!window['$str5']['gm_menus']['$str4']) { return; }delete window['$str5']['gm_menus']['$str4'][name];"
            )) + j0Var.legacyAlias(str33, "unregisterMenuCommand")
            if ((grantMask and 536870912) == 536870912) {
                string += j0Var.modernAlias(str33, "unregisterMenuCommand", "name", false)
            }
        }
        if ((134479872 and grantMask) != 0) {
            if ((262144 and grantMask) == 262144) {
                string += "var GM_notification = function() {};\n"
            }
            if ((134217728 and grantMask) == 134217728) {
                return string + "GM.notification = function() {};\n"
            }
        }
        return string
    }

    fun installationApi(secret: String): String {
        val strK: String = identifier("NP_$secret")
        return "(function () {let key = 'via-fake-tampermonkey';if (window[key]) {return;};" + captureBridge(strK) + "try {window[key] = true;window.external.Tampermonkey = {getVersion: function(e) {e({version:'4.5.1', id: 'Via'});},openOptions: function(e, t) {" + installationRequest(secret, strK, "openOptions", "{name: e, namespace: t}") + ";},isInstalled: function(e, t, n) {var v = " + installationRequest(secret, strK, "isInstalled", "{name: e, namespace: t}") + ";if(v === 'undefined') { return; };try {v = JSON.parse(v);} catch(e) {};n(v);}}} catch (err) {}})();"
    }

    fun legacyAlias(str: String, str2: String): String {
        return "var GM_$str2 = $str;\n"
    }

    fun modernAlias(str: String, str2: String, str3: String, z10: Boolean): String {
        if (!z10) {
            return "GM.$str2 = $str;\n"
        }
        return "GM.$str2 = function($str3) { return new Promise(function(resolve, reject) { resolve($str($str3));});};\n"
    }

    fun function(str: String?, str2: String?, str3: String?): String {
        var str3 = str3
        var str2 = str2
        if (str.isNullOrEmpty()) {
            return ""
        }
        if (str2 == null) {
            str2 = ""
        }
        if (str3 == null) {
            str3 = ""
        }
        return "var $str = function($str2) { $str3 };\n"
    }

    fun captureBridge(str: String): String {
        return "if(!window.$str) {const nativePrompt = window.via_gm && window.via_gm.call ? window.via_gm.call.bind(window.via_gm) : window.prompt;Object.defineProperty(window, '$str', {value: nativePrompt,writable: false,configurable: false,enumerable: true});}"
    }

    fun deliverCallbacks(strArr: Array<out String?>?, str: String?): String? {
        if (strArr.isNullOrEmpty()) {
            return null
        }
        var str3 = ""
        for (str4 in strArr) {
            if (!str4.isNullOrEmpty()) {
                str3 = str3 + "unsafeWindow." + str4 + "(obj);"
            }
        }
        if (str3.isEmpty()) {
            return null
        }
        val sb2: StringBuilder = StringBuilder()
        sb2.append("javascript: (function() { var obj = ")
        val str2: String = if (str.isNullOrEmpty()) {
            "undefined"
        } else {
            "JSON.parse($str)"
        }
        sb2.append(str2)
        sb2.append(";if (obj && obj.responseType) {var type = obj.responseType;if (type == 'undefined') { obj.responseType = undefined; obj.response = obj.responseText; }else if (type == 'json') { try { obj.response = JSON.parse(obj.responseText); }catch(e){} }else if (type == 'document') { try { obj.response = new DOMParser().parseFromString(obj.responseText, 'text/html'); }catch(e){} }else if (type == 'blob' || type == 'arraybuffer' || type == 'stream') {if (obj.responseDataUrl) {const [metadata, data] = obj.responseDataUrl.split(',');const mime = metadata.match(/data:([^;]+)/)[1];const decodedData = atob(data);delete obj.responseDataUrl;obj.responseText = decodedData;const bytes = new Uint8Array(decodedData.length);for (let i = 0; i < decodedData.length; i++) {bytes[i] = decodedData.charCodeAt(i);}if ('blob' == type) { obj.response = new Blob([bytes], { type: mime }); }else if ('stream' == type) { obj.response = new ReadableStream({start(controller) {controller.enqueue(bytes);}, pull(controller) {controller.close();}, cancel() {}}); }else { obj.response = bytes.buffer; }}}else { obj.response = obj.responseText; }}")
        sb2.append(str3)
        sb2.append("})();")
        return sb2.toString()
    }

    fun menuState(secret: String): String {
        return "javascript:var es = window['$secret']['gm_menus'];result = {};for (var k in es) {var items = es[k];var list = [];for (var i in items) {list.push(i);}result[k] = list;}JSON.stringify(result);"
    }

    fun identifier(str: String): String {
        val sb2: StringBuilder = StringBuilder()
        val length: Int = str.length
        for (i10 in 0 until length) {
            val cCharAt: Char = str[i10]
            if ((cCharAt in 'A'..<'[') || ((cCharAt in 'a'..<'{') || ((cCharAt in '0'..<':') || cCharAt == '_'))) {
                sb2.append(cCharAt)
            }
        }
        val string: String = sb2.toString()
        return string
    }

    /** A page-visible global, so it is derived from the bridge secret without revealing it. */
    fun notifyName(scriptId: String, secret: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest("$secret:$scriptId".toByteArray(Charsets.UTF_8))
        return "VIA_GM_NOTIFY_" + digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
