"""Pruebas de check_instrumented_results.py: `python3 scripts/test_check_instrumented_results.py`."""
import os
import tempfile
import unittest

import check_instrumented_results as guard

PASS = '<testcase classname="com.docsmart.features.A.FooTest" name="ok"/>'


def case(cls, name, kind):
    return f'<testcase classname="com.docsmart.features.{cls}" name="{name}"><{kind} message="x"/></testcase>'


class GuardTest(unittest.TestCase):
    def run_guard(self, shards, known=""):
        with tempfile.TemporaryDirectory() as tmp:
            res = os.path.join(tmp, "androidTest-results", "connected", "debug")
            os.makedirs(res)
            for i, cases in enumerate(shards):
                with open(os.path.join(res, f"TEST-s{i}.xml"), "w", encoding="utf-8") as f:
                    f.write(f"<testsuite>{''.join(cases)}</testsuite>")
            known_path = os.path.join(tmp, "known.txt")
            with open(known_path, "w", encoding="utf-8") as f:
                f.write(known)
            return guard.main(["x", os.path.join(tmp, "androidTest-results"), known_path])

    def test_todo_verde_pasa(self):
        self.assertEqual(0, self.run_guard([[PASS, PASS]]))

    def test_falla_nueva_falla(self):
        self.assertEqual(1, self.run_guard([[PASS, case("b.BarTest", "roto", "failure")]]))

    def test_error_tambien_cuenta(self):
        self.assertEqual(1, self.run_guard([[case("b.BarTest", "explota", "error")]]))

    def test_falla_conocida_se_tolera(self):
        self.assertEqual(0, self.run_guard([[case("b.BarTest", "roto", "failure")]], known="BarTest.roto  # motivo\n"))

    def test_conocida_no_tapa_otra_nueva(self):
        shard = [case("b.BarTest", "roto", "failure"), case("b.BarTest", "otro", "failure")]
        self.assertEqual(1, self.run_guard([shard], known="BarTest.roto\n"))

    def test_omitidas_no_son_fallas(self):
        self.assertEqual(0, self.run_guard([[PASS, case("b.BarTest", "ign", "skipped")]]))

    def test_sin_resultados_falla(self):
        self.assertEqual(1, self.run_guard([[]]))

    def test_suma_varios_xml(self):
        self.assertEqual(1, self.run_guard([[PASS], [case("c.BazTest", "roto", "failure")]]))

    def test_comentarios_y_lineas_vacias_en_la_lista(self):
        self.assertEqual({"BarTest.roto"}, guard.load_known(self._lista("# solo comentario\n\nBarTest.roto # y motivo\n")))

    def _lista(self, text):
        fd, path = tempfile.mkstemp(text=True)
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(text)
        self.addCleanup(os.remove, path)
        return path


if __name__ == "__main__":
    unittest.main()
