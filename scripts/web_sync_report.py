#!/usr/bin/env python3
"""Informe de sincronización con la web (repo `docusmart-web`).

La web se trabaja en otro chat y la app aquí, en paralelo. Antes de cada sesión de la app se corre esto
para ver qué se implementó en la web desde la última sincronización:

  python -X utf8 scripts/web_sync_report.py             # solo informa
  python -X utf8 scripts/web_sync_report.py --update    # informa y guarda el estado como nueva base

Qué compara (todo de la rama `preproduccion` de GitHub, el repo es público; no usa credenciales):
  - commits nuevos de la web desde la última base,
  - herramientas nuevas o quitadas del catálogo (`src/features/tools/core/catalog.ts`),
  - cambios del contrato `toolParity.json`,
  - filas del backlog (`docs/BACKLOG.md`) que pasaron a hechas o que son nuevas,
  - herramientas del catálogo que no están clasificadas en `docs/requirements/herramientas-matriz.json`
    (hay que decidir qué hace la app con ellas).
"""
import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO = "blackmouthriver/docusmart-web"
ROOT = Path(__file__).resolve().parent.parent
STATE = ROOT / "docs/requirements/web-sync-state.json"
MATRIX = ROOT / "docs/requirements/herramientas-matriz.json"
HELPER_PREFIX = {"pdf": "pdf/", "conv": "convertir/", "img": "imagen/", "media": "multimedia/", "text": "texto/"}


def fetch(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "docsmart-web-sync"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.read().decode("utf-8")
    except urllib.error.URLError as error:
        sys.exit(f"No se pudo leer {url}: {error}")


def raw(ref: str, path: str) -> str:
    return fetch(f"https://raw.githubusercontent.com/{REPO}/{ref}/{path}")


def api(path: str):
    return json.loads(fetch(f"https://api.github.com/repos/{REPO}/{path}"))


def catalog_keys(text: str) -> list[str]:
    start = text.index("const ENTRIES")
    end = text.index("const BATCH_KEYS") if "const BATCH_KEYS" in text else len(text)
    body = text[start:end]
    keys = [HELPER_PREFIX[kind] + id_ for kind, id_ in re.findall(r"^\s+(pdf|conv|img|media|text)\('([a-z0-9-]+)'", body, re.M)]
    keys += re.findall(r"\{ key: '([a-z0-9/-]+)'", body)
    return keys


def backlog_rows(text: str) -> dict[str, dict[str, str]]:
    rows: dict[str, dict[str, str]] = {}
    for line in text.splitlines():
        match = re.match(r"^\| (W-[\d.]+) \| (.+?) \| (.+?) \| (.+) \|\s*$", line)
        if match:
            wid, title, _who, state = match.groups()
            done = bool(re.match(r"^(✅|Hecho)", state.strip()))
            rows[wid] = {"titulo": re.sub(r"\*\*", "", title)[:110], "hecho": done}
    return rows


def snapshot(ref: str) -> dict:
    head = api(f"commits/{ref}")
    sha = head["sha"]
    parity = json.loads(raw(sha, "src/shared/files/toolParity.json"))["herramientas"]
    return {
        "ref": ref,
        "sha": sha,
        "fecha": head["commit"]["committer"]["date"],
        "herramientas": sorted(catalog_keys(raw(sha, "src/features/tools/core/catalog.ts"))),
        "contrato": {slug: v["app"] for slug, v in parity.items()},
        "backlog": backlog_rows(raw(sha, "docs/BACKLOG.md")),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ref", default="preproduccion", help="rama de la web a leer (por defecto preproduccion)")
    parser.add_argument("--update", action="store_true", help="guarda el estado actual como nueva base")
    args = parser.parse_args()

    now = snapshot(args.ref)
    old = json.loads(STATE.read_text(encoding="utf-8")) if STATE.exists() else None
    classified = {row["key"] for row in json.loads(MATRIX.read_text(encoding="utf-8"))} if MATRIX.exists() else set()

    print(f"# Sincronización con la web ({args.ref})\n")
    print(f"- Web ahora: `{now['sha'][:8]}` ({now['fecha'][:10]}); {len(now['herramientas'])} herramientas en el catálogo.")
    if old is None:
        print("- No hay base anterior: la primera corrida con `--update` la crea.")
    else:
        print(f"- Última base: `{old['sha'][:8]}` ({old['fecha'][:10]}).")
        if old["sha"] != now["sha"]:
            compare = api(f"compare/{old['sha']}...{now['sha']}")
            commits = [c["commit"]["message"].splitlines()[0] for c in compare.get("commits", [])]
            print(f"\n## Commits nuevos en la web: {compare.get('total_commits', len(commits))}\n")
            for message in commits[:40]:
                print(f"- {message[:120]}")
            if len(commits) > 40:
                print(f"- … y {len(commits) - 40} más")
        else:
            print("\nSin commits nuevos desde la última base.")

        added = sorted(set(now["herramientas"]) - set(old["herramientas"]))
        removed = sorted(set(old["herramientas"]) - set(now["herramientas"]))
        print("\n## Catálogo de herramientas\n")
        print(f"- Nuevas en la web: {', '.join(added) or 'ninguna'}")
        print(f"- Quitadas: {', '.join(removed) or 'ninguna'}")

        changed = {k: (old["contrato"].get(k), v) for k, v in now["contrato"].items() if old["contrato"].get(k) != v}
        print("\n## Contrato de paridad (`toolParity.json`)\n")
        print("- Cambios: " + ("; ".join(f"{k}: {a} → {b}" for k, (a, b) in sorted(changed.items())) or "ninguno"))

        done = [(w, r["titulo"]) for w, r in now["backlog"].items() if r["hecho"] and not old["backlog"].get(w, {}).get("hecho")]
        new_rows = [(w, r["titulo"]) for w, r in now["backlog"].items() if w not in old["backlog"]]
        print("\n## Backlog de la web\n")
        print(f"- Pasaron a hechas ({len(done)}):")
        for wid, title in done[:30]:
            print(f"  - {wid} {title}")
        print(f"- Filas nuevas ({len(new_rows)}):")
        for wid, title in new_rows[:30]:
            print(f"  - {wid} {title}")

    unclassified = sorted(set(now["herramientas"]) - classified)
    print("\n## Herramientas del catálogo sin clasificar en la matriz de la app\n")
    print("- " + (", ".join(unclassified) if unclassified else "ninguna (la matriz cubre todo el catálogo)"))
    gone = sorted(classified - set(now["herramientas"]))
    if gone:
        print(f"- En la matriz pero ya no en el catálogo: {', '.join(gone)}")

    if args.update:
        STATE.write_text(json.dumps(now, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
        print(f"\nBase actualizada: `{STATE.relative_to(ROOT)}`")


if __name__ == "__main__":
    main()
