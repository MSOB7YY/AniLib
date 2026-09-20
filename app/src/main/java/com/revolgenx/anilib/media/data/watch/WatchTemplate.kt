package com.revolgenx.anilib.media.data.watch

import android.net.Uri
import java.net.URLEncoder

enum class WatchEncode { QUERY, PATH, NONE }

/**
 * Tiny template engine behind every watch action, so a new site is a preset and never new code.
 *
 * {media.title.romaji}            variable, encoded for the target position
 * {episode|pad2}                  filters: raw, path, lower, upper, slug, pad2, pad3
 * {site.nekobt ?? media.title.romaji ?? "fallback"}   first non blank wins
 * {?episode}...{/}                block, rendered when the variable is not blank
 * {!site.nekobt}...{/}            block, rendered when the variable is blank
 */
object WatchTemplate {

    fun render(template: String, vars: Map<String, String?>, encode: WatchEncode): String =
        render(template, vars, encode, StringBuilder()).toString()

    private fun render(
        template: String,
        vars: Map<String, String?>,
        encode: WatchEncode,
        out: StringBuilder
    ): StringBuilder {
        var index = 0

        while (index < template.length) {
            val open = template.indexOf('{', index)
            if (open < 0) {
                out.append(literal(template.substring(index), encode))
                break
            }

            if (open > index) out.append(literal(template.substring(index, open), encode))

            val close = template.indexOf('}', open)
            if (close < 0) {
                out.append(literal(template.substring(open), encode))
                break
            }

            val token = template.substring(open + 1, close)
            when {
                token == "/" -> index = close + 1

                token.startsWith("?") || token.startsWith("!") -> {
                    val blockEnd = findBlockEnd(template, close + 1)
                    val bodyEnd = if (blockEnd < 0) template.length else blockEnd
                    val present = !vars[token.drop(1).trim()].isNullOrBlank()
                    if (present == token.startsWith("?")) {
                        render(template.substring(close + 1, bodyEnd), vars, encode, out)
                    }
                    index = if (blockEnd < 0) template.length else blockEnd + BLOCK_END.length
                }

                else -> {
                    out.append(evaluate(token, vars, encode))
                    index = close + 1
                }
            }
        }

        return out
    }

    private const val BLOCK_END = "{/}"

    private fun findBlockEnd(template: String, from: Int): Int {
        var depth = 0
        var index = from

        while (index < template.length) {
            val open = template.indexOf('{', index)
            if (open < 0) return -1
            val close = template.indexOf('}', open)
            if (close < 0) return -1

            val token = template.substring(open + 1, close)
            when {
                token.startsWith("?") || token.startsWith("!") -> depth++
                token == "/" -> if (depth == 0) return open else depth--
            }
            index = close + 1
        }

        return -1
    }

    private fun evaluate(
        expression: String,
        vars: Map<String, String?>,
        encode: WatchEncode
    ): String {
        expression.split("??").forEach { rawTerm ->
            val term = rawTerm.trim()
            if (term.isEmpty()) return@forEach

            val parts = term.split("|")
            val name = parts.first().trim()
            var value = if (name.length > 1 && name.startsWith("\"") && name.endsWith("\"")) {
                name.substring(1, name.length - 1)
            } else {
                vars[name]
            }

            if (value.isNullOrBlank()) return@forEach

            var termEncode = encode
            parts.drop(1).forEach { filter ->
                when (filter.trim().lowercase()) {
                    "raw" -> termEncode = WatchEncode.NONE
                    "path" -> termEncode = WatchEncode.PATH
                    "lower" -> value = value!!.lowercase()
                    "upper" -> value = value!!.uppercase()
                    "slug" -> value = slug(value!!)
                    "pad2" -> value = pad(value!!, 2)
                    "pad3" -> value = pad(value!!, 3)
                }
            }

            return encodeValue(value!!, termEncode)
        }

        return ""
    }

    /** Literals are part of the url too, in a query they have to be escaped like any value. */
    private fun literal(text: String, encode: WatchEncode) =
        if (encode == WatchEncode.QUERY) encodeValue(text, encode) else text

    private fun encodeValue(value: String, encode: WatchEncode) = when (encode) {
        WatchEncode.QUERY -> URLEncoder.encode(value, "UTF-8")
        WatchEncode.PATH -> Uri.encode(value, "/:")
        WatchEncode.NONE -> value
    }

    private fun slug(value: String) = value.lowercase()
        .replace(Regex("""[^a-z0-9]+"""), "-")
        .trim('-')

    private fun pad(value: String, length: Int): String {
        val number = value.toIntOrNull() ?: return value
        return number.toString().padStart(length, '0')
    }
}
