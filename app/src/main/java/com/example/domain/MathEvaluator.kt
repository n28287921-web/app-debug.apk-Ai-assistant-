package com.example.domain

import kotlin.math.pow

object MathEvaluator {
    fun evaluate(rawExpression: String): Double {
        val expr = rawExpression.replace("×", "*")
            .replace("÷", "/")
            .replace(",", "")
            .replace(" ", "")
        require(expr.isNotEmpty()) { "Empty expression" }
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < expr.length) expr[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        lateinit var parseExpression: () -> Double

        fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                require(eat(')'.code)) { "Missing closing parenthesis" }
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = expr.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Unexpected character: ${ch.toChar()}")
            }
            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val denom = parseFactor()
                        require(denom != 0.0) { "Division by zero" }
                        x /= denom
                    }
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        parseExpression = {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> break
                }
            }
            x
        }

        nextChar()
        val result = parseExpression()
        require(pos >= expr.length) { "Unexpected character at index $pos" }
        return result
    }
}
