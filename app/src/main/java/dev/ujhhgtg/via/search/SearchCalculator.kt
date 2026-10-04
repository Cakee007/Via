package dev.ujhhgtg.via.search

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.*

/** Address-field arithmetic from tb/b. No scripting engine or network evaluation. */
object SearchCalculator {
    fun evaluate(text: String): String? {
        if (text.isBlank() || text.length > 256) return null
        return runCatching {
            val parser = Parser(text.lowercase(Locale.ROOT))
            val value = parser.expression()
            parser.skipSpace()
            if (parser.index != text.length || !parser.usedOperation || !value.isFinite()) return null
            BigDecimal(value, MathContext(14, RoundingMode.HALF_UP)).stripTrailingZeros().let { if (it.compareTo(BigDecimal.ZERO) == 0) "0" else it.toPlainString() }
        }.getOrNull()
    }
    private class Parser(val text: String) {
        var index = 0
        var usedOperation = false
        fun skipSpace() { while (index < text.length && text[index].isWhitespace()) index++ }
        fun accept(c: Char): Boolean { skipSpace(); if (index < text.length && text[index] == c) { index++; return true }; return false }
        fun expression(): Double {
            var result = term()
            while (true) result = when { accept('+') -> { usedOperation = true; result + term() }; accept('-') -> { usedOperation = true; result - term() }; else -> return result }
        }
        fun term(): Double {
            var result = signed()
            while (true) {
                when {
                    accept('*') || accept('×') -> { usedOperation = true; result *= signed() }
                    accept('/') || accept('÷') -> { usedOperation = true; val divisor = signed(); require(divisor != 0.0); result /= divisor }
                    else -> {
                        skipSpace()
                        if (index == text.length || !(text[index] == '(' || text[index] == '.' || text[index].isDigit() || text[index].isLetter())) return result
                        require(text[index] != '.' && !text[index].isDigit())
                        usedOperation = true; result *= signed()
                    }
                }
            }
        }
        fun signed(): Double = when { accept('+') -> signed(); accept('-') -> { usedOperation = true; -signed() }; else -> power() }
        fun power(): Double {
            var value = primary()
            if (accept('^')) { usedOperation = true; value = value.pow(signed()) }
            while (accept('%')) { usedOperation = true; value /= 100 }
            return value
        }
        fun primary(): Double {
            skipSpace()
            if (accept('(')) { val value = expression(); require(accept(')')); return value }
            if (index < text.length && (text[index].isDigit() || text[index] == '.')) {
                val start = index; var decimal = false
                while (index < text.length) {
                    val char = text[index]
                    if (char.isDigit()) index++ else if (char == '.' && !decimal) { decimal = true; index++ } else break
                }
                return text.substring(start, index).toDouble()
            }
            val start = index
            while (index < text.length && text[index].isLetter()) index++
            val name = text.substring(start, index)
            if (name == "pi") return PI
            if (name == "e") return E
            require(name.isNotEmpty() && accept('('))
            val value = expression(); require(accept(')')); usedOperation = true
            return when (name) {
                "ln" -> { require(value > 0); ln(value) }
                "log" -> { require(value > 0); log10(value) }
                "sqrt" -> { require(value >= 0); sqrt(value) }
                "abs" -> abs(value); "sin" -> sin(value); "cos" -> cos(value); "tan" -> tan(value)
                else -> error("Unknown function")
            }
        }
    }
}
