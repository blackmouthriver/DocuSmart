#!/usr/bin/env python3
"""Falla si las pruebas instrumentadas de un shard tienen fallas que no están en la lista de conocidas.

Por qué existe: `connectedDebugAndroidTest` corre con `ignoreFailures = true` (app/build.gradle.kts) para que
JaCoCo/SonarCloud sigan generando cobertura aunque haya pruebas rojas. Efecto colateral: el CI salía verde con
fallas reales (el 2026-10-08 había 12 de 780 ocultas). Este paso las vuelve visibles sin tocar ese flujo.

Uso: check_instrumented_results.py <directorio-de-resultados> <lista-de-fallas-conocidas>

Lee los TEST-*.xml (JUnit) que deja AGP. Sale con 1 si hay fallas que no estén en la lista, o si no hay
resultados que verificar (sin ellos no se puede afirmar que pasaron). Salida 0 en el resto de los casos.
"""
import os
import sys
import xml.etree.ElementTree as ET


def load_known(path):
    """Entradas `Clase.metodo` (o con paquete completo); `#` abre un comentario."""
    known = set()
    if not os.path.exists(path):
        return known
    with open(path, encoding="utf-8") as handle:
        for raw in handle:
            line = raw.split("#", 1)[0].strip()
            if line:
                known.add(line)
    return known


def collect(results_dir):
    """Devuelve (total, omitidas, fallidas[nombre_completo]) recorriendo todos los XML bajo results_dir."""
    total = skipped = 0
    failed = []
    for root_dir, _, files in os.walk(results_dir):
        for name in files:
            if not name.endswith(".xml"):
                continue
            try:
                tree = ET.parse(os.path.join(root_dir, name))
            except ET.ParseError:
                continue
            for case in tree.getroot().iter("testcase"):
                total += 1
                if case.find("skipped") is not None:
                    skipped += 1
                elif case.find("failure") is not None or case.find("error") is not None:
                    failed.append(f"{case.get('classname', '')}.{case.get('name', '')}")
    return total, skipped, failed


def is_known(full_name, known):
    simple = ".".join(full_name.split(".")[-2:])  # Clase.metodo
    return full_name in known or simple in known


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 2
    results_dir, known_path = argv[1], argv[2]
    total, skipped, failed = collect(results_dir)
    known = load_known(known_path)
    new = sorted({f for f in failed if not is_known(f, known)})
    tolerated = sorted({f for f in failed if is_known(f, known)})

    lines = [f"Pruebas instrumentadas: {total} ejecutadas, {skipped} omitidas, {len(failed)} fallidas."]
    if tolerated:
        lines.append(f"Fallas conocidas toleradas ({len(tolerated)}): " + ", ".join(tolerated))
    if total == 0:
        lines.append(f"ERROR: no se encontraron resultados bajo '{results_dir}'; no se puede verificar.")
    for f in new:
        lines.append(f"FALLA NUEVA: {f}")
    report = "\n".join(lines)
    print(report)

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write("### Pruebas instrumentadas (este shard)\n\n```\n" + report + "\n```\n")

    return 1 if (new or total == 0) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
