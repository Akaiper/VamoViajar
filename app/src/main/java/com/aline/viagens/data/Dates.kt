package com.aline.viagens.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Dates {
    private val ptBR = Locale("pt", "BR")
    private val dayMonth = DateTimeFormatter.ofPattern("d 'de' MMM", ptBR)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", ptBR)

    fun parse(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

    /** Converte a data para o formato que o seletor de datas do Material usa (meia-noite UTC). */
    fun toPickerMillis(s: String): Long? =
        parse(s)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

    fun fromPickerMillis(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()

    fun range(start: String, end: String): String {
        val a = parse(start) ?: return "Sem datas"
        val b = parse(end) ?: return a.format(dayMonthYear)
        return when {
            a == b -> a.format(dayMonthYear)
            a.year == b.year -> "${a.format(dayMonth)} – ${b.format(dayMonthYear)}"
            else -> "${a.format(dayMonthYear)} – ${b.format(dayMonthYear)}"
        }
    }

    fun status(start: String, end: String, today: LocalDate = LocalDate.now()): String {
        val a = parse(start) ?: return ""
        val b = parse(end) ?: a
        return when {
            today.isBefore(a) -> {
                val days = ChronoUnit.DAYS.between(today, a)
                if (days == 1L) "Falta 1 dia" else "Faltam $days dias"
            }
            today.isAfter(b) -> "Já aconteceu"
            else -> "Acontecendo agora"
        }
    }

    fun isPast(end: String, today: LocalDate = LocalDate.now()): Boolean =
        parse(end)?.isBefore(today) ?: false

    fun nights(start: String, end: String): Long? {
        val a = parse(start) ?: return null
        val b = parse(end) ?: return null
        return ChronoUnit.DAYS.between(a, b)
    }
}
