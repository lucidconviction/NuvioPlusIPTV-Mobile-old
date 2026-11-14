package com.nuvio.app.features.web

/**
 * Minimal Tampermonkey-style userscript parser and wrapper, ported from Lucid Hikari
 * (GPL-3.0). Scripts are matched by @match / @include / @exclude, injected in the WebView at
 * document-start or document-end, and get a small GM_* shim backed by [WebAppsRepository].
 */
internal object WebUserscriptManager {

    data class Parsed(
        val name: String,
        val runAtStart: Boolean,
        val matches: List<String>,
        val includes: List<String>,
        val excludes: List<String>,
    )

    fun scriptsFor(url: String, scripts: List<WebUserscript>, atStart: Boolean): List<String> {
        val out = mutableListOf<String>()
        for (script in scripts) {
            if (!script.enabled || script.code.isBlank()) continue
            val parsed = parse(script.code)
            if (parsed.runAtStart != atStart) continue
            if (!urlMatches(parsed, url)) continue
            out += wrapper(script, parsed.name)
        }
        return out
    }

    fun parse(code: String): Parsed {
        val name = grab(code, """@name\s+(\S+)""").firstOrNull() ?: "Userscript"
        val runAtStart = grab(code, """@run-at\s+(\S+)""").any { it == "document-start" }
        return Parsed(
            name = name,
            runAtStart = runAtStart,
            matches = grab(code, """@match\s+(\S+)"""),
            includes = grab(code, """@include\s+(\S+)"""),
            excludes = grab(code, """@exclude\s+(\S+)"""),
        )
    }

    private fun grab(code: String, regex: String): List<String> =
        Regex(regex).findAll(code).map { it.groupValues[1].trim() }.toList()

    private val globCache = HashMap<String, Regex>()

    private fun globRegex(glob: String): Regex {
        globCache[glob]?.let { return it }
        var g = glob.trim()
        val regex = when {
            g.startsWith("/") && g.endsWith("/") && g.length > 2 ->
                runCatching { Regex(g.substring(1, g.length - 1), RegexOption.IGNORE_CASE) }
                    .getOrElse { Regex("^$") }
            else -> {
                val trailingSlashStar = g.endsWith("/*")
                if (trailingSlashStar) g = g.dropLast(2).trimEnd('/')
                val sb = StringBuilder("^")
                var i = 0
                while (i < g.length) {
                    val c = g[i]
                    when {
                        c == '*' && g.startsWith("*://", i) -> { sb.append("[a-z]+://"); i += 4 }
                        c == '*' && i + 1 < g.length && g[i + 1] == '.' -> {
                            sb.append("(?:.*\\.)?"); i += 2
                        }
                        c == '*' -> { sb.append(".*"); i += 1 }
                        c == '?' -> { sb.append('.'); i += 1 }
                        else -> { sb.append(Regex.escape(c.toString())); i += 1 }
                    }
                }
                if (trailingSlashStar) sb.append("(?:/.*)?")
                sb.append("$")
                runCatching { Regex(sb.toString(), RegexOption.IGNORE_CASE) }
                    .getOrElse { Regex("^$") }
            }
        }
        globCache[glob] = regex
        return regex
    }

    private fun urlMatches(parsed: Parsed, url: String): Boolean {
        if (parsed.excludes.any { globRegex(it).matches(url) }) return false
        if (parsed.matches.isNotEmpty()) return parsed.matches.any { globRegex(it).matches(url) }
        if (parsed.includes.isNotEmpty()) return parsed.includes.any { globRegex(it).matches(url) }
        return true
    }

    fun wrapper(script: WebUserscript, displayName: String): String {
        val id = script.id.replace("\\", "\\\\").replace("'", "\\'")
        val name = displayName.replace("\\", "\\\\").replace("'", "\\'")
        val pre = "(function(){try{" +
            "var __usN='$name',__usI='$id';" +
            "function __g(k){try{return NuvioWebBridge.userscriptGet(__usI,k)}catch(e){return null}}" +
            "function __s(k,v){try{NuvioWebBridge.userscriptSet(__usI,k,JSON.stringify(v))}catch(e){}}" +
            "function __d(k){try{NuvioWebBridge.userscriptDelete(__usI,k)}catch(e){}}" +
            "function __l(){try{return NuvioWebBridge.userscriptList(__usI)}catch(e){return ''}}" +
            "function __v(k,d){var x=__g(k);if(x===null||x===undefined)return d;try{return JSON.parse(x)}catch(e){return x}}" +
            "var GM={getValue:__v,setValue:__s,deleteValue:__d," +
            "listValues:function(){return __l().split('\\n').filter(function(x){return x.length>0})}," +
            "addStyle:function(c){var st=document.createElement('style');st.textContent=c;" +
            "(document.head||document.documentElement).appendChild(st);return st}," +
            "addElement:function(t,a){var el=document.createElement(t);for(var k in a||{}){el.setAttribute(k,a[k])}" +
            "(document.head||document.documentElement).appendChild(el);return el}," +
            "setClipboard:function(t){try{navigator.clipboard.writeText(t)}catch(e){}}," +
            "info:{script:{name:__usN,id:__usI},version:undefined,scriptHandler:'Nuvio'}};" +
            "var GM_getValue=GM.getValue,GM_setValue=GM.setValue,GM_deleteValue=GM.deleteValue," +
            "GM_listValues=GM.listValues,GM_addStyle=GM.addStyle,GM_addElement=GM.addElement," +
            "GM_setClipboard=GM.setClipboard,GM_info=GM.info;" +
            "var unsafeWindow=window;"
        val post = "}catch(e){console.error('[Nuvio userscript] '+__usN,e)}})();"
        return pre + script.code + post
    }
}
