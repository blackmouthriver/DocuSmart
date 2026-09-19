package com.docsmart.features.study.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Kotest (pedido explícito del usuario, 2026-09-19, ampliación del gauntlet):
 * `tickPomodoro()` ya tiene buena cobertura de ejemplo puntual en
 * `PomodoroEngineTest.kt` (JUnit5) para las transiciones concretas -- acá se
 * complementa con propiedades que deben cumplirse para CUALQUIER estado de
 * partida válido, no solo los casos fijos ya cubiertos. Es la misma función
 * pura ya extraída en la ronda 14 (sin `Context`, sin efectos secundarios),
 * ideal para `checkAll` porque no depende de nada externo.
 */
class PomodoroEngineKotestSpec :
    FunSpec({

        // Genera solo estados "válidos" (los que tickPomodoro() puede recibir
        // en la práctica real: minutos/segundos nunca negativos, segundos
        // siempre < 60 salvo el caso 0 que gatilla el préstamo del minuto).
        val validState: Arb<PomodoroState> =
            arbitrary {
                PomodoroState(
                    minutes = Arb.int(0..60).bind(),
                    seconds = Arb.int(0..59).bind(),
                    isRunning = Arb.boolean().bind(),
                    isBreak = Arb.boolean().bind(),
                    pomodoroCount = Arb.int(0..50).bind(),
                )
            }

        test("los segundos del resultado siempre están en 0..59") {
            checkAll(validState) { state ->
                val result = tickPomodoro(state)
                (result.seconds >= 0) shouldBe true
                (result.seconds <= 59) shouldBe true
            }
        }

        test("los minutos del resultado nunca son negativos") {
            checkAll(validState) { state ->
                tickPomodoro(state).minutes shouldNotBe -1
                (tickPomodoro(state).minutes >= 0) shouldBe true
            }
        }

        test("pomodoroCount nunca decrece en un tick") {
            checkAll(validState) { state ->
                (tickPomodoro(state).pomodoroCount >= state.pomodoroCount) shouldBe true
            }
        }

        test("pomodoroCount solo aumenta cuando el tick cierra un bloque de ESTUDIO") {
            checkAll(validState) { state ->
                val result = tickPomodoro(state)
                val closedStudyBlock = state.seconds == 0 && state.minutes == 0 && !state.isBreak
                if (closedStudyBlock) {
                    result.pomodoroCount shouldBe state.pomodoroCount + 1
                } else {
                    result.pomodoroCount shouldBe state.pomodoroCount
                }
            }
        }

        test("al cerrar un bloque de estudio, el descanso es largo si y solo si el nuevo conteo es múltiplo de 4") {
            checkAll(validState) { state ->
                val closedStudyBlock = state.seconds == 0 && state.minutes == 0 && !state.isBreak
                if (closedStudyBlock) {
                    val result = tickPomodoro(state)
                    val newCount = state.pomodoroCount + 1
                    val expectedMinutes =
                        if (newCount % POMODORO_LONG_BREAK_INTERVAL == 0) {
                            POMODORO_LONG_BREAK_MINUTES
                        } else {
                            POMODORO_BREAK_MINUTES
                        }
                    result.minutes shouldBe expectedMinutes
                    result.isBreak shouldBe true
                    result.isRunning shouldBe false
                }
            }
        }

        test("al cerrar un bloque de descanso, siempre vuelve a estudio con la duración estándar") {
            checkAll(validState) { state ->
                val closedBreakBlock = state.seconds == 0 && state.minutes == 0 && state.isBreak
                if (closedBreakBlock) {
                    val result = tickPomodoro(state)
                    result.isBreak shouldBe false
                    result.isRunning shouldBe false
                    result.minutes shouldBe POMODORO_STUDY_MINUTES
                    result.seconds shouldBe 0
                }
            }
        }

        test("un tick que no cierra ningún bloque nunca cambia isBreak") {
            checkAll(validState) { state ->
                val closesABlock = state.seconds == 0 && state.minutes == 0
                if (!closesABlock) {
                    tickPomodoro(state).isBreak shouldBe state.isBreak
                }
            }
        }
    })
