#!/usr/bin/env python3
"""Falla si las pruebas unitarias ejecutadas son MENOS de un mínimo.

Por qué existe: al subir mockk 1.13 -> 1.14 el motor `junit-jupiter-engine` dejó de llegar como dependencia
transitiva y `testDebugUnitTest` siguió terminando en verde, pero descubriendo solo los 2 specs de Kotest
(17 pruebas) en vez de ~1550. Un descubrimiento que se desploma no falla ninguna prueba, así que ningún
chequeo normal lo ve. Este script lo vuelve un fallo visible.

Uso: check_unit_test_count.py <directorio con TEST-*.xml> <archivo con el mínimo>
El archivo de mínimo tiene un entero en su primera línea no vacía ni comentada (`#`).
Salida: 0 si hay al menos el mínimo; 1 si no; 2 si no hay resultados o el mínimo es inválido.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def read_minimum(path: Path) -> int:
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            return int(line)
    raise ValueError(f"{path}: no hay ningún mínimo (entero) definido")


def count_tests(results_dir: Path) -> tuple[int, int]:
    """Devuelve (pruebas, archivos de resultados)."""
    total = files = 0
    for xml_file in sorted(results_dir.glob("TEST-*.xml")):
        suite = ET.parse(xml_file).getroot()
        total += int(suite.attrib.get("tests", "0"))
        files += 1
    return total, files


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__)
        return 2
    results_dir, minimum_file = Path(argv[1]), Path(argv[2])
    try:
        minimum = read_minimum(minimum_file)
    except (OSError, ValueError) as error:
        print(f"ERROR: {error}")
        return 2
    total, files = count_tests(results_dir)
    if files == 0:
        print(f"ERROR: no se encontraron resultados TEST-*.xml bajo '{results_dir}'; no se puede verificar.")
        return 2
    print(f"Pruebas unitarias ejecutadas: {total} en {files} clases (mínimo exigido: {minimum}).")
    if total < minimum:
        print(
            f"FALLA: se ejecutaron {total} pruebas unitarias y se exigen al menos {minimum}. "
            "Si bajó de golpe, revisa que el motor de JUnit (junit-jupiter-engine) y el de Kotest sigan "
            "en el classpath de pruebas. Si se eliminaron pruebas a propósito, baja el mínimo en "
            f"{minimum_file}."
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
