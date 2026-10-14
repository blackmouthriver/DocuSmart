import tempfile
import unittest
from pathlib import Path

import check_unit_test_count as guard


def write_suite(directory: Path, name: str, tests: int) -> None:
    (directory / f"TEST-{name}.xml").write_text(
        f'<?xml version="1.0"?><testsuite name="{name}" tests="{tests}" skipped="0" failures="0" errors="0"/>',
        encoding="utf-8",
    )


class CheckUnitTestCountTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp())
        self.results = self.tmp / "results"
        self.results.mkdir()
        self.minimum = self.tmp / "min.txt"

    def run_guard(self) -> int:
        return guard.main(["check_unit_test_count.py", str(self.results), str(self.minimum)])

    def test_pasa_cuando_hay_al_menos_el_minimo(self) -> None:
        write_suite(self.results, "A", 600)
        write_suite(self.results, "B", 400)
        self.minimum.write_text("# comentario\n\n1000\n", encoding="utf-8")
        self.assertEqual(0, self.run_guard())

    def test_falla_cuando_el_descubrimiento_se_desploma(self) -> None:
        # El caso real: solo los 2 specs de Kotest (17 pruebas) en vez de ~1550.
        write_suite(self.results, "FileNameSanitizerKotestSpec", 9)
        write_suite(self.results, "PomodoroEngineKotestSpec", 8)
        self.minimum.write_text("1400\n", encoding="utf-8")
        self.assertEqual(1, self.run_guard())

    def test_error_si_no_hay_resultados(self) -> None:
        self.minimum.write_text("1400\n", encoding="utf-8")
        self.assertEqual(2, self.run_guard())

    def test_error_si_el_minimo_no_es_valido(self) -> None:
        write_suite(self.results, "A", 10)
        self.minimum.write_text("# solo comentarios\n", encoding="utf-8")
        self.assertEqual(2, self.run_guard())


if __name__ == "__main__":
    unittest.main()
