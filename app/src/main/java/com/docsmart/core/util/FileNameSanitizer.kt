package com.docsmart.core.util

/**
 * Hallazgo real de la revisión general 2026-09-16 (path traversal): el
 * campo "Nombre del archivo" de Herramientas PDF/Convertidor es texto libre
 * que llegaba sin sanear hasta `File(outputDir, "..._$name_$timestamp.ext")`
 * en cada UseCase -- `File(parent, child)` de Java resuelve `child` como
 * ruta relativa, así que un nombre como `../../shared_prefs/docusmart_security`
 * escribía FUERA del directorio de salida esperado, dentro del propio
 * `filesDir` de la app (pudiendo sobrescribir la base Room o el store del
 * PIN). Se sanea en un único punto (antes de que el nombre llegue a
 * cualquier UseCase) en vez de repetirlo en los ~27 sitios que arman una
 * ruta de salida.
 */
fun sanitizeOutputFileName(name: String): String {
    // Se queda solo con el último segmento de cualquier ruta (descarta
    // cualquier `/`, `\`, o `..` que el usuario haya escrito) y además
    // filtra a un alfabeto seguro para nombre de archivo -- letras/dígitos/
    // espacio/guion/guion bajo/punto, igual criterio en los 12 idiomas
    // soportados (acentos y demás Unicode se preservan).
    val lastSegment = name.substringAfterLast('/').substringAfterLast('\\')
    val filtered = lastSegment.filter { it.isLetterOrDigit() || it in " _-." }.trim()

    // Hallazgo real de la revisión de seguridad 2026-09-16: filtrar DESPUÉS
    // de colapsar ".." (orden anterior) permite reconstruir ".." si el
    // filtro elimina justo el carácter que separaba los dos puntos (ej.
    // ".!." -> replace("..","_") no encuentra nada -> filter borra "!" ->
    // queda ".."). Ahora se filtra primero y se colapsa ".." en un bucle
    // hasta que no quede ninguna ocurrencia, un punto fijo real.
    var collapsed = filtered
    while (collapsed.contains("..")) {
        collapsed = collapsed.replace("..", "_")
    }
    // Hallazgo real de la ronda 16: sin tope de largo, un nombre pegado muy
    // largo (los UseCase le suman sufijo y timestamp) superaba los 255 bytes
    // de NAME_MAX del sistema de archivos y la operación fallaba con
    // FileNotFoundException recién al escribir. Truncar un texto que ya no
    // contiene ".." no puede volver a crearlo.
    var truncated = collapsed.take(MAX_OUTPUT_NAME_LENGTH)
    if (truncated.isNotEmpty() && truncated.last().isHighSurrogate()) truncated = truncated.dropLast(1)
    return truncated.trim()
}

internal const val MAX_OUTPUT_NAME_LENGTH = 60
