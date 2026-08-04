package com.example.voiceadder

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object NumberParser {

    private val UNITS = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "одно" to 1, "два" to 2, "две" to 2,
        "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7,
        "восемь" to 8, "девять" to 9
    )
    private val TEENS = mapOf(
        "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13,
        "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16,
        "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19
    )
    private val TENS = mapOf(
        "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50,
        "шестьдесят" to 60, "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90
    )
    private val HUNDREDS = mapOf(
        "сто" to 100, "двести" to 200, "триста" to 300, "четыреста" to 400,
        "пятьсот" to 500, "шестьсот" to 600, "семьсот" to 700, "восемьсот" to 800,
        "девятьсот" to 900
    )
    private val THOUSAND = setOf("тысяча", "тысячи", "тысяч")

    private val digitRegex = Regex("""-?\d+([.,]\d+)?""")

    /** Пытается достать число из произнесённой фразы: сперва ищет цифры, потом разбирает числительные. */
    fun extract(rawText: String): Double? {
        val text = rawText.trim()
        if (text.isEmpty()) return null

        digitRegex.find(text)?.let { match ->
            return match.value.replace(',', '.').toDoubleOrNull()
        }
        return wordsToNumber(text)
    }

    private fun wordsToNumber(text: String): Double? {
        val words = text.lowercase(Locale("ru"))
            .replace(Regex("[^а-яё\\s-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        var negative = false
        var total = 0
        var chunk = 0
        var found = false

        for (w in words) {
            when {
                w == "минус" -> negative = true
                UNITS.containsKey(w) -> { chunk += UNITS.getValue(w); found = true }
                TEENS.containsKey(w) -> { chunk += TEENS.getValue(w); found = true }
                TENS.containsKey(w) -> { chunk += TENS.getValue(w); found = true }
                HUNDREDS.containsKey(w) -> { chunk += HUNDREDS.getValue(w); found = true }
                THOUSAND.contains(w) -> {
                    chunk = if (chunk == 0) 1 else chunk
                    total += chunk * 1000
                    chunk = 0
                    found = true
                }
                else -> { /* игнорируем служебные слова */ }
            }
        }
        total += chunk
        if (!found) return null
        return if (negative) -total.toDouble() else total.toDouble()
    }

    private val symbols = DecimalFormatSymbols(Locale("ru")).apply {
        groupingSeparator = '\u2009' // узкий неразрывный пробел
        decimalSeparator = ','
    }
    private val format = DecimalFormat("#,##0.##", symbols)

    fun format(value: Double): String = format.format(value)
}
