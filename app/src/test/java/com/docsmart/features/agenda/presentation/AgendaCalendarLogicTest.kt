package com.docsmart.features.agenda.presentation

import com.docsmart.core.data.db.AgendaEventEntity
import com.docsmart.features.agenda.presentation.components.datePickerInitialMillis
import com.docsmart.features.agenda.presentation.components.eventDates
import com.docsmart.features.agenda.presentation.components.eventsForDate
import com.docsmart.features.agenda.presentation.components.isReminderTriggerPast
import com.docsmart.features.agenda.presentation.components.mergePickedDate
import com.docsmart.features.agenda.presentation.components.mergePickedTime
import com.docsmart.features.agenda.presentation.components.millisUntilNextMidnight
import com.docsmart.features.agenda.presentation.components.monthGridCells
import com.docsmart.features.agenda.presentation.components.toAgendaEpochMillis
import com.docsmart.features.agenda.presentation.components.toAgendaLocalDateTime
import com.docsmart.features.agenda.presentation.components.weekdayForColumn
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class AgendaCalendarLogicTest {
    private val utc = ZoneId.of("UTC")

    private fun event(
        id: String,
        dateTime: LocalDateTime,
    ) = AgendaEventEntity(
        id = id,
        title = id,
        dateTimeMillis = dateTime.atZone(utc).toInstant().toEpochMilli(),
        createdAt = 0L,
    )

    @Test
    fun `weekdayForColumn arranca en el primer dia de la semana y da la vuelta`() {
        weekdayForColumn(DayOfWeek.MONDAY, 0) shouldBe DayOfWeek.MONDAY
        weekdayForColumn(DayOfWeek.MONDAY, 6) shouldBe DayOfWeek.SUNDAY
        weekdayForColumn(DayOfWeek.SUNDAY, 0) shouldBe DayOfWeek.SUNDAY
        weekdayForColumn(DayOfWeek.SUNDAY, 1) shouldBe DayOfWeek.MONDAY
        weekdayForColumn(DayOfWeek.WEDNESDAY, 5) shouldBe DayOfWeek.MONDAY
    }

    @Test
    fun `monthGridCells septiembre 2026 empieza en martes con semana en lunes`() {
        // 1 sep 2026 es martes -> 1 hueco; 30 dias -> 31 celdas -> 5 filas = 35.
        val cells = monthGridCells(YearMonth.of(2026, 9), DayOfWeek.MONDAY)

        cells.size shouldBe 35
        cells[0] shouldBe null
        cells[1] shouldBe LocalDate.of(2026, 9, 1)
        cells[30] shouldBe LocalDate.of(2026, 9, 30)
        cells[31] shouldBe null
    }

    @Test
    fun `monthGridCells con semana en domingo agrega un hueco mas`() {
        val cells = monthGridCells(YearMonth.of(2026, 9), DayOfWeek.SUNDAY)

        cells[0] shouldBe null
        cells[1] shouldBe null
        cells[2] shouldBe LocalDate.of(2026, 9, 1)
        (cells.size % 7) shouldBe 0
    }

    @Test
    fun `monthGridCells febrero de 28 dias que empieza en lunes ocupa exactamente 4 filas`() {
        // 1 feb 2027 es lunes.
        val cells = monthGridCells(YearMonth.of(2027, 2), DayOfWeek.MONDAY)

        cells.size shouldBe 28
        cells.none { it == null } shouldBe true
    }

    @Test
    fun `monthGridCells cuenta todos los dias del mes exactamente una vez`() {
        val cells = monthGridCells(YearMonth.of(2026, 8), DayOfWeek.MONDAY)

        cells.filterNotNull() shouldBe (1..31).map { LocalDate.of(2026, 8, it) }
        (cells.size % 7) shouldBe 0
    }

    @Test
    fun `eventDates agrupa por dia calendario sin duplicar`() {
        val events =
            listOf(
                event("a", LocalDateTime.of(2026, 9, 15, 9, 0)),
                event("b", LocalDateTime.of(2026, 9, 15, 18, 0)),
                event("c", LocalDateTime.of(2026, 9, 16, 8, 0)),
            )

        eventDates(events, utc) shouldBe setOf(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16))
    }

    @Test
    fun `eventsForDate filtra el dia y ordena por hora`() {
        val late = event("tarde", LocalDateTime.of(2026, 9, 15, 18, 0))
        val early = event("temprano", LocalDateTime.of(2026, 9, 15, 7, 0))
        val other = event("otro", LocalDateTime.of(2026, 9, 16, 7, 0))

        val result = eventsForDate(listOf(late, other, early), LocalDate.of(2026, 9, 15), utc)

        result.map { it.id } shouldBe listOf("temprano", "tarde")
    }

    @Test
    fun `eventsForDate sin eventos devuelve vacio`() {
        eventsForDate(emptyList(), LocalDate.of(2026, 9, 15), utc) shouldBe emptyList()
    }

    @Test
    fun `millisUntilNextMidnight calcula hasta el inicio del dia siguiente`() {
        val now = LocalDateTime.of(2026, 9, 19, 23, 0)

        millisUntilNextMidnight(now, LocalDate.of(2026, 9, 19)) shouldBe 3_600_000L
    }

    @Test
    fun `millisUntilNextMidnight nunca baja de un segundo`() {
        // Reloj ya pasado la medianoche del "today" cacheado: la duracion seria negativa.
        val now = LocalDateTime.of(2026, 9, 21, 12, 0)

        millisUntilNextMidnight(now, LocalDate.of(2026, 9, 19)) shouldBe 1000L
    }

    @Test
    fun `isReminderTriggerPast sin recordatorio nunca avisa`() {
        isReminderTriggerPast(1_000_000L, null, 9_999_999_999L) shouldBe false
    }

    @Test
    fun `isReminderTriggerPast detecta disparo ya pasado`() {
        val now = 10_000_000L
        val eventIn3Hours = now + 3 * 60 * 60_000L

        isReminderTriggerPast(eventIn3Hours, 1_440, now) shouldBe true
        isReminderTriggerPast(eventIn3Hours, 60, now) shouldBe false
    }

    @Test
    fun `isReminderTriggerPast trata el instante exacto como pasado`() {
        isReminderTriggerPast(10_000_000L, 0, 10_000_000L) shouldBe true
        isReminderTriggerPast(10_000_001L, 0, 10_000_000L) shouldBe false
    }

    @Test
    fun `conversion millis a LocalDateTime y de vuelta es identidad`() {
        val millis = LocalDateTime.of(2026, 9, 19, 14, 30).atZone(utc).toInstant().toEpochMilli()

        val local = millis.toAgendaLocalDateTime(utc)

        local shouldBe LocalDateTime.of(2026, 9, 19, 14, 30)
        local.toAgendaEpochMillis(utc) shouldBe millis
    }

    @Test
    fun `datePickerInitialMillis es medianoche UTC del dia`() {
        val value = LocalDateTime.of(2026, 9, 19, 23, 45)

        datePickerInitialMillis(value) shouldBe
            LocalDate.of(2026, 9, 19).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    @Test
    fun `mergePickedDate cambia el dia y conserva la hora`() {
        val value = LocalDateTime.of(2026, 9, 19, 14, 30)
        val picked = datePickerInitialMillis(LocalDateTime.of(2026, 10, 2, 0, 0))

        mergePickedDate(value, picked) shouldBe LocalDateTime.of(2026, 10, 2, 14, 30)
    }

    @Test
    fun `mergePickedTime cambia la hora y conserva el dia`() {
        val value = LocalDateTime.of(2026, 9, 19, 14, 30)

        mergePickedTime(value, 8, 5) shouldBe LocalDateTime.of(2026, 9, 19, 8, 5)
    }
}
