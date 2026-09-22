package com.docsmart.features.study.domain

// Verificado a oído real en dispositivo (2026-09-22, feedback directo del
// usuario tras probar el detector de tono): la detección de género por F0
// (VoicePitchEstimator.kt/VoiceGenderProbe.kt) resultó nada confiable para
// el motor de Google TTS -- de las 11 voces "es" instaladas, 6 quedaron mal
// clasificadas (acierto ~45%, peor que adivinar). El motivo real: errores de
// octava en la autocorrelación, en AMBOS sentidos -- algunas voces miden la
// mitad de su tono real (una voz aguda se ve grave) y otras el doble (una
// voz grave se ve aguda) -- no hay una sola corrección direccional que
// arregle los dos casos a la vez sin una técnica de pitch-tracking mucho más
// sofisticada (tipo YIN), fuera de alcance para este arreglo puntual.
//
// Estos códigos técnicos (ej. "es-es-x-eea-local") son ESTÁNDAR del motor de
// Google TTS -- las mismas voces con el mismo código aparecen en cualquier
// Android que tenga el motor instalado, no varían por dispositivo -- así que
// una tabla verificada a mano para estos códigos conocidos es más confiable
// que seguir afinando a ciegas un heurístico de señal que ya demostró
// fallar más de la mitad de las veces. Se usa como fuente de verdad
// PRIORITARIA sobre el detector de tono (que queda como respaldo solo para
// voces fuera de esta lista, ej. otros idiomas u otros motores TTS).
internal val KNOWN_VOICE_GENDERS: Map<String, Boolean> =
    mapOf(
        "es-US-language" to true,
        "es-us-x-sfb-local" to true,
        "es-ES-language" to true,
        "es-es-x-eea-local" to true,
        "es-es-x-eee-local" to true,
        // Corregido 2026-09-22 (segunda ronda de feedback real): esta voz
        // resultó femenina, no masculina como se había marcado en la primera
        // pasada -- confirmado escuchando el personaje "Emilio" (nombre que
        // le tocó en esa corrida) en dispositivo real.
        "es-us-x-esc-local" to true,
        "es-us-x-esd-local" to false,
        "es-us-x-esf-local" to false,
        "es-es-x-eec-local" to false,
        "es-es-x-eed-local" to false,
        "es-es-x-eef-local" to false,
    )
