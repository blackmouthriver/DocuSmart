"""Regenera PATHS de WebToolLinks.kt y el intent-filter de App Links del manifiesto.

Fuente de verdad: el mapa ROUTES de WebToolLinks.kt (herramienta -> destino en la app, a mano) y
`src/shared/files/toolPageSlugs.json` de docusmart-web (ruta de cada herramienta en cada idioma).

Uso:  python scripts/generate_web_links.py <ruta a toolPageSlugs.json>
Tras agregar una herramienta a ROUTES: correr esto, copiar toolParity.json de la web a
app/src/test/resources/parity/web-tool-parity.json y correr WebToolLinksTest y ToolParityContractTest.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KT = ROOT / "app/src/main/java/com/docsmart/core/navegation/WebToolLinks.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"


def main(slugs_path: str) -> None:
    slugs = json.loads(Path(slugs_path).read_text(encoding="utf-8"))
    kt = KT.read_text(encoding="utf-8")
    routes_block = kt.split("val ROUTES")[1].split("val PATHS")[0]
    tools = re.findall(r'^\s+"([a-z0-9-]+)" to ', routes_block, re.M)
    unknown = [t for t in tools if t not in slugs]
    if unknown:
        sys.exit(f"ROUTES tiene herramientas que la web no tiene: {unknown}")

    paths: list[tuple[str, str]] = []
    for tool in tools:
        paths.append((f"/{tool}", tool))
        for lang in sorted(slugs[tool]):
            paths.append((f"/{lang}/{slugs[tool][lang]}", tool))

    head, rest = kt.split("    val PATHS: Map<String, String> =\n        mapOf(\n")
    tail = rest[rest.index("\n        )\n}") :]
    body = "".join(f'            "{p}" to "{t}",\n' for p, t in paths).rstrip("\n")
    KT.write_text(head + "    val PATHS: Map<String, String> =\n        mapOf(\n" + body + tail, encoding="utf-8", newline="\n")

    manifest = MANIFEST.read_text(encoding="utf-8")
    start = manifest.index('<data android:host="docusmart.online" />')
    start = manifest.index("\n", start) + 1
    end = manifest.index("            </intent-filter>", start)
    lines = "".join(f'                <data android:path="{p}" />\n' for p, _ in paths)
    MANIFEST.write_text(manifest[:start] + lines + manifest[end:], encoding="utf-8", newline="\n")
    print(f"{len(tools)} herramientas, {len(paths)} rutas")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
